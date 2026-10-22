package com.matanjerbi.cinematheque.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

/**
 * The period picker on the stats screen.
 * - ALL counts every watch, including undated ones from the past.
 * - YEAR counts exact dates in that year plus watches you only know the year of.
 * - MONTH counts exact dates only.
 */
sealed interface StatsPeriod {
    data object All : StatsPeriod
    data class Year(val year: Int) : StatsPeriod
    data class Month(val month: YearMonth) : StatsPeriod
}

/**
 * Shared period clause, spliced into every stats query. Columns are on `w` (watch_entries).
 * Top-level const so it can be used inside @Query annotations.
 */
private const val PERIOD = """
    (:fromDay IS NULL OR (
        w.watchedDate >= :fromDay AND w.watchedDate < :toDay
        AND (w.datePrecision = 'EXACT' OR (:includeYearPrecision AND w.datePrecision = 'YEAR'))
    ))
"""

/** Turns a period into the three query arguments every stats query takes. */
data class PeriodArgs(val fromDay: Long?, val toDay: Long?, val includeYearPrecision: Boolean)

fun StatsPeriod.toArgs(): PeriodArgs = when (this) {
    StatsPeriod.All -> PeriodArgs(null, null, true)
    is StatsPeriod.Year -> PeriodArgs(
        LocalDate.of(year, 1, 1).toEpochDay(),
        LocalDate.of(year + 1, 1, 1).toEpochDay(),
        includeYearPrecision = true,
    )
    is StatsPeriod.Month -> PeriodArgs(
        month.atDay(1).toEpochDay(),
        month.plusMonths(1).atDay(1).toEpochDay(),
        includeYearPrecision = false,
    )
}

data class StatsTotals(
    val watchCount: Int,
    val titleCount: Int,
    val movieCount: Int,
    val seriesCount: Int,
    /** Movie runtimes only; series have no reliable total length at season level. */
    val movieMinutes: Long,
    val avgRating: Double?,
)

data class PersonStat(
    val tmdbId: Int,
    val name: String,
    val profilePath: String?,
    val titleCount: Int,
    val avgRating: Double,
)

data class TagStat(
    val tagId: Long,
    val name: String,
    val uses: Int,
    /** Share of the period's watches carrying this tag, 0..1. */
    val share: Double,
)

data class GroupRatingStat(
    val label: String,
    val watchCount: Int,
    val avgRating: Double,
)

data class DisagreementStat(
    val titleId: Long,
    val title: String,
    val posterLocalPath: String?,
    val myRating: Double,
    val tmdbRating: Double,
    /** Positive: you liked it more than TMDB users did. */
    val diff: Double,
)

/**
 * All stats are computed with local SQL; no network involved.
 * Every query filters watches with the same period clause.
 */
@Dao
interface StatsDao {

    @Query(
        """
        SELECT
            COUNT(*) AS watchCount,
            COUNT(DISTINCT w.titleId) AS titleCount,
            COUNT(DISTINCT CASE WHEN t.type = 'MOVIE' THEN t.id END) AS movieCount,
            COUNT(DISTINCT CASE WHEN t.type = 'TV' THEN t.id END) AS seriesCount,
            COALESCE(SUM(CASE WHEN t.type = 'MOVIE' THEN t.runtimeMinutes END), 0) AS movieMinutes,
            AVG(w.rating) AS avgRating
        FROM watch_entries w JOIN titles t ON t.id = w.titleId
        WHERE $PERIOD
        """
    )
    fun observeTotals(fromDay: Long?, toDay: Long?, includeYearPrecision: Boolean): Flow<StatsTotals>

