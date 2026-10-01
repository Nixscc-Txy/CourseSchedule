package com.example.courseschedule.data

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 版本信息 + 更新检查。
 *
 * 检查只做一件事: 读 GitHub 的 releases/latest 接口(一个 JSON), 判断有没有新版本。
 * **不自己下载、不自己安装** —— 有新版就把用户交给浏览器去下载, 所以只需要 INTERNET 一个权限,
 * 不用 REQUEST_INSTALL_PACKAGES, 也避开了应用市场对"应用内自行下载安装"的限制。
 * 这也是本项目唯一需要联网的地方, 只读取、不上传任何数据(课表不会被碰到)。
 */
object AppUpdate {

    const val RELEASES_URL = "https://github.com/Nixscc-Txy/CourseSchedule/releases/latest"
    private const val API_LATEST =
        "https://api.github.com/repos/Nixscc-Txy/CourseSchedule/releases/latest"

    /** 最新一次发布 */
    data class Latest(
        val tag: String,
        val name: String,
        val notes: String,
        val pageUrl: String,
        val apkUrl: String?
    )

    sealed interface CheckResult {
        /** 有更新 */
        data class Newer(val latest: Latest, val current: String) : CheckResult

        /** 已是最新 */
        data class UpToDate(val latest: Latest, val current: String) : CheckResult

        /** 网络/接口失败 —— 界面要保留"打开发布页"这条兜底路 */
        data class Failed(val message: String) : CheckResult
    }

    /** 形如 "1.0.0 (build 1)" */
    fun versionLabel(context: Context): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (build ${PackageInfoCompat.getLongVersionCode(info)})"
        } catch (_: Exception) {
            "未知"
        }
    }

    fun currentVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
        } catch (_: Exception) {
            "0"
        }
    }

    /** 请求 GitHub 并比对版本; 内部切到 IO 线程, 直接在协程里调即可 */
    suspend fun check(context: Context): CheckResult = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject(requestLatestJson())
            val latest = Latest(
                tag = json.optString("tag_name"),
                name = json.optString("name"),
                notes = json.optString("body"),
                pageUrl = json.optString("html_url").ifBlank { RELEASES_URL },
                apkUrl = json.optJSONArray("assets")?.let { assets ->
                    (0 until assets.length())
                        .map { assets.getJSONObject(it) }
                        .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                        ?.optString("browser_download_url")
                        ?.takeIf { it.isNotBlank() }
                }
            )
            val current = currentVersionName(context)
            val cmp = compareVersions(latest.tag, current)
            // 版本号格式看不懂时 (cmp == null) 也当"有更新", 提示用户去发布页自己看一眼,
            // 总比默默说"已是最新"把人骗过去强
            if (cmp == null || cmp > 0) CheckResult.Newer(latest, current)
            else CheckResult.UpToDate(latest, current)
        } catch (e: Exception) {
            CheckResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun requestLatestJson(): String {
        val conn = (URL(API_LATEST).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            // GitHub API 对没有 User-Agent 的请求直接 403, 这个头必须带
            setRequestProperty("User-Agent", "CourseSchedule-Android")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        return try {
            val code = conn.responseCode
            if (code != 200) throw IllegalStateException("GitHub 返回 HTTP $code")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 发布说明在 GitHub 上是 Markdown, 原样塞进弹窗会露出 "##" 和 "**" 这些记号。
     * 这里只做最轻的清理(标题号 / 粗体 / 行内代码 / 多余空行), 不引 Markdown 渲染库。
     */
    fun plainNotes(markdown: String): String {
        return markdown.lineSequence()
            .map { line ->
                line.trim().trimStart('#').trim()
                    .replace("**", "")
                    .replace("`", "")
            }
            .joinToString("\n")
            .trim()
            .replace(Regex("\\n{3,}"), "\n\n")
    }
}

/**
 * 比较两个版本号, **按点分段做数值比较**, 不用字符串比大小
 * ("1.0.10" 字符串比 "1.0.9" 小, 但版本上更大, 这是经典坑)。
 * 返回 null 表示格式不认识, 无法比较。
 */
fun compareVersions(a: String, b: String): Int? {
    val pa = versionParts(a) ?: return null
    val pb = versionParts(b) ?: return null
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val diff = (pa.getOrNull(i) ?: 0) - (pb.getOrNull(i) ?: 0)
        if (diff != 0) return diff
    }
    return 0
}

/** "v1.0.10" -> [1, 0, 10]; "1.0-beta" -> [1, 0]; 认不出返回 null */
private fun versionParts(raw: String): List<Int>? {
    val s = raw.trim().removePrefix("v").removePrefix("V")
    if (s.isEmpty()) return null
    val core = s.takeWhile { it.isDigit() || it == '.' }
    if (core.isEmpty()) return null
    val parts = core.split('.').map { it.toIntOrNull() ?: return null }
    return parts.ifEmpty { null }
}
