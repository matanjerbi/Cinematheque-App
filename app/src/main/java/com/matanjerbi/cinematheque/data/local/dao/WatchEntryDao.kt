package com.matanjerbi.cinematheque.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.matanjerbi.cinematheque.data.local.entity.EntryTagEntity
import com.matanjerbi.cinematheque.data.local.entity.EntryWithDetails
import com.matanjerbi.cinematheque.data.local.entity.MediaType
import com.matanjerbi.cinematheque.data.local.entity.WatchEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class WatchEntryDao {

    /**
     * The diary. Dated watches newest first; undated ("from the past") ones sink to the bottom,
     * where the UI shows them under their own heading.
     * Every filter is optional: pass null to skip it.
     */
    @Transaction
    @Query(
        """
        SELECT w.* FROM watch_entries w
        JOIN titles t ON t.id = w.titleId
        WHERE (:type IS NULL OR t.type = :type)
          AND (:minRating IS NULL OR w.rating >= :minRating)
          AND (:tagId IS NULL OR EXISTS (
                SELECT 1 FROM entry_tags et WHERE et.entryId = w.id AND et.tagId = :tagId))
        ORDER BY w.watchedDate IS NULL, w.watchedDate DESC, w.createdAt DESC
        """
    )
    abstract fun observeDiary(
        type: MediaType? = null,
        minRating: Int? = null,
        tagId: Long? = null,
    ): Flow<List<EntryWithDetails>>

    @Transaction
    @Query("SELECT * FROM watch_entries WHERE titleId = :titleId ORDER BY watchedDate IS NULL, watchedDate DESC, createdAt DESC")
    abstract fun observeForTitle(titleId: Long): Flow<List<EntryWithDetails>>

    @Transaction
    @Query("SELECT * FROM watch_entries WHERE id = :id")
    abstract suspend fun getWithDetails(id: Long): EntryWithDetails?

    @Query("SELECT COUNT(*) FROM watch_entries WHERE titleId = :titleId")
    abstract suspend fun countForTitle(titleId: Long): Int

    @Insert
    protected abstract suspend fun insertEntry(entry: WatchEntryEntity): Long

    @Update
    protected abstract suspend fun updateEntry(entry: WatchEntryEntity)

    @Query("DELETE FROM watch_entries WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM entry_tags WHERE entryId = :entryId")
    protected abstract suspend fun clearTags(entryId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertTags(tags: List<EntryTagEntity>)

    @Query("DELETE FROM watchlist WHERE titleId = :titleId")
    protected abstract suspend fun removeFromWatchlist(titleId: Long)

    /**
     * Creates or updates a watch together with its tags.
     * A new watch on a title that already has one is marked as a rewatch automatically,
     * and watching something takes it off the watchlist.
     */
    @Transaction
    open suspend fun save(entry: WatchEntryEntity, tagIds: List<Long>): Long {
        require(entry.rating in 1..10) { "rating must be 1..10" }
        val id = if (entry.id == 0L) {
            val isRewatch = countForTitle(entry.titleId) > 0
            insertEntry(entry.copy(isRewatch = isRewatch)).also { removeFromWatchlist(entry.titleId) }
        } else {
            updateEntry(entry)
            entry.id
        }
        clearTags(id)
        insertTags(tagIds.distinct().map { EntryTagEntity(id, it) })
        return id
    }
}
