// DailySetScreen.kt
package com.trililingo.ui.screens.dailyset

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.trililingo.ui.design.NeonBottomSheet
import com.trililingo.ui.design.NeonButton
import com.trililingo.ui.design.NeonCard

private enum class DailySetViewMode { GRID, LIST }
private enum class DailySetSort { PROMPT, READING, DIFFICULTY }

@Composable
fun DailySetScreen(
    language: String,
    skill: String,
    onBack: () -> Unit,
    vm: DailySetViewModel = hiltViewModel()
) {
    val title = when (skill) {
        "HIRAGANA" -> "Hiragana"
        "KATAKANA" -> "Katakana"
        "KANJI" -> "Kanji"
        "HANZI" -> "Hanzi"
        else -> skill
    }

    LaunchedEffect(language, skill) {
        vm.load(language, skill)
    }

    val state by vm.state.collectAsState()
    val cs = MaterialTheme.colorScheme
    val cfg = LocalConfiguration.current

    // ✅ Responsividade: espaçamentos e paddings variam por largura/altura
    val compact = (cfg.screenWidthDp < 360) || (cfg.screenHeightDp < 700)
    val outerPadding = if (compact) 14.dp else 18.dp
    val cardGap = if (compact) 10.dp else 14.dp
    val gapXS = if (compact) 4.dp else 6.dp
    val gapS = if (compact) 6.dp else 10.dp

    var query by rememberSaveable { mutableStateOf("") }
    var viewMode by rememberSaveable { mutableStateOf(DailySetViewMode.GRID) }
    var sort by rememberSaveable { mutableStateOf(DailySetSort.PROMPT) }

    // ✅ Escalável: exibe pronúncia se existir (sem hardcode por idioma)
    val showPronPt = true

    // ✅ estado do item selecionado para exibir detalhes via long press
    var details by remember { mutableStateOf<DailySetItemUi?>(null) }

    // ✅ novo: modal de itens abre ao clicar no header "Itens"
    var itemsModalOpen by rememberSaveable { mutableStateOf(false) }

    val selectedCount = state.selectedIds.size
    val minReq = state.minRequired

    val filtered = run {
        val base = if (query.isBlank()) state.items else {
            val q = query.trim().lowercase()
            state.items.filter { it.searchableText().lowercase().contains(q) }
        }

        when (sort) {
            DailySetSort.PROMPT -> base.sortedBy { it.prompt }
            DailySetSort.READING -> base.sortedBy { it.answer }
            DailySetSort.DIFFICULTY -> base.sortedByDescending { it.meta?.difficulty ?: 0 }
        }
    }

    val canSave = !state.enabled || selectedCount >= minReq

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(outerPadding),
        verticalArrangement = Arrangement.spacedBy(cardGap)
    ) {
        // ==========================
        // ✅ CARD DE FILTROS (compactado)
        // ==========================
        NeonCard {
            Text("Conjunto do desafio diário", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(gapXS))
            Text(
                text = "$title • $language/$skill",
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(gapS))

            when {
                state.loading -> Text("Carregando conteúdo offline…", style = MaterialTheme.typography.bodyLarge)
                state.error != null -> Text(state.error ?: "Erro", style = MaterialTheme.typography.bodyLarge)
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TinyToggle(
                            label = if (state.enabled) "Usar meu conjunto ✅" else "Usar meu conjunto",
                            onClick = { vm.toggleEnabled() }
                        )
                        Text(
                            text = "Selecionados: $selectedCount (mín. $minReq)",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (state.enabled && selectedCount < minReq) cs.tertiary else cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (state.enabled && selectedCount < minReq) {
                        Spacer(Modifier.height(gapXS))
                        Text(
                            text = "Selecione pelo menos $minReq itens para o desafio diário usar somente seu conjunto.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.tertiary.copy(alpha = 0.9f)
                        )
                    }

                    Spacer(Modifier.height(gapS))

                    SearchBox(
                        value = query,
                        onChange = { query = it },
                        onClear = { query = "" }
                    )

                    Spacer(Modifier.height(gapS))

                    // ✅ Em vez de Row “travada”, usa LazyRow (não quebra e não cresce verticalmente)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            TinyToggle(
                                label = if (viewMode == DailySetViewMode.GRID) "Grade ✅" else "Grade",
                                onClick = { viewMode = DailySetViewMode.GRID }
                            )
                        }
                        item {
                            TinyToggle(
                                label = if (viewMode == DailySetViewMode.LIST) "Lista ✅" else "Lista",
                                onClick = { viewMode = DailySetViewMode.LIST }
                            )
                        }

                        item { Spacer(Modifier.padding(horizontal = 2.dp)) }

                        item {
                            TinyToggle(
                                label = if (sort == DailySetSort.PROMPT) "Prompt ✅" else "Prompt",
                                onClick = { sort = DailySetSort.PROMPT }
                            )
                        }
                        item {
                            TinyToggle(
                                label = if (sort == DailySetSort.READING) "Leitura ✅" else "Leitura",
                                onClick = { sort = DailySetSort.READING }
                            )
                        }
                        item {
                            TinyToggle(
                                label = if (sort == DailySetSort.DIFFICULTY) "Dificuldade ✅" else "Dificuldade",
                                onClick = { sort = DailySetSort.DIFFICULTY }
                            )
                        }
                    }

                    Spacer(Modifier.height(gapS))

                    // ✅ AÇÕES compactas (chips), em vez de botões altos ocupando muita área
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActionChip(
                            label = "Selecionar tudo",
                            enabled = !state.loading && state.items.isNotEmpty(),
                            onClick = { vm.selectAll() }
                        )
                        ActionChip(
                            label = "Limpar",
                            enabled = !state.loading && state.selectedIds.isNotEmpty(),
                            onClick = { vm.clearSelection() }
                        )

                        Spacer(Modifier.weight(1f))

                        // ✅ feedback rápido (compacto)
                        Text(
                            text = "${filtered.size}/${state.items.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ==========================
        // ✅ CARD ITENS (header clicável abre modal)
        // ==========================
        NeonCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .combinedClickable(
                        onClick = { itemsModalOpen = true },
                        onLongClick = null
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Itens",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(gapS))

            if (!state.loading && state.error == null) {
                if (filtered.isEmpty()) {
                    Text(
                        text = if (query.isBlank()) "Sem itens para exibir." else "Nada encontrado para \"$query\".",
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    // ✅ aqui continua scroll normal “embutido”
                    val gridCells = rememberDailySetGridCells()

                    if (viewMode == DailySetViewMode.GRID) {
                        DailySetGrid(
                            items = filtered,
                            selectedIds = state.selectedIds,
                            showPronPt = showPronPt,
                            columns = gridCells,
                            onToggle = { vm.toggleItem(it) },
                            onDetails = { details = it }
                        )
                    } else {
                        DailySetList(
                            items = filtered,
                            selectedIds = state.selectedIds,
                            showPronPt = showPronPt,
                            onToggle = { vm.toggleItem(it) },
                            onDetails = { details = it }
                        )
                    }
                }
            }
        }

        // ==========================
        // ✅ AÇÕES PRINCIPAIS
        // ==========================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NeonButton(
                text = "Voltar",
                modifier = Modifier.weight(1f),
                onClick = onBack
            )
            NeonButton(
                text = "Salvar",
                modifier = Modifier.weight(1f),
                enabled = !state.loading && state.error == null && canSave,
                onClick = { vm.save(language, skill, onSaved = onBack) }
            )
        }
    }
    // ==========================
    // ✅ BOTTOMSHEET DE ITENS (swipe para baixo fecha)
    // ==========================
    if (itemsModalOpen) {
        DailySetItemsBottomSheet(
            title = "Itens",
            subtitle = "$title • $language/$skill",
            countLabel = "${filtered.size}/${state.items.size}",
            viewMode = viewMode,
            sort = sort,
            onSetViewMode = { viewMode = it },
            onSetSort = { sort = it },
            items = filtered,
            selectedIds = state.selectedIds,
            showPronPt = showPronPt,
            onToggle = { vm.toggleItem(it) },
            onDetails = { details = it },
            onClose = { itemsModalOpen = false }
        )
    }

    // ==========================
    // ✅ DETALHES DO ITEM (long press)
    // ==========================
    details?.let { item ->
        DailySetItemDetailsDialog(
            item = item,
            onClose = { details = null }
        )
    }
}

