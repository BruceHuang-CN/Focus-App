package com.example.focus_app.util

import android.content.Context
import android.content.pm.PackageManager
import com.example.focus_app.data.repository.AppInfo

data class InstalledApp(val packageName: String, val appName: String) {
    fun toAppInfo() = AppInfo(packageName, appName)
}

/** 列出带启动入口的已安装应用（依赖 AndroidManifest 中的 <queries> 声明）。 */
fun loadInstalledApps(context: Context): List<InstalledApp> {
    return try {
        context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { context.packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .map {
                InstalledApp(
                    it.packageName,
                    context.packageManager.getApplicationLabel(it).toString()
                )
            }
            .sortedBy { it.appName.lowercase() }
    } catch (_: Exception) {
        emptyList()
    }
}

/** 只读取一个已知包名的显示名称，避免为了展示名称而扫描所有应用。 */
fun loadInstalledAppName(context: Context, packageName: String): String? {
    if (packageName.isBlank()) return null
    return try {
        val info = context.packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        context.packageManager.getApplicationLabel(info).toString()
    } catch (_: Exception) {
        null
    }
}
