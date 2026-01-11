package com.trililingo.ui.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color

private val UpsideDownColors = darkColorScheme(
    // Vermelhos “Upside Down”
    primary = Color(0xFFE50914),
    secondary = Color(0xFFB00020),
    tertiary = Color(0xFFFF3B3B),

    // Base super escura (não é preto chapado)
    background = Color(0xFF050208),
    surface = Color(0xFF0C020A),
    surfaceVariant = Color(0xFF14030F),

    // Texto sempre claro
    onPrimary = Color(0xFF12010A),
    onSecondary = Color(0xFF12010A),
    onTertiary = Color(0xFF12010A),
    onBackground = Color(0xFFF3EAF0),
    onSurface = Color(0xFFF3EAF0),
    onSurfaceVariant = Color(0xFFE6D5DE),

    outline = Color(0xFF3A0A18),
    error = Color(0xFFFF355D),
    onError = Color(0xFF12010A)
)

@Composable
fun NeonTheme(content: @Composable () -> Unit) {
    val typography = rememberNeonTypography()

    MaterialTheme(
        colorScheme = UpsideDownColors,
        typography = typography
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            UpsideDownBackground(
                enableScanlines = true,
                enableMist = true,
                enableVines = true,
                content = content
            )
        }
    }
}
