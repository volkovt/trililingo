package com.trililingo.ui.screens.alphabet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trililingo.ui.design.NeonBottomSheet
import com.trililingo.ui.design.NeonButton
import com.trililingo.ui.design.NeonCard

private enum class AlphabetViewMode { GRID, LIST }
private enum class AlphabetSort { PROMPT, READING, MEANING, DIFFICULTY }

@Composable
fun AlphabetScreen(
    language: String,
    skill: String,
    onBack: () -> Unit,
    vm: AlphabetViewModel = hiltViewModel()
) {
    val title = when (skill) {
        "HIRAGANA" -> "Hiragana"
        "KATAKANA" -> "Katakana"
        "KANJI" -> "Kanji"
        "HANZI" -> "Hanzi"
        else -> skill
    }

    val state by vm.state.collectAsState()

    LaunchedEffect(language, skill) {
        vm.load(language, skill)
    }

    var query by rememberSaveable { mutableStateOf("") }
    var viewMode by rememberSaveable { mutableStateOf(AlphabetViewMode.GRID) }
    var sort by rememberSaveable { mutableStateOf(AlphabetSort.PROMPT) }
    var selected by remember { mutableStateOf<AlphabetRowUi?>(null) }

    val filtered = remember(state.items, query, sort) {
        val q = query.trim().lowercase()

        val base = if (q.isBlank()) {
            state.items
        } else {
            state.items.filter { row ->
                val e = row.entity
                val m = row.meta
                e.prompt.lowercase().contains(q) ||
                        e.answer.lowercase().contains(q) ||
                        e.meaning.lowercase().contains(q) ||
                        e.id.lowercase().contains(q) ||
                        (m?.category?.lowercase()?.contains(q) == true) ||
                        (m?.romanization?.value?.lowercase()?.contains(q) == true) ||
                        (m?.mnemonicPt?.lowercase()?.contains(q) == true) ||
                        (m?.tags?.any { it.lowercase().contains(q) } == true)
            }
        }

        when (sort) {
            AlphabetSort.PROMPT -> base.sortedBy { it.entity.prompt }
            AlphabetSort.READING -> base.sortedBy { it.entity.answer }
            AlphabetSort.MEANING -> base.sortedBy { it.entity.meaning }
            AlphabetSort.DIFFICULTY -> base.sortedByDescending { it.meta?.difficulty ?: 0 }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        NeonCard {
            Text("$title • $language/$skill", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.padding(top = 8.dp))

            when {
                state.loading -> Text("Carregando tabela offline…", style = MaterialTheme.typography.bodyLarge)
                state.error != null -> Text(state.error ?: "Erro", style = MaterialTheme.typography.bodyLarge)
                else -> Text("Mostrando: ${filtered.size}/${state.items.size}", style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(Modifier.padding(top = 12.dp))
            NeonButton(text = "Voltar", onClick = onBack)
        }

        NeonCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Text("Caracteres", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.padding(top = 10.dp))

            SearchBox(value = query, onChange = { query = it }, onClear = { query = "" })
            Spacer(Modifier.padding(top = 10.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    TinyToggle(label = if (viewMode == AlphabetViewMode.GRID) "Grade ✅" else "Grade") {
                        viewMode = AlphabetViewMode.GRID
                    }
                }
                item {
                    TinyToggle(label = if (viewMode == AlphabetViewMode.LIST) "Lista ✅" else "Lista") {
                        viewMode = AlphabetViewMode.LIST
                    }
                }

                item { Spacer(Modifier.padding(horizontal = 6.dp)) }

                item {
                    TinyToggle(label = if (sort == AlphabetSort.PROMPT) "Caractere ✅" else "Caractere") {
                        sort = AlphabetSort.PROMPT
                    }
                }
                item {
                    TinyToggle(label = if (sort == AlphabetSort.READING) "Leitura ✅" else "Leitura") {
                        sort = AlphabetSort.READING
                    }
                }
                item {
                    TinyToggle(label = if (sort == AlphabetSort.MEANING) "Significado ✅" else "Significado") {
                        sort = AlphabetSort.MEANING
                    }
                }
                item {
                    TinyToggle(label = if (sort == AlphabetSort.DIFFICULTY) "Dificuldade ✅" else "Dificuldade") {
                        sort = AlphabetSort.DIFFICULTY
                    }
                }
            }

            Spacer(Modifier.padding(top = 12.dp))

            if (!state.loading && state.items.isEmpty()) {
                Text(
                    text = "Nenhum item encontrado para $language/$skill.\nVerifique se existe pack em assets/packs/lang.",
                    style = MaterialTheme.typography.bodyMedium
                )
                return@NeonCard
            }

            if (!state.loading && filtered.isEmpty()) {
                Text(text = "Nada encontrado para \"$query\".", style = MaterialTheme.typography.bodyMedium)
                return@NeonCard
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (viewMode) {
                    AlphabetViewMode.GRID -> AlphabetGrid(items = filtered, onClick = { selected = it })
                    AlphabetViewMode.LIST -> AlphabetList(items = filtered, onClick = { selected = it })
                }
            }
        }
    }

    selected?.let { row ->
        CharacterDetailsSheet(
            row = row,
            sheetSubtitle = "$title • $language/$skill",
            onClose = { selected = null }
        )
    }
}

@Composable
private fun SearchBox(value: String, onChange: (String) -> Unit, onClear: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Buscar: caractere, leitura, romaji, tags, categoria…") },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = cs.surfaceVariant,
            unfocusedContainerColor = cs.surfaceVariant.copy(alpha = 0.75f),
            focusedTextColor = cs.onSurface,
            unfocusedTextColor = cs.onSurface,
            focusedIndicatorColor = cs.tertiary.copy(alpha = 0.65f),
            unfocusedIndicatorColor = cs.outline.copy(alpha = 0.55f),
            focusedPlaceholderColor = cs.onSurfaceVariant,
            unfocusedPlaceholderColor = cs.onSurfaceVariant
        ),
        trailingIcon = {
            if (value.isNotBlank()) {
                Text("✖", modifier = Modifier.padding(end = 10.dp).clickable { onClear() })
            } else {
                Text("🔎", modifier = Modifier.padding(end = 10.dp))
            }
        }
    )
}

