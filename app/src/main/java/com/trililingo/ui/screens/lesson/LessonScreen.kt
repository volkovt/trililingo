package com.trililingo.ui.screens.lesson

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trililingo.core.language.PronunciationPtResolver
import com.trililingo.data.repo.ItemMeta
import com.trililingo.domain.subject.AnswerMode
import com.trililingo.domain.subject.StudyDifficulty
import com.trililingo.ui.design.AnswerOptionCard
import com.trililingo.ui.design.NeonButton
import com.trililingo.ui.design.NeonCard
import com.trililingo.ui.design.OptionVisualState
import com.trililingo.ui.sound.SoundFx
import kotlinx.coroutines.delay
import java.text.Normalizer
import java.util.Locale

@Composable
fun LessonScreen(
    language: String,
    skill: String,
    mode: String,
    difficulty: StudyDifficulty,
    onDone: (xp: Int, correct: Int, wrong: Int, sessionId: String) -> Unit,
    onAbort: () -> Unit,
    vm: LessonViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

    LaunchedEffect(language, skill, mode, difficulty) {
        vm.start(language, skill, mode, difficulty)
    }

    val ctx = LocalContext.current
    val sfx = remember { SoundFx(ctx) }
    DisposableEffect(Unit) { onDispose { sfx.release() } }

    var showComplete by remember { mutableStateOf(false) }
    BackHandler(enabled = !state.loading && !showComplete) {
        vm.abortSession { onAbort() }
    }

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

    if (state.loading) {
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Text("Carregando...", style = MaterialTheme.typography.titleLarge)
        }
        return
    }

    val challenge = state.challenges.getOrNull(state.index)
    if (challenge == null) {
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Text("Sem desafios (adicione itens no pack).", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            NeonButton("Voltar") {
                vm.abortSession { onAbort() }
            }
        }
        return
    }

    val answerMode = state.answerModes.getOrNull(state.index) ?: AnswerMode.MULTIPLE_CHOICE

    val meta = state.metaById[challenge.itemId]
    var startedAt by remember { mutableStateOf(System.currentTimeMillis()) }

    // resets por item
    var hintMeaning by remember(challenge.itemId) { mutableStateOf(false) }
    var hintMnemonic by remember(challenge.itemId) { mutableStateOf(false) }
    var hintRomaji by remember(challenge.itemId) { mutableStateOf(false) }
    var hintGojuon by remember(challenge.itemId) { mutableStateOf(false) }

    // ✅ input por item (para modo TYPING)
    var typedAnswer by remember(challenge.itemId) { mutableStateOf("") }

    val isDaily = mode.equals("daily", ignoreCase = true)
    val headerTitle = if (isDaily) "Desafio diário" else "Treino livre"

    // conta quantas dicas foram usadas nesta questão
    val hintCount = listOf(hintMeaning, hintMnemonic, hintRomaji, hintGojuon).count { it }

    val scrollState = rememberScrollState()

    LaunchedEffect(state.index) {
        scrollState.scrollTo(0)
        startedAt = System.currentTimeMillis()
        typedAnswer = ""
    }

    LaunchedEffect(state.phase) {
        if (state.phase == QuestionPhase.REVEAL) {
            delay(120)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            NeonCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$headerTitle • $language/$skill • ${difficulty.title}",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false
                    )
                    NeonButton(
                        text = "Sair",
                        fullWidth = false
                    ) {
                        vm.abortSession { onAbort() }
                    }
                }
                Spacer(Modifier.height(8.dp))

                val modeLabel = when (answerMode) {
                    AnswerMode.MULTIPLE_CHOICE -> "Opções"
                    AnswerMode.TYPING -> "Digitação"
                }

                Text(
                    "Questão: ${state.index + 1}/${state.challenges.size} • XP: ${state.totalXp} • ✅ ${state.correct}  ❌ ${state.wrong} • Modo: $modeLabel",
                    style = MaterialTheme.typography.bodyLarge
                )

                metaBadges(meta)
            }

            NeonCard {
                Text("Painel", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                Text(text = challenge.prompt, style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.height(12.dp))

                HintHeader(
                    onToggleMeaning = { hintMeaning = !hintMeaning },
                    onToggleMnemonic = { hintMnemonic = !hintMnemonic },
                    onToggleRomaji = { hintRomaji = !hintRomaji },
                    onToggleGojuon = { hintGojuon = !hintGojuon },
                    hasMnemonic = !meta?.mnemonicPt.isNullOrBlank(),
                    hasRomaji = !meta?.romanization?.value.isNullOrBlank(),
                    hasGojuon = !meta?.gojuon?.rowLabel.isNullOrBlank()
                )

                AnimatedVisibility(visible = hintMeaning) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Text("Significado: ${challenge.meaningHint}", style = MaterialTheme.typography.bodyLarge)
                    }
                }

                AnimatedVisibility(visible = hintMnemonic && !meta?.mnemonicPt.isNullOrBlank()) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Text("Mnemônico: ${meta?.mnemonicPt}", style = MaterialTheme.typography.bodyLarge)
                    }
                }

                AnimatedVisibility(visible = hintRomaji && !meta?.romanization?.value.isNullOrBlank()) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Romaji (${meta?.romanization?.system ?: "—"}): ${meta?.romanization?.value}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                AnimatedVisibility(visible = hintGojuon && !meta?.gojuon?.rowLabel.isNullOrBlank()) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Gojūon: ${meta?.gojuon?.rowLabel} • vogal ${meta?.gojuon?.vowel ?: "—"}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                if (!hintMeaning && !hintMnemonic && !hintRomaji && !hintGojuon) {
                    Text("Toque nos ícones para revelar dicas.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        text = "Dicas usadas: $hintCount (XP será reduzido).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            NeonCard {
                Text(
                    text = if (answerMode == AnswerMode.TYPING) "Digite a leitura correta" else "Qual a leitura correta?",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(10.dp))

                val reveal = state.reveal
                val answering = state.phase == QuestionPhase.ANSWERING

                if (answerMode == AnswerMode.MULTIPLE_CHOICE) {
                    challenge.options.forEach { opt ->
                        Spacer(Modifier.height(10.dp))

                        val optState = when {
                            reveal == null -> OptionVisualState.DEFAULT
                            opt == reveal.correct -> OptionVisualState.CORRECT
                            opt == reveal.chosen && !reveal.isCorrect -> OptionVisualState.WRONG_SELECTED
                            opt == reveal.chosen && reveal.isCorrect -> OptionVisualState.CORRECT_SELECTED
                            else -> OptionVisualState.DISABLED
                        }

                        AnswerOptionCard(
                            text = opt,
                            enabled = answering,
                            visualState = optState,
                            maxLinesCollapsed = 2,
                            sheetTitle = "Opção (Idiomas)",
                            sheetSubtitle = "Segurar abre o texto completo sem responder.",
                            onSelect = {
                                val responseMs = System.currentTimeMillis() - startedAt

                                val okForSfx = isCorrectByGenericTolerance(opt, challenge.correct)
                                if (okForSfx) sfx.correct() else sfx.wrong()

                                vm.submitAnswer(
                                    optionRaw = opt,
                                    responseMs = responseMs,
                                    hintCount = hintCount,
                                    onDone = { _, _, _ -> }
                                )

                                startedAt = System.currentTimeMillis()
                            }
                        )
                    }
                } else {
                    val cs = MaterialTheme.colorScheme

                    OutlinedTextField(
                        value = typedAnswer,
                        onValueChange = { typedAnswer = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = answering,
                        placeholder = { Text("Digite aqui…") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = cs.surfaceVariant.copy(alpha = 0.55f),
                            unfocusedContainerColor = cs.surfaceVariant.copy(alpha = 0.45f)
                        )
                    )

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        NeonButton(
                            text = "Responder",
                            enabled = answering && typedAnswer.trim().isNotBlank(),
                            fullWidth = true
                        ) {
                            val responseMs = System.currentTimeMillis() - startedAt

                            val okForSfx = isCorrectByGenericTolerance(typedAnswer, challenge.correct)
                            if (okForSfx) sfx.correct() else sfx.wrong()

                            vm.submitAnswer(
                                optionRaw = typedAnswer,
                                responseMs = responseMs,
                                hintCount = hintCount,
                                onDone = { _, _, _ -> }
                            )

                            startedAt = System.currentTimeMillis()
                        }
                    }

                    if (answering) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Tolerância: acentos, maiúsculas/minúsculas, espaços e pontuação simples são ignorados.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (state.phase == QuestionPhase.REVEAL && state.reveal != null) {
                    Spacer(Modifier.height(14.dp))

                    val r = state.reveal!!
                    val title = if (r.isCorrect) "Acertou!" else "Errou!"

                    Text(
                        text = "$title • Resposta correta: ${r.correct}",
                        style = MaterialTheme.typography.titleLarge
                    )

                    if (!r.acceptanceNote.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = r.acceptanceNote!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "XP nesta questão: +${r.xpAwarded}" + if (r.hintCount > 0) " (penalizado por dicas)" else "",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(Modifier.height(12.dp))

                    val cs = MaterialTheme.colorScheme
                    val meaning = challenge.meaningHint.trim()
                    val romaji = meta?.romanization?.value?.trim().orEmpty()
                    val mnemonic = meta?.mnemonicPt?.trim().orEmpty()

                    val pronunciationPt = remember(language, meta, r.correct) {
                        PronunciationPtResolver.resolve(
                            languageCode = language,
                            itemPronunciationPt = meta?.pronunciationPt,
                            romanization = meta?.romanization?.value,
                            fallbackText = r.correct
                        )
                    }

                    Surface(
                        color = cs.surfaceVariant.copy(alpha = 0.60f),
                        contentColor = cs.onSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.45f)),
                        tonalElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "O que isso significa",
                                style = MaterialTheme.typography.titleMedium
                            )

                            if (meaning.isNotBlank()) {
                                Text(
                                    text = "Significado: $meaning",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            } else {
                                Text(
                                    text = "Significado: —",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = cs.onSurfaceVariant
                                )
                            }

                            if (!pronunciationPt.isNullOrBlank()) {
                                Text(
                                    text = "Pronúncia (PT-BR): $pronunciationPt",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = cs.onSurface
                                )
                            }

                            if (romaji.isNotBlank()) {
                                Text(
                                    text = "Romaji: $romaji",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = cs.onSurfaceVariant
                                )
                            }

                            val badge = buildString {
                                meta?.category?.let { append("Categoria: $it") }
                                meta?.difficulty?.let { d ->
                                    if (isNotBlank()) append(" • ")
                                    append("Dificuldade: $d/5")
                                }
                            }
                            if (badge.isNotBlank()) {
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = cs.onSurfaceVariant
                                )
                            }

                            if (mnemonic.isNotBlank()) {
                                Text(
                                    text = "Mnemônico: $mnemonic",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            Text(
                                text = "Feedback pós-resposta (não conta como dica).",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                val isLast = (state.index + 1) >= state.challenges.size
                NeonButton(
                    text = if (isLast) "Finalizar" else "Continuar",
                    enabled = state.phase == QuestionPhase.REVEAL
                ) {
                    if (isLast) {
                        finalXp = state.totalXp
                        finalCorrect = state.correct
                        finalWrong = state.wrong
                        showComplete = true
                    } else {
                        vm.continueAfterReveal { _, _, _, _ -> }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    ChallengeCompleteOverlay(
        visible = showComplete,
        xp = finalXp,
        correct = finalCorrect,
        wrong = finalWrong
    )
}

@Composable
private fun metaBadges(meta: ItemMeta?) {
    if (meta == null) return
    val parts = buildList {
        meta.category?.let { add("Categoria: $it") }
        meta.difficulty?.let { add("Dificuldade: $it/5") }
    }
    if (parts.isEmpty()) return

    Spacer(Modifier.height(10.dp))
    Text(parts.joinToString(" • "), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun HintHeader(
    onToggleMeaning: () -> Unit,
    onToggleMnemonic: () -> Unit,
    onToggleRomaji: () -> Unit,
    onToggleGojuon: () -> Unit,
    hasMnemonic: Boolean,
    hasRomaji: Boolean,
    hasGojuon: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Dicas", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))

        IconButton(onClick = onToggleMeaning) { Text("👁️", style = MaterialTheme.typography.titleLarge) }
        if (hasMnemonic) IconButton(onClick = onToggleMnemonic) { Text("💡", style = MaterialTheme.typography.titleLarge) }
        if (hasRomaji) IconButton(onClick = onToggleRomaji) { Text("🔤", style = MaterialTheme.typography.titleLarge) }
        if (hasGojuon) IconButton(onClick = onToggleGojuon) { Text("🧭", style = MaterialTheme.typography.titleLarge) }
    }
}

/**
 * Mesma ideia de tolerância do ViewModel, mas aqui usamos só para tocar o SFX corretamente.
 */
private fun isCorrectByGenericTolerance(chosenRaw: String, correctRaw: String): Boolean {
    val chosenN = normalizeForMatchGeneric(chosenRaw)
    val correctN = normalizeForMatchGeneric(correctRaw)
    return chosenN.isNotBlank() && chosenN == correctN
}

private fun normalizeForMatchGeneric(raw: String): String {
    if (raw.isBlank()) return ""

    var s = Normalizer.normalize(raw, Normalizer.Form.NFKC)
    s = s.replace('\u3000', ' ')

    s = s.replace("·", " ")
        .replace("-", " ")
        .replace("_", " ")
        .replace("’", "'")
        .replace("'", "")
        .replace("“", "\"")
        .replace("”", "\"")
        .replace("\"", "")

    val decomposed = Normalizer.normalize(s, Normalizer.Form.NFD)
    s = decomposed.replace(Regex("\\p{Mn}+"), "")

    s = s.trim()
        .split(Regex("\\s+"))
        .joinToString(" ")
        .lowercase(Locale.ROOT)

    return s
}
