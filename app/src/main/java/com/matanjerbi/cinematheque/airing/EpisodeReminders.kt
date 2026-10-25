package com.matanjerbi.cinematheque.airing

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.matanjerbi.cinematheque.MainActivity
import com.matanjerbi.cinematheque.R
import com.matanjerbi.cinematheque.data.local.CinemathequeDatabase
import com.matanjerbi.cinematheque.data.local.entity.FollowedShowEntity

/**
 * One alarm per followed show, always pointing at its next airing.
 * When it fires we post "a new episode is out" and arm the following one.
 * Alarms don't survive a reboot or a clock change, so [rescheduleAll] runs then and on app start.
 */
class EpisodeReminders(private val context: Context, private val db: CinemathequeDatabase) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val dao get() = db.followedShowDao()

    fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "פרקים חדשים", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "תזכורת כשיוצא פרק חדש בסדרה שאתה עוקב אחריה"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    suspend fun rescheduleAll() {
        dao.getAll().forEach { schedule(it) }
    }

    fun schedule(show: FollowedShowEntity) {
        cancel(show.titleId)
        if (!show.remindersOn) return
        val next = show.nextAiringAfter(nowHere()) ?: return
        val at = next.toInstant().toEpochMilli()
        val pi = alarmIntent(show.titleId, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        try {
            if (canScheduleExact()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(titleId: Long) {
        alarmIntent(titleId, PendingIntent.FLAG_NO_CREATE)?.let {
            alarms.cancel(it)
            it.cancel()
        }
    }

    /** Called by [EpisodeAlarmReceiver] when an airing time is reached. */
    suspend fun onAlarm(titleId: Long) {
        val row = dao.getWithTitle(titleId) ?: return
        val show = row.show
        if (show.remindersOn) {
            val now = nowHere()
            val episode = show.airedAt(now)
            val finished = show.seasonFinishedAt(now)
            val text = buildString {
                append("עונה ${show.season}, פרק $episode")
                if (finished) append(" — הפרק האחרון בעונה")
                show.seasonEpisodeCount?.takeIf { !finished }?.let { append(" מתוך $it") }
            }
            notify(titleId, "פרק חדש: ${row.title.title}", text)
        }
        schedule(show)
    }

    private fun notify(titleId: Long, title: String, text: String) {
        if (!canNotify()) return
        val open = PendingIntent.getActivity(
            context,
            titleId.toInt(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_TITLE, titleId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_episode)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(titleId.toInt(), notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }

    private fun alarmIntent(titleId: Long, flag: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        titleId.toInt(),
        Intent(context, EpisodeAlarmReceiver::class.java).putExtra(EXTRA_TITLE_ID, titleId),
        flag or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CHANNEL_ID = "new_episodes"
        const val EXTRA_TITLE_ID = "titleId"
    }
}
