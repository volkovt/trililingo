package com.trililingo.ui.screens.lesson

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.trililingo.R

@Composable
fun ChallengeCompleteOverlay(
    visible: Boolean,
    xp: Int,
    correct: Int,
    wrong: Int
) {
    if (!visible) return

    val cs = MaterialTheme.colorScheme
    val infinite = rememberInfiniteTransition(label = "complete_anim")

    val pulse by infinite.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(520, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val glow by infinite.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    val ctx = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        // glow atrás
        Box(
            modifier = Modifier
                .size(260.dp)
                .blur(30.dp)
                .background(cs.tertiary.copy(alpha = glow))
        )

        Surface(
            color = cs.surfaceVariant.copy(alpha = 0.92f),
            contentColor = cs.onSurface,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 22.dp, vertical = 18.dp)
                    .widthIn(min = 260.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // SVG do cubo
                AsyncImage(
                    model = ImageRequest.Builder(ctx)
                        .data(R.raw.game_over)
                        .decoderFactory(SvgDecoder.Factory())
                        .build(),
                    contentDescription = "Cubo mágico (Stranger Things)",
                    modifier = Modifier
                        .size(160.dp)
                        .scale(pulse)
                )

                Spacer(Modifier.height(10.dp))
                Text("Sessão concluída!", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text("XP +$xp", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(6.dp))
                Text("✅ $correct  •  ❌ $wrong", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
