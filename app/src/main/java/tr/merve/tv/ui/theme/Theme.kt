@file:OptIn(ExperimentalTextApi::class)

package tr.merve.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import tr.merve.tv.R
import tr.merve.tv.data.Motion

object C {
    val Ink = Color(0xFF0A0B10)
    val Surface = Color(0xFF14161F)
    val Surface2 = Color(0xFF1C1F2B)
    val Line = Color(0xFF2A2E3D)
    val Text = Color(0xFFF4F2EE)
    val Soft = Color(0xFFC9CBD6)
    val Muted = Color(0xFFA3A7B7)
    val Accent = Color(0xFFFF4F79)
    val AccentSoft = Color(0xFFFF8FA8)
    val Live = Color(0xFF2ED3B7)
    val Violet = Color(0xFF5B4BFF)
}

private fun outfit(w: Int) = Font(
    R.font.outfit, FontWeight(w), FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

val Outfit = FontFamily(outfit(400), outfit(500), outfit(600), outfit(700))

val Fraunces = FontFamily(
    Font(
        R.font.fraunces_italic, FontWeight.Bold, FontStyle.Italic,
        variationSettings = FontVariation.Settings(FontVariation.weight(700), FontVariation.Setting("opsz", 144f)),
    ),
)

object T {
    val Hero = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 60.sp, letterSpacing = (-1.5).sp, color = C.Text)
    val H1 = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.5).sp, color = C.Text)
    val H2 = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = C.Text)
    val Body = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Normal, fontSize = 17.sp, color = C.Soft)
    val Label = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = C.Text)
    val Small = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Normal, fontSize = 13.sp, color = C.Muted)
    val Badge = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.2.sp)
    val Wordmark = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, color = C.Text)
}

val LocalMotion = staticCompositionLocalOf { Motion.Full }

@Composable
fun MerveTheme(motion: Motion, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Accent,
            onPrimary = C.Ink,
            background = C.Ink,
            onBackground = C.Text,
            surface = C.Surface,
            onSurface = C.Text,
            surfaceVariant = C.Surface2,
            onSurfaceVariant = C.Soft,
            border = C.Line,
        ),
    ) {
        CompositionLocalProvider(LocalMotion provides motion, content = content)
    }
}
