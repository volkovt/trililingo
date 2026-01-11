package com.trililingo.ui.screens.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trililingo.ui.catalog.SubjectKind
import com.trililingo.ui.catalog.TrackUi
import com.trililingo.ui.design.NeonButton
import com.trililingo.ui.design.NeonCard

@Composable
fun HomeScreen(
    onOpenTrack: (trackId: String) -> Unit,
    onOpenAnalytics: () -> Unit,
    vm: HomeViewModel = hiltViewModel()
) {
    val streak by vm.streak.collectAsState()
    val xp by vm.totalXp.collectAsState()

    val catalog by vm.catalog.collectAsState()
    val selectedSubject = catalog.subjects.firstOrNull { it.id == catalog.selectedSubjectId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        NeonCard {
            Text("TRILILINGO", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenAnalytics() }
                    .padding(vertical = 10.dp, horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Streak: $streak dias • XP total: $xp",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text("›", style = MaterialTheme.typography.headlineSmall)
            }

            Text(
                "Toque para ver analytics",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // ✅ Chips de assuntos (languages + subjects carregados do packs/subjects)
        NeonCard {
            Text("Assuntos", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(catalog.subjects.size) { idx ->
                    val s = catalog.subjects[idx]
                    NeonChip(
                        text = s.title,
                        selected = s.id == catalog.selectedSubjectId,
                        onClick = { vm.selectSubject(s.id) }
                    )
                }
            }
        }

        // ✅ Idiomas continuam iguais
        if (selectedSubject?.kind == SubjectKind.LANGUAGE) {
            NeonCard {
                Text("Selecione o idioma", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(catalog.languages.size) { idx ->
                        val lang = catalog.languages[idx]
                        FlagChip(
                            flagEmoji = lang.flagEmoji,
                            label = lang.title,
                            selected = lang.code == catalog.selectedLanguageCode,
                            onClick = { vm.selectLanguage(lang.code) }
                        )
                    }
                }
            }
        }


        // ✅ LANGUAGE: mantém grid de trilhas como estava
        NeonCard(modifier = Modifier.fillMaxWidth()) {
            Text("Trilhas", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
            ) {
                items(catalog.tracks) { track ->
                    TrackCard(
                        track = track,
                        onOpen = { if (track.enabled) onOpenTrack(track.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackCard(
    track: TrackUi,
    onOpen: () -> Unit
) {
    val cs = MaterialTheme.colorScheme

    NeonCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = track.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = if (track.enabled) cs.onSurfaceVariant else cs.onSurfaceVariant.copy(alpha = 0.65f)
        )
        Spacer(Modifier.height(12.dp))

        NeonButton(
            text = if (track.enabled) "Abrir" else "Em breve",
            enabled = track.enabled,
            onClick = onOpen
        )
    }
}

@Composable
private fun NeonChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val infinite = rememberInfiniteTransition(label = "chip_glow")
    val glow by infinite.animateFloat(
        initialValue = 0.14f,
        targetValue = if (selected) 0.42f else 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    val shape = RoundedCornerShape(999.dp)

    Box(
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(14.dp)
                .background(cs.tertiary.copy(alpha = glow * 0.22f))
        )

        Surface(
            color = if (selected) cs.surfaceVariant else cs.surfaceVariant.copy(alpha = 0.70f),
            contentColor = cs.onSurface,
            border = BorderStroke(
                width = 1.dp,
                color = (if (selected) cs.primary else cs.outline).copy(alpha = 0.75f)
            ),
            shape = shape
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = text,
                    fontSize = 14.sp,
                    color = cs.onSurface.copy(alpha = 0.95f)
                )
            }
        }
    }
}

@Composable
private fun FlagChip(
    flagEmoji: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val border = if (selected) cs.tertiary else cs.outline

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = if (selected) cs.surfaceVariant else cs.surfaceVariant.copy(alpha = 0.72f),
        contentColor = cs.onSurface,
        border = BorderStroke(1.dp, border.copy(alpha = 0.85f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(flagEmoji, fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
