package com.matanjerbi.cinematheque.data.repository

import com.matanjerbi.cinematheque.data.remote.DetailsDto
import com.matanjerbi.cinematheque.data.remote.DiscoverTvDto
import com.matanjerbi.cinematheque.data.remote.EpisodeRefDto
import com.matanjerbi.cinematheque.data.remote.TmdbApi
import com.matanjerbi.cinematheque.data.remote.TmdbClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate

/** Why a series shows up in the "new in Israel" list. */
enum class IsraelShowKind { NEW_SERIES, NEW_SEASON, COMING_SOON }

data class EpisodeInfo(val season: Int, val episode: Int, val airDate: LocalDate?, val name: String?)

/** One card in the "new in Israel" overview. Nothing here is stored until the user picks it. */
data class IsraelShow(
    val tmdbId: Int,
    val title: String,
    val originalTitle: String,
    val overview: String,
    val posterUrl: String?,
    val firstAirDate: LocalDate?,
    val kind: IsraelShowKind,
    /** The season that is airing now (or about to). */
    val season: Int?,
    val seasonEpisodes: Int?,
    val lastEpisode: EpisodeInfo?,
    val nextEpisode: EpisodeInfo?,
    /** Channel / streaming service as TMDB knows it, e.g. "Kan 11", "Netflix". */
    val whereToWatch: List<String>,
    val genres: List<String>,
    val tmdbRating: Double?,
    val voteCount: Int,
    val popularity: Double,
)

/**
 * Israeli series (origin country IL) that have an episode airing around now.
 *
 * Network budget per refresh: 2 discover pages + one details call per series
 * (with watch providers appended), plus an English call only when the Hebrew overview
 * is missing. The result is kept in memory for [CACHE_MS], so moving between tabs is free.
 */
class IsraelAiringRepository(private val api: TmdbApi) {
    private val mutex = Mutex()
    private var cache: Pair<Long, List<IsraelShow>>? = null

    suspend fun load(force: Boolean = false): Result<List<IsraelShow>> = resultOf {
        mutex.withLock {
            val now = System.currentTimeMillis()
            cache?.takeIf { !force && now - it.first < CACHE_MS }?.let { return@withLock it.second }
            val fresh = fetch(LocalDate.now())
            cache = now to fresh
            fresh
        }
    }

    private suspend fun fetch(today: LocalDate): List<IsraelShow> = coroutineScope {
        val from = today.minusDays(DAYS_BACK).toString()
        val to = today.plusDays(DAYS_AHEAD).toString()

        val found = LinkedHashMap<Int, DiscoverTvDto>()
        var page = 1
        var totalPages = 1
        while (page <= totalPages && page <= MAX_PAGES) {
            val response = api.discoverTv(ORIGIN_COUNTRY, from, to, page = page)
            response.results
                .filter { dto -> dto.genreIds.none { it in SKIPPED_GENRES } }
                .forEach { found.putIfAbsent(it.id, it) }
            totalPages = response.totalPages
            page++
        }

        val gate = Semaphore(PARALLEL_CALLS)
        found.values.map { dto ->
            async {
                gate.withPermit {
                    // A single series failing (rare TMDB 404s) shouldn't hide the whole list.
                    resultOf { build(dto, today) }.getOrNull() ?: fromDiscoverOnly(dto, today)
                }
            }
        }.awaitAll()
            .filterNot { it.title.isBlank() }
    }

