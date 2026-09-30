package com.example.focus_app.data.feedback

import android.content.Context
import com.example.focus_app.domain.feedback.AppFeedbackRequest
import com.example.focus_app.domain.feedback.FeedbackContextDiagnostics
import com.google.gson.Gson
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 一次尚未确认收到的投稿：本地展示正文 + 冻结的请求体（含 id）。 */
data class PendingFeedback(
    val displayBody: String,
    val payload: AppFeedbackRequest,
    val formSteps: String? = null,
    val context: FeedbackContextDiagnostics? = null
)

/**
 * 待发送请求的本地存储。
 *
 * 首次发送前原子写入，失败重试或进程恢复继续使用同一个 id，避免重复反馈。
 * 成功后删除；失败时绝不清空。
 */
interface FeedbackDraftStore {
    suspend fun save(pending: PendingFeedback)

    suspend fun load(): PendingFeedback?

    suspend fun clear()
}

@Singleton
class FileFeedbackDraftStore(
    private val file: File,
    private val gson: Gson = Gson()
) : FeedbackDraftStore {

    @Inject
    constructor(@ApplicationContext context: Context) :
        this(File(context.filesDir, FILE_NAME))

    override suspend fun save(pending: PendingFeedback) {
        // 先写临时文件再改名，避免进程中断留下半个 JSON。
        val temp = File(file.parentFile, "$FILE_NAME.tmp")
        temp.writeText(gson.toJson(pending))
        if (file.exists() && !file.delete()) {
            temp.delete()
            error("cannot replace feedback draft")
        }
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
    }

    override suspend fun load(): PendingFeedback? = runCatching {
        if (!file.exists()) return null
        val text = file.readText()
        if (text.isBlank()) return null
        gson.fromJson(text, PendingFeedback::class.java)
    }.getOrNull()

    override suspend fun clear() {
        check(!file.exists() || file.delete()) { "cannot clear feedback draft" }
    }

    companion object {
        const val FILE_NAME = "feedback_pending.json"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FeedbackModule {
    @Binds
    @Singleton
    abstract fun bindFeedbackDraftStore(impl: FileFeedbackDraftStore): FeedbackDraftStore
}
