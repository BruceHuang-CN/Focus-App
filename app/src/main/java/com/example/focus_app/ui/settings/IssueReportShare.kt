package com.example.focus_app.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent

/**
 * 系统分享与显式复制。
 *
 * 只负责把已经冻结的正文交出去：不重新采集诊断、不附加额外内容、不写入任何存储，
 * 也不需要附件、FileProvider 或存储权限。
 */
internal object IssueReportShare {

    /** 打开系统分享面板。[body] 必须来自已预览的正文。 */
    fun share(context: Context, body: String, subject: String): Boolean {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        return try {
            context.startActivity(Intent.createChooser(sendIntent, subject))
            true
        } catch (_: Exception) {
            // 设备上没有可接收 text/plain 的应用时系统会抛出 ActivityNotFoundException。
            false
        }
    }

    /** 复制正文兜底；不保证系统分享面板里一定有微信或邮件。 */
    fun copy(context: Context, body: String, label: String): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        return try {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, body))
            true
        } catch (_: Exception) {
            false
        }
    }
}
