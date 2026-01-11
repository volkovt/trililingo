package com.trililingo.data.repo

import com.trililingo.core.time.TimeProvider
import com.trililingo.data.db.TrililingoDb
import com.trililingo.data.db.entities.ActivityAttemptEntity
import com.trililingo.data.db.entities.SrsStateEntity
import com.trililingo.data.db.entities.StudySessionEntity
import com.trililingo.data.db.entities.SyncEventEntity
import com.trililingo.domain.engine.ActivityDefinition
import com.trililingo.domain.engine.ActivityEngine
import com.trililingo.domain.engine.Challenge
import com.trililingo.python.PythonSrsBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

class StudyRepository(
    private val db: TrililingoDb,
    private val prefs: UserPrefsRepository,
    private val srs: PythonSrsBridge,
    private val time: TimeProvider,
    private val seeder: ContentSeeder
) {
    suspend fun ensureSeeded() {
        seeder.ensureSeeded()
    }

    suspend fun loadAlphabet(language: String, skill: String) = withContext(Dispatchers.IO) {
        ensureSeeded()
        db.languageItemDao().getBySkill(language, skill)
    }

    suspend fun loadItemsByIds(ids: List<String>) = withContext(Dispatchers.IO) {
        ensureSeeded()
        db.languageItemDao().getByIds(ids.distinct())
    }

    suspend fun loadItemMeta(ids: List<String>): Map<String, ItemMeta> = withContext(Dispatchers.IO) {
        ensureSeeded()
        seeder.getMetaByIds(ids)
    }

    fun latestSessions(): Flow<List<StudySessionEntity>> = db.sessionDao().latestSessions()

    fun attemptsForSession(sessionId: String): Flow<List<ActivityAttemptEntity>> =
        db.attemptDao().bySession(sessionId)

    suspend fun loadChallenges(def: ActivityDefinition, mode: String): List<Challenge> = withContext(Dispatchers.IO) {
        ensureSeeded()

        val allItems = db.languageItemDao().getBySkill(def.language, def.skill)
        if (allItems.isEmpty()) return@withContext emptyList()

        val now = time.nowMs()
        val itemById = allItems.associateBy { it.id }

        val states = db.srsStateDao().getAll(allItems.map { it.id })
        val dueSorted = states.sortedWith(
            compareByDescending<SrsStateEntity> { (now - it.dueAtMs).coerceAtLeast(0) }
                .thenByDescending { it.lapses }
        )

        val allIds = allItems.map { it.id }.distinct()

        val universeForDaily: List<String>? = if (mode.uppercase() == "DAILY") {
            getDailyUniverseIds(
                language = def.language,
                skill = def.skill,
                allIds = allIds,
                minSize = 10
            )
        } else null

        val preferredIds = when (mode.uppercase()) {
            "DAILY" -> {
                val universe = universeForDaily
                if (universe != null) {
                    pickDailyIdsFromUniverse(
                        universeIds = universe,
                        dueSorted = dueSorted,
                        allIds = allIds,
                        language = def.language,
                        skill = def.skill,
                        nowMs = now,
                        limit = def.length
                    )
                } else {
                    pickDailyIds(
                        dueSorted = dueSorted,
                        allIds = allIds,
                        language = def.language,
                        skill = def.skill,
                        nowMs = now,
                        limit = def.length
                    )
                }
            }

            else -> dueSorted.map { it.itemId }.take(def.length).ifEmpty { allIds.take(def.length) }
        }

        val preferredItems = preferredIds.mapNotNull { itemById[it] }.ifEmpty { allItems }

        val baseChallenges = ActivityEngine.generateComicCompareChallenges(preferredItems, def.length)

        val allowedOptionItemById = run {
            val universe = universeForDaily
            if (mode.uppercase() == "DAILY" && universe != null) {
                val set = universe.toHashSet()
                itemById.filterKeys { it in set }
            } else {
                itemById
            }
        }

        val metaById = seeder.getMetaByIds(baseChallenges.map { it.itemId })
        val final = baseChallenges.map { ch ->
            val meta = metaById[ch.itemId]
            val betterOptions = buildOptions(
                correct = ch.correct,
                meta = meta,
                itemById = allowedOptionItemById,
                seedKey = "${ch.itemId}|${dayKey(now)}|${mode.uppercase()}",
                desired = 4
            )
            ch.copy(options = betterOptions)
        }

        final
    }

    suspend fun abortSessionById(
        sessionId: String,
        totalXp: Int,
        avgResponseMs: Long,
        correct: Int,
        wrong: Int
    ) = withContext(Dispatchers.IO) {
        val end = time.nowMs()

        db.sessionDao().finish(
            sessionId = sessionId,
            endedAtMs = end,
            xp = totalXp,
            avg = avgResponseMs,
            correct = correct,
            wrong = wrong,
            abandoned = true
        )

        db.syncEventDao().insert(
            SyncEventEntity(
                type = "SESSION_ABORT",
                payloadJson = """{"sessionId":"$sessionId","xp":$totalXp}""",
                createdAtMs = end,
                state = "PENDING"
            )
        )
    }

    private suspend fun getDailyUniverseIds(
        language: String,
        skill: String,
        allIds: List<String>,
        minSize: Int
    ): List<String>? {
        val sel = prefs.getDailySelection(language, skill)

        val allSet = allIds.toHashSet()
        val valid = sel.itemIds
            .distinct()
            .filter { it in allSet }

        if (valid.size < minSize) return null

        if (!sel.enabled) {
            prefs.setDailySelection(
                language = language,
                skill = skill,
                enabled = true,
                itemIds = valid
            )
        }

        return valid
    }

    private fun pickDailyIdsFromUniverse(
        universeIds: List<String>,
        dueSorted: List<SrsStateEntity>,
        allIds: List<String>,
        language: String,
        skill: String,
        nowMs: Long,
        limit: Int
    ): List<String> {
        val signature = universeIds.sorted().joinToString("|")
        val key = "${dayKey(nowMs)}|$language|$skill|U:${stableSeed(signature)}"
        val rng = Random(stableSeed(key))

        val universeSet = universeIds.toHashSet()

        val dueInUniverse = dueSorted.map { it.itemId }.distinct().filter { it in universeSet }
        val base = if (dueInUniverse.isNotEmpty()) dueInUniverse else universeIds.distinct()

        val picked = base.shuffled(rng).take(limit).toMutableList()

        if (picked.size < limit) {
            val remainingUniverse = universeIds.distinct().filter { it !in picked }.shuffled(rng)
            for (id in remainingUniverse) {
                if (picked.size >= limit) break
                picked.add(id)
            }
        }

        if (picked.size < limit) {
            val remainingGlobal = allIds.distinct().filter { it !in picked }.shuffled(rng)
            for (id in remainingGlobal) {
                if (picked.size >= limit) break
                picked.add(id)
            }
        }

        return picked.take(limit)
    }

    private fun buildOptions(
        correct: String,
        meta: ItemMeta?,
        itemById: Map<String, com.trililingo.data.db.entities.LanguageItemEntity>,
        seedKey: String,
        desired: Int
    ): List<String> {
        val seed = stableSeed(seedKey)
        val rng = Random(seed)

        val distractorAnswersFromIds = meta
            ?.distractorIds
            ?.mapNotNull { id -> itemById[id]?.answer }
            .orEmpty()

        val pool = itemById.values.map { it.answer }.distinct().filter { it != correct }
        val picks = mutableListOf<String>()

        picks.add(correct)

        distractorAnswersFromIds
            .filter { it != correct }
            .distinct()
            .shuffled(rng)
            .forEach {
                if (picks.size < desired) picks.add(it)
            }

        pool.shuffled(rng).forEach {
            if (picks.size < desired) picks.add(it)
        }

        return picks.distinct().shuffled(rng).take(desired)
    }

    private fun pickDailyIds(
        dueSorted: List<SrsStateEntity>,
        allIds: List<String>,
        language: String,
        skill: String,
        nowMs: Long,
        limit: Int
    ): List<String> {
        val key = "${dayKey(nowMs)}|$language|$skill"
        val rng = Random(stableSeed(key))

        val dueIds = dueSorted.map { it.itemId }.distinct()
        val base = if (dueIds.isNotEmpty()) dueIds else allIds.distinct()

        return base.shuffled(rng).take(limit)
    }

    private fun dayKey(nowMs: Long): String {
        val zone = ZoneId.of("America/Sao_Paulo")
        val d = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        return d.toString()
    }

    private fun stableSeed(key: String): Int {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(key.toByteArray(Charsets.UTF_8))
        var acc = 0
        for (i in 0 until 4) {
            acc = (acc shl 8) or (bytes[i].toInt() and 0xFF)
        }
        return acc
    }

    suspend fun startSession(def: ActivityDefinition): StudySessionEntity = withContext(Dispatchers.IO) {
        val session = StudySessionEntity(
            language = def.language,
            activityType = def.activityType,
            startedAtMs = time.nowMs(),
            endedAtMs = null,
            xpGained = 0,
            avgResponseMs = 0,
            correctCount = 0,
            wrongCount = 0,
            abandoned = false
        )
        db.sessionDao().insert(session)
        session
    }

    suspend fun recordAttempt(
        sessionId: String,
        itemId: String,
        isCorrect: Boolean,
        responseMs: Long,
        chosen: String,
        correct: String,
        hintCount: Int = 0,
        xpMultiplier: Double = 1.0
    ): Int = withContext(Dispatchers.IO) {
        val now = time.nowMs()

        val baseXp = ActivityEngine.score(isCorrect, responseMs)

        val safeMultiplier = xpMultiplier.coerceIn(0.0, 1.0)

        val finalXp = if (!isCorrect) {
            0
        } else {
            if (safeMultiplier >= 1.0) {
                baseXp
            } else {
                max(1, (baseXp * safeMultiplier).roundToInt())
            }
        }

        db.attemptDao().insert(
            ActivityAttemptEntity(
                sessionId = sessionId,
                itemId = itemId,
                isCorrect = isCorrect,
                responseMs = responseMs,
                chosenAnswer = chosen,
                correctAnswer = correct,
                createdAtMs = now,

                hintCount = hintCount.coerceAtLeast(0),
                baseXp = baseXp,
                xpMultiplier = safeMultiplier,
                xpAwarded = finalXp
            )
        )

        val current = db.srsStateDao().get(itemId) ?: SrsStateEntity(
            itemId = itemId,
            ease = 2.5,
            intervalDays = 0,
            repetitions = 0,
            lapses = 0,
            dueAtMs = 0
        )

        val stateJson = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.buildJsonObject {
                put("itemId", kotlinx.serialization.json.JsonPrimitive(current.itemId))
                put("ease", kotlinx.serialization.json.JsonPrimitive(current.ease))
                put("intervalDays", kotlinx.serialization.json.JsonPrimitive(current.intervalDays))
                put("repetitions", kotlinx.serialization.json.JsonPrimitive(current.repetitions))
                put("lapses", kotlinx.serialization.json.JsonPrimitive(current.lapses))
                put("dueAtMs", kotlinx.serialization.json.JsonPrimitive(current.dueAtMs))
            }
        )

        val updatedJson = try {
            srs.updateState(stateJson, isCorrect, now, responseMs)
        } catch (_: Throwable) {
            stateJson
        }

        val obj = org.json.JSONObject(updatedJson)
        val updated = SrsStateEntity(
            itemId = obj.getString("itemId"),
            ease = obj.optDouble("ease", 2.5),
            intervalDays = obj.optInt("intervalDays", 0),
            repetitions = obj.optInt("repetitions", 0),
            lapses = obj.optInt("lapses", 0),
            dueAtMs = obj.optLong("dueAtMs", now)
        )
        db.srsStateDao().upsert(updated)

        db.syncEventDao().insert(
            SyncEventEntity(
                type = "ATTEMPT",
                payloadJson = """
                    {
                      "sessionId":"$sessionId",
                      "itemId":"$itemId",
                      "correct":$isCorrect,
                      "xp":$finalXp,
                      "baseXp":$baseXp,
                      "hintCount":${hintCount.coerceAtLeast(0)},
                      "xpMultiplier":$safeMultiplier
                    }
                """.trimIndent(),
                createdAtMs = now,
                state = "PENDING"
            )
        )

        finalXp
    }

    suspend fun finishSessionById(
        sessionId: String,
        totalXp: Int,
        avgResponseMs: Long,
        correct: Int,
        wrong: Int,
        abandoned: Boolean
    ) = withContext(Dispatchers.IO) {
        val end = time.nowMs()
        db.sessionDao().finish(
            sessionId = sessionId,
            endedAtMs = end,
            xp = totalXp,
            avg = avgResponseMs,
            correct = correct,
            wrong = wrong,
            abandoned = abandoned
        )

        prefs.addXpAndUpdateStreak(totalXp)

        db.syncEventDao().insert(
            SyncEventEntity(
                type = "SESSION_END",
                payloadJson = """{"sessionId":"$sessionId","xp":$totalXp}""",
                createdAtMs = end,
                state = "PENDING"
            )
        )
    }
}
