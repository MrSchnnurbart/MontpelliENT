package fr.montpellient.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.Contrast
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import fr.montpellient.core.model.AppSettings
import fr.montpellient.core.model.ContrastLevel
import fr.montpellient.core.model.ThemeMode

/** Paramètres d'apparence extraits des réglages. */
@Immutable
data class ThemeConfig(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val seedColor: Long = 0xFF00467F,
    val amoledBlack: Boolean = false,
    val contrast: ContrastLevel = ContrastLevel.STANDARD,
) {
    companion object {
        fun from(s: AppSettings) = ThemeConfig(s.themeMode, s.dynamicColor, s.seedColor, s.amoledBlack, s.contrast)
    }
}

/** Indique aux écrans si le thème courant est sombre (couleurs par matière, etc.). */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

val isDynamicColorSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun MontpelliENTTheme(
    config: ThemeConfig = ThemeConfig(),
    content: @Composable () -> Unit,
) {
    val dark = when (config.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = colorSchemeFor(config, dark)

    androidx.compose.runtime.CompositionLocalProvider(LocalIsDarkTheme provides dark) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

@Composable
private fun colorSchemeFor(config: ThemeConfig, dark: Boolean): ColorScheme {
    val useWallpaper = config.dynamicColor && isDynamicColorSupported
    val context = LocalContext.current

    // Material You « pur » : schéma système, avec éventuellement le noir AMOLED.
    if (useWallpaper && config.contrast == ContrastLevel.STANDARD) {
        val system = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        return if (dark && config.amoledBlack) system.toAmoled() else system
    }

    // Sinon : schéma généré à partir d'une couleur source (palette, couleur libre ou fond d'écran).
    val seed = if (useWallpaper) dynamicLightColorScheme(context).primary else Color(config.seedColor)
    return rememberDynamicColorScheme(
        seedColor = seed,
        isDark = dark,
        isAmoled = dark && config.amoledBlack,
        style = PaletteStyle.TonalSpot,
        contrastLevel = config.contrast.toMaterialKolor().value,
    )
}

private fun ContrastLevel.toMaterialKolor(): Contrast = when (this) {
    ContrastLevel.STANDARD -> Contrast.Default
    ContrastLevel.MEDIUM -> Contrast.Medium
    ContrastLevel.HIGH -> Contrast.High
}

/** Fonds et surfaces noirs pour les écrans OLED. */
private fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222),
)
