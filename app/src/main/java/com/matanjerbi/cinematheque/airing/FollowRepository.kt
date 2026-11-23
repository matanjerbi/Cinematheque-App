package com.matanjerbi.cinematheque.airing

import com.matanjerbi.cinematheque.data.local.CinemathequeDatabase
import com.matanjerbi.cinematheque.data.local.entity.FollowedShowEntity
import com.matanjerbi.cinematheque.data.local.entity.MediaType
import com.matanjerbi.cinematheque.data.remote.DetailsDto
import com.matanjerbi.cinematheque.data.remote.TmdbApi
import com.matanjerbi.cinematheque.data.repository.resultOf
import java.time.DayOfWeek
import java.time.LocalDate

/** What TMDB knows about where a series is right now; used to prefill the follow form. */
data class AiringSuggestion(
    val season: Int,
    val aired: Int,
    val seasonEpisodes: Int?,
    /** Weekday of the next (or last) original air date. US dates, so it can be a day off here. */
    val airDay: DayOfWeek?,
    val ended: Boolean,
    /** Premiere day of [season] when it's still ahead (from TMDB's next episode), else null. */
    val seasonStart: LocalDate? = null,
)

/**
 * Follow / unfollow, and the light TMDB sync.
 *
 * The aired count is driven by the user's schedule, not by TMDB: TMDB air dates are by
 * calendar day in the US and would double-count against an Israeli air time. So TMDB only
 * prefills the form (and the "update from TMDB" button), fills in the season length,
 * and moves a finished season on to the next one.
 */
class FollowRepository(
    private val api: TmdbApi,
    private val db: CinemathequeDatabase,
    private val reminders: EpisodeReminders,
) {
    private val dao get() = db.followedShowDao()

    suspend fun suggest(titleId: Long): Result<AiringSuggestion> = resultOf {
        val title = requireNotNull(db.titleDao().getById(titleId)) { "title $titleId not found" }
        require(title.type == MediaType.TV) { "not a series" }
        api.tvDetails(title.tmdbId, append = null).toSuggestion()
    }

    suspend fun save(show: FollowedShowEntity) {
        dao.upsert(show)
        reminders.schedule(show)
    }

    suspend fun setReminders(titleId: Long, on: Boolean) {
        val show = dao.get(titleId) ?: return
        save(show.copy(remindersOn = on))
    }

    suspend fun unfollow(titleId: Long) {
        dao.delete(titleId)
        reminders.cancel(titleId)
    }

    /** At most once per [maxAgeMs] per show; failures (offline) are ignored. */
    suspend fun syncStale(maxAgeMs: Long = 12 * 60 * 60 * 1000L) {
        val now = System.currentTimeMillis()
        dao.getAll()
            // A season that hasn't started and has no start date yet: check right away.
            .filter { (it.tmdbSyncedAt ?: 0L) < now - maxAgeMs || (it.airedCount == 0 && it.seasonStart == null) }
            .forEach { show -> resultOf { sync(show, now) } }
    }

    private suspend fun sync(show: FollowedShowEntity, nowMs: Long) {
        val title = db.titleDao().getById(show.titleId) ?: return
        val s = api.tvDetails(title.tmdbId, append = null).toSuggestion()
        val now = nowHere()
        val updated = when {
            // The season we're on finished and TMDB lists a newer one: move to it.
            s.season > show.season && show.seasonFinishedAt(now) -> show.copy(
                season = s.season,
                airedCount = s.aired,
                airedCountAsOf = nowMs,
                seasonEpisodeCount = s.seasonEpisodes,
                seasonStart = s.seasonStart,
            )
            // Same season, nothing out yet, and TMDB now knows the premiere date.
            s.season == show.season && show.airedCount == 0 && show.seasonStart == null && s.seasonStart != null &&
                show.airedAt(now) == 0 -> show.copy(
                seasonStart = s.seasonStart,
                seasonEpisodeCount = show.seasonEpisodeCount ?: s.seasonEpisodes,
            )
            // Same season: only learn its length if we didn't know it.
            s.season == show.season && show.seasonEpisodeCount == null && s.seasonEpisodes != null ->
                show.copy(seasonEpisodeCount = s.seasonEpisodes)
            else -> show
        }.copy(tmdbSyncedAt = nowMs)
        // Re-read guard: don't overwrite an edit the user made while we were on the network.
        if (dao.get(show.titleId) == show) save(updated)
    }
}

internal fun DetailsDto.toSuggestion(): AiringSuggestion {
    val last = lastEpisodeToAir?.takeIf { it.seasonNumber > 0 }
    val next = nextEpisodeToAir?.takeIf { it.seasonNumber > 0 }
    val (season, aired) = when {
        next != null && (last == null || next.seasonNumber > last.seasonNumber) ->
            next.seasonNumber to (next.episodeNumber - 1).coerceAtLeast(0)
        last != null -> last.seasonNumber to last.episodeNumber
        else -> 1 to 0
    }
    val seasonEpisodes = seasons.firstOrNull { it.seasonNumber == season }?.episodeCount?.takeIf { it > 0 }
    val airDay = (next?.airDate ?: last?.airDate)
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?.dayOfWeek
    val ended = next == null && status in setOf("Ended", "Canceled")
    val today = LocalDate.now()
    val seasonStart = next
        ?.takeIf { aired == 0 && it.seasonNumber == season }
        ?.airDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?.takeIf { it.isAfter(today) }
    return AiringSuggestion(season, aired, seasonEpisodes, airDay, ended, seasonStart)
}