    /** Directors (movies) and creators (series) you keep coming back to. */
    @Query(
        """
        SELECT p.tmdbId, p.name, p.profilePath,
               COUNT(DISTINCT w.titleId) AS titleCount, AVG(w.rating) AS avgRating
        FROM watch_entries w
        JOIN title_people tp ON tp.titleId = w.titleId AND tp.role IN ('DIRECTOR', 'CREATOR')
        JOIN people p ON p.tmdbId = tp.personId
        WHERE $PERIOD
        GROUP BY p.tmdbId
        ORDER BY titleCount DESC, avgRating DESC
        LIMIT :limit
        """
    )
    fun observeTopDirectors(fromDay: Long?, toDay: Long?, includeYearPrecision: Boolean, limit: Int = 10): Flow<List<PersonStat>>

    @Query(
        """
        SELECT p.tmdbId, p.name, p.profilePath,
               COUNT(DISTINCT w.titleId) AS titleCount, AVG(w.rating) AS avgRating
        FROM watch_entries w
        JOIN title_people tp ON tp.titleId = w.titleId AND tp.role = 'CAST'
        JOIN people p ON p.tmdbId = tp.personId
        WHERE $PERIOD
        GROUP BY p.tmdbId
        ORDER BY titleCount DESC, avgRating DESC
        LIMIT :limit
        """
    )
    fun observeTopCast(fromDay: Long?, toDay: Long?, includeYearPrecision: Boolean, limit: Int = 10): Flow<List<PersonStat>>

    @Query(
        """
        SELECT tg.id AS tagId, tg.name, COUNT(*) AS uses,
               CAST(COUNT(*) AS REAL) / (
                   SELECT COUNT(*) FROM watch_entries w WHERE $PERIOD
               ) AS share
        FROM watch_entries w
        JOIN entry_tags et ON et.entryId = w.id
        JOIN tags tg ON tg.id = et.tagId
        WHERE $PERIOD
        GROUP BY tg.id
        ORDER BY uses DESC
        LIMIT :limit
        """
    )
    fun observeTopTags(fromDay: Long?, toDay: Long?, includeYearPrecision: Boolean, limit: Int = 10): Flow<List<TagStat>>

    @Query(
        """
        SELECT g.genre AS label, COUNT(*) AS watchCount, AVG(w.rating) AS avgRating
        FROM watch_entries w JOIN title_genres g ON g.titleId = w.titleId
        WHERE $PERIOD
        GROUP BY g.genre
        ORDER BY avgRating DESC
        """
    )
    fun observeRatingByGenre(fromDay: Long?, toDay: Long?, includeYearPrecision: Boolean): Flow<List<GroupRatingStat>>

    /** Decade of the title's release (e.g. "1970"), not of when you watched it. */
    @Query(
        """
        SELECT CAST((t.year / 10) * 10 AS TEXT) AS label, COUNT(*) AS watchCount, AVG(w.rating) AS avgRating
        FROM watch_entries w JOIN titles t ON t.id = w.titleId
        WHERE t.year IS NOT NULL AND $PERIOD
        GROUP BY t.year / 10
        ORDER BY label
        """
    )
    fun observeRatingByDecade(fromDay: Long?, toDay: Long?, includeYearPrecision: Boolean): Flow<List<GroupRatingStat>>

    /**
     * Where your taste and the crowd's split furthest, either way.
     * Your rating is averaged across rewatches inside the period.
     */
    @Query(
        """
        SELECT t.id AS titleId, t.title, t.posterLocalPath,
               AVG(w.rating) AS myRating, t.tmdbRating,
               AVG(w.rating) - t.tmdbRating AS diff
        FROM watch_entries w JOIN titles t ON t.id = w.titleId
        WHERE t.tmdbRating IS NOT NULL AND t.tmdbRating > 0 AND $PERIOD
        GROUP BY t.id
        ORDER BY ABS(AVG(w.rating) - t.tmdbRating) DESC
        LIMIT :limit
        """
    )
    fun observeDisagreements(fromDay: Long?, toDay: Long?, includeYearPrecision: Boolean, limit: Int = 10): Flow<List<DisagreementStat>>
}
