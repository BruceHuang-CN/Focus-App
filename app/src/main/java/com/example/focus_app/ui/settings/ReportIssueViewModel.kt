package com.example.focus_app.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focus_app.data.diagnostics.DiagnosticSnapshotSource
import com.example.focus_app.data.feedback.FeedbackDraftStore
import com.example.focus_app.data.feedback.PendingFeedback
import com.example.focus_app.data.repository.FeedbackRepository
import com.example.focus_app.data.repository.FeedbackSubmitResult
import com.example.focus_app.domain.feedback.DiagnosticLevel
import com.example.focus_app.domain.feedback.DiagnosticSnapshot
import com.example.focus_app.domain.feedback.IssueReportFormatter
import com.example.focus_app.domain.feedback.IssueReportTexts
import com.example.focus_app.domain.feedback.PreparedSubmission
import com.example.focus_app.domain.feedback.buildAppFeedbackRequest
import com.example.focus_app.domain.feedback.toDiagnosticSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 单个输入框的字符上限。协议与此一致；调整时同步文档与测试。 */
const val ISSUE_REPORT_MAX_FIELD_LENGTH = 2_000

/** 提交失败的用户可理解分类，具体文案由界面按资源映射。 */
sealed interface SubmitError {
    data object Unavailable : SubmitError

    data class Rejected(val code: String) : SubmitError

    data object Conflict : SubmitError

    data class RateLimited(val retryAfterSeconds: Long) : SubmitError
}

data class ReportIssueUiState(
    val problem: String = "",
    val steps: String = "",
    val expected: String = "",
    val stepsExpanded: Boolean = false,
    val diagnosticLevel: DiagnosticLevel = DiagnosticLevel.BASIC,
    val isCollecting: Boolean = false,
    val snapshot: DiagnosticSnapshot? = null,
    val diagnosticsText: String? = null,
    val showProblemError: Boolean = false,
    val showCombinedLengthError: Boolean = false,
    val diagnosticsFailed: Boolean = false,
    val isSubmitting: Boolean = false,
    /** 已冻结、可预览、可复制、可重试的本次投稿；编辑或改变级别后失效。 */
    val prepared: PreparedSubmission? = null,
    /** 本次页面是从本地待发送记录恢复的，界面提示用户手动重试。 */
    val restoredPending: Boolean = false,
    /** 服务端确认保存后的回执编号。 */
    val receipt: String? = null,
    val submitError: SubmitError? = null
) {
    val problemTooLong: Boolean get() = problem.length > ISSUE_REPORT_MAX_FIELD_LENGTH
    val stepsTooLong: Boolean get() = steps.length > ISSUE_REPORT_MAX_FIELD_LENGTH
    val expectedTooLong: Boolean get() = expected.length > ISSUE_REPORT_MAX_FIELD_LENGTH
    val anyTooLong: Boolean get() = problemTooLong || stepsTooLong || expectedTooLong

    val includesDiagnostics: Boolean get() = diagnosticLevel != DiagnosticLevel.NONE

    /** 超长不静默截断，只拒绝提交；采集中或提交中禁止重复操作。 */
    val canSubmit: Boolean get() = !anyTooLong && !isCollecting && !isSubmitting
}

/**
 * 问题反馈表单、诊断快照、冻结投稿与直接上传的唯一状态来源。
 *
 * 诊断只在需要时采集一次；预览、复制、系统分享和实际上传都使用同一份 [PreparedSubmission]。
 * 首次发送前先持久化待发送请求，失败重试或进程恢复沿用同一个 id。
 */