@Composable
private fun rememberDailySetGridCells(): GridCells {
    val cfg = LocalConfiguration.current
    val w = cfg.screenWidthDp

    return remember(w) {
        when {
            w < 360 -> GridCells.Fixed(2)
            w < 600 -> GridCells.Fixed(3)
            w < 840 -> GridCells.Fixed(4)
            else -> GridCells.Fixed(5)
        }
    }
}

@Composable
private fun DailySetItemsBottomSheet(
    title: String,
    subtitle: String,
    countLabel: String,
    viewMode: DailySetViewMode,
    sort: DailySetSort,
    onSetViewMode: (DailySetViewMode) -> Unit,
    onSetSort: (DailySetSort) -> Unit,
    items: List<DailySetItemUi>,
    selectedIds: Set<String>,
    showPronPt: Boolean,
    onToggle: (String) -> Unit,
    onDetails: (DailySetItemUi) -> Unit,
    onClose: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val cfg = LocalConfiguration.current
    val maxListH = (cfg.screenHeightDp.dp * 0.68f)

    val gridCells = rememberDailySetGridCells()

    NeonBottomSheet(
        onDismiss = onClose,
        title = title,
        subtitle = subtitle,
        headerTrailing = {
            // Mantém o botão, mas agora swipe-down e tap fora também fecham (onDismiss)
            ActionChip(label = "Fechar", enabled = true, onClick = onClose)
        },
        maxHeightFraction = 0.92f
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Mostrando: $countLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }

            // ✅ controles compactos dentro do sheet
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    TinyToggle(
                        label = if (viewMode == DailySetViewMode.GRID) "Grade ✅" else "Grade",
                        onClick = { onSetViewMode(DailySetViewMode.GRID) }
                    )
                }
                item {
                    TinyToggle(
                        label = if (viewMode == DailySetViewMode.LIST) "Lista ✅" else "Lista",
                        onClick = { onSetViewMode(DailySetViewMode.LIST) }
                    )
                }

                item { Spacer(Modifier.padding(horizontal = 2.dp)) }

                item {
                    TinyToggle(
                        label = if (sort == DailySetSort.PROMPT) "Prompt ✅" else "Prompt",
                        onClick = { onSetSort(DailySetSort.PROMPT) }
                    )
                }
                item {
                    TinyToggle(
                        label = if (sort == DailySetSort.READING) "Leitura ✅" else "Leitura",
                        onClick = { onSetSort(DailySetSort.READING) }
                    )
                }
                item {
                    TinyToggle(
                        label = if (sort == DailySetSort.DIFFICULTY) "Dificuldade ✅" else "Dificuldade",
                        onClick = { onSetSort(DailySetSort.DIFFICULTY) }
                    )
                }
            }

            // ✅ área rolável (lista/grade) com altura limitada para UX em telas pequenas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxListH)
            ) {
                if (items.isEmpty()) {
                    Text("Sem itens para exibir.", style = MaterialTheme.typography.bodyLarge)
                } else {
                    if (viewMode == DailySetViewMode.GRID) {
                        DailySetGrid(
                            items = items,
                            selectedIds = selectedIds,
                            showPronPt = showPronPt,
                            columns = gridCells,
                            onToggle = onToggle,
                            onDetails = onDetails
                        )
                    } else {
                        DailySetList(
                            items = items,
                            selectedIds = selectedIds,
                            showPronPt = showPronPt,
                            onToggle = onToggle,
                            onDetails = onDetails
                        )
                    }
                }
            }
        }
    }
}