@Composable
private fun TinyToggle(label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = cs.surfaceVariant.copy(alpha = 0.72f),
        contentColor = cs.onSurface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
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
private fun AlphabetGrid(items: List<AlphabetRowUi>, onClick: (AlphabetRowUi) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // ✅ Compatível com versões antigas do Compose: usa overload por "count"
        items(
            count = items.size,
            key = { idx -> items[idx].entity.id }
        ) { idx ->
            val row = items[idx]
            Box(modifier = Modifier.clickable { onClick(row) }) {
                NeonCard {
                    Text(row.entity.prompt, style = MaterialTheme.typography.displayLarge, maxLines = 1)
                    Text(
                        row.entity.answer,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val badge = buildString {
                        row.meta?.category?.let { append(it) }
                        row.meta?.difficulty?.let { d ->
                            if (isNotBlank()) append(" • ")
                            append("D$d")
                        }
                    }
                    if (badge.isNotBlank()) {
                        Text(
                            badge,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlphabetList(items: List<AlphabetRowUi>, onClick: (AlphabetRowUi) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ✅ Também usando overload por "count" (evita qualquer conflito/versão)
        items(
            count = items.size,
            key = { idx -> items[idx].entity.id }
        ) { idx ->
            val row = items[idx]
            Box(modifier = Modifier.clickable { onClick(row) }) {
                NeonCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            row.entity.prompt,
                            style = MaterialTheme.typography.displayLarge,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                row.entity.answer,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                row.entity.meaning,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            val extra = listOfNotNull(
                                row.meta?.romanization?.value?.let { "romaji:$it" },
                                row.meta?.category,
                                row.meta?.difficulty?.let { "D$it" }
                            ).joinToString(" • ")
                            if (extra.isNotBlank()) {
                                Text(
                                    extra,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterDetailsSheet(
    row: AlphabetRowUi,
    sheetSubtitle: String,
    onClose: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val e = row.entity
    val m = row.meta

    val cfg = LocalConfiguration.current
    val maxH = (cfg.screenHeightDp.dp * 0.80f)

    NeonBottomSheet(
        onDismiss = onClose,
        title = "Detalhes",
        subtitle = sheetSubtitle,
        headerTrailing = {
            Surface(
                modifier = Modifier.clickable { onClose() },
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
                tonalElevation = 0.dp
            ) {
                Text(
                    text = "Fechar",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        },
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NeonButton(text = "Copiar (caractere + leitura)") {
                    clipboard.setText(AnnotatedString("${e.prompt} — ${e.answer}"))
                }
                NeonButton(text = "Copiar (mnemônico)") {
                    clipboard.setText(AnnotatedString(m?.mnemonicPt ?: ""))
                }
            }
        },
        maxHeightFraction = 0.92f
    ) {
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxH)
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(e.prompt, style = MaterialTheme.typography.displayLarge)

            Text("Leitura: ${e.answer}", style = MaterialTheme.typography.bodyLarge)
            Text("Significado: ${e.meaning}", style = MaterialTheme.typography.bodyLarge)

            if (!m?.romanization?.value.isNullOrBlank()) {
                Text("Romaji: ${m?.romanization?.value}", style = MaterialTheme.typography.bodyLarge)
            }
            if (!m?.gojuon?.rowLabel.isNullOrBlank()) {
                Text("Gojūon: ${m?.gojuon?.rowLabel}", style = MaterialTheme.typography.bodyLarge)
            }
            if (!m?.mnemonicPt.isNullOrBlank()) {
                Text("Mnemônico: ${m?.mnemonicPt}", style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(Modifier.padding(top = 6.dp))

            Text("ID: ${e.id}", style = MaterialTheme.typography.bodyMedium)
            Text("Skill: ${e.skill}", style = MaterialTheme.typography.bodyMedium)
            if (!m?.category.isNullOrBlank()) Text("Categoria: ${m?.category}", style = MaterialTheme.typography.bodyMedium)
            if (m?.difficulty != null) Text("Dificuldade: ${m.difficulty}/5", style = MaterialTheme.typography.bodyMedium)

            if (!m?.tags.isNullOrEmpty()) {
                Text(
                    "Tags: ${m?.tags?.take(10)?.joinToString(", ")}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (!m?.distractorIds.isNullOrEmpty()) {
                Text(
                    "Distractors sugeridos: ${m?.distractorIds?.take(8)?.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.padding(top = 4.dp))
        }
    }
}


