package com.linode.manager.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.linode.manager.data.ThemeMode

// Every container/surface role is defined explicitly so cards, sheets, menus
// and dialogs look the same on every device (no fallback to M3 baseline
// purple tones that appear when a role is left unset).

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF3DDC97),
        onPrimary = Color(0xFF003824),
        primaryContainer = Color(0xFF005236),
        onPrimaryContainer = Color(0xFF8EF8C3),
        secondary = Color(0xFF7CC8F8),
        onSecondary = Color(0xFF00344F),
        secondaryContainer = Color(0xFF1B3A52),
        onSecondaryContainer = Color(0xFFCAE6FF),
        tertiary = Color(0xFFFFB86B),
        onTertiary = Color(0xFF4A2800),
        tertiaryContainer = Color(0xFF5C3A12),
        onTertiaryContainer = Color(0xFFFFDDB8),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF0E1419),
        onBackground = Color(0xFFDEE3E8),
        surface = Color(0xFF0E1419),
        onSurface = Color(0xFFDEE3E8),
        surfaceVariant = Color(0xFF3F4850),
        onSurfaceVariant = Color(0xFFBEC7D0),
        surfaceDim = Color(0xFF0E1419),
        surfaceBright = Color(0xFF343A40),
        surfaceContainerLowest = Color(0xFF090F13),
        surfaceContainerLow = Color(0xFF161C21),
        surfaceContainer = Color(0xFF1A2026),
        surfaceContainerHigh = Color(0xFF242B31),
        surfaceContainerHighest = Color(0xFF2F363C),
        inverseSurface = Color(0xFFDEE3E8),
        inverseOnSurface = Color(0xFF2B3136),
        inversePrimary = Color(0xFF006C48),
        outline = Color(0xFF88919A),
        outlineVariant = Color(0xFF3F4850),
        scrim = Color.Black,
    )

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF006C48),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF8EF8C3),
        onPrimaryContainer = Color(0xFF002114),
        secondary = Color(0xFF00658F),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFCAE6FF),
        onSecondaryContainer = Color(0xFF001E2F),
        tertiary = Color(0xFF8A5100),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFDDB8),
        onTertiaryContainer = Color(0xFF2C1600),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFF6F8FA),
        onBackground = Color(0xFF171C20),
        surface = Color(0xFFF6F8FA),
        onSurface = Color(0xFF171C20),
        surfaceVariant = Color(0xFFDCE3EA),
        onSurfaceVariant = Color(0xFF40484F),
        surfaceDim = Color(0xFFD6DADE),
        surfaceBright = Color(0xFFF6F8FA),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFFFFFFF),
        surfaceContainer = Color(0xFFEEF1F4),
        surfaceContainerHigh = Color(0xFFE8EBEF),
        surfaceContainerHighest = Color(0xFFE2E6EA),
        inverseSurface = Color(0xFF2C3135),
        inverseOnSurface = Color(0xFFEDF1F5),
        inversePrimary = Color(0xFF3DDC97),
        outline = Color(0xFF707880),
        outlineVariant = Color(0xFFC6CDD4),
        scrim = Color.Black,
    )

private val AppTypography =
    Typography(
        displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp),
        headlineMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                letterSpacing = (-0.2).sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 26.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                letterSpacing = 0.1.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
        labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    )

private val AppShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

/** Semantic status colors that stay legible in both themes. */
@Immutable
data class StatusColors(
    val ok: Color,
    val warn: Color,
    val bad: Color,
    val idle: Color,
)

private val DarkStatus = StatusColors(Color(0xFF3DDC97), Color(0xFFFFB86B), Color(0xFFFF8A80), Color(0xFF8A97A5))
private val LightStatus = StatusColors(Color(0xFF16A34A), Color(0xFFD97706), Color(0xFFDC2626), Color(0xFF64748B))

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

@Composable
fun LinodeManagerTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark =
        when (mode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    val colors = if (dark) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.decorView.setBackgroundColor(colors.background.toArgb())
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

enum class StatusTone { OK, WARN, BAD, IDLE }

fun statusTone(status: String?): StatusTone =
    when (status?.lowercase()) {
        "running", "active", "enabled", "ok", "ready", "finished", "notification", "paid" -> StatusTone.OK
        "offline", "stopped", "disabled", "suspended", "failed", "deleted" -> StatusTone.BAD
        "booting", "rebooting", "provisioning", "creating", "resizing", "rebuilding",
        "cloning", "migrating", "restoring", "busy", "shutting_down", "started", "scheduled",
        "pending", "migration_pending",
        -> StatusTone.WARN
        else -> StatusTone.IDLE
    }

@Composable
fun statusColor(status: String?): Color {
    val c = LocalStatusColors.current
    return when (statusTone(status)) {
        StatusTone.OK -> c.ok
        StatusTone.WARN -> c.warn
        StatusTone.BAD -> c.bad
        StatusTone.IDLE -> c.idle
    }
}
