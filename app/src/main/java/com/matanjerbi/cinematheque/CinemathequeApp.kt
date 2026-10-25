package com.matanjerbi.cinematheque

import android.app.Application
import android.content.Context
import com.matanjerbi.cinematheque.airing.EpisodeReminders
import com.matanjerbi.cinematheque.airing.FollowRepository
import com.matanjerbi.cinematheque.data.local.CinemathequeDatabase
import com.matanjerbi.cinematheque.data.remote.TmdbClient
import com.matanjerbi.cinematheque.data.repository.PosterStore
import com.matanjerbi.cinematheque.data.repository.TitleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CinemathequeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.episodeReminders.ensureChannel()
        // Cheap, and keeps alarms right after an update or if the system dropped them.
        container.appScope.launch { container.episodeReminders.rescheduleAll() }
    }
}

/** Manual DI: one place that owns the database, network client and repositories. */
class AppContainer(context: Context) {
    val database: CinemathequeDatabase = CinemathequeDatabase.build(context)
    private val tmdb = TmdbClient.create(BuildConfig.TMDB_API_KEY)
    private val posterStore = PosterStore(context, tmdb.httpClient)

    val titleRepository = TitleRepository(tmdb.api, database, posterStore)

    /** For work that should outlive a screen (re-arming alarms, background sync). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val episodeReminders = EpisodeReminders(context, database)
    val followRepository = FollowRepository(tmdb.api, database, episodeReminders)
}

val Context.appContainer: AppContainer
    get() = (applicationContext as CinemathequeApp).container
