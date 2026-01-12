package com.trililingo.ui.screens.track

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trililingo.domain.subject.StudyDifficulty
import com.trililingo.ui.catalog.CatalogIds
import com.trililingo.ui.catalog.FeatureUi
import com.trililingo.ui.catalog.TrackUi
import com.trililingo.ui.design.NeonButton
import com.trililingo.ui.design.NeonCard

@Composable
fun TrackHubScreen(
    track: TrackUi,
    selectedDifficulty: StudyDifficulty,
    onDifficultyChange: (StudyDifficulty) -> Unit,
    onBack: () -> Unit,
    onOpenFeature: (FeatureUi, StudyDifficulty) -> Unit
) {
    val enabledFeatures = track.features.filter { it.enabled }

    val isLanguageTrack = track.subjectId == CatalogIds.SUBJECT_LANGUAGES
    val effectiveDifficulty = if (isLanguageTrack) selectedDifficulty else StudyDifficulty.BASIC

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 18.dp)
    ) {
        item {
            NeonCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.padding(top = 6.dp))
                Text(track.subtitle, style = MaterialTheme.typography.bodyLarge)

                Spacer(Modifier.padding(top = 12.dp))

                if (isLanguageTrack) {
                    Text("Dificuldade", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.padding(top = 6.dp))
                    Text(
                        "Define como você responde cada pergunta (opções vs digitar).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.padding(top = 10.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            DifficultyChip(
                                selected = selectedDifficulty == StudyDifficulty.BASIC,
                                title = StudyDifficulty.BASIC.title,
                                subtitle = StudyDifficulty.BASIC.subtitle,
                                onClick = { onDifficultyChange(StudyDifficulty.BASIC) }
                            )
                        }
                        item {
                            DifficultyChip(
                                selected = selectedDifficulty == StudyDifficulty.MIXED,
                                title = StudyDifficulty.MIXED.title,
                                subtitle = StudyDifficulty.MIXED.subtitle,
                                onClick = { onDifficultyChange(StudyDifficulty.MIXED) }
                            )
                        }
                        item {
                            DifficultyChip(
                                selected = selectedDifficulty == StudyDifficulty.HARDCORE,
                                title = StudyDifficulty.HARDCORE.title,
                                subtitle = StudyDifficulty.HARDCORE.subtitle,
                                onClick = { onDifficultyChange(StudyDifficulty.HARDCORE) }
                            )
                        }
                    }

                    Spacer(Modifier.padding(top = 12.dp))
                } else {
                    Text("Modo", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.padding(top = 6.dp))
                    Text(
                        "Básico (sempre com opções)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.padding(top = 12.dp))
                }

                NeonButton("Voltar") { onBack() }
            }
        }

        items(
            items = enabledFeatures,
            key = { it.id }
        ) { f ->
            NeonCard(modifier = Modifier.fillMaxWidth()) {
                Text("${f.emoji}  ${f.title}", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.padding(top = 6.dp))
                Text(f.subtitle, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.padding(top = 12.dp))
                NeonButton("Abrir") { onOpenFeature(f, effectiveDifficulty) }
            }
        }

        if (enabledFeatures.isEmpty()) {
            item {
                NeonCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Nenhuma feature disponível ainda.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.padding(top = 10.dp))
                    NeonButton("Voltar") { onBack() }
                }
            }
        }
    }
}

@Composable
private fun DifficultyChip(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val bg = if (selected) cs.secondary.copy(alpha = 0.28f) else cs.surfaceVariant.copy(alpha = 0.70f)
    val fg = if (selected) cs.onSecondaryContainer else cs.onSurface
    val border = if (selected) cs.tertiary.copy(alpha = 0.55f) else cs.outline.copy(alpha = 0.35f)

    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, border)
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.padding(top = 2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
