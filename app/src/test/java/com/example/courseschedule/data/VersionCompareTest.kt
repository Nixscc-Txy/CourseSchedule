package com.example.courseschedule.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 版本比较必须按数值, 不能按字符串 */
class VersionCompareTest {

    @Test
    fun `字符串比大小会翻车的那个经典例子`() {
        assertEquals(1, compareVersions("1.0.10", "1.0.9"))
        assertTrue("1.0.10" < "1.0.9")   // 证明字符串比较确实是错的
    }

    @Test
    fun `带 v 前缀也能比`() {
        assertEquals(1, compareVersions("v1.0.1", "1.0.0"))
        assertEquals(0, compareVersions("V1.0.0", "1.0.0"))
        assertEquals(-1, compareVersions("v1.0.0", "v1.0.1"))
    }

    @Test
    fun `段数不同按缺位补零`() {
        assertEquals(0, compareVersions("1.1", "1.1.0"))
        assertEquals(1, compareVersions("1.1.1", "1.1"))
        assertEquals(-1, compareVersions("1.0", "1.0.0.1"))
    }

    @Test
    fun `两位数的段按数值比而不是按字典序`() {
        assertTrue(compareVersions("10.0.0", "2.0.0")!! > 0)   // 字典序下 "10" < "2", 会判错
        assertTrue(compareVersions("2.0.0", "10.0.0")!! < 0)
        assertTrue(compareVersions("1.2.0", "1.10.0")!! < 0)
        assertTrue(compareVersions("1.10.0", "1.2.0")!! > 0)
    }

    @Test
    fun `后缀被忽略`() {
        assertEquals(0, compareVersions("1.0.0-beta", "1.0.0"))
        assertEquals(1, compareVersions("1.0.1-rc1", "1.0.0"))
    }

    @Test
    fun `认不出的格式返回 null(界面据此提示去发布页)`() {
        assertNull(compareVersions("abc", "1.0.0"))
        assertNull(compareVersions("", "1.0.0"))
        assertNull(compareVersions("v", "1.0.0"))
        assertNull(compareVersions("1.", "1.0.0"))
        assertNull(compareVersions("release-1.0", "1.0.0"))
    }
}
