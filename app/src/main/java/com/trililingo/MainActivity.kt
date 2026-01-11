package com.trililingo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.trililingo.sync.SyncScheduler
import com.trililingo.ui.design.NeonTheme
import com.trililingo.ui.nav.NavGraph
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.core.view.WindowCompat

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var syncScheduler: SyncScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        syncScheduler.ensureScheduled()

        setContent {
            NeonTheme {
                NavGraph()
            }
        }
    }
}
