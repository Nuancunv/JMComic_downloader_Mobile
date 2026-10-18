package com.nuancun.jmcomic.mobile.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.nuancun.jmcomic.mobile.R

enum class AppearanceMode(val label: String) {
    SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色");

    companion object {
        fun fromStored(value: String?): AppearanceMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

class AppearancePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("appearance_preferences", Context.MODE_PRIVATE)
    fun load(): AppearanceMode = AppearanceMode.fromStored(preferences.getString("mode", null))
    fun save(mode: AppearanceMode) { preferences.edit { putString("mode", mode.name) } }
}

internal val LocalWarmDark = staticCompositionLocalOf { false }
internal val actionInk: Color
    @Composable get() = if (LocalWarmDark.current) Color(0xFFF0B39C) else Color(0xFF943E28)

private val LightColors = lightColorScheme(
    primary = Color(0xFFD97757), onPrimary = Color(0xFF251B16),
    primaryContainer = Color(0xFFF1DDD0), onPrimaryContainer = Color(0xFF74331F),
    secondary = Color(0xFF626C50), onSecondary = Color(0xFFFAF7EF),
    secondaryContainer = Color(0xFFE2E5D6), onSecondaryContainer = Color(0xFF39432E),
    tertiary = Color(0xFFD97706), onTertiary = Color(0xFF251B16),
    tertiaryContainer = Color(0xFFF4E5C9), onTertiaryContainer = Color(0xFF6F4511),
    background = Color(0xFFF0EEE6), onBackground = Color(0xFF302A24),
    surface = Color(0xFFFAF7EF), onSurface = Color(0xFF302A24),
    surfaceVariant = Color(0xFFEAE5DA), onSurfaceVariant = Color(0xFF706458),
    surfaceContainerLowest = Color(0xFFFFFCF5), surfaceContainerLow = Color(0xFFFAF7EF),
    surfaceContainer = Color(0xFFF6F1E7), surfaceContainerHigh = Color(0xFFEFE9DC),
    surfaceContainerHighest = Color(0xFFE8E1D3), surfaceTint = Color.Transparent,
    surfaceBright = Color(0xFFFFFCF5), surfaceDim = Color(0xFFE2DACE),
    inverseSurface = Color(0xFF302A24), inverseOnSurface = Color(0xFFFAF7EF),
    inversePrimary = Color(0xFFF0B39C), scrim = Color(0xFF141413),
    outline = Color(0xFF8E8172), outlineVariant = Color(0xFFF0EEE6),
    error = Color(0xFFB53333), onError = Color.White,
    errorContainer = Color(0xFFF8DEDA), onErrorContainer = Color(0xFF792020),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD97757), onPrimary = Color(0xFF251B16),
    primaryContainer = Color(0xFF51362B), onPrimaryContainer = Color(0xFFF3C2AD),
    secondary = Color(0xFFB5BEA3), onSecondary = Color(0xFF23291E),
    secondaryContainer = Color(0xFF343B2C), onSecondaryContainer = Color(0xFFD5DBC8),
    tertiary = Color(0xFFD97706), onTertiary = Color(0xFF251B16),
    tertiaryContainer = Color(0xFF4B371F), onTertiaryContainer = Color(0xFFF1D2A1),
    background = Color(0xFF141413), onBackground = Color(0xFFF0EEE6),
    surface = Color(0xFF252523), onSurface = Color(0xFFF0EEE6),
    surfaceVariant = Color(0xFF34332E), onSurfaceVariant = Color(0xFFBAB3A6),
    surfaceContainerLowest = Color(0xFF191917), surfaceContainerLow = Color(0xFF252523),
    surfaceContainer = Color(0xFF292823), surfaceContainerHigh = Color(0xFF302E28),
    surfaceContainerHighest = Color(0xFF3A372F), surfaceTint = Color.Transparent,
    surfaceBright = Color(0xFF3B3830), surfaceDim = Color(0xFF141413),
    inverseSurface = Color(0xFFF0EEE6), inverseOnSurface = Color(0xFF302A24),
    inversePrimary = Color(0xFF943E28), scrim = Color(0xFF141413),
    outline = Color(0xFF8D8475), outlineVariant = Color(0xFF30302E),
    error = Color(0xFFF2A6A0), onError = Color(0xFF4C1717),
    errorContainer = Color(0xFF502727), onErrorContainer = Color(0xFFFBD8D4),
)

private val BookSerif = FontFamily(Font(R.font.noto_serif_sc_medium, FontWeight.Medium, loadingStrategy = FontLoadingStrategy.Async))
private fun text(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, serif: Boolean = false) = TextStyle(
    fontFamily = if (serif) BookSerif else FontFamily.SansSerif,
    fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = 0.sp,
)
private val AppTypography = Typography(
    headlineLarge = text(34, 46, FontWeight.Medium, true),
    headlineMedium = text(30, 42, FontWeight.Medium, true),
    headlineSmall = text(26, 38, FontWeight.Medium, true),
    titleLarge = text(23, 34, FontWeight.Medium, true),
    titleMedium = text(18, 28, FontWeight.Medium, true),
    titleSmall = text(16, 25, FontWeight.Medium, true),
    bodyLarge = text(17, 28), bodyMedium = text(15, 25), bodySmall = text(13, 21),
    labelLarge = text(15, 24, FontWeight.Medium),
    labelMedium = text(13, 21, FontWeight.Medium), labelSmall = text(11, 18),
)

@Composable
fun JMComicTheme(mode: AppearanceMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        AppearanceMode.SYSTEM -> isSystemInDarkTheme()
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
    }
    val activity = LocalContext.current.hostActivity() as? ComponentActivity
    SideEffect {
        val style = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(22.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        content = { CompositionLocalProvider(LocalWarmDark provides dark,
            LocalContentColor provides if (dark) DarkColors.onBackground else LightColors.onBackground, content = content) },
    )
}

internal fun Context.hostActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
