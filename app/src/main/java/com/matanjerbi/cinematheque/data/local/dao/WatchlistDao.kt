package com.matanjerbi.cinematheque.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.matanjerbi.cinematheque.data.local.entity.WatchlistItemEntity
import com.matanjerbi.cinematheque.data.local.entity.WatchlistItemWithTitle
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {

    @Transaction
    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<WatchlistItemWithTitle>>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE titleId = :titleId)")
    fun observeContains(titleId: Long): Flow<Boolean>

    @Upsert
    suspend fun upsert(item: WatchlistItemEntity)

    @Query("DELETE FROM watchlist WHERE titleId = :titleId")
    suspend fun remove(titleId: Long)
}
