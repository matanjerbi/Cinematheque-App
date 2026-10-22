package com.matanjerbi.cinematheque

import android.app.Application
import android.content.Context
import com.matanjerbi.cinematheque.data.local.CinemathequeDatabase
import com.matanjerbi.cinematheque.data.remote.TmdbClient
import com.matanjerbi.cinematheque.data.repository.PosterStore
import com.matanjerbi.cinematheque.data.repository.TitleRepository

class CinemathequeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manual DI: one place that owns the database, network client and repositories. */
class AppContainer(context: Context) {
    val database: CinemathequeDatabase = CinemathequeDatabase.build(context)
    private val tmdb = TmdbClient.create(BuildConfig.TMDB_API_KEY)
    private val posterStore = PosterStore(context, tmdb.httpClient)

    val titleRepository = TitleRepository(tmdb.api, database, posterStore)
}

val Context.appContainer: AppContainer
    get() = (applicationContext as CinemathequeApp).container
