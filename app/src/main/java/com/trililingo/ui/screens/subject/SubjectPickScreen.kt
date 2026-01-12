package com.trililingo.ui.screens.subject

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trililingo.ui.design.NeonBottomSheet
import com.trililingo.ui.design.NeonButton
import com.trililingo.ui.design.NeonCard
import java.util.Locale

private enum class ChapterViewMode { LIST, GRID }
private enum class ChapterSort { CHAPTER, TITLE, QUESTIONS }

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

    val cfg = LocalConfiguration.current
    val compact = (cfg.screenWidthDp < 360) || (cfg.screenHeightDp < 700)
    val outerPadding = if (compact) 14.dp else 18.dp
    val gap = if (compact) 10.dp else 14.dp

    val headerTitle = if (mode.equals("daily", true)) "Desafio Diário" else "Estudo Livre"
    val headerDesc = if (mode.equals("daily", true)) {
        "Sessão curta com limite • foco em revisão e streak."
    } else {
        "Sessão aberta • sem limite • ótimo pra aprofundar."
    }

    var query by rememberSaveable { mutableStateOf("") }
    var selectedTypeTag by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(ChapterSort.CHAPTER) }
    var viewMode by rememberSaveable { mutableStateOf(ChapterViewMode.LIST) }

    var details by remember { mutableStateOf<SubjectChapterUi?>(null) }

    val filtered = remember(state.chapters, query, selectedTypeTag, sort) {
        var list = state.chapters

        val q = query.trim().lowercase(Locale.getDefault())
        if (q.isNotBlank()) {
            list = list.filter { ch ->
                buildString {
                    append(ch.headerLabel).append(" ")
                    append(ch.keyTopics.joinToString(" ")).append(" ")
                    append(ch.distinctTypeTags.joinToString(" ")).append(" ")
                    append("cap ").append(ch.chapterNumber ?: "")
                }.lowercase(Locale.getDefault()).contains(q)
            }
        }

        selectedTypeTag?.let { tag ->
            list = list.filter { it.distinctTypeTags.contains(tag) }
        }

        list = when (sort) {
            ChapterSort.CHAPTER -> list.sortedWith(
                compareBy<SubjectChapterUi> { it.chapterNumber ?: Int.MAX_VALUE }.thenBy { it.title.lowercase(Locale.getDefault()) }
            )
            ChapterSort.TITLE -> list.sortedBy { it.title.lowercase(Locale.getDefault()) }
            ChapterSort.QUESTIONS -> list.sortedByDescending { it.questionCountValid }
        }

        list
    }

    val selectedId = state.selectedChapterId
    val bottomBarHeight = if (compact) 84.dp else 92.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        when (viewMode) {
            ChapterViewMode.LIST -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = outerPadding, vertical = outerPadding),
                    verticalArrangement = Arrangement.spacedBy(gap),
                    contentPadding = PaddingValues(bottom = bottomBarHeight + 14.dp)
                ) {
                    item {
                        HeaderCard(
                            title = headerTitle,
                            subjectTitle = state.subjectTitle,
                            trackTitle = state.trackTitle,
                            trackSubtitle = state.trackSubtitle,
                            description = headerDesc,
                            compact = compact,
                            onBack = onBack
                        )
                    }

                    item {
                        ControlsCard(
                            compact = compact,
                            query = query,
                            onQueryChange = { query = it },
                            typeTags = state.availableTypeTags,
                            selectedTypeTag = selectedTypeTag,
                            onTypeTagChange = { selectedTypeTag = it },
                            sort = sort,
                            onSortChange = { sort = it },
                            viewMode = viewMode,
                            onViewModeChange = { viewMode = it }
                        )
                    }

                    item {
                        ChapterCard(
                            compact = compact,
                            title = "Geral (todos)",
                            subtitle = "Usa o pool completo do assunto (melhor p/ revisão).",
                            selected = selectedId == null,
                            metrics = "Perguntas válidas: ${state.chapters.sumOf { it.questionCountValid }} • Capítulos: ${state.chapters.size}",
                            badgeRight = "ALL",
                            onClick = { vm.selectChapter(null) },
                            onLongPress = { details = null } // nada
                        )
                    }

                    if (state.loading) {
                        item { NeonCard { Text("Carregando capítulos…", style = MaterialTheme.typography.bodyLarge) } }
                    } else if (state.error != null) {
                        item {
                            NeonCard {
                                Text("Erro ao carregar", style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(8.dp))
                                Text(state.error ?: "Erro desconhecido", style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.height(12.dp))
                                NeonButton("Voltar") { onBack() }
                            }
                        }
                    } else {
                        items(filtered, key = { it.id }) { ch ->
                            ChapterCard(
                                compact = compact,
                                title = ch.headerLabel,
                                subtitle = ch.keyTopics.take(3).joinToString(" • ").ifBlank { "Sem tópicos cadastrados" },
                                selected = selectedId == ch.id,
                                metrics = "Perguntas: ${ch.questionCountValid}/${ch.questionCountTotal} • Dificuldade: ${ch.avgDifficulty}/5",
                                badgeRight = "☆".repeat(ch.avgDifficulty).takeIf { it.isNotBlank() } ?: "",
                                onClick = { vm.selectChapter(ch.id) },
                                onLongPress = { details = ch }
                            )
                        }

                        if (filtered.isEmpty()) {
                            item {
                                NeonCard {
                                    Text("Nada encontrado", style = MaterialTheme.typography.titleLarge)
                                    Spacer(Modifier.height(8.dp))
                                    Text("Tente remover filtros ou ajustar a busca.", style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            }

            ChapterViewMode.GRID -> {
                val columns = if (compact) 1 else 2

                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = outerPadding, vertical = outerPadding),
                    verticalArrangement = Arrangement.spacedBy(gap),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    contentPadding = PaddingValues(bottom = bottomBarHeight + 14.dp)
                ) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(columns) }) {
                        HeaderCard(
                            title = headerTitle,
                            subjectTitle = state.subjectTitle,
                            trackTitle = state.trackTitle,
                            trackSubtitle = state.trackSubtitle,
                            description = headerDesc,
                            compact = compact,
                            onBack = onBack
                        )
                    }

                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(columns) }) {
                        ControlsCard(
                            compact = compact,
                            query = query,
                            onQueryChange = { query = it },
                            typeTags = state.availableTypeTags,
                            selectedTypeTag = selectedTypeTag,
                            onTypeTagChange = { selectedTypeTag = it },
                            sort = sort,
                            onSortChange = { sort = it },
                            viewMode = viewMode,
                            onViewModeChange = { viewMode = it }
                        )
                    }

                    item {
                        ChapterCard(
                            compact = compact,
                            title = "Geral (todos)",
                            subtitle = "Pool completo do assunto.",
                            selected = selectedId == null,
                            metrics = "Perguntas válidas: ${state.chapters.sumOf { it.questionCountValid }}",
                            badgeRight = "ALL",
                            onClick = { vm.selectChapter(null) },
                            onLongPress = { details = null }
                        )
                    }

                    if (state.loading) {
                        item {
                            NeonCard(modifier = Modifier.fillMaxWidth()) {
                                Text("Carregando…", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    } else if (state.error != null) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(columns) }) {
                            NeonCard {
                                Text("Erro", style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(8.dp))
                                Text(state.error ?: "Erro desconhecido", style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.height(12.dp))
                                NeonButton("Voltar") { onBack() }
                            }
                        }
                    } else {
                        items(filtered, key = { it.id }) { ch ->
                            ChapterCard(
                                compact = compact,
                                title = ch.headerLabel,
                                subtitle = ch.keyTopics.take(2).joinToString(" • ").ifBlank { "Sem tópicos" },
                                selected = selectedId == ch.id,
                                metrics = "Perguntas: ${ch.questionCountValid}/${ch.questionCountTotal} • Dif: ${ch.avgDifficulty}/5",
                                badgeRight = "${ch.avgDifficulty}/5",
                                onClick = { vm.selectChapter(ch.id) },
                                onLongPress = { details = ch }
                            )
                        }

                        if (filtered.isEmpty()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(columns) }) {
                                NeonCard {
                                    Text("Nada encontrado", style = MaterialTheme.typography.titleLarge)
                                    Spacer(Modifier.height(8.dp))
                                    Text("Remova filtros ou altere a busca.", style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ===== Bottom CTA (padrão premium) =====
        AnimatedVisibility(
            visible = !state.loading && state.error == null && (state.chapters.isNotEmpty()),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            NeonCard(
                modifier = Modifier
                    .padding(horizontal = outerPadding, vertical = 10.dp)
                    .fillMaxWidth()
                    .heightIn(min = bottomBarHeight)
            ) {
                val chosenLabel = if (selectedId == null) "Geral (todos)" else "Capítulo selecionado"
                Text(
                    chosenLabel,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NeonButton(text = "Voltar", fullWidth = false) { onBack() }

                    NeonButton(text = "Iniciar", fullWidth = true) {
                        val chapterOrAll = selectedId ?: "all"
                        onStart(chapterOrAll)
                    }
                }
            }
        }

        // ===== Bottom sheet de detalhes =====
        details?.let { ch ->
            ChapterDetailsSheet(
                chapter = ch,
                onDismiss = { details = null },
                onStart = {
                    vm.selectChapter(ch.id)
                    details = null
                    onStart(ch.id)
                }
            )
        }
    }
}

@Composable
private fun HeaderCard(
    title: String,
    subjectTitle: String,
    trackTitle: String,
    trackSubtitle: String,
    description: String,
    compact: Boolean,
    onBack: () -> Unit
) {
    NeonCard(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(if (compact) 6.dp else 8.dp))

        Text(
            "$subjectTitle • $trackTitle",
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        trackSubtitle.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
        Text(description, style = MaterialTheme.typography.bodyLarge)

        Spacer(Modifier.height(if (compact) 10.dp else 12.dp))
        NeonButton(text = "Voltar") { onBack() }
    }
}

@Composable
private fun ControlsCard(
    compact: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    typeTags: List<String>,
    selectedTypeTag: String?,
    onTypeTagChange: (String?) -> Unit,
    sort: ChapterSort,
    onSortChange: (ChapterSort) -> Unit,
    viewMode: ChapterViewMode,
    onViewModeChange: (ChapterViewMode) -> Unit
) {
    val cs = MaterialTheme.colorScheme

    NeonCard(modifier = Modifier.fillMaxWidth()) {
        Text("Filtrar & organizar", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(if (compact) 8.dp else 10.dp))

        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar capítulo, tópico ou tag…") },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = cs.surfaceVariant.copy(alpha = 0.65f),
                unfocusedContainerColor = cs.surfaceVariant.copy(alpha = 0.55f),
                focusedIndicatorColor = cs.primary.copy(alpha = 0.55f),
                unfocusedIndicatorColor = cs.outline.copy(alpha = 0.35f)
            ),
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(Modifier.height(if (compact) 10.dp else 12.dp))

        Text("Type tags", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                MiniChip(
                    selected = (selectedTypeTag == null),
                    label = "Todos",
                    onClick = { onTypeTagChange(null) }
                )
            }
            items(typeTags) { t ->
                MiniChip(
                    selected = (selectedTypeTag == t),
                    label = t,
                    onClick = { onTypeTagChange(if (selectedTypeTag == t) null else t) }
                )
            }
        }

        Spacer(Modifier.height(if (compact) 10.dp else 12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Ordenação", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        MiniChip(selected = sort == ChapterSort.CHAPTER, label = "Cap.", onClick = { onSortChange(ChapterSort.CHAPTER) })
                    }
                    item {
                        MiniChip(selected = sort == ChapterSort.TITLE, label = "Título", onClick = { onSortChange(ChapterSort.TITLE) })
                    }
                    item {
                        MiniChip(selected = sort == ChapterSort.QUESTIONS, label = "Perguntas", onClick = { onSortChange(ChapterSort.QUESTIONS) })
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text("Visual", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniChip(selected = viewMode == ChapterViewMode.LIST, label = "Lista", onClick = { onViewModeChange(ChapterViewMode.LIST) })
                    MiniChip(selected = viewMode == ChapterViewMode.GRID, label = "Grid", onClick = { onViewModeChange(ChapterViewMode.GRID) })
                }
            }
        }
    }
}

