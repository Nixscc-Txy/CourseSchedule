package com.example.courseschedule.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 更新检查解析的是 GitHub 的 Atom 源。固件按真实源的结构写(字段名/顺序/XML 转义的 HTML 都一样),
 * 但内容用本项目的真实发布形态 —— 源本身是机器生成的固定结构, 不依赖网络。
 */
class AppUpdateTest {

    private val fixture = """
<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom" xmlns:media="http://search.yahoo.com/mrss/" xml:lang="en-US">
  <id>tag:github.com,2008:https://github.com/Nixscc-Txy/CourseSchedule/releases</id>
  <link type="text/html" rel="alternate" href="https://github.com/Nixscc-Txy/CourseSchedule/releases"/>
  <link type="application/atom+xml" rel="self" href="https://github.com/Nixscc-Txy/CourseSchedule/releases.atom"/>
  <title>Release notes from CourseSchedule</title>
  <updated>2026-10-02T00:27:46Z</updated>
  <entry>
    <id>tag:github.com,2008:Repository/1322716877/v1.0.2</id>
    <updated>2026-10-02T00:30:19Z</updated>
    <link rel="alternate" type="text/html" href="https://github.com/Nixscc-Txy/CourseSchedule/releases/tag/v1.0.2"/>
    <title>课表 v1.0.2</title>
    <content type="html">&lt;h2&gt;课表 v1.0.2&lt;/h2&gt;
&lt;p&gt;修复 &lt;strong&gt;检查更新&lt;/strong&gt; 被限流的问题。&lt;/p&gt;
&lt;ul&gt;
&lt;li&gt;改用 Atom 源&lt;/li&gt;
&lt;/ul&gt;</content>
  </entry>
  <entry>
    <id>tag:github.com,2008:Repository/1322716877/v1.0.1</id>
    <updated>2026-10-01T16:30:19Z</updated>
    <link rel="alternate" type="text/html" href="https://github.com/Nixscc-Txy/CourseSchedule/releases/tag/v1.0.1"/>
    <title>课表 v1.0.1</title>
    <content type="html">&lt;p&gt;更旧的一版&lt;/p&gt;</content>
  </entry>
</feed>
""".trimIndent()

    @Test
    fun `取到最新一条发布的版本号与页面地址`() {
        val latest = AppUpdate.parseLatest(fixture)!!
        assertEquals("v1.0.2", latest.tag)
        assertEquals("课表 v1.0.2", latest.name)
        assertEquals(
            "https://github.com/Nixscc-Txy/CourseSchedule/releases/tag/v1.0.2",
            latest.pageUrl
        )
    }

    @Test
    fun `取的是第一条(最新), 不是更旧的那条, 也不是 feed 自己的链接`() {
        val latest = AppUpdate.parseLatest(fixture)!!
        assertTrue(latest.tag == "v1.0.2")
        assertFalse(latest.pageUrl.endsWith("/releases"))     // 别取到 feed 级的 link
        assertFalse(latest.notes.contains("更旧的一版"))
    }

    @Test
    fun `发布说明从转义 HTML 变成可读纯文本`() {
        val notes = AppUpdate.parseLatest(fixture)!!.notes
        assertTrue(notes.contains("课表 v1.0.2"))
        assertTrue(notes.contains("修复 检查更新 被限流的问题。"))
        assertTrue(notes.contains("改用 Atom 源"))
        assertFalse("不应残留标签或转义实体", notes.contains("<") || notes.contains("&lt;") || notes.contains("&amp;"))
    }

    @Test
    fun `html 转文本时段落各占一行`() {
        assertEquals("第一段\n第二段", htmlToText("&lt;p&gt;第一段&lt;/p&gt;&lt;p&gt;第二段&lt;/p&gt;"))
        assertEquals("标题\n正文", htmlToText("&lt;h2&gt;标题&lt;/h2&gt;&lt;div&gt;正文&lt;/div&gt;"))
        assertEquals("甲\n乙", htmlToText("甲&lt;br/&gt;乙"))
    }

    @Test
    fun `实体还原且不二次还原`() {
        // 只做一遍还原: &amp;amp; 应得到 "&amp;" 而不是 "&"
        assertEquals("a &amp; b", htmlToText("a &amp;amp; b"))
        // &amp;lt; 应得到 "&lt;" 而不是 "<"
        assertEquals("&lt;", htmlToText("&amp;lt;"))
    }

    @Test
    fun `源里没有 entry 或没有 tag 链接时返回 null`() {
        assertNull(AppUpdate.parseLatest("<feed><title>空</title></feed>"))
        assertNull(AppUpdate.parseLatest("<feed><entry><title>x</title></entry></feed>"))
    }
}
