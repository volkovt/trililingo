package com.trililingo.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * NeonBottomSheet (Material3 ModalBottomSheet) — reutilizável e "premium":
 * - Drag handle neon (leve e sem blur caro)
 * - Header sticky (fica fixo enquanto o conteúdo rola)
 * - Footer fixo opcional (ações sempre visíveis)
 *
 * Importante:
 * - Para rolagem, coloque um LazyColumn/LazyGrid/Column(scroll) dentro do `content { }`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeonBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    headerLeading: (@Composable () -> Unit)? = null,
    headerTrailing: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    showDragHandle: Boolean = true,
    skipPartiallyExpanded: Boolean = true,
    maxHeightFraction: Float = 0.92f,
    containerPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = skipPartiallyExpanded
    )

    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val maxHeight = remember(cfg.screenHeightDp, maxHeightFraction) {
        (cfg.screenHeightDp.dp * maxHeightFraction)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = if (showDragHandle) {
            { NeonDragHandle() }
        } else null
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(containerPadding)
                .heightIn(max = maxHeight),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val showHeader =
                    !title.isNullOrBlank() || !subtitle.isNullOrBlank() || headerLeading != null || headerTrailing != null

                // =========================
                // Header sticky (não rola)
                // =========================
                if (showHeader) {
                    NeonSheetHeader(
                        title = title,
                        subtitle = subtitle,
                        leading = headerLeading,
                        trailing = headerTrailing
                    )
                    NeonDividerSoft()
                }

                // =========================
                // Body
                // =========================
                if (footer != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true)
                            .defaultMinSize(minHeight = 1.dp)
                    ) {
                        content()
                    }

                    NeonDividerSoft()

                    // =========================
                    // Footer fixo (não rola)
                    // =========================
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        tonalElevation = 0.dp
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            footer()
                        }
                    }
                } else {
                    content()
                }

                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun NeonSheetHeader(
    title: String?,
    subtitle: String?,
    leading: (@Composable () -> Unit)?,
    trailing: (@Composable () -> Unit)?
) {
    val cs = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (leading != null) leading()

        Column(modifier = Modifier.weight(1f)) {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (trailing != null) trailing()
    }
}

@Composable
private fun NeonDividerSoft() {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp),
        color = cs.outline.copy(alpha = 0.25f),
        content = {}
    )
}

@Composable
private fun NeonDragHandle(
    width: Dp = 54.dp,
    height: Dp = 6.dp
) {
    val cs = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        // Glow leve
        Surface(
            modifier = Modifier
                .shadow(elevation = 10.dp, shape = RoundedCornerShape(999.dp))
                .width(width)
                .height(height),
            color = cs.tertiary.copy(alpha = 0.12f),
            shape = RoundedCornerShape(999.dp),
            tonalElevation = 0.dp
        ) {}

        // Handle principal
        Surface(
            modifier = Modifier
                .width(width)
                .height(height),
            color = cs.surfaceVariant.copy(alpha = 0.55f),
            shape = RoundedCornerShape(999.dp),
            border = BorderStroke(1.dp, cs.tertiary.copy(alpha = 0.55f)),
            tonalElevation = 0.dp
        ) {
            // “miolo” brilhante
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier
                        .width(width * 0.55f)
                        .height(2.dp),
                    color = cs.tertiary.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(999.dp),
                    content = {}
                )
            }
        }
    }
}
