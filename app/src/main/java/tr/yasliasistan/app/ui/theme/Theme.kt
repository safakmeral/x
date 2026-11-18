package tr.yasliasistan.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// NFG-01: high contrast (near-black on white, dark blue accents, contrast > 7:1)
// and large type. Sizes are in sp, so the phone's own font-size setting scales them further.
private val Ink = Color(0xFF111111)
private val Paper = Color(0xFFFFFFFF)
private val Accent = Color(0xFF0B3D91)
private val Highlight = Color(0xFFFFD400) // V-05: yellow highlight colour
private val Error = Color(0xFFB00020) // V-05: red is used only for errors

private val HighContrastColors = lightColorScheme(
    primary = Accent,
    onPrimary = Paper,
    secondary = Highlight,
    onSecondary = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Paper,
    onSurfaceVariant = Ink,
    outline = Ink,
    error = Error,
    onError = Paper,
)

private val LargeTypography = Typography(
    headlineLarge = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 24.sp, lineHeight = 34.sp),
    bodyMedium = TextStyle(fontSize = 22.sp, lineHeight = 30.sp),
    labelLarge = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
)

@Composable
fun AssistantTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HighContrastColors,
        typography = LargeTypography,
        content = content,
    )
}