@HiltViewModel
class ReportIssueViewModel @Inject constructor(
    private val snapshotSource: DiagnosticSnapshotSource,
    private val feedbackRepository: FeedbackRepository,
    private val draftStore: FeedbackDraftStore,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(
        ReportIssueUiState(
            problem = savedStateHandle.get<String>(KEY_PROBLEM).orEmpty(),
            steps = savedStateHandle.get<String>(KEY_STEPS).orEmpty(),
            expected = savedStateHandle.get<String>(KEY_EXPECTED).orEmpty(),
            stepsExpanded = savedStateHandle.get<Boolean>(KEY_STEPS_EXPANDED) ?: false,
            diagnosticLevel = savedStateHandle.get<String>(KEY_LEVEL)
                ?.let(DiagnosticLevel::fromWire)
                ?: DiagnosticLevel.BASIC
        )
    )
    val state: StateFlow<ReportIssueUiState> = _state.asStateFlow()

    /** 每次采集（或取消采集）都会自增，用来丢弃已经过期的迟到结果。 */
    private var collectionRevision = 0
    private var formRevision = 0
    /** 清理旧投稿必须先于随后保存的新投稿完成。 */
    private val draftMutex = Mutex()

    init {
        // 必须先读取既有选择；Main.immediate 下的状态保存协程会立刻写入默认值。
        val savedLevel = savedStateHandle.get<String>(KEY_LEVEL)?.let(DiagnosticLevel::fromWire)
        // 保留普通表单草稿，进程回收后重新进入页面不会只剩空白。
        viewModelScope.launch {
            _state.collect { current ->
                savedStateHandle[KEY_PROBLEM] = current.problem
                savedStateHandle[KEY_STEPS] = current.steps
                savedStateHandle[KEY_EXPECTED] = current.expected
                savedStateHandle[KEY_STEPS_EXPANDED] = current.stepsExpanded
                savedStateHandle[KEY_LEVEL] = current.diagnosticLevel.wireValue
            }
        }
        // 进程恢复：迟到的旧投稿不能覆盖用户在当前页面的输入或隐私选择。
        val restoreRevision = formRevision
        viewModelScope.launch {
            val pending = draftMutex.withLock { draftStore.load() } ?: return@launch
            if (formRevision != restoreRevision) return@launch
            val level = DiagnosticLevel.fromWire(pending.payload.diagnosticLevel)
            if (savedLevel != null && savedLevel != level) {
                clearPending()
                return@launch
            }
            _state.update {
                it.copy(
                    problem = pending.payload.problem,
                    steps = pending.formSteps ?: pending.payload.steps,
                    expected = pending.payload.expected,
                    diagnosticLevel = level,
                    snapshot = pending.payload.toDiagnosticSnapshot()?.copy(context = pending.context),
                    prepared = PreparedSubmission(pending.payload, pending.displayBody, level, pending.formSteps, pending.context),
                    restoredPending = true,
                    receipt = null,
                    submitError = null
                )
            }
        }
    }

    fun updateProblem(value: String) {
        updateForm {
            it.copy(problem = value, showProblemError = it.showProblemError && value.isBlank())
        }
    }

    fun updateSteps(value: String) = updateForm { it.copy(steps = value) }

    fun updateExpected(value: String) = updateForm { it.copy(expected = value) }

    fun setStepsExpanded(expanded: Boolean) {
        _state.update { it.copy(stepsExpanded = expanded) }
    }

    /**
     * 改变诊断级别。关闭基础诊断时必须同时关闭详细诊断（界面负责联动）。
     * 任何旧快照、诊断预览和冻结投稿都会立即失效，采集中或迟到的结果也不会重新附加。
     */
    fun setDiagnosticLevel(level: DiagnosticLevel) {
        if (_state.value.isSubmitting || _state.value.diagnosticLevel == level) return
        collectionRevision++
        formRevision++
        // 立即保留选择，避免旧文件读取完成或进程恢复时重新启用诊断。
        savedStateHandle[KEY_LEVEL] = level.wireValue
        _state.update {
            it.copy(
                diagnosticLevel = level,
                showCombinedLengthError = false,
                isCollecting = false,
                diagnosticsFailed = false,
                snapshot = null,
                diagnosticsText = null,
                prepared = null,
                restoredPending = false,
                submitError = null
            )
        }
        viewModelScope.launch { clearPending() }
    }

    private suspend fun clearPending() {
        try {
            draftMutex.withLock { draftStore.clear() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _state.update { it.copy(submitError = SubmitError.Unavailable) }
        }
    }

    /** 查看诊断信息：已有快照时直接复用，不重复采集。 */
    fun showDiagnostics(texts: IssueReportTexts) {
        val current = _state.value
        if (!current.includesDiagnostics) return
        val snapshot = current.snapshot
        if (snapshot != null) {
            _state.update {
                it.copy(diagnosticsText = IssueReportFormatter(texts).formatDiagnostics(snapshot, it.diagnosticLevel))
            }
        } else {
            viewModelScope.launch { ensureSnapshot(texts) }
        }
    }

    /** 主动刷新诊断：旧快照、诊断预览和冻结投稿一起失效。 */
    fun refreshDiagnostics(texts: IssueReportTexts) {
        if (_state.value.isSubmitting || !_state.value.includesDiagnostics) return
        collectionRevision++
        _state.update {
            it.copy(
                snapshot = null,
                diagnosticsText = null,
                prepared = null,
                restoredPending = false,
                diagnosticsFailed = false
            )
        }
        viewModelScope.launch { ensureSnapshot(texts) }
    }

    /** 生成冻结预览；不触发上传，也不重复生成。 */
    fun previewSubmission(texts: IssueReportTexts) {
        val current = _state.value
        if (!current.canSubmit) return
        if (current.problem.isBlank()) {
            _state.update { it.copy(showProblemError = true) }
            return
        }
        if (current.prepared != null) return
        formRevision++
        viewModelScope.launch {
            val snapshot = when (val outcome = ensureSnapshot(texts)) {
                is SnapshotOutcome.Aborted -> return@launch
                is SnapshotOutcome.Ready -> outcome.snapshot
            }
            val fresh = _state.value
            if (fresh.problem.isBlank() || fresh.anyTooLong || fresh.prepared != null) return@launch
            val prepared = buildPrepared(texts, fresh, snapshot) ?: return@launch
            _state.update { it.copy(prepared = prepared, showProblemError = false) }
        }
    }

    /**
     * 提交反馈。已经冻结的投稿（预览过或从本地恢复）直接使用同一份数据；
     * 否则先采集所需诊断、冻结、持久化待发送请求，再上传。
     */
    fun submit(texts: IssueReportTexts) {
        val current = _state.value
        if (!current.canSubmit) return
        if (current.problem.isBlank()) {
            _state.update { it.copy(showProblemError = true) }
            return
        }
        formRevision++
        current.prepared?.let { existing ->
            send(existing)
            return
        }
        viewModelScope.launch {
            val snapshot = when (val outcome = ensureSnapshot(texts)) {
                is SnapshotOutcome.Aborted -> return@launch
                is SnapshotOutcome.Ready -> outcome.snapshot
            }
            val fresh = _state.value
            if (fresh.problem.isBlank() || fresh.anyTooLong || fresh.prepared != null) return@launch
            val prepared = buildPrepared(texts, fresh, snapshot) ?: return@launch
            _state.update { it.copy(prepared = prepared, showProblemError = false) }
            send(prepared)
        }
    }

    /** 成功后“再反馈一个问题”：清空表单并删除本地待发送记录。 */
    fun startNewReport() {
        collectionRevision++
        formRevision++
        _state.value = ReportIssueUiState()
        viewModelScope.launch { clearPending() }
    }

    private fun send(prepared: PreparedSubmission) {
        if (_state.value.isSubmitting) return
        _state.update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            // 先原子保存，再发送：即使响应丢失或进程被杀，也能用同一个 id 重试。
            try {
                draftMutex.withLock {
                    draftStore.save(PendingFeedback(
                        displayBody = prepared.displayBody,
                        payload = prepared.payload,
                        formSteps = prepared.formSteps,
                        context = prepared.context
                    ))
                }
            } catch (cancelled: CancellationException) {
                _state.update { it.copy(isSubmitting = false) }
                throw cancelled
            } catch (_: Exception) {
                // 本地无法保存时不冒险上传，避免响应丢失后无法用同一 id 重试。
                _state.update { it.copy(isSubmitting = false, submitError = SubmitError.Unavailable) }
                return@launch
            }
            val result = try {
                feedbackRepository.submit(prepared.payload)
            } catch (cancelled: CancellationException) {
                _state.update { it.copy(isSubmitting = false) }
                throw cancelled
            }
            when (result) {
                is FeedbackSubmitResult.Saved -> {
                    clearPending()
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            receipt = result.receipt,
                            prepared = null,
                            restoredPending = false,
                            submitError = null
                        )
                    }
                }

                FeedbackSubmitResult.Unavailable ->
                    _state.update { it.copy(isSubmitting = false, submitError = SubmitError.Unavailable) }

                is FeedbackSubmitResult.Rejected ->
                    _state.update { it.copy(isSubmitting = false, submitError = SubmitError.Rejected(result.code)) }

                FeedbackSubmitResult.Conflict ->
                    _state.update { it.copy(isSubmitting = false, submitError = SubmitError.Conflict) }

                is FeedbackSubmitResult.RateLimited ->
                    _state.update { it.copy(isSubmitting = false, submitError = SubmitError.RateLimited(result.retryAfterSeconds)) }
            }
        }
    }

    private fun buildPrepared(
        texts: IssueReportTexts,
        current: ReportIssueUiState,
        snapshot: DiagnosticSnapshot?
    ): PreparedSubmission? {
        val level = current.diagnosticLevel
        val formatter = IssueReportFormatter(texts)
        val supplement = formatter.formatContext(snapshot, level)
        val wireSteps = listOf(current.steps.trim(), supplement).filter { it.isNotBlank() }.joinToString("\n\n")
        if (wireSteps.length > ISSUE_REPORT_MAX_FIELD_LENGTH) {
            _state.update { it.copy(showCombinedLengthError = true, stepsExpanded = true) }
            return null
        }
        val payload = buildAppFeedbackRequest(
            id = UUID.randomUUID().toString(),
            problem = current.problem,
            steps = wireSteps,
            expected = current.expected,
            level = level,
            snapshot = snapshot
        )
        val body = IssueReportFormatter(texts).formatReport(
            problem = current.problem,
            steps = current.steps,
            expected = current.expected,
            snapshot = snapshot,
            level = level
        )
        return PreparedSubmission(
            payload = payload, displayBody = body, level = level,
            formSteps = current.steps,
            context = if (level == DiagnosticLevel.NONE) null else snapshot?.context
        )
    }

    /** 需要时采集一次快照；级别为 none 时返回 Ready(null)，被更晚的操作作废时返回 Aborted。 */
    private suspend fun ensureSnapshot(texts: IssueReportTexts): SnapshotOutcome {
        if (_state.value.diagnosticLevel == DiagnosticLevel.NONE) return SnapshotOutcome.Ready(null)
        _state.value.snapshot?.let { return SnapshotOutcome.Ready(it) }

        val revision = ++collectionRevision
        _state.update { it.copy(isCollecting = true, diagnosticsFailed = false) }
        var failed = false
        val snapshot = try {
            snapshotSource.collect()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
            DiagnosticSnapshot.unavailable(System.currentTimeMillis())
        }

        if (revision != collectionRevision) {
            // 采集中改变了级别或刷新：迟到结果不得重新附加诊断。
            _state.update { it.copy(isCollecting = false) }
            return SnapshotOutcome.Aborted
        }
        val level = _state.value.diagnosticLevel
        if (level == DiagnosticLevel.NONE) {
            _state.update { it.copy(isCollecting = false, diagnosticsFailed = false) }
            return SnapshotOutcome.Ready(null)
        }
        _state.update {
            it.copy(
                isCollecting = false,
                diagnosticsFailed = failed,
                snapshot = snapshot,
                diagnosticsText = IssueReportFormatter(texts).formatDiagnostics(snapshot, level)
            )
        }
        return SnapshotOutcome.Ready(snapshot)
    }

    private sealed interface SnapshotOutcome {
        data class Ready(val snapshot: DiagnosticSnapshot?) : SnapshotOutcome

        data object Aborted : SnapshotOutcome
    }

    /** 普通表单编辑属于新投稿：旧冻结投稿和连接状态一并失效。 */
    private fun updateForm(transform: (ReportIssueUiState) -> ReportIssueUiState) {
        if (_state.value.isSubmitting) return
        formRevision++
        _state.update { current ->
            transform(current).copy(prepared = null, restoredPending = false, submitError = null, showCombinedLengthError = false)
        }
    }

    private companion object {
        const val KEY_PROBLEM = "report_issue_problem"
        const val KEY_STEPS = "report_issue_steps"
        const val KEY_EXPECTED = "report_issue_expected"
        const val KEY_STEPS_EXPANDED = "report_issue_steps_expanded"
        const val KEY_LEVEL = "report_issue_level"
    }
}
