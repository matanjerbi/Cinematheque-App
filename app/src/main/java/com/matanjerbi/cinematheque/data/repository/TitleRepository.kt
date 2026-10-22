package com.matanjerbi.cinematheque.data.repository

import com.matanjerbi.cinematheque.data.local.CinemathequeDatabase
import com.matanjerbi.cinematheque.data.local.dao.CreditInput
import com.matanjerbi.cinematheque.data.local.entity.MediaType
import com.matanjerbi.cinematheque.data.local.entity.PersonEntity
import com.matanjerbi.cinematheque.data.local.entity.PersonRole
import com.matanjerbi.cinematheque.data.local.entity.TitleEntity
import com.matanjerbi.cinematheque.data.remote.DetailsDto
import com.matanjerbi.cinematheque.data.remote.TmdbApi
import com.matanjerbi.cinematheque.data.remote.TmdbClient
import com.matanjerbi.cinematheque.data.remote.yearOrNull
import kotlin.coroutines.cancellation.CancellationException

/** A row in the search screen. `localId` is set when the title is already in the library. */
data class SearchResult(
    val tmdbId: Int,
    val type: MediaType,
    val title: String,
    val originalTitle: String,
    val year: Int?,
    val posterUrl: String?,
    val tmdbRating: Double?,
    val localId: Long?,
)

/**
 * The only place that talks to TMDB.
 *
 * Network budget per title:
 *  - search: one call per (debounced) query, nothing stored
 *  - save: one details+credits call, plus one English fallback call only when the
 *    Hebrew overview is missing, plus one poster download
 *  - after that, everything is read from Room; TMDB is called again only on manual refresh
 */
class TitleRepository(
    private val api: TmdbApi,
    private val db: CinemathequeDatabase,
    private val posters: PosterStore,
) {
    private val titleDao = db.titleDao()

    suspend fun search(query: String): Result<List<SearchResult>> = resultOf {
        val q = query.trim()
        if (q.length < MIN_QUERY_LENGTH) return@resultOf emptyList()

        val items = api.searchMulti(q).results
            .filter { it.mediaType == "movie" || it.mediaType == "tv" }

        val local = titleDao.findAllByTmdbIds(items.map { it.id })
            .associateBy { it.tmdbId to it.type }

        items.map { dto ->
            val type = if (dto.mediaType == "movie") MediaType.MOVIE else MediaType.TV
            SearchResult(
                tmdbId = dto.id,
                type = type,
                title = dto.title ?: dto.name.orEmpty(),
                originalTitle = dto.originalTitle ?: dto.originalName.orEmpty(),
                year = (dto.releaseDate ?: dto.firstAirDate).yearOrNull(),
                posterUrl = dto.posterPath?.let { PosterStore.remoteUrl(it, "w185") },
                tmdbRating = dto.voteAverage,
                localId = local[dto.id to type]?.id,
            )
        }
    }

    /**
     * Returns the local id for a TMDB title, fetching and caching it on first use.
     * Called when the user taps "watched" / "to watchlist" / "add to collection" on a search result.
     */
    suspend fun ensureSaved(tmdbId: Int, type: MediaType): Result<Long> = resultOf {
        titleDao.findByTmdb(tmdbId, type)?.let { return@resultOf it.id }
        fetchAndStore(tmdbId, type)
    }

    /** Manual refresh from the title page: re-fetches metadata, keeps the id and all your watches. */
    suspend fun refresh(titleId: Long): Result<Long> = resultOf {
        val existing = requireNotNull(titleDao.getById(titleId)) { "title $titleId not found" }
        fetchAndStore(existing.tmdbId, existing.type)
    }

    private suspend fun fetchAndStore(tmdbId: Int, type: MediaType): Long {
        val he = fetchDetails(tmdbId, type, TmdbClient.LANGUAGE, withCredits = true)
        val needsFallback = he.overview.isNullOrBlank()
        val en = if (needsFallback) fetchDetails(tmdbId, type, TmdbClient.FALLBACK_LANGUAGE, withCredits = false) else null

        val posterPath = he.posterPath ?: en?.posterPath
        val localPoster = posters.download(type, tmdbId, posterPath)

        val title = TitleEntity(
            tmdbId = tmdbId,
            type = type,
            title = (he.title ?: he.name).orEmpty().ifBlank { (en?.title ?: en?.name).orEmpty() },
            originalTitle = (he.originalTitle ?: he.originalName).orEmpty(),
            originalLanguage = he.originalLanguage,
            year = (he.releaseDate ?: he.firstAirDate).yearOrNull(),
            overview = he.overview.orEmpty().ifBlank { en?.overview.orEmpty() },
            posterLocalPath = localPoster,
            posterRemotePath = posterPath,
            runtimeMinutes = he.runtime?.takeIf { type == MediaType.MOVIE && it > 0 },
            seasonsCount = he.numberOfSeasons?.takeIf { type == MediaType.TV },
            tmdbRating = he.voteAverage?.takeIf { it > 0 },
            fetchedAt = System.currentTimeMillis(),
        )

        val (people, credits) = extractCredits(he, type)
        return titleDao.saveFull(title, he.genres.map { it.name }, people, credits)
    }

    private suspend fun fetchDetails(tmdbId: Int, type: MediaType, language: String, withCredits: Boolean): DetailsDto {
        val append = if (withCredits) "credits" else null
        return when (type) {
            MediaType.MOVIE -> api.movieDetails(tmdbId, language, append)
            MediaType.TV -> api.tvDetails(tmdbId, language, append)
        }
    }

    /** Directors (movies) or creators (series), plus the top-billed cast. */
    private fun extractCredits(dto: DetailsDto, type: MediaType): Pair<List<PersonEntity>, List<CreditInput>> {
        val people = mutableListOf<PersonEntity>()
        val credits = mutableListOf<CreditInput>()

        when (type) {
            MediaType.MOVIE -> dto.credits?.crew.orEmpty()
                .filter { it.job == "Director" }
                .forEach {
                    people += PersonEntity(it.id, it.name, it.profilePath)
                    credits += CreditInput(it.id, PersonRole.DIRECTOR, 0)
                }
            MediaType.TV -> dto.createdBy.forEach {
                people += PersonEntity(it.id, it.name, it.profilePath)
                credits += CreditInput(it.id, PersonRole.CREATOR, 0)
            }
        }

        dto.credits?.cast.orEmpty()
            .sortedBy { it.order }
            .take(CAST_LIMIT)
            .forEachIndexed { index, cast ->
                people += PersonEntity(cast.id, cast.name, cast.profilePath)
                credits += CreditInput(cast.id, PersonRole.CAST, index)
            }

        return people to credits.distinctBy { it.personId to it.role }
    }

    companion object {
        const val MIN_QUERY_LENGTH = 2
        const val CAST_LIMIT = 10
    }
}

/** Like runCatching, but lets coroutine cancellation propagate instead of turning it into a failure. */
internal inline fun <T> resultOf(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
