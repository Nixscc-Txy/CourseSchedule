package com.example.courseschedule.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------- iOS 系统色

/** iOS 蓝 (浅色/深色) */
val IosBlueLight = Color(0xFF007AFF)
val IosBlueDark = Color(0xFF0A84FF)

/** iOS 靛蓝 (次要色) */
val IosIndigoLight = Color(0xFF5856D6)
val IosIndigoDark = Color(0xFF5E5CE6)

/** iOS 粉 (第三色) */
val IosPinkLight = Color(0xFFFF2D55)
val IosPinkDark = Color(0xFFFF375F)

/** iOS 红 (错误色) */
val IosRedLight = Color(0xFFFF3B30)
val IosRedDark = Color(0xFFFF453A)

/** iOS 橙 (今日高亮) */
val IosOrangeLight = Color(0xFFFF9500)
val IosOrangeDark = Color(0xFFFF9F0A)

/** iOS 分组背景 */
val IosGroupedBgLight = Color(0xFFF2F2F7)
val IosGroupedBgDark = Color(0xFF000000)

/** iOS 卡片背景 */
val IosCardBgLight = Color(0xFFFFFFFF)
val IosCardBgDark = Color(0xFF1C1C1E)

/** iOS 标签文字 */
val IosLabelLight = Color(0xFF000000)
val IosLabelDark = Color(0xFFFFFFFF)

/** iOS 次要文字 */
val IosSecondaryLabelLight = Color(0xFF6E6E73)
val IosSecondaryLabelDark = Color(0xFF98989E)

// ---------------------------------------------------------------- M3 配色方案

/** iOS 风格浅色方案 */
val IosLightColorScheme = lightColorScheme(
    primary = IosBlueLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCE4FF),
    onPrimaryContainer = Color(0xFF00336E),
    secondary = IosIndigoLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3E2FF),
    onSecondaryContainer = Color(0xFF1B1B63),
    tertiary = IosPinkLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9DF),
    onTertiaryContainer = Color(0xFF5E0013),
    error = IosRedLight,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF5C110B),
    background = IosGroupedBgLight,
    onBackground = IosLabelLight,
    surface = IosCardBgLight,
    onSurface = IosLabelLight,
    surfaceVariant = Color(0xFFE9E9EE),
    onSurfaceVariant = IosSecondaryLabelLight,
    outline = Color(0xFFC7C7CC),
    outlineVariant = Color(0xFFD8D8DD),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2C2C2E),
    inverseOnSurface = IosGroupedBgLight,
    inversePrimary = Color(0xFF66B0FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F7F9),
    surfaceContainer = Color(0xFFEFEFF4),
    surfaceContainerHigh = Color(0xFFE9E9EE),
    surfaceContainerHighest = Color(0xFFE2E2E7)
)

/** iOS 风格深色方案 */
val IosDarkColorScheme = darkColorScheme(
    primary = IosBlueDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF003A73),
    onPrimaryContainer = Color(0xFFA8C7FF),
    secondary = IosIndigoDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF26266B),
    onSecondaryContainer = Color(0xFFD0CFFF),
    tertiary = IosPinkDark,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF6E0025),
    onTertiaryContainer = Color(0xFFFFD9E0),
    error = IosRedDark,
    onError = Color(0xFF5C110B),
    errorContainer = Color(0xFF7A1E16),
    onErrorContainer = Color(0xFFFFDAD6),
    background = IosGroupedBgDark,
    onBackground = IosLabelDark,
    surface = IosCardBgDark,
    onSurface = IosLabelDark,
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = IosSecondaryLabelDark,
    outline = Color(0xFF48484A),
    outlineVariant = Color(0xFF3A3A3C),
    scrim = Color(0xFF000000),
    inverseSurface = IosGroupedBgLight,
    inverseOnSurface = IosCardBgDark,
    inversePrimary = IosBlueLight,
    surfaceContainerLowest = Color(0xFF0C0C0E),
    surfaceContainerLow = Color(0xFF17171A),
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF242427),
    surfaceContainerHighest = Color(0xFF2C2C2E)
)
