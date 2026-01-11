package com.trililingo.ui.screens.subject

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trililingo.ui.design.NeonButton
import com.trililingo.ui.design.NeonCard

@Composable
fun SubjectPickScreen(
    mode: String, // "daily" | "study"
    subjectId: String,
    trackId: String,
    onBack: () -> Unit,
    onStart: (chapterIdOrAll: String) -> Unit,
    vm: SubjectPickViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

    LaunchedEffect(subjectId, trackId) {
        vm.load(subjectId, trackId)
    }

    val title = if (mode.equals("daily", true)) "Desafio Diário" else "Estudo Livre"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        NeonCard {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))

            Text(
                if (mode.equals("daily", true))
                    "Toque em um capítulo (ou Geral) para iniciar. No diário, você faz até 10 perguntas."
                else
                    "Toque em um capítulo (ou Geral) para iniciar. No estudo livre, não há limite.",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NeonButton("Voltar") { onBack() }
            }
        }

        if (state.loading) {
            NeonCard { Text("Carregando capítulos...", style = MaterialTheme.typography.bodyLarge) }
            return
        }

        if (state.error != null) {
            NeonCard { Text("Erro: ${state.error}", style = MaterialTheme.typography.bodyLarge) }
            return
        }

        NeonCard(modifier = Modifier.fillMaxWidth()) {
            Text("Capítulos", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))

            // ✅ Geral inicia imediatamente
            ChapterRow(
                label = "Geral (todos)",
                selected = state.selectedChapterId == null,
                onClick = {
                    vm.selectChapter(null)
                    onStart("all")
                }
            )

            // ✅ Cada capítulo inicia imediatamente
            state.chapters.forEach { ch ->
                ChapterRow(
                    label = (ch.chapter?.let { "Cap. $it — " } ?: "") + ch.title,
                    selected = state.selectedChapterId == ch.id,
                    onClick = {
                        vm.selectChapter(ch.id)
                        onStart(ch.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun ChapterRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (selected) "●  $label" else "○  $label",
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) cs.primary else cs.onSurface
        )
    }
}
