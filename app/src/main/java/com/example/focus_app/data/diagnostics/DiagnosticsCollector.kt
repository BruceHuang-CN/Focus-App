package com.example.focus_app.data.diagnostics

import android.content.Context
import android.os.Build
import androidx.core.app.LocaleManagerCompat
import com.example.focus_app.data.repository.AppSettings
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.domain.feedback.AccessibilityDiagnosticsSnapshot
import com.example.focus_app.domain.feedback.DiagnosticSnapshot
import com.example.focus_app.domain.feedback.DiagnosticTime
import com.example.focus_app.domain.feedback.DeviceDiagnostics
import com.example.focus_app.domain.feedback.SettingsDiagnostics
import com.example.focus_app.service.AccessibilityDiagnosticsStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/** 可替换的设备信息边界，便于在 JVM 单元测试中固定取值。 */
interface DeviceInfoSource {
    fun device(): DeviceDiagnostics

    fun collectedAtMillis(): Long
}

/** 从系统与安装包元数据读取设备信息；单项读取失败时该项为 null（显示“无法读取”）。 */
@Singleton
class AndroidDeviceInfoSource @Inject constructor(
    @ApplicationContext private val context: Context
) : DeviceInfoSource {

    override fun device(): DeviceDiagnostics {
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        return DeviceDiagnostics(
            manufacturer = Build.MANUFACTURER?.takeIf { it.isNotBlank() },
            model = Build.MODEL?.takeIf { it.isNotBlank() },
            androidVersion = Build.VERSION.RELEASE?.takeIf { it.isNotBlank() },
            apiLevel = Build.VERSION.SDK_INT,
            versionName = packageInfo?.versionName?.takeIf { it.isNotBlank() },
            versionCode = packageInfo?.let { info ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    info.versionCode.toLong()
                }
            },
            systemLanguage = systemLanguage()
        )
    }

    override fun collectedAtMillis(): Long = System.currentTimeMillis()

    /**
     * 系统优先语言标签（BCP 47）。
     *
     * 通过 [LocaleManagerCompat.getSystemLocales] 读取真正的系统语言，不随 App 内自选语言变化；
     * 系统中文而 App 英文时依然返回系统语言。该字段只在 detailed 级别上传。
     */
    private fun systemLanguage(): String? = runCatching {
        val locales = LocaleManagerCompat.getSystemLocales(context)
        if (locales.isEmpty) null else locales[0]?.toLanguageTag()
    }.getOrNull()
}

/** 反馈流程只依赖“取得当前诊断快照”这一能力，便于测试替换。 */
fun interface DiagnosticSnapshotSource {
    suspend fun collect(): DiagnosticSnapshot
}

/**
 * 统一的白名单诊断采集器，只在用户需要诊断预览或完整预览时调用一次。
 *
 * 只读现有 [AccessibilityDiagnosticsStore] 的状态，不写入服务记录；
 * 只从 [SettingsRepository] 读取少量设置摘要，且逐字段映射，不整体序列化 AppSettings。
 */
@Singleton
class DiagnosticsCollector @Inject constructor(
    private val deviceInfoSource: DeviceInfoSource,
    private val accessibilityDiagnosticsStore: AccessibilityDiagnosticsStore,
    private val settingsRepository: SettingsRepository,
    private val feedbackContextSource: FeedbackContextSource = FeedbackContextSource { _, _ -> null }
) : DiagnosticSnapshotSource {

    override suspend fun collect(): DiagnosticSnapshot {
        val collectedAtMillis = deviceInfoSource.collectedAtMillis()
        val device = try {
            deviceInfoSource.device()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            DeviceDiagnostics()
        }
        val accessibility = try {
            val state = accessibilityDiagnosticsStore.state.value
            AccessibilityDiagnosticsSnapshot(
                serviceBound = state.serviceBound,
                lastConnectedAt = state.lastConnectedAtMillis.toDiagnosticTime(),
                lastDestroyedAt = state.lastDestroyedAtMillis.toDiagnosticTime(),
                lastInterruptedAt = state.lastInterruptedAtMillis.toDiagnosticTime()
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AccessibilityDiagnosticsSnapshot(
                serviceBound = null,
                lastConnectedAt = DiagnosticTime.Unavailable,
                lastDestroyedAt = DiagnosticTime.Unavailable,
                lastInterruptedAt = DiagnosticTime.Unavailable
            )
        }
        val appSettings = try {
            settingsRepository.getSettings()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 读取失败时不伪造默认值，每一项都按“无法读取”呈现。
            null
        }
        val context = try {
            feedbackContextSource.collect(appSettings, collectedAtMillis)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        return DiagnosticSnapshot(
            device = device,
            accessibility = accessibility,
            settings = appSettings?.toSettingsDiagnostics(),
            collectedAtMillis = collectedAtMillis,
            context = context
        )
    }
}

private fun Long?.toDiagnosticTime(): DiagnosticTime =
    if (this == null) DiagnosticTime.NoRecord else DiagnosticTime.Recorded(this)

private fun AppSettings.toSettingsDiagnostics(): SettingsDiagnostics = SettingsDiagnostics(
    guardianEnabled = guardianEnabled,
    detectionMode = detectionMode,
    reminderDelaySeconds = reminderDelaySeconds,
    reminderWindowMinutes = reminderWindowMinutes,
    maxRemindersPerWindow = maxRemindersPerWindow
)

@Module
@InstallIn(SingletonComponent::class)
abstract class DiagnosticsModule {
    @Binds
    @Singleton
    abstract fun bindDeviceInfoSource(impl: AndroidDeviceInfoSource): DeviceInfoSource

    @Binds
    @Singleton
    abstract fun bindFeedbackSystemStateSource(impl: AndroidFeedbackSystemStateSource): FeedbackSystemStateSource

    @Binds
    @Singleton
    abstract fun bindFeedbackContextSource(impl: DefaultFeedbackContextSource): FeedbackContextSource

    @Binds
    @Singleton
    abstract fun bindDiagnosticSnapshotSource(impl: DiagnosticsCollector): DiagnosticSnapshotSource
}
