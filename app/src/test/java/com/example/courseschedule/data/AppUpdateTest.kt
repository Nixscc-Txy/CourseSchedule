package com.example.courseschedule.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppUpdateTest {

    @Test
    fun `发布说明去掉 Markdown 记号`() {
        val md = "## 课表 v1.0.1\n\n修复 **小组件** 浅色过亮，见 `README`。\n"
        assertEquals(
            "课表 v1.0.1\n\n修复 小组件 浅色过亮，见 README。",
            AppUpdate.plainNotes(md)
        )
    }

    @Test
    fun `多级标题和空行压缩`() {
        assertEquals("标题\n\n- 第一条\n- 第二条", AppUpdate.plainNotes("### 标题\n\n\n\n- 第一条\n- 第二条"))
    }

    @Test
    fun `本来就干净的内容不动它`() {
        assertEquals("已是最新版本", AppUpdate.plainNotes("已是最新版本"))
        assertEquals("", AppUpdate.plainNotes(""))
    }
}
