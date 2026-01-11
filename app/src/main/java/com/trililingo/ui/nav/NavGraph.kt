package com.trililingo.ui.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.trililingo.domain.engine.StudyDifficulty
import com.trililingo.ui.catalog.CatalogIds
import com.trililingo.ui.catalog.FeatureType
import com.trililingo.ui.catalog.TopicTrackIds
import com.trililingo.ui.screens.alphabet.AlphabetScreen
import com.trililingo.ui.screens.analytics.AnalyticsScreen
import com.trililingo.ui.screens.dailyset.DailySetScreen
import com.trililingo.ui.screens.home.HomeScreen
import com.trililingo.ui.screens.lesson.LessonScreen
import com.trililingo.ui.screens.result.ResultScreen
import com.trililingo.ui.screens.subject.SubjectPickScreen
import com.trililingo.ui.screens.subject.SubjectSessionScreen
import com.trililingo.ui.screens.track.TrackHubScreen
import com.trililingo.ui.screens.track.TrackHubViewModel

object Routes {
    const val Home = "home"
    const val Analytics = "analytics"
    const val TrackHub = "track/{trackId}"

    const val Alphabet = "alphabet/{language}/{skill}"

    const val Lesson = "lesson/{language}/{skill}/{mode}?difficulty={difficulty}"

    const val DailySet = "dailyset/{language}/{skill}"
    const val Result = "result/{xp}/{correct}/{wrong}?sessionId={sessionId}"

    const val SubjectPick = "subject_pick/{mode}/{subjectId}/{trackId}"
    const val SubjectSession = "subject_session/{mode}/{subjectId}/{trackId}/{chapterId}"
}

private fun resultRoute(
    xp: Int,
    correct: Int,
    wrong: Int,
    sessionId: String?
): String {
    val base = "result/$xp/$correct/$wrong"
    return if (sessionId.isNullOrBlank()) base
    else base + "?sessionId=" + Uri.encode(sessionId)
}

private fun lessonRoute(
    language: String,
    skill: String,
    mode: String,
    difficulty: StudyDifficulty
): String {
    // query param para não quebrar compatibilidade
    return "lesson/$language/$skill/$mode?difficulty=${Uri.encode(difficulty.id)}"
}

