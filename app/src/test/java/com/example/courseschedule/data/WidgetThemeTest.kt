package com.example.courseschedule.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 小组件配色解析: follow 跟主体, light/dark 直接说了算 */
class WidgetThemeTest {

    @Test
    fun `跟随主体时听主体主题的`() {
        assertFalse(isDarkWidget("follow", "light", systemDark = true))
        assertTrue(isDarkWidget("follow", "dark", systemDark = false))
    }

    @Test
    fun `主体跟随系统时看系统深色开关`() {
        assertTrue(isDarkWidget("follow", "system", systemDark = true))
        assertFalse(isDarkWidget("follow", "system", systemDark = false))
    }

    @Test
    fun `单独指定浅色深色与主体无关`() {
        assertFalse(isDarkWidget("light", "dark", systemDark = true))
        assertTrue(isDarkWidget("dark", "light", systemDark = false))
    }

    @Test
    fun `默认值就是跟随主体`() {
        assertTrue(isDarkWidget(SettingsManager.WIDGET_THEME_FOLLOW, "dark", systemDark = false))
        // 取到意外值时也按"跟随主体"处理, 不会突然变成浅色
        assertTrue(isDarkWidget("whatever", "dark", systemDark = false))
    }
}
