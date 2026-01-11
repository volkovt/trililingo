package com.trililingo.ui.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun UpsideDownBackground(
    enableScanlines: Boolean,
    enableMist: Boolean,
    enableVines: Boolean,
    content: @Composable () -> Unit
) {
    val cs = androidx.compose.material3.MaterialTheme.colorScheme

    val infinite = rememberInfiniteTransition(label = "upside_time")
    val t by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "t"
    )

    val base = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF020107),
            cs.background,
            Color(0xFF09010B),
            Color(0xFF020107)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(base)
    ) {
        if (enableMist) RedMistLayer(phase = t)
        if (enableVines) HiveVinesLayer(phase = t)
        if (enableScanlines) ScanlinesLayer(phase = t)

        content()
    }
}

@Composable
private fun RedMistLayer(phase: Float) {
    val cs = androidx.compose.material3.MaterialTheme.colorScheme

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val base = min(w, h)

        fun fog(center: Offset, radius: Float, alpha: Float) {
            val brush = Brush.radialGradient(
                colors = listOf(
                    cs.primary.copy(alpha = alpha),
                    cs.secondary.copy(alpha = alpha * 0.6f),
                    Color.Transparent
                ),
                center = center,
                radius = radius
            )
            drawCircle(brush = brush, radius = radius, center = center)
        }

        val p1 = Offset(w * (0.25f + 0.05f * sin(phase * 2f * PI).toFloat()), h * 0.25f)
        val p2 = Offset(w * 0.75f, h * (0.55f + 0.05f * sin((phase + 0.33f) * 2f * PI).toFloat()))
        val p3 = Offset(w * (0.55f + 0.06f * sin((phase + 0.66f) * 2f * PI).toFloat()), h * 0.85f)

        // ✅ radius baseado no menor eixo (fica bom em landscape e tablets)
        fog(p1, radius = base * 0.90f, alpha = 0.10f)
        fog(p2, radius = base * 0.78f, alpha = 0.09f)
        fog(p3, radius = base * 1.02f, alpha = 0.08f)

        drawRect(
            brush = Brush.verticalGradient(
                listOf(cs.tertiary.copy(alpha = 0.08f), Color.Transparent)
            )
        )
    }
}

@Composable
private fun ScanlinesLayer(phase: Float) {
    val cs = androidx.compose.material3.MaterialTheme.colorScheme

    Canvas(modifier = Modifier.fillMaxSize()) {
        val h = size.height

        val flicker = 0.035f + 0.015f * sin((phase * 2f * PI) * 2f).toFloat()

        // ✅ step em dp -> px (fica consistente em densidades diferentes)
        val step = 9.dp.toPx()
        var y = (phase * step * 6f) % step

        while (y < h) {
            drawLine(
                color = cs.onBackground.copy(alpha = flicker),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
            y += step
        }
    }
}

@Composable
private fun HiveVinesLayer(phase: Float) {
    val cs = androidx.compose.material3.MaterialTheme.colorScheme
    val cfg = LocalConfiguration.current
    val wDp = cfg.screenWidthDp

    // ✅ responsivo: mais vines em telas maiores
    val vineCount = when {
        wDp < 360 -> 6
        wDp < 600 -> 9
        wDp < 840 -> 11
        else -> 13
    }

    data class Vine(
        val xNorm: Float,
        val seed: Int,
        val thickness: Float,
        val sway: Float,
        val branchiness: Float
    )

    val rng = remember(vineCount) { Random(1337) }
    val vines = remember(vineCount) {
        List(vineCount) {
            Vine(
                xNorm = rng.nextFloat(),
                seed = rng.nextInt(),
                thickness = 1.1f + rng.nextFloat() * 2.4f,
                sway = 12f + rng.nextFloat() * 28f,
                branchiness = 0.28f + rng.nextFloat() * 0.42f
            )
        }
    }
    val paths = remember(vineCount) { List(vineCount) { Path() } }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val vineColor = cs.secondary.copy(alpha = 0.55f)
        val highlight = cs.tertiary.copy(alpha = 0.25f)

        val time = phase * 2f * PI

        for (i in vines.indices) {
            val v = vines[i]
            val path = paths[i]
            path.reset()

            val xBase = w * v.xNorm
            val segments = 7

            val startY = -h * 0.10f
            path.moveTo(xBase, startY)

            var lastX = xBase
            var lastY = startY

            for (s in 1..segments) {
                val tt = s / segments.toFloat()
                val y = h * (tt * 1.08f)

                val sway = v.sway * sin(time * (0.7f + v.xNorm) + tt * 5.2f + v.seed).toFloat()
                val x = xBase + sway

                val cx = (lastX + x) * 0.5f + (sway * 0.15f)
                val cy = (lastY + y) * 0.5f

                path.quadraticBezierTo(cx, cy, x, y)

                // galhos: mantém leve, mas reduz em telas pequenas
                val allowBranches = (wDp >= 360)
                if (allowBranches && s in 2..(segments - 1) && (v.branchiness > 0.35f)) {
                    val dir = if ((v.seed + s) % 2 == 0) 1f else -1f
                    val bx = x + dir * (18f + 22f * sin(time + tt * 3.2f).toFloat())
                    val by = y + 10f

                    drawLine(
                        color = highlight,
                        start = Offset(x, y),
                        end = Offset(bx, by),
                        strokeWidth = v.thickness * 0.65f
                    )
                }

                lastX = x
                lastY = y
            }

            drawPath(
                path = path,
                color = vineColor,
                style = Stroke(width = v.thickness)
            )

            drawPath(
                path = path,
                color = cs.primary.copy(alpha = 0.10f),
                style = Stroke(width = v.thickness * 0.55f)
            )
        }
    }
}
