package com.forge.workout.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.forge.workout.R

/** Palette lifted from the Forge design. */
object C {
    val Bg = Color(0xFF0C0C0E)
    val Card = Color(0xFF141417)
    val CardAlt = Color(0xFF131316)
    val Panel = Color(0xFF15161A)
    val Chip = Color(0xFF1F2025)
    val Border = Color(0xFF212227)
    val BorderSoft = Color(0xFF24252A)
    val Line = Color(0xFF17181B)
    val Track = Color(0xFF202126)

    val Accent = Color(0xFFD8FB52)
    val AccentText = Color(0xFFC6E75F)
    val Blue = Color(0xFF8AD1FF)
    val OnAccent = Color(0xFF0D1005)

    val Text = Color(0xFFF4F4F2)
    val Muted = Color(0xFF83838C)
    val Dim = Color(0xFF71717A)
    val Faint = Color(0xFF5C5C64)
    val Ghost = Color(0xFF6D6D76)

    val DayHeader = Color(0xFF9EA78E)
    val DayEyebrow = Color(0xFFA8B98A)
    val Light = Color(0xFFF4F4F2)
}

val Anton = FontFamily(Font(R.font.anton_regular, FontWeight.Normal))

@OptIn(ExperimentalTextApi::class)
private fun archivo(axis: Int, weight: FontWeight) = Font(
    resId = R.font.archivo_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(axis)),
)

val Archivo = FontFamily(
    archivo(400, FontWeight.Normal),
    archivo(500, FontWeight.Medium),
    archivo(600, FontWeight.SemiBold),
    archivo(700, FontWeight.Bold),
    archivo(800, FontWeight.ExtraBold),
)

/**
 * Display type — Anton, as used for every headline and numeral in the design.
 * [line] and [track] are multipliers of the font size, mirroring the CSS `font: 400 40px/.92`
 * and `letter-spacing: .01em` shorthand the design uses.
 */
fun display(size: Double, color: Color = C.Text, line: Double = 1.0, track: Double = 0.01) = TextStyle(
    fontFamily = Anton,
    fontWeight = FontWeight.Normal,
    fontSize = size.sp,
    lineHeight = (size * line).sp,
    letterSpacing = (size * track).sp,
    color = color,
)

/** Body/UI type — Archivo. [weight] is the CSS numeric weight, [track] and [line] are em multipliers. */
fun arch(
    size: Double,
    weight: Int = 500,
    color: Color = C.Text,
    track: Double = 0.0,
    line: Double = 1.3,
) = TextStyle(
    fontFamily = Archivo,
    fontWeight = when {
        weight >= 800 -> FontWeight.ExtraBold
        weight >= 700 -> FontWeight.Bold
        weight >= 600 -> FontWeight.SemiBold
        weight >= 500 -> FontWeight.Medium
        else -> FontWeight.Normal
    },
    fontSize = size.sp,
    lineHeight = (size * line).sp,
    letterSpacing = (size * track).sp,
    color = color,
)

@Composable
fun ForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Accent,
            onPrimary = C.OnAccent,
            background = C.Bg,
            onBackground = C.Text,
            surface = C.Card,
            onSurface = C.Text,
        ),
        content = content,
    )
}
