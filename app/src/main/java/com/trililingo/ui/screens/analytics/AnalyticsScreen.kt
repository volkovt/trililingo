package com.trililingo.ui.screens.analytics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.trililingo.ui.design.NeonButton
import com.trililingo.ui.design.NeonCard
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

@Composable
fun AnalyticsScreen(
    onBack: () -> Unit,
    vm: AnalyticsViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()
    var selectedDay by remember { mutableStateOf<DayProgressUi?>(null) }

    var showAllSessions by remember { mutableStateOf(false) }
    var modalLanguage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 18.dp)
    ) {
        item {
            NeonCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Analytics", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (state.weekLabel.isBlank()) "—" else state.weekLabel,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Box(modifier = Modifier.width(132.dp)) {
                        NeonButton(text = "Voltar", onClick = onBack)
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatPill("XP total", "${state.totalXp}", Modifier.weight(1f))
                    StatPill("Streak", "${state.streak} dias", Modifier.weight(1f))
                }

                Spacer(Modifier.height(10.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { SmallActionChip("← Semana", onClick = { vm.previousWeek() }) }
                    item { SmallActionChip("Semana atual", onClick = { vm.resetWeek() }) }
                    item { SmallActionChip("Semana →", onClick = { vm.nextWeek() }) }
                }

                if (state.error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text("Erro: ${state.error}", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        item {
            NeonCard {
                Text("Resumo da semana", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatPill("XP (semana)", "${state.weekXp}", Modifier.weight(1f))
                    StatPill("Sessões", "${state.weekSessions}", Modifier.weight(1f))
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatPill("Tempo", "${state.weekMinutes} min", Modifier.weight(1f))
                    StatPill("Accuracy", "${state.weekAccuracy}%", Modifier.weight(1f))
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatPill("Avg resp", "${state.weekAvgResponseMs} ms", Modifier.weight(1f))
                    StatPill("Melhor dia", state.bestDayLabel.ifBlank { "—" }, Modifier.weight(1f))
                }
            }
        }

        item {
            NeonCard {
                Text("Progresso semanal (Seg → Dom)", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))

                WeeklyBars(
                    data = state.byDay,
                    onDayClick = { selectedDay = it }
                )

                Spacer(Modifier.height(8.dp))
                Text("Toque em um dia para ver detalhes.", style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (state.languageBreakdown.isNotEmpty()) {
            item {
                NeonCard {
                    Text("XP por idioma (semana)", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(10.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.languageBreakdown.forEach { (lang, xp) ->
                            LanguageRow(
                                lang = lang,
                                xp = xp,
                                onClick = {
                                    modalLanguage = lang
                                    showAllSessions = true
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Dica: toque em um idioma para abrir as sessões filtradas.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        item {
            RecentSessionsCard(
                sessions = state.latestSessions,
                onShowAll = {
                    modalLanguage = null
                    showAllSessions = true
                }
            )
        }
    }

    selectedDay?.let { day ->
        DayDetailsDialog(day = day, onClose = { selectedDay = null })
    }

    if (showAllSessions) {
        RecentSessionsDialog(
            sessions = state.latestSessions,
            initialLanguage = modalLanguage,
            onClose = {
                showAllSessions = false
                modalLanguage = null
            }
        )
    }
}

@Composable
private fun WeeklyBars(
    data: List<DayProgressUi>,
    onDayClick: (DayProgressUi) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val maxXp = max(1, data.maxOfOrNull { it.xp } ?: 1)

    val infinite = rememberInfiniteTransition(label = "bars")
    val glow by infinite.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEach { d ->
            val ratio = d.xp.toFloat() / maxXp.toFloat()
            val barH = (24f + (160f * ratio)).dp

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onDayClick(d) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .width(26.dp)
                        .height(barH)
                        .clip(RoundedCornerShape(10.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(cs.tertiary.copy(alpha = 0.85f))
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .blur(14.dp)
                            .background(cs.secondary.copy(alpha = glow * 0.25f))
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(d.label, style = MaterialTheme.typography.bodyMedium)
                Text("${d.xp}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun LanguageRow(
    lang: String,
    xp: Int,
    onClick: (() -> Unit)? = null
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        color = cs.surfaceVariant.copy(alpha = 0.62f),
        contentColor = cs.onSurface,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = lang,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text("$xp XP", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun RecentSessionsCard(
    sessions: List<SessionRowUi>,
    onShowAll: () -> Unit
) {
    NeonCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Sessões recentes", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${sessions.size} registradas",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            SmallActionChip(
                label = if (sessions.isEmpty()) "—" else "Ver todas",
                onClick = { if (sessions.isNotEmpty()) onShowAll() }
            )
        }

        Spacer(Modifier.height(10.dp))

        if (sessions.isEmpty()) {
            Text("Ainda não há sessões registradas.", style = MaterialTheme.typography.bodyLarge)
            return@NeonCard
        }

        val preview = sessions.take(8)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 340.dp)
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 2.dp)
            ) {
                items(preview) { s ->
                    SessionRowCompact(s)
                }

                if (sessions.size > preview.size) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onShowAll() },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Mostrar mais ${sessions.size - preview.size}…",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("→", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class SessionFilter(val label: String) {
    ALL("Todas"),
    DONE("Concluídas"),
    ABANDONED("Abandonadas")
}

private enum class SessionSort(val label: String) {
    RECENT("Mais recentes"),
    XP("Mais XP")
}

@Composable
private fun RecentSessionsDialog(
    sessions: List<SessionRowUi>,
    initialLanguage: String?,
    onClose: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val zone = remember { ZoneId.of("America/Sao_Paulo") }
    val ptBr = remember { Locale("pt", "BR") }
    val dateHeaderFmt = remember { DateTimeFormatter.ofPattern("EEE, dd/MM/yyyy", ptBr) }

    val cfg = LocalConfiguration.current
    val maxDialogH = (cfg.screenHeightDp.dp * 0.92f)

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(SessionFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(SessionSort.RECENT) }
    var selectedLanguage by rememberSaveable { mutableStateOf<String?>(initialLanguage) }
    val initialLangState by rememberUpdatedState(initialLanguage)
    var selectedDayKey by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (initialLangState != null) selectedLanguage = initialLangState
    }

    fun extractLanguage(s: SessionRowUi): String {
        val t = s.title
        val idx = t.indexOf("•")
        return if (idx > 0) t.substring(0, idx).trim() else t.take(2).uppercase()
    }

    fun toLocalDate(ms: Long): LocalDate =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    val languages = remember(sessions) {
        sessions.map { extractLanguage(it) }.distinct().sorted()
    }

    val filtered = remember(sessions, query, filter, sort, selectedLanguage, selectedDayKey) {
        val q = query.trim().lowercase()
        var list = sessions

        if (q.isNotBlank()) {
            list = list.filter { s -> s.title.lowercase().contains(q) || s.subtitle.lowercase().contains(q) }
        }

        list = when (filter) {
            SessionFilter.DONE -> list.filter { !it.abandoned }
            SessionFilter.ABANDONED -> list.filter { it.abandoned }
            SessionFilter.ALL -> list
        }

        if (selectedLanguage != null) {
            val lang = selectedLanguage!!
            list = list.filter { extractLanguage(it) == lang }
        }

        if (selectedDayKey != null) {
            val d = LocalDate.parse(selectedDayKey)
            list = list.filter { toLocalDate(it.atMs) == d }
        }

        list = when (sort) {
            SessionSort.XP -> list.sortedByDescending { it.xp }
            SessionSort.RECENT -> list.sortedByDescending { it.atMs }
        }

        list
    }

    data class Group(val date: LocalDate, val items: List<SessionRowUi>)

    val groups = remember(filtered) {
        filtered
            .groupBy { s -> toLocalDate(s.atMs) }
            .entries
            .sortedByDescending { it.key }
            .map { (date, items) -> Group(date, items) }
    }

    fun relativeHeader(date: LocalDate): String {
        val today = LocalDate.now(zone)
        return when (date) {
            today -> "Hoje • ${date.format(dateHeaderFmt)}"
            today.minusDays(1) -> "Ontem • ${date.format(dateHeaderFmt)}"
            else -> date.format(dateHeaderFmt)
        }
    }

    val expandedByDateKey = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(groups) {
        if (expandedByDateKey.isEmpty()) {
            val today = LocalDate.now(zone)
            val yesterday = today.minusDays(1)
            groups.forEach { g ->
                val key = g.date.toString()
                expandedByDateKey[key] = (g.date == today || g.date == yesterday)
            }
        } else {
            groups.forEach { g ->
                val key = g.date.toString()
                if (!expandedByDateKey.containsKey(key)) expandedByDateKey[key] = false
            }
        }
    }

    LaunchedEffect(selectedDayKey) {
        val k = selectedDayKey
        if (k != null) expandedByDateKey[k] = true
    }

    fun setAllExpanded(value: Boolean) {
        groups.forEach { g -> expandedByDateKey[g.date.toString()] = value }
    }

    Dialog(onDismissRequest = onClose) {
        // ✅ Limita altura e evita corte em telas pequenas
        NeonCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .heightIn(max = maxDialogH)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Todas as sessões",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        val subtitle = buildString {
                            append("${filtered.size} itens")
                            if (selectedLanguage != null) append(" • ${selectedLanguage}")
                            if (selectedDayKey != null) append(" • ${selectedDayKey}")
                        }

                        Spacer(Modifier.height(2.dp))
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                    }

                    SmallActionChip(label = "Fechar", onClick = onClose)
                }

                Spacer(Modifier.height(12.dp))

                // ✅ Em telas pequenas, a área de filtros pode rolar independentemente
                val filterScroll = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = (cfg.screenHeightDp.dp * 0.38f))
                        .verticalScroll(filterScroll),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Buscar (idioma, tipo, etc.)") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = cs.surfaceVariant.copy(alpha = 0.35f),
                            unfocusedContainerColor = cs.surfaceVariant.copy(alpha = 0.25f)
                        )
                    )

                    Text("Idiomas", style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            SegChip(
                                selected = selectedLanguage == null,
                                label = "Todos",
                                onClick = { selectedLanguage = null }
                            )
                        }
                        items(languages) { lang ->
                            SegChip(
                                selected = selectedLanguage == lang,
                                label = lang,
                                onClick = { selectedLanguage = lang }
                            )
                        }
                    }

                    Text("Filtros", style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        item { SegChip(selected = filter == SessionFilter.ALL, label = SessionFilter.ALL.label) { filter = SessionFilter.ALL } }
                        item { SegChip(selected = filter == SessionFilter.DONE, label = SessionFilter.DONE.label) { filter = SessionFilter.DONE } }
                        item { SegChip(selected = filter == SessionFilter.ABANDONED, label = SessionFilter.ABANDONED.label) { filter = SessionFilter.ABANDONED } }
                    }

                    Text("Ordenação", style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        item { SegChip(selected = sort == SessionSort.RECENT, label = SessionSort.RECENT.label) { sort = SessionSort.RECENT } }
                        item { SegChip(selected = sort == SessionSort.XP, label = SessionSort.XP.label) { sort = SessionSort.XP } }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        item { SmallActionChip("Expandir tudo", onClick = { setAllExpanded(true) }) }
                        item { SmallActionChip("Recolher tudo", onClick = { setAllExpanded(false) }) }
                        if (selectedDayKey != null) item { SmallActionChip("Limpar dia", onClick = { selectedDayKey = null }) }
                        if (selectedLanguage != null) item { SmallActionChip("Limpar idioma", onClick = { selectedLanguage = null }) }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ✅ Lista ocupa o resto: sempre acessível
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (groups.isEmpty()) {
                        Text("Nenhuma sessão encontrada.", style = MaterialTheme.typography.bodyLarge)
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 6.dp)
                        ) {
                            groups.forEach { g ->
                                val key = g.date.toString()
                                val expanded = expandedByDateKey[key] == true
                                val xpSum = g.items.sumOf { it.xp }
                                val abandonedCount = g.items.count { it.abandoned }

                                item {
                                    PremiumDayHeader(
                                        text = relativeHeader(g.date),
                                        isDayFiltered = (selectedDayKey == key),
                                        expanded = expanded,
                                        sessionsCount = g.items.size,
                                        xpSum = xpSum,
                                        abandonedCount = abandonedCount,
                                        onHeaderClick = { selectedDayKey = if (selectedDayKey == key) null else key },
                                        onToggleExpand = { expandedByDateKey[key] = !expanded }
                                    )
                                }

                                item {
                                    if (!expanded) {
                                        CollapsedDayPreview(
                                            sessions = g.items.take(2),
                                            remainingCount = (g.items.size - 2).coerceAtLeast(0),
                                            onExpand = { expandedByDateKey[key] = true }
                                        )
                                    }
                                }

                                item {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        AnimatedVisibility(
                                            visible = expanded,
                                            enter = fadeIn(tween(160)) + expandVertically(
                                                tween(220, easing = FastOutSlowInEasing)
                                            ),
                                            exit = fadeOut(tween(120)) + shrinkVertically(
                                                tween(180, easing = FastOutSlowInEasing)
                                            )
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                g.items.forEach { s -> SessionRow(s) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumDayHeader(
    text: String,
    isDayFiltered: Boolean,
    expanded: Boolean,
    sessionsCount: Int,
    xpSum: Int,
    abandonedCount: Int,
    onHeaderClick: () -> Unit,
    onToggleExpand: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val chevron = if (expanded) "▾" else "▸"

    val meta = buildString {
        append("$sessionsCount sessões • $xpSum XP")
        if (abandonedCount > 0) append(" • $abandonedCount abandonadas")
        if (isDayFiltered) append(" • FILTRADO")
    }

    val bg = if (isDayFiltered) cs.tertiary.copy(alpha = 0.16f) else cs.secondary.copy(alpha = 0.14f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onHeaderClick() },
        color = bg,
        contentColor = cs.onSecondaryContainer,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = chevron, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = cs.onSecondaryContainer.copy(alpha = 0.85f)
                )
            }

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable { onToggleExpand() },
                color = cs.surfaceVariant.copy(alpha = 0.45f),
                contentColor = cs.onSurface,
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = if (expanded) "Recolher" else "Expandir",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun CollapsedDayPreview(
    sessions: List<SessionRowUi>,
    remainingCount: Int,
    onExpand: () -> Unit
) {
    val cs = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        sessions.forEach { s ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = cs.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = s.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = s.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = cs.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.width(10.dp))
                    Text(text = "${s.xp} XP", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        if (remainingCount > 0) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onExpand() },
                color = cs.surfaceVariant.copy(alpha = 0.42f),
                contentColor = cs.onSurface,
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "+$remainingCount mais…",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("→", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
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
        shape = RoundedCornerShape(999.dp)
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
private fun SessionRowCompact(s: SessionRowUi) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cs.surfaceVariant.copy(alpha = 0.62f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = s.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (s.abandoned) "${s.subtitle} • abandonada" else s.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (s.abandoned) cs.tertiary else cs.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(10.dp))
            Text(text = "${s.xp} XP", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SessionRow(s: SessionRowUi) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cs.surfaceVariant.copy(alpha = 0.72f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = s.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))

                val subtitle = buildString {
                    append(s.subtitle)
                    if (s.abandoned) append(" • abandonada")
                }

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (s.abandoned) cs.tertiary else cs.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(text = "${s.xp} XP", style = MaterialTheme.typography.titleLarge)
                if (s.abandoned) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        color = cs.tertiary.copy(alpha = 0.16f),
                        contentColor = cs.tertiary,
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = "ABANDONADA",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        color = cs.surfaceVariant.copy(alpha = 0.72f),
        contentColor = cs.onSurface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun SmallActionChip(label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        color = cs.surfaceVariant.copy(alpha = 0.70f),
        contentColor = cs.onSurface,
        shape = RoundedCornerShape(999.dp)
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
private fun DayDetailsDialog(day: DayProgressUi, onClose: () -> Unit) {
    val dateFmt = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    val cfg = LocalConfiguration.current
    val maxH = (cfg.screenHeightDp.dp * 0.86f)

    Dialog(onDismissRequest = onClose) {
        NeonCard(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxH)
        ) {
            // ✅ Scroll interno (evita corte em telas pequenas)
            val scroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll)
            ) {
                Text("Detalhes do dia", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))

                Text("${day.label} • ${day.date.format(dateFmt)}", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(10.dp))

                Text("XP: ${day.xp}", style = MaterialTheme.typography.titleLarge)
                Text("Sessões: ${day.sessions}", style = MaterialTheme.typography.titleLarge)
                Text("Tempo: ${day.minutes} min", style = MaterialTheme.typography.titleLarge)

                Spacer(Modifier.height(14.dp))
                NeonButton(text = "Fechar", onClick = onClose)
            }
        }
    }
}