@Composable
fun NavGraph() {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.Home) {

        composable(Routes.Home) {
            HomeScreen(
                onOpenTrack = { trackId -> nav.navigate("track/$trackId") },
                onOpenAnalytics = { nav.navigate(Routes.Analytics) }
            )
        }

        composable(Routes.Analytics) {
            AnalyticsScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.TrackHub) {
            val vm: TrackHubViewModel = hiltViewModel()
            val track by vm.track.collectAsState()

            val safeTrack = track
            if (safeTrack == null) {
                nav.popBackStack()
                return@composable
            }

            // ✅ dificuldade fica “guardada” no backstack do TrackHub
            val (difficulty, setDifficulty) = rememberSaveable {
                mutableStateOf(StudyDifficulty.BASIC)
            }

            TrackHubScreen(
                track = safeTrack,
                selectedDifficulty = difficulty,
                onDifficultyChange = setDifficulty,
                onBack = { nav.popBackStack() },
                onOpenFeature = { feature, diff ->
                    if (safeTrack.subjectId == CatalogIds.SUBJECT_LANGUAGES) {
                        val lang = safeTrack.languageCode
                        val skill = safeTrack.skillCode

                        when (feature.type) {
                            FeatureType.ALPHABET ->
                                if (lang != null && skill != null) nav.navigate("alphabet/$lang/$skill")

                            FeatureType.DAILY_CHALLENGE ->
                                if (lang != null && skill != null) nav.navigate(lessonRoute(lang, skill, "daily", diff))

                            FeatureType.DAILY_SET ->
                                if (lang != null && skill != null) nav.navigate("dailyset/$lang/$skill")

                            FeatureType.PRACTICE ->
                                if (lang != null && skill != null) nav.navigate(lessonRoute(lang, skill, "practice", diff))

                            else -> {}
                        }
                    } else {
                        val decoded = TopicTrackIds.decode(safeTrack.id) ?: return@TrackHubScreen
                        val mode = when (feature.type) {
                            FeatureType.DAILY_CHALLENGE -> "daily"
                            FeatureType.STUDY -> "study"
                            else -> return@TrackHubScreen
                        }
                        nav.navigate("subject_pick/$mode/${decoded.subjectId}/${decoded.trackId}")
                    }
                }
            )
        }

        composable(Routes.Alphabet) { backStack ->
            val lang = backStack.arguments?.getString("language") ?: "JA"
            val skill = backStack.arguments?.getString("skill") ?: "HIRAGANA"
            AlphabetScreen(language = lang, skill = skill, onBack = { nav.popBackStack() })
        }

        composable(Routes.DailySet) { backStack ->
            val lang = backStack.arguments?.getString("language") ?: "JA"
            val skill = backStack.arguments?.getString("skill") ?: "HIRAGANA"
            DailySetScreen(language = lang, skill = skill, onBack = { nav.popBackStack() })
        }

        composable(
            route = Routes.Lesson,
            arguments = listOf(
                navArgument("difficulty") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = StudyDifficulty.BASIC.id
                }
            )
        ) { backStack ->
            val lang = backStack.arguments?.getString("language") ?: "JA"
            val skill = backStack.arguments?.getString("skill") ?: "HIRAGANA"
            val mode = backStack.arguments?.getString("mode") ?: "practice"
            val diffId = backStack.arguments?.getString("difficulty")
            val difficulty = StudyDifficulty.fromId(diffId)

            LessonScreen(
                language = lang,
                skill = skill,
                mode = mode,
                difficulty = difficulty,
                onDone = { xp, correct, wrong, sessionId ->
                    nav.navigate(resultRoute(xp, correct, wrong, sessionId)) {
                        popUpTo(Routes.Home) { inclusive = false }
                    }
                },
                onAbort = { nav.popBackStack() }
            )
        }

        composable(
            route = Routes.Result,
            arguments = listOf(
                navArgument("sessionId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStack ->
            val xp = backStack.arguments?.getString("xp")?.toIntOrNull() ?: 0
            val correct = backStack.arguments?.getString("correct")?.toIntOrNull() ?: 0
            val wrong = backStack.arguments?.getString("wrong")?.toIntOrNull() ?: 0
            val sessionId = backStack.arguments?.getString("sessionId")

            ResultScreen(
                xp = xp,
                correct = correct,
                wrong = wrong,
                sessionId = sessionId,
                onBackHome = { nav.popBackStack(Routes.Home, inclusive = false) }
            )
        }

        // ==========================
        // ✅ TOPIC ROUTES (inalteradas)
        // ==========================
        composable(Routes.SubjectPick) { backStack ->
            val mode = backStack.arguments?.getString("mode") ?: "daily"
            val subjectId = backStack.arguments?.getString("subjectId") ?: return@composable
            val trackId = backStack.arguments?.getString("trackId") ?: return@composable

            SubjectPickScreen(
                mode = mode,
                subjectId = subjectId,
                trackId = trackId,
                onBack = { nav.popBackStack() },
                onStart = { chapterIdOrAll ->
                    nav.navigate("subject_session/$mode/$subjectId/$trackId/$chapterIdOrAll")
                }
            )
        }

        composable(
            route = Routes.SubjectSession,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType },
                navArgument("subjectId") { type = NavType.StringType },
                navArgument("trackId") { type = NavType.StringType },
                navArgument("chapterId") { type = NavType.StringType }
            )
        ) { backStack ->
            val mode = backStack.arguments?.getString("mode") ?: "daily"
            val subjectId = backStack.arguments?.getString("subjectId") ?: return@composable
            val trackId = backStack.arguments?.getString("trackId") ?: return@composable
            val chapterId = backStack.arguments?.getString("chapterId") ?: "all"

            SubjectSessionScreen(
                mode = mode,
                subjectId = subjectId,
                trackId = trackId,
                chapterId = chapterId,
                onAbort = { nav.popBackStack() },
                onDone = { xp, correct, wrong, sessionId ->
                    nav.navigate(resultRoute(xp, correct, wrong, sessionId)) {
                        popUpTo(Routes.Home) { inclusive = false }
                    }
                }
            )
        }
    }
}
