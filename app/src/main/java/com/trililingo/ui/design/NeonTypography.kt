package com.trililingo.ui.design

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Mantém compatibilidade:
 * - `NeonTypography` continua existindo (default scale = 1f)
 * - `rememberNeonTypography()` adapta tamanhos conforme screenWidthDp
 */
val NeonTypography: Typography = buildNeonTypography(scale = 1f)

fun buildNeonTypography(scale: Float): Typography {
    fun fs(v: Int) = (v * scale).sp
    fun ls(v: Float) = (v * scale).sp

    return Typography(
        headlineMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.ExtraBold,
            fontSize = fs(28),
            letterSpacing = ls(1.2f)
        ),
        titleLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = fs(20),
            letterSpacing = ls(0.6f)
        ),
        titleMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = fs(16)
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = fs(16),
            lineHeight = fs(22).value.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = fs(14),
            lineHeight = fs(20).value.sp
        ),
        displayLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.ExtraBold,
            fontSize = fs(64),
            letterSpacing = ls(0.5f)
        )
    )
}

@Composable
fun rememberNeonTypography(): Typography {
    val cfg = LocalConfiguration.current
    val w = cfg.screenWidthDp

    val scale = when {
        w < 360 -> 0.92f
        w < 600 -> 1.00f
        w < 840 -> 1.07f
        else -> 1.14f
    }

    return remember(scale) { buildNeonTypography(scale) }
}
