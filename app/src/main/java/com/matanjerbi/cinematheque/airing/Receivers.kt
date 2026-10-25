package com.matanjerbi.cinematheque.airing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.matanjerbi.cinematheque.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Runs [block] off the main thread while keeping the broadcast alive until it's done. */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

class EpisodeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val titleId = intent.getLongExtra(EpisodeReminders.EXTRA_TITLE_ID, -1L)
        if (titleId < 0) return
        runAsync { context.appContainer.episodeReminders.onAlarm(titleId) }
    }
}

/** Reboot, app update, clock or time-zone change, exact-alarm permission granted: re-arm everything. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runAsync { context.appContainer.episodeReminders.rescheduleAll() }
    }
}