@Composable
private fun ChapterCard(
    compact: Boolean,
    title: String,
    subtitle: String,
    metrics: String,
    selected: Boolean,
    badgeRight: String,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val border = if (selected) cs.primary.copy(alpha = 0.65f) else cs.outline.copy(alpha = 0.30f)
    val bg = if (selected) cs.secondary.copy(alpha = 0.22f) else cs.surfaceVariant.copy(alpha = 0.70f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            ),
        shape = RoundedCornerShape(18.dp),
        color = bg,
        border = BorderStroke(1.dp, border)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = if (compact) 12.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (badgeRight.isNotBlank()) {
                    Spacer(Modifier.size(10.dp))
                    MiniPill(label = badgeRight, selected = selected)
                }
            }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = metrics,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "Toque para selecionar • Segure para detalhes",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChapterDetailsSheet(
    chapter: SubjectChapterUi,
    onDismiss: () -> Unit,
    onStart: () -> Unit
) {
    NeonBottomSheet(
        onDismiss = onDismiss,
        title = chapter.headerLabel,
        subtitle = "Perguntas válidas: ${chapter.questionCountValid}/${chapter.questionCountTotal} • Dificuldade média: ${chapter.avgDifficulty}/5",
        headerTrailing = { MiniChip(selected = false, label = "Fechar", onClick = onDismiss) },
        maxHeightFraction = 0.92f
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (chapter.keyTopics.isNotEmpty()) {
                Text("Tópicos-chave", style = MaterialTheme.typography.titleLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(chapter.keyTopics.take(20)) { t ->
                        MiniChip(selected = false, label = t, onClick = {})
                    }
                }
            }

            if (chapter.distinctTypeTags.isNotEmpty()) {
                Text("Type tags", style = MaterialTheme.typography.titleLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(chapter.distinctTypeTags.take(30)) { t ->
                        MiniChip(selected = false, label = t, onClick = {})
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            NeonButton(text = "Iniciar este capítulo") { onStart() }
        }
    }
}

@Composable
private fun MiniChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val bg = if (selected) cs.secondary.copy(alpha = 0.28f) else cs.surfaceVariant.copy(alpha = 0.70f)
    val border = if (selected) cs.primary.copy(alpha = 0.55f) else cs.outline.copy(alpha = 0.35f)

    Surface(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = {}),
        color = bg,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, border)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MiniPill(
    label: String,
    selected: Boolean
) {
    val cs = MaterialTheme.colorScheme
    val bg = if (selected) cs.primary.copy(alpha = 0.22f) else cs.surfaceVariant.copy(alpha = 0.55f)
    val border = cs.outline.copy(alpha = 0.30f)

    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, border)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