    private suspend fun build(dto: DiscoverTvDto, today: LocalDate): IsraelShow {
        val he = api.tvDetails(dto.id, TmdbClient.LANGUAGE, append = "watch/providers")
        val overview = he.overview.orEmpty().ifBlank { dto.overview.orEmpty() }.ifBlank {
            resultOf { api.tvDetails(dto.id, TmdbClient.FALLBACK_LANGUAGE, append = null).overview }.getOrNull().orEmpty()
        }

        val last = he.lastEpisodeToAir?.toInfo()?.takeIf { it.season > 0 }
        val next = he.nextEpisodeToAir?.toInfo()?.takeIf { it.season > 0 }
        val season = next?.season ?: last?.season
        val first = he.firstAirDate.toDateOrNull() ?: dto.firstAirDate.toDateOrNull()

        return IsraelShow(
            tmdbId = dto.id,
            title = (he.name ?: dto.name).orEmpty().ifBlank { he.originalName ?: dto.originalName.orEmpty() },
            originalTitle = (he.originalName ?: dto.originalName).orEmpty(),
            overview = overview,
            posterUrl = (he.posterPath ?: dto.posterPath)?.let { PosterStore.remoteUrl(it) },
            firstAirDate = first,
            kind = kindOf(first, season, next, today),
            season = season,
            seasonEpisodes = season?.let { s -> he.seasons.firstOrNull { it.seasonNumber == s }?.episodeCount }?.takeIf { it > 0 },
            lastEpisode = last,
            nextEpisode = next,
            whereToWatch = whereToWatch(he),
            genres = he.genres.map { it.name },
            tmdbRating = (he.voteAverage ?: dto.voteAverage)?.takeIf { it > 0 },
            voteCount = dto.voteCount ?: 0,
            popularity = dto.popularity ?: 0.0,
        )
    }

    /** Used when the details call fails: less information, but the series still appears. */
    private fun fromDiscoverOnly(dto: DiscoverTvDto, today: LocalDate): IsraelShow {
        val first = dto.firstAirDate.toDateOrNull()
        return IsraelShow(
            tmdbId = dto.id,
            title = dto.name.orEmpty().ifBlank { dto.originalName.orEmpty() },
            originalTitle = dto.originalName.orEmpty(),
            overview = dto.overview.orEmpty(),
            posterUrl = dto.posterPath?.let { PosterStore.remoteUrl(it) },
            firstAirDate = first,
            kind = kindOf(first, null, null, today),
            season = null,
            seasonEpisodes = null,
            lastEpisode = null,
            nextEpisode = null,
            whereToWatch = emptyList(),
            genres = emptyList(),
            tmdbRating = dto.voteAverage?.takeIf { it > 0 },
            voteCount = dto.voteCount ?: 0,
            popularity = dto.popularity ?: 0.0,
        )
    }

    companion object {
        const val ORIGIN_COUNTRY = "IL"
        const val DAYS_BACK = 45L
        const val DAYS_AHEAD = 21L
        /** A series that premiered within this many days counts as "new". */
        const val NEW_SERIES_DAYS = 120L
        const val MAX_PAGES = 3
        const val PARALLEL_CALLS = 6
        const val CACHE_MS = 3 * 60 * 60 * 1000L
        /** TMDB TV genres that aren't drama/comedy series: news, talk shows, kids. */
        val SKIPPED_GENRES = setOf(10763, 10767, 10762)
    }
}

internal fun kindOf(firstAirDate: LocalDate?, season: Int?, next: EpisodeInfo?, today: LocalDate): IsraelShowKind = when {
    firstAirDate != null && firstAirDate.isAfter(today) -> IsraelShowKind.COMING_SOON
    next != null && next.episode == 1 && (next.airDate?.isAfter(today) == true) ->
        if (next.season == 1) IsraelShowKind.COMING_SOON else IsraelShowKind.NEW_SEASON
    firstAirDate != null && !firstAirDate.isBefore(today.minusDays(IsraelAiringRepository.NEW_SERIES_DAYS)) &&
        (season == null || season == 1) -> IsraelShowKind.NEW_SERIES
    else -> IsraelShowKind.NEW_SEASON
}

/** Israeli broadcaster names from TMDB networks, then Israeli streaming providers, without duplicates. */
private fun whereToWatch(dto: DetailsDto): List<String> {
    val il = dto.watchProviders?.results?.get("IL")
    val providers = il?.let { it.flatrate + it.free + it.ads }.orEmpty().map { it.name }
    return (dto.networks.map { it.name } + providers)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
        .take(4)
}

private fun EpisodeRefDto.toInfo() = EpisodeInfo(seasonNumber, episodeNumber, airDate.toDateOrNull(), name?.takeIf { it.isNotBlank() })

internal fun String?.toDateOrNull(): LocalDate? =
    this?.takeIf { it.length >= 10 }?.let { runCatching { LocalDate.parse(it.substring(0, 10)) }.getOrNull() }
