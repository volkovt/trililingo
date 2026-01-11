package com.trililingo.data.repo

import androidx.datastore.core.DataStore
import com.trililingo.core.time.TimeProvider
import com.trililingo.datastore.DailyChallengeSelection
import com.trililingo.datastore.UserPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class DailySelection(
    val enabled: Boolean,
    val itemIds: List<String>
)

class UserPrefsRepository(
    private val store: DataStore<UserPrefs>,
    private val time: TimeProvider
) {
    val prefsFlow: Flow<UserPrefs> = store.data

    val streakFlow: Flow<Int> = store.data.map { it.streakCount }
    val totalXpFlow: Flow<Long> = store.data.map { it.totalXp }
    val activeLanguageFlow: Flow<String> = store.data.map { it.activeLanguage }

    private fun dailyKey(language: String, skill: String) = "$language|$skill"

    fun dailySelectionFlow(language: String, skill: String): Flow<DailySelection> {
        val key = dailyKey(language, skill)
        return store.data.map { prefs ->
            val sel = prefs.dailySelectionsMap[key]
            DailySelection(
                enabled = sel?.enabled ?: false,
                itemIds = sel?.itemIdsList ?: emptyList()
            )
        }
    }

    suspend fun getDailySelection(language: String, skill: String): DailySelection {
        val key = dailyKey(language, skill)
        val prefs = store.data.first()
        val sel = prefs.dailySelectionsMap[key]
        return DailySelection(
            enabled = sel?.enabled ?: false,
            itemIds = sel?.itemIdsList ?: emptyList()
        )
    }

    suspend fun setDailySelection(
        language: String,
        skill: String,
        enabled: Boolean,
        itemIds: List<String>
    ) {
        val key = dailyKey(language, skill)
        val clean = itemIds.distinct()

        val newSel = DailyChallengeSelection.newBuilder()
            .setEnabled(enabled)
            .addAllItemIds(clean)
            .setUpdatedAtMs(time.nowMs())
            .build()

        store.updateData { current ->
            // ✅ jeito compatível com a maioria dos builders gerados para map<>
            current.toBuilder()
                .putDailySelections(key, newSel)
                .build()
        }
    }

    suspend fun clearDailySelection(language: String, skill: String) {
        val key = dailyKey(language, skill)
        store.updateData { current ->
            current.toBuilder()
                .removeDailySelections(key)
                .build()
        }
    }

    suspend fun setActiveLanguage(lang: String) {
        store.updateData { it.toBuilder().setActiveLanguage(lang).build() }
    }

    suspend fun addXpAndUpdateStreak(xpEarned: Int) {
        val today = time.todayEpochDay()
        store.updateData { current ->
            val last = current.lastStudyEpochDay
            val streak = current.streakCount

            val newStreak = when {
                last == 0L -> 1
                today == last -> streak
                today == last + 1 -> streak + 1
                else -> 1
            }

            current.toBuilder()
                .setTotalXp(current.totalXp + xpEarned)
                .setLastStudyEpochDay(today)
                .setStreakCount(newStreak)
                .build()
        }
    }
}
