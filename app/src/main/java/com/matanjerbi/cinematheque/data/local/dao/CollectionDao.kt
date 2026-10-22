package com.matanjerbi.cinematheque.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.matanjerbi.cinematheque.data.local.entity.CollectionEntity
import com.matanjerbi.cinematheque.data.local.entity.CollectionItemEntity
import com.matanjerbi.cinematheque.data.local.entity.CollectionItemRow
import com.matanjerbi.cinematheque.data.local.entity.CollectionSummary
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CollectionDao {

    @Query(
        """
        SELECT c.*, COUNT(ci.titleId) AS itemCount
        FROM collections c LEFT JOIN collection_items ci ON ci.collectionId = c.id
        GROUP BY c.id
        ORDER BY c.createdAt DESC
        """
    )
    abstract fun observeSummaries(): Flow<List<CollectionSummary>>

    /** First four posters for the collage on the collections grid. */
    @Query(
        """
        SELECT t.posterLocalPath FROM collection_items ci
        JOIN titles t ON t.id = ci.titleId
        WHERE ci.collectionId = :collectionId AND t.posterLocalPath IS NOT NULL
        ORDER BY ci.position LIMIT 4
        """
    )
    abstract fun observeCollagePosters(collectionId: Long): Flow<List<String>>

    @Query("SELECT * FROM collections WHERE id = :id")
    abstract fun observe(id: Long): Flow<CollectionEntity?>

    @Transaction
    @Query(
        """
        SELECT ci.*, EXISTS(SELECT 1 FROM watch_entries w WHERE w.titleId = ci.titleId) AS watched
        FROM collection_items ci
        WHERE ci.collectionId = :collectionId
        ORDER BY ci.position
        """
    )
    abstract fun observeItems(collectionId: Long): Flow<List<CollectionItemRow>>

    /** Which collections a title is in, for the toggle sheet on the title page. */
    @Query("SELECT collectionId FROM collection_items WHERE titleId = :titleId")
    abstract fun observeCollectionIdsFor(titleId: Long): Flow<List<Long>>

    @Insert
    abstract suspend fun create(collection: CollectionEntity): Long

    @Update
    abstract suspend fun update(collection: CollectionEntity)

    @Query("DELETE FROM collections WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM collection_items WHERE collectionId = :collectionId")
    protected abstract suspend fun maxPosition(collectionId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertItem(item: CollectionItemEntity): Long

    /** Appends to the end of the collection; no-op if the title is already in it. */
    @Transaction
    open suspend fun addItem(collectionId: Long, titleId: Long, note: String = "") {
        insertItem(CollectionItemEntity(collectionId, titleId, maxPosition(collectionId) + 1, note))
    }

    @Query("DELETE FROM collection_items WHERE collectionId = :collectionId AND titleId = :titleId")
    abstract suspend fun removeItem(collectionId: Long, titleId: Long)

    @Query("UPDATE collection_items SET position = :position WHERE collectionId = :collectionId AND titleId = :titleId")
    protected abstract suspend fun setPosition(collectionId: Long, titleId: Long, position: Int)

    /** Persists the order after a drag: titleIds in their new order. */
    @Transaction
    open suspend fun reorder(collectionId: Long, orderedTitleIds: List<Long>) {
        orderedTitleIds.forEachIndexed { index, titleId -> setPosition(collectionId, titleId, index) }
    }
}
