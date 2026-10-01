package com.example.courseschedule.data

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 版本信息 + 更新检查。
 *
 * 读的是 GitHub 的 **Atom 源** (`releases.atom`), 不是 REST API —— 这一点很关键:
 * 未认证的 `api.github.com` 限 60 次/小时, 而且**按公网 IP 算**。国内手机普遍走运营商共享出口
 * (CGNAT), 这个额度经常被同网其他人提前用光, App 里就会莫名其妙报 HTTP 403(实测到过:
 * 连手机浏览器直接打开该接口都被拒, 报文写明 rate limit 与手机出口 IP)。
 * Atom 源是普通页面资源, 不受该限额影响 —— 同一台手机、同一张卡上实测正常。
 *
 * 只检测、不下载、不安装: 有新版就把用户交给浏览器打开发布页。因此只需要 INTERNET 一个权限,
 * 不用 REQUEST_INSTALL_PACKAGES, 也避开应用市场对"应用内自行下载安装"的限制。
 */
object AppUpdate {

    const val RELEASES_URL = "https://github.com/Nixscc-Txy/CourseSchedule/releases/latest"
    private const val ATOM_URL = "https://github.com/Nixscc-Txy/CourseSchedule/releases.atom"

    /** 最新一次发布 */
    data class Latest(
        val tag: String,
        val name: String,
        val notes: String,
        val pageUrl: String
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

    /** 请求 Atom 源并比对版本; 内部切到 IO 线程, 直接在协程里调即可 */
    suspend fun check(context: Context): CheckResult = withContext(Dispatchers.IO) {
        try {
            val latest = parseLatest(request(ATOM_URL))
                ?: throw IllegalStateException("发布信息里没有找到版本")
            val current = currentVersionName(context)
            val cmp = compareVersions(latest.tag, current)
            // 版本号格式看不懂时 (cmp == null) 也当"有更新", 让用户去发布页自己看一眼,
            // 总比默默说"已是最新"把人骗过去强
            if (cmp == null || cmp > 0) CheckResult.Newer(latest, current)
            else CheckResult.UpToDate(latest, current)
        } catch (e: Exception) {
            CheckResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * 解析 Atom 源里最新一条发布。源按时间倒序, 所以第一个 entry 就是最新版。
     * 只用正则取几个固定字段 —— 这是 GitHub 机器生成的固定结构, 且有真实源文本作单测固件。
     */
    fun parseLatest(xml: String): Latest? {
        val entry = between(xml, "<entry>", "</entry>") ?: return null

        val pageUrl = Regex("<link[^>]*rel=\"alternate\"[^>]*href=\"([^\"]+)\"")
            .find(entry)?.groupValues?.get(1)
            ?.let(::unescapeXml)
            ?: return null
        val tag = pageUrl.substringAfterLast("/releases/tag/", "")
        if (tag.isBlank()) return null

        val name = between(entry, "<title>", "</title>")?.let(::unescapeXml) ?: tag
        val notes = between(entry, "<content", "</content>")
            ?.substringAfter('>', "")          // 跳过 <content type="html"> 的剩余属性
            ?.let(::htmlToText)
            .orEmpty()

        return Latest(tag = tag, name = name, notes = notes, pageUrl = pageUrl)
    }

    private fun request(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "CourseSchedule-Android")
        }
        return try {
            val code = conn.responseCode
            if (code != 200) throw IllegalStateException("GitHub 返回 HTTP $code")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun between(text: String, start: String, end: String): String? {
        val i = text.indexOf(start)
        if (i < 0) return null
        val j = text.indexOf(end, i + start.length)
        if (j < 0) return null
        return text.substring(i + start.length, j)
    }
}

/**
 * Atom 源里的发布说明是 **XML 转义过的 HTML** (`&lt;h2&gt;...`), 先反转义再摘掉标签,
 * 得到能直接塞进弹窗的纯文本。
 */
fun htmlToText(escapedHtml: String): String {
    val html = unescapeXml(escapedHtml)
    return html
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</(p|li|h[1-6]|ul|ol)>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .lines()
        .joinToString("\n") { it.trim() }
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

private fun unescapeXml(text: String): String {
    return text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&amp;", "&")     // 放最后, 免得把 &amp;lt; 二次还原成 <
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
