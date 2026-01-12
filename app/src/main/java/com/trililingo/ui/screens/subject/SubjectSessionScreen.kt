package com.trililingo.ui.screens.subject

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trililingo.ui.design.AnswerOptionCard
import com.trililingo.ui.design.NeonButton
import com.trililingo.ui.design.NeonCard
import com.trililingo.ui.design.OptionVisualState
import com.trililingo.ui.screens.lesson.ChallengeCompleteOverlay
import com.trililingo.ui.sound.SoundFx
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

@Composable
fun SubjectSessionScreen(
    mode: String,
    subjectId: String,
    trackId: String,
    chapterId: String, // "all" ou chapterId real
    onAbort: () -> Unit,
    onDone: (xp: Int, correct: Int, wrong: Int, sessionId: String) -> Unit,
    vm: SubjectSessionViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

    LaunchedEffect(mode, subjectId, trackId, chapterId) {
        vm.start(mode, subjectId, trackId, chapterId)
    }

    var showComplete by remember { mutableStateOf(false) }
    var finalXp by remember { mutableStateOf(0) }
    var finalCorrect by remember { mutableStateOf(0) }
    var finalWrong by remember { mutableStateOf(0) }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    val sfx = remember { SoundFx(ctx) }
    DisposableEffect(Unit) { onDispose { sfx.release() } }

    BackHandler(enabled = !state.loading && !showComplete) {
        vm.abortSession { onAbort() }
    }

    LaunchedEffect(showComplete) {
        if (showComplete) {
            sfx.complete()
            delay(3500)
            onDone(finalXp, finalCorrect, finalWrong, state.sessionId.orEmpty())
            showComplete = false
        }
    }

    val scroll = rememberScrollState()
    LaunchedEffect(state.index) {
        scroll.scrollTo(0)
    }
    LaunchedEffect(state.phase) {
        if (state.phase == SubjectQuestionPhase.REVEAL) {
            delay(120)
            scroll.animateScrollTo(scroll.maxValue)
        }
    }

    val headerTitle = if (mode.equals("daily", true)) "Desafio Diário" else "Estudo Livre"

    if (state.loading) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(18.dp)
        ) {
            Text("Montando sessão…", style = MaterialTheme.typography.titleLarge)
        }
        return
    }

    if (state.error != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(18.dp)
        ) {
            NeonCard {
                Text("Erro", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(state.error ?: "Erro desconhecido", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
                NeonButton("Voltar") { vm.abortSession { onAbort() } }
            }
        }
        return
    }

    val challenge = state.challenges.getOrNull(state.index)
    if (challenge == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(18.dp)
        ) {
            NeonCard { Text("Sem perguntas para esta sessão.", style = MaterialTheme.typography.bodyLarge) }
            Spacer(Modifier.height(12.dp))
            NeonButton("Voltar") { vm.abortSession { onAbort() } }
        }
        return
    }

    var startedAt by remember(challenge.questionId) { mutableStateOf(System.currentTimeMillis()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ===== Header (padrão Idiomas) =====
            NeonCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$headerTitle • $subjectId",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    NeonButton(text = "Sair", fullWidth = false) {
                        vm.abortSession { onAbort() }
                    }
                }

                Spacer(Modifier.height(8.dp))

                val total = max(1, state.challenges.size)
                val idx = (state.index + 1).coerceIn(1, total)
                val progress = idx.toFloat() / total.toFloat()

                Text(
                    "Questão: $idx/$total • XP: ${state.totalXp} • ✅ ${state.correct}  ❌ ${state.wrong}",
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(Modifier.height(10.dp))
                NeonProgressBar(progress = progress)

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniBadge(label = "Dif ${challenge.difficulty}/5")
                    if (challenge.typeTags.isNotEmpty()) MiniBadge(label = challenge.typeTags.first())
                    if (challenge.tags.isNotEmpty()) MiniBadge(label = "#${challenge.tags.first()}")
                }
            }

            // ===== Prompt =====
            NeonCard {
                Text("Pergunta", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                Text(challenge.prompt, style = MaterialTheme.typography.bodyLarge)
            }

            // ===== Options =====
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

                    AnswerOptionCard(
                        text = opt,
                        enabled = answering,
                        visualState = optState,
                        maxLinesCollapsed = 2,
                        sheetTitle = "Opção (Subjects)",
                        sheetSubtitle = "Segurar abre o texto completo sem responder.",
                        onSelect = {
                            val responseMs = System.currentTimeMillis() - startedAt
                            val ok = idx == challenge.correctIndex
                            if (ok) sfx.correct() else sfx.wrong()

                            vm.submitAnswer(
                                chosenIndex = idx,
                                responseMs = responseMs
                            ) { _, _, _ -> }

                            startedAt = System.currentTimeMillis()
                        }
                    )
                }

                // ===== Reveal =====
                if (state.phase == SubjectQuestionPhase.REVEAL && state.reveal != null) {
                    Spacer(Modifier.height(14.dp))

                    val r = state.reveal!!
                    val title = if (r.isCorrect) "Acertou!" else "Errou!"
                    Text(
                        text = "$title • XP nesta questão: +${r.xpAwarded}",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(Modifier.height(10.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.30f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Resposta correta", style = MaterialTheme.typography.titleMedium)
                            Text(challenge.expected, style = MaterialTheme.typography.bodyLarge)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    val isLast = (state.index + 1) >= state.challenges.size
                    NeonButton(text = if (isLast) "Finalizar" else "Continuar") {
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

@Composable
private fun NeonProgressBar(progress: Float) {
    val cs = MaterialTheme.colorScheme
    val p = min(1f, max(0f, progress))

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
    ) {
        val w = size.width
        val h = size.height

        // trilho
        drawLine(
            color = cs.outline.copy(alpha = 0.35f),
            start = Offset(0f, h / 2f),
            end = Offset(w, h / 2f),
            strokeWidth = h,
            cap = StrokeCap.Round
        )

        // progresso
        drawLine(
            color = cs.primary.copy(alpha = 0.85f),
            start = Offset(0f, h / 2f),
            end = Offset(w * p, h / 2f),
            strokeWidth = h,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun MiniBadge(label: String) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.surfaceVariant.copy(alpha = 0.65f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.30f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}
