package com.matanjerbi.cinematheque.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.matanjerbi.cinematheque.data.local.entity.MediaType
import com.matanjerbi.cinematheque.data.local.entity.PersonCredit
import com.matanjerbi.cinematheque.data.local.entity.PersonEntity
import com.matanjerbi.cinematheque.data.local.entity.PersonRole
import com.matanjerbi.cinematheque.data.local.entity.TitleEntity
import com.matanjerbi.cinematheque.data.local.entity.TitleGenreEntity
import com.matanjerbi.cinematheque.data.local.entity.TitlePersonEntity
import com.matanjerbi.cinematheque.data.local.entity.TitleWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TitleDao {

    @Query("SELECT * FROM titles WHERE tmdbId = :tmdbId AND type = :type LIMIT 1")
    abstract suspend fun findByTmdb(tmdbId: Int, type: MediaType): TitleEntity?

    @Query("SELECT * FROM titles WHERE id = :id")
    abstract suspend fun getById(id: Long): TitleEntity?

    @Transaction
    @Query("SELECT * FROM titles WHERE id = :id")
    abstract fun observeWithDetails(id: Long): Flow<TitleWithDetails?>

    /** Credits for the title page: directors/creators first, then cast by billing. */
    @Query(
        """
        SELECT p.*, tp.role, tp.sortOrder FROM title_people tp
        JOIN people p ON p.tmdbId = tp.personId
        WHERE tp.titleId = :titleId
        ORDER BY CASE tp.role WHEN 'CAST' THEN 1 ELSE 0 END, tp.sortOrder
        """
    )
    abstract fun observeCredits(titleId: Long): Flow<List<PersonCredit>>

    /** Local lookup of already-cached titles, used to mark search results as "already in your library". */
    @Query("SELECT * FROM titles WHERE tmdbId IN (:tmdbIds)")
    abstract suspend fun findAllByTmdbIds(tmdbIds: List<Int>): List<TitleEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertTitle(title: TitleEntity): Long

    @Update
    protected abstract suspend fun updateTitle(title: TitleEntity)

    @Query("UPDATE titles SET posterLocalPath = :path WHERE id = :titleId")
    abstract suspend fun setPosterLocalPath(titleId: Long, path: String?)

    @Query("DELETE FROM title_genres WHERE titleId = :titleId")
    protected abstract suspend fun clearGenres(titleId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertGenres(genres: List<TitleGenreEntity>)

    @Query("DELETE FROM title_people WHERE titleId = :titleId")
    protected abstract suspend fun clearCredits(titleId: Long)

    @Upsert
    protected abstract suspend fun upsertPeople(people: List<PersonEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertCredits(credits: List<TitlePersonEntity>)

    /**
     * Writes a title with its genres and credits in one transaction.
     * Creates the row on first save, or refreshes it in place (keeping its id) on a manual refresh.
     * Returns the local title id.
     */
    @Transaction
    open suspend fun saveFull(
        title: TitleEntity,
        genres: List<String>,
        people: List<PersonEntity>,
        credits: List<CreditInput>,
    ): Long {
        val existing = findByTmdb(title.tmdbId, title.type)
        val id = if (existing == null) {
            insertTitle(title.copy(id = 0))
        } else {
            updateTitle(title.copy(id = existing.id, posterLocalPath = title.posterLocalPath ?: existing.posterLocalPath))
            existing.id
        }
        clearGenres(id)
        insertGenres(genres.distinct().map { TitleGenreEntity(id, it) })
        clearCredits(id)
        upsertPeople(people.distinctBy { it.tmdbId })
        insertCredits(credits.map { TitlePersonEntity(id, it.personId, it.role, it.sortOrder) })
        return id
    }
}

/** A credit to attach to a title being saved (the title id isn't known yet). */
data class CreditInput(val personId: Int, val role: PersonRole, val sortOrder: Int)