@Composable
private fun DailySetGrid(
    items: List<DailySetItemUi>,
    selectedIds: Set<String>,
    showPronPt: Boolean,
    columns: GridCells,
    onToggle: (String) -> Unit,
    onDetails: (DailySetItemUi) -> Unit
) {
    LazyVerticalGrid(
        columns = columns,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items) { item ->
            DailySetTile(
                item = item,
                selected = item.id in selectedIds,
                showPronPt = showPronPt,
                onClick = { onToggle(item.id) },
                onLongPress = { onDetails(item) }
            )
        }
    }
}

@Composable
private fun DailySetList(
    items: List<DailySetItemUi>,
    selectedIds: Set<String>,
    showPronPt: Boolean,
    onToggle: (String) -> Unit,
    onDetails: (DailySetItemUi) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items) { item ->
            DailySetRow(
                item = item,
                selected = item.id in selectedIds,
                showPronPt = showPronPt,
                onClick = { onToggle(item.id) },
                onLongPress = { onDetails(item) }
            )
        }
    }
}

@Composable
private fun DailySetTile(
    item: DailySetItemUi,
    selected: Boolean,
    showPronPt: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val border = if (selected) cs.primary.copy(alpha = 0.85f) else cs.outline.copy(alpha = 0.65f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            ),
        color = cs.surfaceVariant.copy(alpha = if (selected) 0.95f else 0.72f),
        contentColor = cs.onSurface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, border)
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = item.prompt,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.answer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val pron = item.pronunciationPt
                if (showPronPt && !pron.isNullOrBlank()) {
                    Text(
                        text = pron,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val romaji = item.meta?.romanization?.value
                if (!romaji.isNullOrBlank() && romaji != item.answer) {
                    Text(
                        text = romaji,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (selected) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.titleLarge,
                    color = cs.primary,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }
    }
}

@Composable
private fun DailySetRow(
    item: DailySetItemUi,
    selected: Boolean,
    showPronPt: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val border = if (selected) cs.primary.copy(alpha = 0.85f) else cs.outline.copy(alpha = 0.65f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            ),
        color = cs.surfaceVariant.copy(alpha = if (selected) 0.95f else 0.72f),
        contentColor = cs.onSurface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = item.prompt,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(0.35f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Column(modifier = Modifier.weight(0.65f)) {
                Text(
                    text = item.answer,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val extra = listOfNotNull(
                    item.meaning?.takeIf { it.isNotBlank() },
                    item.meta?.romanization?.value?.takeIf { it.isNotBlank() && it != item.answer },
                    item.pronunciationPt?.takeIf { showPronPt && it.isNotBlank() }
                ).joinToString(" • ")
                if (extra.isNotBlank()) {
                    Text(
                        text = extra,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = if (selected) "✓" else "",
                style = MaterialTheme.typography.titleLarge,
                color = cs.primary
            )
        }
    }
}

@Composable
private fun DailySetItemDetailsDialog(
    item: DailySetItemUi,
    onClose: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val meta = item.meta

    Dialog(onDismissRequest = onClose) {
        NeonCard(modifier = Modifier.fillMaxWidth()) {
            Text("Detalhes do item", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp))

            Text(
                text = item.prompt,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))

            Text(
                text = "Leitura: ${item.answer}",
                style = MaterialTheme.typography.bodyLarge
            )

            val pron = item.pronunciationPt?.trim().orEmpty()
            if (pron.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Pronúncia (PT-BR): $pron",
                    style = MaterialTheme.typography.bodyLarge,
                    color = cs.onSurface
                )
            }

            val meaning = item.meaning?.trim().orEmpty()
            Text(
                text = if (meaning.isNotBlank()) "Significado: $meaning" else "Significado: —",
                style = MaterialTheme.typography.bodyLarge,
                color = if (meaning.isNotBlank()) cs.onSurface else cs.onSurfaceVariant
            )

            val romaji = meta?.romanization?.value?.trim().orEmpty()
            if (romaji.isNotBlank() && romaji != item.answer) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Romaji: $romaji",
                    style = MaterialTheme.typography.bodyLarge,
                    color = cs.onSurface
                )
            }

            val mnemonic = meta?.mnemonicPt?.trim().orEmpty()
            if (mnemonic.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Mnemônico: $mnemonic",
                    style = MaterialTheme.typography.bodyLarge,
                    color = cs.onSurface
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
                Spacer(Modifier.height(10.dp))
                Text(
                    text = badge,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }

            val tags = meta?.tags.orEmpty()
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Tags: ${tags.take(12).joinToString(", ")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(14.dp))
            NeonButton(text = "Fechar", onClick = onClose)
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
        placeholder = { Text("Buscar: caractere, leitura, pronúncia, romaji, tags, categoria…") },
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
private fun TinyToggle(label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = null),
        color = cs.surfaceVariant.copy(alpha = 0.72f),
        contentColor = cs.onSurface,
        shape = RoundedCornerShape(999.dp),
        tonalElevation = 0.dp
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ActionChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(999.dp)
    val border = if (enabled) cs.outline.copy(alpha = 0.65f) else cs.outline.copy(alpha = 0.35f)
    val bg = if (enabled) cs.surfaceVariant.copy(alpha = 0.72f) else cs.surfaceVariant.copy(alpha = 0.45f)
    val fg = if (enabled) cs.onSurface else cs.onSurfaceVariant

    Surface(
        modifier = Modifier
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
        color = bg,
        contentColor = fg,
        shape = shape,
        border = BorderStroke(1.dp, border),
        tonalElevation = 0.dp
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
