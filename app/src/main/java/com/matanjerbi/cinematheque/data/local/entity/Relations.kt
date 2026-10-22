package com.matanjerbi.cinematheque.data.local.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/** A diary row: the watch, its title and its tags. */
data class EntryWithDetails(
    @Embedded val entry: WatchEntryEntity,
    @Relation(parentColumn = "titleId", entityColumn = "id")
    val title: TitleEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(EntryTagEntity::class, parentColumn = "entryId", entityColumn = "tagId")
    )
    val tags: List<TagEntity>,
)

/** Everything the title page needs. */
data class TitleWithDetails(
    @Embedded val title: TitleEntity,
    @Relation(parentColumn = "id", entityColumn = "titleId")
    val genres: List<TitleGenreEntity>,
    @Relation(parentColumn = "id", entityColumn = "titleId", entity = WatchEntryEntity::class)
    val entries: List<WatchEntryEntity>,
)

data class PersonCredit(
    @Embedded val person: PersonEntity,
    val role: PersonRole,
    val sortOrder: Int,
)

data class WatchlistItemWithTitle(
    @Embedded val item: WatchlistItemEntity,
    @Relation(parentColumn = "titleId", entityColumn = "id")
    val title: TitleEntity,
)

data class CollectionSummary(
    @Embedded val collection: CollectionEntity,
    val itemCount: Int,
)

data class CollectionItemRow(
    @Embedded val item: CollectionItemEntity,
    @Relation(parentColumn = "titleId", entityColumn = "id")
    val title: TitleEntity,
    /** Computed in the query: has this title been watched at least once. */
    val watched: Boolean,
)
