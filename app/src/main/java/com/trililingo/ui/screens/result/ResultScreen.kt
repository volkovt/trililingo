package com.trililingo.ui.screens.result

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trililingo.ui.design.NeonBottomSheet
import com.trililingo.ui.design.NeonButton
import com.trililingo.ui.design.NeonCard
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * ResultScreen agora lê TUDO do banco (Room) quando sessionId é fornecido:
 * - Attempts: activity_attempts (hintCount, responseMs, chosenAnswer, correctAnswer, baseXp, xpMultiplier, xpAwarded)
 * - Items: language_items (meaning, etc.) + meta (romanization/pronunciation)
 *
 * Mantém retrocompatibilidade: se não houver sessionId, funciona em modo legado (xp/correct/wrong).
 */
@Composable
fun ResultScreen(
    xp: Int,
    correct: Int,
    wrong: Int,
    onBackHome: () -> Unit,

    // ✅ NOVO: se você passar o sessionId, a tela fica “premium/offline” com detalhes do banco
    sessionId: String? = null,

    // opcionais
    onRetry: (() -> Unit)? = null,

    // ✅ injeção padrão do VM (Hilt)
    vm: ResultViewModel = hiltViewModel()
) {
    val cs = MaterialTheme.colorScheme
    val cfg = LocalConfiguration.current

    val compact = (cfg.screenWidthDp < 360) || (cfg.screenHeightDp < 700)
    val outerPadding = if (compact) 14.dp else 18.dp
    val gap = if (compact) 10.dp else 14.dp

    // ======= bind no sessionId (Room -> Flow) =======
    LaunchedEffect(sessionId) {
        vm.bindSession(sessionId)
    }

    val state by vm.state.collectAsState()

    val hasSession = !sessionId.isNullOrBlank()
    val hasDbDetails = hasSession && !state.loading && state.error == null && state.sessionId == sessionId

    // ======= dados efetivos (modo legado vs modo banco) =======
    val attemptsAll = remember(state.correctItems, state.wrongItems) { state.correctItems + state.wrongItems }

    val effCorrect = if (hasDbDetails) state.correctItems.size else correct
    val effWrong = if (hasDbDetails) state.wrongItems.size else wrong
    val total = max(0, effCorrect + effWrong)

    val accuracy = if (total == 0) 0f else (effCorrect.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val accuracyPct = (accuracy * 100f).roundToInt()

    val dbXpAwardedTotal = if (hasDbDetails) attemptsAll.sumOf { it.xpAwarded } else 0
    val effXp = if (hasDbDetails) dbXpAwardedTotal else xp

    val dbBaseXpTotal = if (hasDbDetails) attemptsAll.sumOf { it.baseXp } else 0
    val dbHintTotal = if (hasDbDetails) attemptsAll.sumOf { it.hintCount } else 0

    val avgRespMs = if (hasDbDetails && attemptsAll.isNotEmpty()) {
        (attemptsAll.sumOf { it.responseMs } / attemptsAll.size).coerceAtLeast(0L)
    } else null

    // “duração” (estimativa): soma do tempo de resposta por item (não é wall-clock, mas é útil)
    val totalRespMs = if (hasDbDetails) attemptsAll.sumOf { it.responseMs }.coerceAtLeast(0L) else null

    val penalty = if (hasDbDetails) max(0, dbBaseXpTotal - dbXpAwardedTotal) else 0

    val grade = remember(accuracyPct, total) { gradeFor(accuracyPct, total) }
    val vibe = remember(grade) { vibeForGrade(grade) }

    // ======= filtros da lista =======
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(AttemptFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(AttemptSort.ORDER) }
    var selected by remember { mutableStateOf<ResultAttemptUi?>(null) }

    val filtered = remember(attemptsAll, query, filter, sort, hasDbDetails) {
        if (!hasDbDetails) return@remember emptyList<ResultAttemptUi>()

        var list = attemptsAll

        val q = query.trim().lowercase(Locale.getDefault())
        if (q.isNotBlank()) {
            list = list.filter { it.searchable().lowercase(Locale.getDefault()).contains(q) }
        }

        list = when (filter) {
            AttemptFilter.ALL -> list
            AttemptFilter.CORRECT -> list.filter { it.isCorrect }
            AttemptFilter.WRONG -> list.filterNot { it.isCorrect }
        }

        list = when (sort) {
            AttemptSort.ORDER -> list // a ordem atual é a do Flow (geralmente por createdAtMs asc no DAO)
            AttemptSort.SLOWEST -> list.sortedByDescending { it.responseMs }
            AttemptSort.LEAST_XP -> list.sortedBy { it.xpAwarded }
            AttemptSort.MOST_HINTS -> list.sortedByDescending { it.hintCount }
        }

        list
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = outerPadding, vertical = outerPadding),
        verticalArrangement = Arrangement.spacedBy(gap),
        contentPadding = PaddingValues(bottom = 18.dp)
    ) {
        item {
            ResultHeroCard(
                title = if (hasDbDetails) "Sessão finalizada!" else "Sessão finalizada (modo legado)",
                subtitle = if (hasDbDetails) {
                    buildString {
                        append("Detalhes carregados do banco")
                        sessionId?.let {
                            append(" • ")
                            append(it.take(8))
                        }
                    }
                } else {
                    "Resumo baseado apenas em XP/Acertos/Erros"
                },
                grade = grade,
                vibe = vibe,
                accuracy = accuracy,
                accuracyPct = accuracyPct,
                xp = effXp,
                correct = effCorrect,
                wrong = effWrong,
                total = total,
                avgMs = avgRespMs,
                totalMs = totalRespMs
            )
        }

        // ======= aviso modo legado / loading / erro =======
        if (hasSession) {
            item {
                when {
                    state.loading -> NeonCard {
                        Text("Carregando detalhes offline…", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Buscando attempts desta sessão no Room e montando o resumo completo.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant
                        )
                    }

                    state.error != null -> NeonCard {
                        Text("Falha ao carregar detalhes", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            state.error ?: "Erro desconhecido",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.tertiary
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Mesmo com erro, o modo legado ainda funciona (XP/Acertos/Erros).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant
                        )
                    }

                    !hasDbDetails -> NeonCard {
                        Text("Detalhes não disponíveis", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Não encontrei attempts para essa sessionId ainda. Se você acabou de finalizar a sessão, isso pode acontecer se a navegação ocorrer antes de persistir os attempts.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            item {
                NeonCard {
                    Text("Modo legado", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Para exibir itens certos/errados, tempo, dicas e XP por item, passe sessionId ao navegar para esta tela.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                }
            }
        }

        // ======= KPIs do banco =======
        if (hasDbDetails) {
            item {
                NeonCard {
                    Text("KPIs reais (Room)", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(10.dp))

                    val twoCols = cfg.screenWidthDp >= 600
                    if (twoCols) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            KpiTile("XP total", "$dbXpAwardedTotal", "somando xpAwarded", Modifier.weight(1f))
                            KpiTile("Base XP", "$dbBaseXpTotal", "somando baseXp", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            KpiTile("Penalidade", "$penalty", "base - awarded", Modifier.weight(1f))
                            KpiTile("Dicas", "$dbHintTotal", "somando hintCount", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            KpiTile("Avg resp", avgRespMs?.let { "${it}ms" } ?: "—", "tempo médio", Modifier.weight(1f))
                            KpiTile("Tempo total", totalRespMs?.let { formatDurationMs(it) } ?: "—", "soma responseMs", Modifier.weight(1f))
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            KpiTile("XP total", "$dbXpAwardedTotal", "xpAwarded", Modifier.weight(1f))
                            KpiTile("Base XP", "$dbBaseXpTotal", "baseXp", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            KpiTile("Penalidade", "$penalty", "base-awarded", Modifier.weight(1f))
                            KpiTile("Dicas", "$dbHintTotal", "hintCount", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            KpiTile("Avg", avgRespMs?.let { "${it}ms" } ?: "—", "resposta", Modifier.weight(1f))
                            KpiTile("Total", totalRespMs?.let { formatDurationMs(it) } ?: "—", "tempo", Modifier.weight(1f))
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    InsightStrip(
                        accuracyPct = accuracyPct,
                        correct = effCorrect,
                        wrong = effWrong,
                        xp = dbXpAwardedTotal,
                        hintsUsed = dbHintTotal
                    )
                }
            }

            // ======= Lista de itens =======
            item {
                NeonCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Itens da sessão", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${filtered.size}/${attemptsAll.size} itens",
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    SearchBox(
                        value = query,
                        onChange = { query = it },
                        onClear = { query = "" }
                    )

                    Spacer(Modifier.height(10.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item { SegChip(selected = filter == AttemptFilter.ALL, label = AttemptFilter.ALL.label) { filter = AttemptFilter.ALL } }
                        item { SegChip(selected = filter == AttemptFilter.CORRECT, label = AttemptFilter.CORRECT.label) { filter = AttemptFilter.CORRECT } }
                        item { SegChip(selected = filter == AttemptFilter.WRONG, label = AttemptFilter.WRONG.label) { filter = AttemptFilter.WRONG } }

                        item { Spacer(Modifier.width(6.dp)) }

                        item { SegChip(selected = sort == AttemptSort.ORDER, label = AttemptSort.ORDER.label) { sort = AttemptSort.ORDER } }
                        item { SegChip(selected = sort == AttemptSort.SLOWEST, label = AttemptSort.SLOWEST.label) { sort = AttemptSort.SLOWEST } }
                        item { SegChip(selected = sort == AttemptSort.LEAST_XP, label = AttemptSort.LEAST_XP.label) { sort = AttemptSort.LEAST_XP } }
                        item { SegChip(selected = sort == AttemptSort.MOST_HINTS, label = AttemptSort.MOST_HINTS.label) { sort = AttemptSort.MOST_HINTS } }
                    }

                    Spacer(Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = (cfg.screenHeightDp.dp * 0.56f))
                    ) {
                        if (filtered.isEmpty()) {
                            Text(
                                text = if (query.isBlank()) "Sem itens para exibir." else "Nada encontrado para \"$query\".",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 4.dp)
                            ) {
                                items(filtered, key = { it.stableKey() }) { a ->
                                    AttemptRow(
                                        a = a,
                                        onOpen = { selected = a }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NeonButton(
                    text = "Voltar ao início",
                    modifier = Modifier.weight(1f),
                    onClick = onBackHome
                )

                if (onRetry != null) {
                    NeonButton(
                        text = "Jogar de novo",
                        modifier = Modifier.weight(1f),
                        onClick = onRetry
                    )
                }
            }
        }
    }

    // ======= BottomSheet do item =======
    selected?.let { a ->
        AttemptDetailsSheet(
            a = a,
            onClose = { selected = null }
        )
    }
}

/* =========================================================================================
   UI - Hero, KPIs, Lista e Details
   ========================================================================================= */

private enum class AttemptFilter(val label: String) {
    ALL("Todos"),
    CORRECT("Certos"),
    WRONG("Errados")
}

private enum class AttemptSort(val label: String) {
    ORDER("Ordem"),
    SLOWEST("Mais lentos"),
    LEAST_XP("Menor XP"),
    MOST_HINTS("Mais dicas")
}

@Composable
private fun ResultHeroCard(
    title: String,
    subtitle: String,
    grade: String,
    vibe: String,
    accuracy: Float,
    accuracyPct: Int,
    xp: Int,
    correct: Int,
    wrong: Int,
    total: Int,
    avgMs: Long?,
    totalMs: Long?
) {
    val cs = MaterialTheme.colorScheme

    val infinite = rememberInfiniteTransition(label = "hero")
    val glow by infinite.animateFloat(
        initialValue = 0.16f,
        targetValue = 0.44f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    val animAcc by animateFloatAsState(
        targetValue = accuracy,
        animationSpec = tween(650, easing = FastOutSlowInEasing),
        label = "acc"
    )

    NeonCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(96.dp),
                contentAlignment = Alignment.Center
            ) {
                ProgressRing(
                    progress = animAcc,
                    glow = glow
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$accuracyPct%",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "accuracy",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)

                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Badge(label = "GRAU $grade", strong = true)
                    Badge(label = vibe, strong = false)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = cs.surfaceVariant.copy(alpha = 0.58f),
            contentColor = cs.onSurface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScoreMini("XP", "$xp", Modifier.weight(1f))
                ScoreMini("Itens", "$total", Modifier.weight(1f))
                ScoreMini("✔", "$correct", Modifier.weight(1f))
                ScoreMini("✖", "$wrong", Modifier.weight(1f))
            }
        }

        if (avgMs != null || totalMs != null) {
            Spacer(Modifier.height(10.dp))
            val meta = buildString {
                if (avgMs != null) append("Avg resposta: ${avgMs}ms")
                if (avgMs != null && totalMs != null) append(" • ")
                if (totalMs != null) append("Tempo total: ${formatDurationMs(totalMs)}")
            }
            Text(meta, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProgressRing(progress: Float, glow: Float) {
    val cs = MaterialTheme.colorScheme
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = 10.dp.toPx()
        val pad = 8.dp.toPx()
        val size = min(size.width, size.height) - pad * 2f
        val topLeft = Offset((this.size.width - size) / 2f, (this.size.height - size) / 2f)

        drawArc(
            color = cs.outline.copy(alpha = 0.35f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = Size(size, size),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        drawArc(
            color = cs.secondary.copy(alpha = glow * 0.22f),
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = topLeft,
            size = Size(size, size),
            style = Stroke(width = stroke * 1.35f, cap = StrokeCap.Round)
        )

        drawArc(
            color = cs.tertiary.copy(alpha = 0.92f),
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = topLeft,
            size = Size(size, size),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun AttemptRow(
    a: ResultAttemptUi,
    onOpen: () -> Unit
) {
    val cs = MaterialTheme.colorScheme

    val bg = if (a.isCorrect) cs.surfaceVariant.copy(alpha = 0.70f) else cs.tertiary.copy(alpha = 0.10f)
    val border = if (a.isCorrect) cs.outline.copy(alpha = 0.45f) else cs.tertiary.copy(alpha = 0.55f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onOpen
            ),
        color = bg,
        contentColor = cs.onSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, border),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Linha principal: correta (leitura)
                Text(
                    text = a.correctAnswer,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Linha: sua resposta
                Text(
                    text = "Sua: ${a.chosenAnswer}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Linha: significado + pron + romaji
                val line2 = buildString {
                    a.meaning?.takeIf { it.isNotBlank() }?.let { append(it) }
                    a.pronunciationPt?.takeIf { it.isNotBlank() }?.let {
                        if (isNotBlank()) append(" • ")
                        append("pron.: ").append(it)
                    }
                    a.romanization?.takeIf { it.isNotBlank() && it != a.correctAnswer }?.let {
                        if (isNotBlank()) append(" • ")
                        append("rom.: ").append(it)
                    }
                }
                if (line2.isNotBlank()) {
                    Text(
                        text = line2,
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Meta: tempo, dicas, xp
                val meta = buildString {
                    append("${a.responseMs}ms")
                    if (a.hintCount > 0) {
                        append(" • dicas: ").append(a.hintCount)
                    }
                    append(" • xp: ").append(a.xpAwarded)
                    if (a.baseXp > 0 && a.xpAwarded <= a.baseXp) {
                        append("/").append(a.baseXp)
                    }
                    if (a.xpMultiplier != 1.0) {
                        append(" • mult ").append(String.format(Locale.US, "%.2f", a.xpMultiplier))
                    }
                }
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = if (a.isCorrect) "✓" else "✖",
                style = MaterialTheme.typography.titleLarge,
                color = if (a.isCorrect) cs.primary else cs.tertiary
            )
        }
    }
}

@Composable
private fun AttemptDetailsSheet(
    a: ResultAttemptUi,
    onClose: () -> Unit
) {
    val cs = MaterialTheme.colorScheme

    NeonBottomSheet(
        onDismiss = onClose,
        title = if (a.isCorrect) "✅ Acertou" else "❌ Errou",
        subtitle = "Item: ${a.itemId.take(12)}",
        headerTrailing = { MiniActionChip(label = "Fechar", onClick = onClose) },
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
                color = if (a.isCorrect) cs.primary.copy(alpha = 0.10f) else cs.tertiary.copy(alpha = 0.10f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.30f))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sua resposta: ${a.chosenAnswer}", style = MaterialTheme.typography.bodyLarge)
                    Text("Resposta correta: ${a.correctAnswer}", style = MaterialTheme.typography.bodyLarge)

                    val xpLine = buildString {
                        append("XP: ").append(a.xpAwarded)
                        if (a.baseXp > 0) append(" (base ").append(a.baseXp).append(")")
                        if (a.xpMultiplier != 1.0) append(" • mult ").append(String.format(Locale.US, "%.2f", a.xpMultiplier))
                        if (a.hintCount > 0) append(" • dicas ").append(a.hintCount)
                        append(" • tempo ").append(a.responseMs).append("ms")
                    }
                    Text(xpLine, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                }
            }

            a.meaning?.trim().orEmpty().takeIf { it.isNotBlank() }?.let {
                InfoBlock(title = "Significado", value = it)
            }

            a.pronunciationPt?.trim().orEmpty().takeIf { it.isNotBlank() }?.let {
                InfoBlock(title = "Pronúncia (PT-BR)", value = it)
            }

            a.romanization?.trim().orEmpty().takeIf { it.isNotBlank() && it != a.correctAnswer }?.let {
                InfoBlock(title = "Romanização", value = it)
            }

            Spacer(Modifier.height(6.dp))
            NeonButton(text = "Fechar", onClick = onClose)
        }
    }
}

/* =========================================================================================
   Aux UI
   ========================================================================================= */

@Composable
private fun InsightStrip(
    accuracyPct: Int,
    correct: Int,
    wrong: Int,
    xp: Int,
    hintsUsed: Int
) {
    val cs = MaterialTheme.colorScheme

    val headline = remember(accuracyPct, wrong, correct) {
        when {
            correct + wrong == 0 -> "Sem respostas registradas"
            accuracyPct >= 95 -> "Performance absurda. Você tá voando."
            accuracyPct >= 85 -> "Excelente consistência. Mais um passo e vira automático."
            accuracyPct >= 70 -> "Bom ritmo. Lapida os detalhes e vai subir muito."
            else -> "Sessão difícil — e isso é ótimo: é onde o cérebro constrói memória."
        }
    }

    val next = remember(accuracyPct, wrong) {
        when {
            wrong == 0 -> "Tenta aumentar a dificuldade ou avançar o conteúdo."
            accuracyPct < 70 -> "Revisar erros agora vai te dar o maior ganho de retenção."
            else -> "Repetir a sessão (ou só os erros) solidifica rápido."
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cs.secondary.copy(alpha = 0.12f),
        contentColor = cs.onSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.30f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(headline, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(next, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)

            Spacer(Modifier.height(8.dp))
            Text(
                "XP: $xp • Dicas usadas: $hintsUsed",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun KpiTile(title: String, value: String, subtitle: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        color = cs.surfaceVariant.copy(alpha = 0.72f),
        contentColor = cs.onSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun Badge(label: String, strong: Boolean) {
    val cs = MaterialTheme.colorScheme
    val bg = if (strong) cs.tertiary.copy(alpha = 0.18f) else cs.surfaceVariant.copy(alpha = 0.55f)
    val fg = if (strong) cs.tertiary else cs.onSurface

    Surface(
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.35f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ScoreMini(label: String, value: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun InfoBlock(title: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cs.surfaceVariant.copy(alpha = 0.62f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.28f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun SearchBox(
    value: String,
    onChange: (String) -> Unit,
    onClear: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Buscar: resposta, significado, pronúncia, romaji, xp, hints…") },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = cs.surfaceVariant,
            unfocusedContainerColor = cs.surfaceVariant.copy(alpha = 0.75f),
            focusedTextColor = cs.onSurface,
            unfocusedTextColor = cs.onSurface,
            focusedIndicatorColor = cs.tertiary.copy(alpha = 0.65f),
            unfocusedIndicatorColor = cs.outline.copy(alpha = 0.45f),
            cursorColor = cs.tertiary
        ),
        trailingIcon = {
            if (value.isNotBlank()) {
                Text(
                    "✖",
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .clickable { onClear() }
                )
            }
        }
    )
}

@Composable
private fun SegChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        color = if (selected) cs.secondary.copy(alpha = 0.30f) else cs.surfaceVariant.copy(alpha = 0.70f),
        contentColor = if (selected) cs.onSecondaryContainer else cs.onSurface,
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

@Composable
private fun MiniActionChip(
    label: String,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
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

/* =========================================================================================
   Helpers
   ========================================================================================= */

private fun ResultAttemptUi.searchable(): String = buildString {
    append(itemId).append(' ')
    append(chosenAnswer).append(' ')
    append(correctAnswer).append(' ')
    meaning?.let { append(it).append(' ') }
    pronunciationPt?.let { append(it).append(' ') }
    romanization?.let { append(it).append(' ') }
    append("xp ").append(xpAwarded).append(' ')
    append("base ").append(baseXp).append(' ')
    append("hints ").append(hintCount).append(' ')
    append("ms ").append(responseMs).append(' ')
}

private fun ResultAttemptUi.stableKey(): String =
    "attempt::$itemId::$createdFingerprint"

private val ResultAttemptUi.createdFingerprint: String
    get() = "${chosenAnswer.take(16)}|${correctAnswer.take(16)}|$responseMs|$xpAwarded|$hintCount"

private fun gradeFor(accuracyPct: Int, total: Int): String {
    if (total <= 0) return "—"
    return when {
        accuracyPct >= 96 -> "S"
        accuracyPct >= 90 -> "A"
        accuracyPct >= 80 -> "B"
        accuracyPct >= 65 -> "C"
        else -> "D"
    }
}

private fun vibeForGrade(grade: String): String {
    return when (grade) {
        "S" -> "Modo Lenda ✨"
        "A" -> "Brutal demais 🔥"
        "B" -> "Consistente ⚡"
        "C" -> "Em evolução 🧠"
        "D" -> "Forja ativa 💀"
        else -> "Sem dados"
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}
