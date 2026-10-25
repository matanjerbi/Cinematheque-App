package com.matanjerbi.cinematheque.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.matanjerbi.cinematheque.data.local.entity.FollowedShowEntity
import com.matanjerbi.cinematheque.data.local.entity.FollowedShowWithTitle
import kotlinx.coroutines.flow.Flow

@Dao
interface FollowedShowDao {

    @Transaction
    @Query("SELECT * FROM followed_shows")
    fun observeAllWithTitle(): Flow<List<FollowedShowWithTitle>>

    @Query("SELECT * FROM followed_shows WHERE titleId = :titleId")
    fun observe(titleId: Long): Flow<FollowedShowEntity?>

    @Query("SELECT * FROM followed_shows WHERE titleId = :titleId")
    suspend fun get(titleId: Long): FollowedShowEntity?

    @Transaction
    @Query("SELECT * FROM followed_shows WHERE titleId = :titleId")
    suspend fun getWithTitle(titleId: Long): FollowedShowWithTitle?

    @Query("SELECT * FROM followed_shows")
    suspend fun getAll(): List<FollowedShowEntity>

    @Upsert
    suspend fun upsert(show: FollowedShowEntity)

    @Query("DELETE FROM followed_shows WHERE titleId = :titleId")
    suspend fun delete(titleId: Long)
}
