package be.rimeco.weekmenu.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF8F4A00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCC2),
    onPrimaryContainer = Color(0xFF2F1500),
    secondary = Color(0xFF745944),
    secondaryContainer = Color(0xFFE8CCB6),
    tertiary = Color(0xFF4C6B3A),
    background = Color(0xFFFFF8F4),
    surface = Color(0xFFFFF8F4),
    surfaceVariant = Color(0xFFF4E0D2),
    onSurface = Color(0xFF211A14),
    onSurfaceVariant = Color(0xFF52443A),
    outline = Color(0xFF85736A),
    outlineVariant = Color(0xFFD7C3B7),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB77A),
    onPrimary = Color(0xFF4C2600),
    primaryContainer = Color(0xFF6D3800),
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFFE3C0A5),
    secondaryContainer = Color(0xFF5A4130),
    tertiary = Color(0xFFB1D199),
    background = Color(0xFF1F1B16),
    surface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFF3A2F26),
    onSurface = Color(0xFFEBE1D9),
    onSurfaceVariant = Color(0xFFD7C3B7),
    outline = Color(0xFF9F8D82),
    outlineVariant = Color(0xFF52443A),
)

@Composable
fun WeekmenuTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
