package com.trililingo.ui.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class NeonDimens(
    val cardCorner: Dp,
    val cardPadding: Dp,
    val cardBorder: Dp,

    val buttonCorner: Dp,
    val buttonHeight: Dp,
    val buttonBorder: Dp,
    val buttonMinWidth: Dp,

    val glowBlur: Dp,
    val glowAlphaMin: Float,
    val glowAlphaMax: Float,

    val glitchAmpDp: Float
)

@Composable
fun rememberNeonDimens(): NeonDimens {
    val cfg = LocalConfiguration.current
    val w = cfg.screenWidthDp

    return remember(w) {
        when {
            w < 360 -> NeonDimens(
                cardCorner = 16.dp,
                cardPadding = 12.dp,
                cardBorder = 1.dp,

                buttonCorner = 14.dp,
                buttonHeight = 48.dp,
                buttonBorder = 1.dp,
                buttonMinWidth = 140.dp,

                glowBlur = 12.dp,
                glowAlphaMin = 0.14f,
                glowAlphaMax = 0.36f,

                glitchAmpDp = 0.9f
            )

            w < 600 -> NeonDimens(
                cardCorner = 18.dp,
                cardPadding = 14.dp,
                cardBorder = 1.dp,

                buttonCorner = 16.dp,
                buttonHeight = 52.dp,
                buttonBorder = 1.dp,
                buttonMinWidth = 160.dp,

                glowBlur = 14.dp,
                glowAlphaMin = 0.18f,
                glowAlphaMax = 0.42f,

                glitchAmpDp = 1.2f
            )

            w < 840 -> NeonDimens(
                cardCorner = 20.dp,
                cardPadding = 16.dp,
                cardBorder = 1.dp,

                buttonCorner = 18.dp,
                buttonHeight = 56.dp,
                buttonBorder = 1.dp,
                buttonMinWidth = 200.dp,

                glowBlur = 16.dp,
                glowAlphaMin = 0.18f,
                glowAlphaMax = 0.44f,

                glitchAmpDp = 1.35f
            )

            else -> NeonDimens(
                cardCorner = 22.dp,
                cardPadding = 18.dp,
                cardBorder = 1.dp,

                buttonCorner = 20.dp,
                buttonHeight = 58.dp,
                buttonBorder = 1.dp,
                buttonMinWidth = 220.dp,

                glowBlur = 18.dp,
                glowAlphaMin = 0.18f,
                glowAlphaMax = 0.46f,

                glitchAmpDp = 1.45f
            )
        }
    }
}

@Composable
fun NeonCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val d = rememberNeonDimens()
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(d.cardCorner)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cs.surfaceVariant)
            .border(
                width = d.cardBorder,
                color = cs.outline.copy(alpha = 0.75f),
                shape = shape
            )
            .padding(d.cardPadding)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

@Composable
fun NeonButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fullWidth: Boolean = true,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val d = rememberNeonDimens()
    val shape = RoundedCornerShape(d.buttonCorner)

    val infinite = rememberInfiniteTransition(label = "upside_btn")

    val glowAlpha by infinite.animateFloat(
        initialValue = d.glowAlphaMin,
        targetValue = d.glowAlphaMax,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val glitch by infinite.animateFloat(
        initialValue = -d.glitchAmpDp,
        targetValue = d.glitchAmpDp,
        animationSpec = infiniteRepeatable(
            animation = tween(220, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glitch"
    )

    val brush = Brush.horizontalGradient(
        listOf(
            cs.secondary.copy(alpha = 0.95f),
            cs.primary.copy(alpha = 0.95f),
            cs.tertiary.copy(alpha = 0.95f)
        )
    )

    val outer = modifier.then(
        if (fullWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth()
    )

    Box(modifier = outer) {
        // Glow externo acompanha exatamente o tamanho do botão
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(brush = brush, alpha = glowAlpha)
                .blur(d.glowBlur)
        )

        val buttonMod =
            (if (fullWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth().widthIn(min = d.buttonMinWidth))
                .height(d.buttonHeight)
                .border(
                    width = d.buttonBorder,
                    color = cs.primary.copy(alpha = 0.55f),
                    shape = shape
                )

        Button(
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = cs.surfaceVariant,
                contentColor = cs.onSurface
            ),
            modifier = buttonMod,
            shape = shape
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = text,
                    fontSize = 16.sp,
                    color = cs.tertiary.copy(alpha = 0.35f),
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
                Text(
                    text = text,
                    fontSize = 16.sp,
                    color = cs.tertiary.copy(alpha = 0.35f),
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .clip(shape)
                )

                // glitch layer 1
                Text(
                    text = text,
                    fontSize = 16.sp,
                    color = cs.tertiary.copy(alpha = 0.35f),
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .align(Alignment.Center)
                        .then(Modifier)
                        .run { androidx.compose.ui.Modifier }
                )

                Text(
                    text = text,
                    fontSize = 16.sp,
                    color = cs.tertiary.copy(alpha = 0.35f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 2.dp)
                )

                Text(
                    text = text,
                    fontSize = 16.sp,
                    color = cs.tertiary.copy(alpha = 0.35f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 2.dp)
                        .offset(x = glitch.dp, y = (-glitch * 0.4f).dp)
                )
                Text(
                    text = text,
                    fontSize = 16.sp,
                    color = cs.secondary.copy(alpha = 0.28f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 2.dp)
                        .offset(x = (-glitch).dp, y = (glitch * 0.35f).dp)
                )

                // texto final “limpo”
                Text(
                    text = text,
                    fontSize = 16.sp,
                    color = cs.onSurface,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}
