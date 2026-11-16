@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package ai.roadie.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.unit.sp
import ai.roadie.app.R
import ai.roadie.core.Theme

val Pink = Color(0xFFF3A6CE)
val Lime = Color(0xFFDDFF82)
val Cream = Color(0xFFFFFEF5)
val Silver = Color(0xFFBFC3CB)
val Ink = Color(0xFF24262B)
val Night = Color(0xFF16181C)
val Korean = FontFamily(
    Font(R.font.noto_sans_kr, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.noto_sans_kr, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.noto_sans_kr, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)
val Brand = FontFamily(Font(R.font.dm_sans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))))
val Mono = FontFamily(Font(R.font.space_mono))

@Composable fun RoadieTheme(theme: Theme, content: @Composable () -> Unit) {
    val colors = if (theme == Theme.NIGHT) darkColorScheme(
        primary = Lime, onPrimary = Ink, background = Night, onBackground = Cream,
        surface = Color(0xFF22252B), onSurface = Cream, onSurfaceVariant = Color(0xFFA9ADB3),
        secondary = Pink, outline = Color(0xFF555961), error = Color(0xFFFFB4AB),
    ) else lightColorScheme(
        primary = Lime, onPrimary = Ink, background = Cream, onBackground = Ink,
        surface = Color(0xFFF4F2E8), onSurface = Ink, onSurfaceVariant = Color(0xFF64666C),
        secondary = Pink, outline = Color(0xFF8A8C90), error = Color(0xFF9C3030),
    )
    MaterialTheme(colorScheme = colors, typography = Typography(
        displayLarge = TextStyle(fontFamily = Korean, fontSize = 52.sp, lineHeight = 72.sp, fontWeight = FontWeight.Medium),
        headlineLarge = TextStyle(fontFamily = Korean, fontSize = 40.sp, lineHeight = 54.sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontFamily = Korean, fontSize = 24.sp, lineHeight = 36.sp),
        bodyMedium = TextStyle(fontFamily = Korean, fontSize = 20.sp, lineHeight = 30.sp),
        labelLarge = TextStyle(fontFamily = Korean, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium),
        labelMedium = TextStyle(fontFamily = Korean, fontSize = 18.sp, lineHeight = 26.sp),
    )) {
        CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
    }
}
