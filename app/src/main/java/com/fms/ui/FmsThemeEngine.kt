package com.fms.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.fms.zclone.enginecoremode.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class FmsFontChoice(val displayName: String) {
    SPACE_GROTESK("Space Grotesk"),
    PLUS_JAKARTA_SANS("Plus Jakarta Sans"),
    JETBRAINS_MONO("JetBrains Mono")
}

enum class FmsIconStyle(val displayName: String) {
    ROUNDED("Rounded"),
    SQUIRCLE("Squircle"),
    NEON_RING("Neon Ring")
}

@Immutable
data class FmsThemePreset(
    val id: String,
    val name: String,
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val primary: Color,
    val secondary: Color,
    val accentSuccess: Color,
    val accentGold: Color,
    val onBackground: Color,
    val onSurfaceMuted: Color,
    val fontChoice: FmsFontChoice = FmsFontChoice.PLUS_JAKARTA_SANS,
    val iconStyle: FmsIconStyle = FmsIconStyle.SQUIRCLE,
    val customTheme: Boolean = false
)

/**
 * FMSThemeEngine, FMSAutoTheme & FMSThemeStudio:
 * Independent theme token engine with 6 built-in presets matching the Dark Neon Obsidian design.
 */
object FMSThemeEngine {
    val SpaceGroteskFamily = FontFamily(Font(R.font.space_grotesk, FontWeight.Normal))
    val PlusJakartaFamily = FontFamily(Font(R.font.plus_jakarta_sans, FontWeight.Normal))
    val JetBrainsMonoFamily = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal))

    val presets: List<FmsThemePreset> = listOf(
        FmsThemePreset(
            id = "dark_neon",
            name = "Dark Neon",
            isDark = true,
            background = Color(0xFF070B19),
            surface = Color(0xFF11182E),
            surfaceElevated = Color(0xFF182240),
            border = Color(0xFF24325B),
            primary = Color(0xFF00D2FF),
            secondary = Color(0xFF8B5CF6),
            accentSuccess = Color(0xFF10B981),
            accentGold = Color(0xFFF59E0B),
            onBackground = Color(0xFFF8FAFC),
            onSurfaceMuted = Color(0xFF94A3B8)
        ),
        FmsThemePreset(
            id = "light_blue",
            name = "Light Blue",
            isDark = false,
            background = Color(0xFFF1F5FB),
            surface = Color(0xFFFFFFFF),
            surfaceElevated = Color(0xFFE6EEF9),
            border = Color(0xFFC5D5EE),
            primary = Color(0xFF2563EB),
            secondary = Color(0xFF0EA5E9),
            accentSuccess = Color(0xFF059669),
            accentGold = Color(0xFFD97706),
            onBackground = Color(0xFF0F172A),
            onSurfaceMuted = Color(0xFF475569)
        ),
        FmsThemePreset(
            id = "purple_dream",
            name = "Purple Dream",
            isDark = true,
            background = Color(0xFF0D081D),
            surface = Color(0xFF191233),
            surfaceElevated = Color(0xFF241B47),
            border = Color(0xFF3E2D73),
            primary = Color(0xFFA855F7),
            secondary = Color(0xFFEC4899),
            accentSuccess = Color(0xFF10B981),
            accentGold = Color(0xFFFBBF24),
            onBackground = Color(0xFFFAF5FF),
            onSurfaceMuted = Color(0xFFA79BC2)
        ),
        FmsThemePreset(
            id = "green_nature",
            name = "Green Nature",
            isDark = true,
            background = Color(0xFF061410),
            surface = Color(0xFF0E241D),
            surfaceElevated = Color(0xFF15352B),
            border = Color(0xFF235243),
            primary = Color(0xFF10B981),
            secondary = Color(0xFF34D399),
            accentSuccess = Color(0xFF22C55E),
            accentGold = Color(0xFFF59E0B),
            onBackground = Color(0xFFECFDF5),
            onSurfaceMuted = Color(0xFF8EB8A9)
        ),
        FmsThemePreset(
            id = "sunset",
            name = "Sunset",
            isDark = true,
            background = Color(0xFF160B12),
            surface = Color(0xFF26141F),
            surfaceElevated = Color(0xFF351D2C),
            border = Color(0xFF593047),
            primary = Color(0xFFF97316),
            secondary = Color(0xFFF43F5E),
            accentSuccess = Color(0xFF10B981),
            accentGold = Color(0xFFFBBF24),
            onBackground = Color(0xFFFFF7ED),
            onSurfaceMuted = Color(0xFFC9A6B5)
        ),
        FmsThemePreset(
            id = "custom",
            name = "Custom",
            isDark = true,
            background = Color(0xFF070B19),
            surface = Color(0xFF11182E),
            surfaceElevated = Color(0xFF1A2444),
            border = Color(0xFF2C3E73),
            primary = Color(0xFF3A7BD5),
            secondary = Color(0xFF00D2FF),
            accentSuccess = Color(0xFF10B981),
            accentGold = Color(0xFFF59E0B),
            onBackground = Color(0xFFF8FAFC),
            onSurfaceMuted = Color(0xFF94A3B8),
            customTheme = true
        )
    )

    private val _activeTheme = MutableStateFlow(presets.first())
    val activeTheme: StateFlow<FmsThemePreset> = _activeTheme.asStateFlow()

    private val _autoThemeEnabled = MutableStateFlow(false)
    val autoThemeEnabled: StateFlow<Boolean> = _autoThemeEnabled.asStateFlow()

    fun applyPreset(presetId: String) {
        val found = presets.firstOrNull { it.id == presetId } ?: presets.first()
        _activeTheme.value = found.copy(
            fontChoice = _activeTheme.value.fontChoice,
            iconStyle = _activeTheme.value.iconStyle
        )
    }

    fun applyCustomTheme(
        primary: Color,
        secondary: Color,
        fontChoice: FmsFontChoice,
        iconStyle: FmsIconStyle
    ) {
        val base = _activeTheme.value
        _activeTheme.value = base.copy(
            id = "custom",
            name = "Custom",
            primary = primary,
            secondary = secondary,
            fontChoice = fontChoice,
            iconStyle = iconStyle,
            customTheme = true
        )
    }

    fun setFontChoice(fontChoice: FmsFontChoice) {
        _activeTheme.value = _activeTheme.value.copy(fontChoice = fontChoice)
    }

    fun setIconStyle(iconStyle: FmsIconStyle) {
        _activeTheme.value = _activeTheme.value.copy(iconStyle = iconStyle)
    }

    /**
     * FMSAutoTheme: Adapts theme brightness/accent automatically by system dark mode or time-of-day.
     */
    fun toggleAutoTheme(enabled: Boolean, systemDark: Boolean) {
        _autoThemeEnabled.value = enabled
        if (enabled) {
            applyPreset(if (systemDark) "dark_neon" else "light_blue")
        }
    }

    fun toColorScheme(preset: FmsThemePreset): ColorScheme {
        return if (preset.isDark) {
            darkColorScheme(
                primary = preset.primary,
                onPrimary = Color(0xFF04101C),
                secondary = preset.secondary,
                onSecondary = Color.White,
                tertiary = preset.accentGold,
                background = preset.background,
                onBackground = preset.onBackground,
                surface = preset.surface,
                onSurface = preset.onBackground,
                surfaceVariant = preset.surfaceElevated,
                onSurfaceVariant = preset.onSurfaceMuted,
                outline = preset.border,
                error = Color(0xFFEF4444),
                onError = Color.White
            )
        } else {
            lightColorScheme(
                primary = preset.primary,
                onPrimary = Color.White,
                secondary = preset.secondary,
                onSecondary = Color.White,
                tertiary = preset.accentGold,
                background = preset.background,
                onBackground = preset.onBackground,
                surface = preset.surface,
                onSurface = preset.onBackground,
                surfaceVariant = preset.surfaceElevated,
                onSurfaceVariant = preset.onSurfaceMuted,
                outline = preset.border,
                error = Color(0xFFDC2626),
                onError = Color.White
            )
        }
    }

    fun toTypography(preset: FmsThemePreset): Typography {
        val bodyFont = when (preset.fontChoice) {
            FmsFontChoice.SPACE_GROTESK -> SpaceGroteskFamily
            FmsFontChoice.PLUS_JAKARTA_SANS -> PlusJakartaFamily
            FmsFontChoice.JETBRAINS_MONO -> JetBrainsMonoFamily
        }
        val displayFont = SpaceGroteskFamily
        return Typography(
            displaySmall = TextStyle(
                fontFamily = displayFont,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                lineHeight = 36.sp
            ),
            headlineMedium = TextStyle(
                fontFamily = displayFont,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                lineHeight = 30.sp
            ),
            titleLarge = TextStyle(
                fontFamily = displayFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 26.sp
            ),
            titleMedium = TextStyle(
                fontFamily = bodyFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 22.sp
            ),
            bodyLarge = TextStyle(
                fontFamily = bodyFont,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                lineHeight = 22.sp
            ),
            bodyMedium = TextStyle(
                fontFamily = bodyFont,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                lineHeight = 19.sp
            ),
            labelLarge = TextStyle(
                fontFamily = bodyFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp
            ),
            labelSmall = TextStyle(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        )
    }

    fun Color.toHex(): String {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        val r = (red * 255).toInt().coerceIn(0, 255)
        val g = (green * 255).toInt().coerceIn(0, 255)
        val b = (blue * 255).toInt().coerceIn(0, 255)
        return if (a == 255) "#%02X%02X%02X".format(r, g, b) else "#%02X%02X%02X%02X".format(a, r, g, b)
    }
}
