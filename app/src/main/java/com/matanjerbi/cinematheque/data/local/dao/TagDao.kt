package com.matanjerbi.cinematheque.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.matanjerbi.cinematheque.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

data class TagWithUsage(
    val id: Long,
    val name: String,
    val isDefault: Boolean,
    val uses: Int,
)

@Dao
abstract class TagDao {

    /** Defaults first, then the user's own; most-used first inside each group. */
    @Query(
        """
        SELECT t.id, t.name, t.isDefault, COUNT(et.entryId) AS uses
        FROM tags t LEFT JOIN entry_tags et ON et.tagId = t.id
        GROUP BY t.id
        ORDER BY t.isDefault DESC, uses DESC, t.name
        """
    )
    abstract fun observeAllWithUsage(): Flow<List<TagWithUsage>>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    abstract suspend fun findByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insert(tag: TagEntity): Long

    /** Adds a tag from the "+ new tag" chip; returns the existing id if the name is taken. */
    @Transaction
    open suspend fun getOrCreate(name: String): Long {
        val clean = name.trim()
        require(clean.isNotEmpty())
        findByName(clean)?.let { return it.id }
        return insert(TagEntity(name = clean))
    }

    @Query("UPDATE tags SET name = :newName WHERE id = :id")
    abstract suspend fun rename(id: Long, newName: String)

    @Query("DELETE FROM tags WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("INSERT OR IGNORE INTO entry_tags (entryId, tagId) SELECT entryId, :intoId FROM entry_tags WHERE tagId = :fromId")
    protected abstract suspend fun copyUsages(fromId: Long, intoId: Long)

    /** Merges one tag into another: every watch tagged `from` ends up tagged `into`. */
    @Transaction
    open suspend fun merge(fromId: Long, intoId: Long) {
        if (fromId == intoId) return
        copyUsages(fromId, intoId)
        delete(fromId) // cascades the old entry_tags rows
    }
}
