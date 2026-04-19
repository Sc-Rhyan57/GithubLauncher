package com.gitlaunch

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

object AppPalettes {
    val Dynamic = 0
    val TealLime = 1
    val PurplePink = 2
    val BlueRed = 3
    val OrangeYellow = 4
    val GreenTeal = 5
    val IndigoPurple = 6
    val NavyBlue = 7
    val TealBlue = 8
}

data class ColorPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color
)

val palettes = mapOf(
    AppPalettes.Dynamic    to ColorPalette(Color(0xFF1565C0), Color(0xFF0D47A1), Color(0xFF42A5F5)),
    AppPalettes.TealLime   to ColorPalette(Color(0xFF00897B), Color(0xFF26A69A), Color(0xFFCDDC39)),
    AppPalettes.PurplePink to ColorPalette(Color(0xFF7B1FA2), Color(0xFFAB47BC), Color(0xFFF48FB1)),
    AppPalettes.BlueRed    to ColorPalette(Color(0xFF1565C0), Color(0xFF42A5F5), Color(0xFFEF5350)),
    AppPalettes.OrangeYellow to ColorPalette(Color(0xFFE65100), Color(0xFFFFA726), Color(0xFFFFEE58)),
    AppPalettes.GreenTeal  to ColorPalette(Color(0xFF2E7D32), Color(0xFF43A047), Color(0xFF26C6DA)),
    AppPalettes.IndigoPurple to ColorPalette(Color(0xFF283593), Color(0xFF3949AB), Color(0xFF7E57C2)),
    AppPalettes.NavyBlue   to ColorPalette(Color(0xFF1A237E), Color(0xFF3F51B5), Color(0xFF64B5F6)),
    AppPalettes.TealBlue   to ColorPalette(Color(0xFF006064), Color(0xFF00838F), Color(0xFF29B6F6))
)

fun buildDarkScheme(palette: ColorPalette) = darkColorScheme(
    primary             = palette.primary,
    onPrimary           = Color.White,
    primaryContainer    = palette.primary.copy(alpha = 0.3f),
    secondary           = palette.secondary,
    onSecondary         = Color.White,
    tertiary            = palette.tertiary,
    background          = Color(0xFF0D1117),
    surface             = Color(0xFF161B22),
    surfaceVariant      = Color(0xFF21262D),
    onBackground        = Color(0xFFE6EDF3),
    onSurface           = Color(0xFFE6EDF3),
    outline             = Color(0xFF30363D),
    error               = Color(0xFFF85149),
    onError             = Color.White
)

fun buildLightScheme(palette: ColorPalette) = lightColorScheme(
    primary             = palette.primary,
    onPrimary           = Color.White,
    primaryContainer    = palette.primary.copy(alpha = 0.15f),
    secondary           = palette.secondary,
    onSecondary         = Color.White,
    tertiary            = palette.tertiary,
    background          = Color(0xFFF6F8FA),
    surface             = Color(0xFFFFFFFF),
    surfaceVariant      = Color(0xFFF0F6FF),
    onBackground        = Color(0xFF1F2328),
    onSurface           = Color(0xFF1F2328),
    outline             = Color(0xFFD0D7DE),
    error               = Color(0xFFCF222E),
    onError             = Color.White
)

fun buildPureBlackScheme(palette: ColorPalette) = darkColorScheme(
    primary             = palette.primary,
    onPrimary           = Color.White,
    primaryContainer    = palette.primary.copy(alpha = 0.3f),
    secondary           = palette.secondary,
    onSecondary         = Color.White,
    tertiary            = palette.tertiary,
    background          = Color(0xFF000000),
    surface             = Color(0xFF0D1117),
    surfaceVariant      = Color(0xFF161B22),
    onBackground        = Color(0xFFE6EDF3),
    onSurface           = Color(0xFFE6EDF3),
    outline             = Color(0xFF21262D),
    error               = Color(0xFFF85149),
    onError             = Color.White
)
