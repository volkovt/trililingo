package com.trililingo.ui.screens.subject

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trililingo.ui.design.NeonButton
import com.trililingo.ui.design.NeonCard
import com.trililingo.ui.screens.lesson.ChallengeCompleteOverlay
import com.trililingo.ui.sound.SoundFx
import kotlinx.coroutines.delay

@Composable
fun SubjectSessionScreen(
    mode: String,
    subjectId: String,
    trackId: String,
    chapterId: String,
    onAbort: () -> Unit,
    onDone: (xp: Int, correct: Int, wrong: Int, sessionId: String) -> Unit,
    vm: SubjectSessionViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

    LaunchedEffect(mode, subjectId, trackId, chapterId) {
        vm.start(mode, subjectId, trackId, chapterId)
    }

    val ctx = LocalContext.current
    val sfx = remember { SoundFx(ctx) }
    DisposableEffect(Unit) { onDispose { sfx.release() } }

    var showComplete by remember { mutableStateOf(false) }
    var finalXp by remember { mutableStateOf(0) }
    var finalCorrect by remember { mutableStateOf(0) }
    var finalWrong by remember { mutableStateOf(0) }

    LaunchedEffect(showComplete) {
        if (showComplete) {
            sfx.complete()
            delay(3500)
            onDone(finalXp, finalCorrect, finalWrong, state.sessionId!!)
            showComplete = false
        }
    }

    val headerTitle = if (mode.equals("daily", true)) "Desafio Diário" else "Estudo Livre"

    if (state.loading) {
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Text("Montando sessão...", style = MaterialTheme.typography.titleLarge)
        }
        return
    }

    if (state.error != null) {
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            NeonCard { Text("Erro: ${state.error}", style = MaterialTheme.typography.bodyLarge) }
            Spacer(Modifier.height(12.dp))
            NeonButton("Voltar") {
                vm.abortSession {
                    onAbort()
                }
            }
        }
        return
    }

    val challenge = state.challenges.getOrNull(state.index)
    if (challenge == null) {
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            NeonCard { Text("Sem perguntas (ou sem 'expected') para este capítulo.", style = MaterialTheme.typography.bodyLarge) }
            Spacer(Modifier.height(12.dp))
            NeonButton("Voltar") {
                vm.abortSession {
                    onAbort()
                }
            }
        }
        return
    }

    var startedAt by remember { mutableStateOf(System.currentTimeMillis()) }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            NeonCard {
                Text(headerTitle, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Questão: ${state.index + 1}/${state.challenges.size} • XP: ${state.totalXp} • ✅ ${state.correct}  ❌ ${state.wrong}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(10.dp))
                NeonButton("Sair") {
                    vm.abortSession {
                        onAbort()
                    }
                }
            }

            NeonCard {
                Text("Pergunta", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                Text(challenge.prompt, style = MaterialTheme.typography.bodyLarge)
            }

            NeonCard {
                Text("Escolha a melhor resposta", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))

                val reveal = state.reveal
                val answering = state.phase == SubjectQuestionPhase.ANSWERING

                challenge.options.forEachIndexed { idx, opt ->
                    Spacer(Modifier.height(10.dp))

                    val optState = when {
                        reveal == null -> OptionVisualState.DEFAULT
                        idx == reveal.correctIndex -> OptionVisualState.CORRECT
                        idx == reveal.chosenIndex && !reveal.isCorrect -> OptionVisualState.WRONG_SELECTED
                        idx == reveal.chosenIndex && reveal.isCorrect -> OptionVisualState.CORRECT_SELECTED
                        else -> OptionVisualState.DISABLED
                    }

                    SubjectOptionButton(
                        text = opt,
                        enabled = answering,
                        visualState = optState
                    ) {
                        val responseMs = System.currentTimeMillis() - startedAt
                        val ok = idx == challenge.correctIndex
                        if (ok) sfx.correct() else sfx.wrong()

                        vm.submitAnswer(
                            chosenIndex = idx,
                            responseMs = responseMs
                        ) { xp, c, w ->
                            finalXp = xp
                            finalCorrect = c
                            finalWrong = w
                            showComplete = true
                        }

                        startedAt = System.currentTimeMillis()
                    }
                }

                if (state.phase.name == "REVEAL" && state.reveal != null) {
                    Spacer(Modifier.height(14.dp))

                    val r = state.reveal!!
                    val title = if (r.isCorrect) "Acertou!" else "Errou!"
                    Text(
                        text = "$title • XP nesta questão: +${r.xpAwarded}",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(Modifier.height(12.dp))

                    val isLast = (state.index + 1) >= state.challenges.size
                    NeonButton(
                        text = if (isLast) "Finalizar" else "Continuar"
                    ) {
                        vm.continueAfterReveal { xp, c, w ->
                            finalXp = xp
                            finalCorrect = c
                            finalWrong = w
                            showComplete = true
                        }
                    }
                }
            }
        }

        ChallengeCompleteOverlay(
            visible = showComplete,
            xp = finalXp,
            correct = finalCorrect,
            wrong = finalWrong
        )
    }
}

private enum class OptionVisualState {
    DEFAULT,
    CORRECT,
    WRONG_SELECTED,
    CORRECT_SELECTED,
    DISABLED
}

@Composable
private fun SubjectOptionButton(
    text: String,
    enabled: Boolean,
    visualState: OptionVisualState,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme

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
        OptionVisualState.CORRECT, OptionVisualState.CORRECT_SELECTED -> "✅ "
        OptionVisualState.WRONG_SELECTED -> "❌ "
        else -> ""
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        color = bg,
        contentColor = cs.onSurface,
        border = BorderStroke(1.dp, border),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = labelPrefix + text,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            if (!enabled) {
                Box(
                    modifier = Modifier
                        .height(10.dp)
                        .blur(12.dp)
                )
            }
        }
    }
}
