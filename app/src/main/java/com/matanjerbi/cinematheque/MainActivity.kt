package com.matanjerbi.cinematheque

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matanjerbi.cinematheque.ui.navigation.CinemathequeNavHost
import com.matanjerbi.cinematheque.ui.theme.CinemathequeTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** Title to open, set when the app is launched from a new-episode notification. */
    private val openTitle = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent {
            CinemathequeTheme {
                val toOpen by openTitle.collectAsStateWithLifecycle()
                CinemathequeNavHost(
                    openTitleId = toOpen,
                    onOpenedTitle = { openTitle.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_OPEN_TITLE, -1L) ?: -1L
        if (id > 0) openTitle.value = id
    }

    companion object {
        const val EXTRA_OPEN_TITLE = "openTitleId"
    }
}
