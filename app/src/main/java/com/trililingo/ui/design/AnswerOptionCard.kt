package com.trililingo.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Estado visual compartilhado entre Idiomas (Lesson), Subjects e (se desejar) Result/Reviews no futuro.
 */
enum class OptionVisualState {
    DEFAULT,
    CORRECT,
    WRONG_SELECTED,
    CORRECT_SELECTED,
    DISABLED
}

/**
 * Card único para alternativas.
 *
 * - Tap curto (quando enabled=true): chama onSelect()
 * - Long press (sempre): abre um BottomSheet com o texto completo (sem submeter)
 *
 * Assim você resolve o problema de truncamento do texto nas opções e garante consistência visual.
 */
@Composable
fun AnswerOptionCard(
    text: String,
    enabled: Boolean,
    visualState: OptionVisualState,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    maxLinesCollapsed: Int = 2,
    sheetTitle: String = "Visualizar opção",
    sheetSubtitle: String? = null
) {
    val cs = MaterialTheme.colorScheme

    var showSheet by remember(text) { mutableStateOf(false) }

    val border = when (visualState) {
        OptionVisualState.CORRECT, OptionVisualState.CORRECT_SELECTED -> cs.tertiary
        OptionVisualState.WRONG_SELECTED -> cs.error
        else -> cs.outline
    }.copy(alpha = 0.85f)

    val bg = when (visualState) {
        OptionVisualState.CORRECT, OptionVisualState.CORRECT_SELECTED -> cs.tertiary.copy(alpha = 0.18f)
        OptionVisualState.WRONG_SELECTED -> cs.error.copy(alpha = 0.14f)
        else -> cs.surfaceVariant.copy(alpha = 0.85f)
    }

    val labelPrefix = when (visualState) {
        OptionVisualState.CORRECT -> "✅ "
        OptionVisualState.CORRECT_SELECTED -> "✅ "
        OptionVisualState.WRONG_SELECTED -> "❌ "
        else -> ""
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            // IMPORTANT:
            // - enabled do combinedClickable controla TODAS as interações.
            // - nós queremos long press funcionando mesmo quando enabled=false.
            // Então: enabled = true e nós mesmos bloqueamos o onClick.
            .combinedClickable(
                enabled = true,
                onClick = { if (enabled) onSelect() },
                onLongClick = { showSheet = true }
            ),
        color = bg,
        contentColor = cs.onSurface,
        border = BorderStroke(1.dp, border),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = labelPrefix + text,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = maxLinesCollapsed,
                overflow = TextOverflow.Ellipsis
            )

            // mini hint sutil de UX (sem poluir): quando enabled, sugere que dá pra segurar
            if (enabled) {
                Text(
                    text = "↗",
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurfaceVariant
                )
            }
        }
    }

    if (showSheet) {
        NeonBottomSheet(
            onDismiss = { showSheet = false },
            title = sheetTitle,
            subtitle = sheetSubtitle ?: "Toque e segure para ler tudo (sem responder).",
            headerTrailing = {
                SimpleSheetChip(label = "Fechar") { showSheet = false }
            },
            maxHeightFraction = 0.92f
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = cs.surfaceVariant.copy(alpha = 0.62f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.28f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Texto completo", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        SelectionContainer {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = cs.onSurface
                            )
                        }
                    }
                }

                Text(
                    text = "Dica: toque curto seleciona; segurar abre este painel.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )

                Spacer(Modifier.height(4.dp))
                NeonButton(text = "Fechar") { showSheet = false }
            }
        }
    }
}

@Composable
private fun SimpleSheetChip(
    label: String,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .combinedClickable(
                enabled = true,
                onClick = onClick,
                onLongClick = onClick
            ),
        color = cs.surfaceVariant.copy(alpha = 0.72f),
        contentColor = cs.onSurface,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.25f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
