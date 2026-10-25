package com.matanjerbi.cinematheque.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class MediaType { MOVIE, TV }

/** How much we know about when a watch happened. Past watches are often YEAR or UNKNOWN. */
enum class DatePrecision { EXACT, YEAR, UNKNOWN }

enum class PersonRole { DIRECTOR, CREATOR, CAST }

/**
 * A movie or series, cached from TMDB once and then read locally.
 * TMDB ids are only unique per media type, hence the (tmdbId, type) unique index.
 */
@Entity(
    tableName = "titles",
    indices = [Index(value = ["tmdbId", "type"], unique = true)]
)
data class TitleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tmdbId: Int,
    val type: MediaType,
    val title: String,
    val originalTitle: String,
    val originalLanguage: String?,
    val year: Int?,
    val overview: String,
    /** Path under filesDir once the poster was downloaded; null until then. */
    val posterLocalPath: String?,
    /** TMDB path (e.g. "/abc.jpg") kept so a missing local file can be re-downloaded. */
    val posterRemotePath: String?,
    /** Movies only. */
    val runtimeMinutes: Int?,
    /** Series only. */
    val seasonsCount: Int?,
    val tmdbRating: Double?,
    val fetchedAt: Long,
)

@Entity(
    tableName = "title_genres",
    primaryKeys = ["titleId", "genre"],
    foreignKeys = [ForeignKey(
        entity = TitleEntity::class, parentColumns = ["id"], childColumns = ["titleId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class TitleGenreEntity(
    val titleId: Long,
    val genre: String,
)

/**
 * One viewing. A title can have several (rewatches); tags hang off the watch,
 * not the title, so a second viewing can feel different from the first.
 * For YEAR precision, watchedDate is Jan 1 of that year; for UNKNOWN it is null.
 */
@Entity(
    tableName = "watch_entries",
    foreignKeys = [ForeignKey(
        entity = TitleEntity::class, parentColumns = ["id"], childColumns = ["titleId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("titleId"), Index("watchedDate")]
)
data class WatchEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titleId: Long,
    val watchedDate: LocalDate?,
    val datePrecision: DatePrecision,
    /** 1..10 */
    val rating: Int,
    val freeText: String = "",
    val isRewatch: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isDefault: Boolean = false,
)

@Entity(
    tableName = "entry_tags",
    primaryKeys = ["entryId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = WatchEntryEntity::class, parentColumns = ["id"], childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        ),
    ],
    indices = [Index("tagId")]
)
data class EntryTagEntity(
    val entryId: Long,
    val tagId: Long,
)

@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey val tmdbId: Int,
    val name: String,
    val profilePath: String?,
)

@Entity(
    tableName = "title_people",
    primaryKeys = ["titleId", "personId", "role"],
    foreignKeys = [
        ForeignKey(
            entity = TitleEntity::class, parentColumns = ["id"], childColumns = ["titleId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PersonEntity::class, parentColumns = ["tmdbId"], childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        ),
    ],
    indices = [Index("personId")]
)
data class TitlePersonEntity(
    val titleId: Long,
    val personId: Int,
    val role: PersonRole,
    /** Billing order for cast; 0 for directors/creators. */
    val sortOrder: Int,
)

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "collection_items",
    primaryKeys = ["collectionId", "titleId"],
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class, parentColumns = ["id"], childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TitleEntity::class, parentColumns = ["id"], childColumns = ["titleId"],
            onDelete = ForeignKey.CASCADE
        ),
    ],
    indices = [Index("titleId")]
)
data class CollectionItemEntity(
    val collectionId: Long,
    val titleId: Long,
    /** Manual order inside the collection (drag to reorder). */
    val position: Int,
    val note: String = "",
)

@Entity(
    tableName = "watchlist",
    foreignKeys = [ForeignKey(
        entity = TitleEntity::class, parentColumns = ["id"], childColumns = ["titleId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class WatchlistItemEntity(
    @PrimaryKey val titleId: Long,
    val addedAt: Long = System.currentTimeMillis(),
    val note: String = "",
    val recommendedBy: String = "",
)

/**
 * A series that is airing now and that the user follows episode by episode.
 *
 * The aired count is anchored: [airedCount] episodes were out at [airedCountAsOf],
 * and every scheduled slot since then adds one (capped by [seasonEpisodeCount]).
 * That way the count moves forward on its own, offline, with no daily job.
 */
@Entity(
    tableName = "followed_shows",
    foreignKeys = [ForeignKey(
        entity = TitleEntity::class, parentColumns = ["id"], childColumns = ["titleId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class FollowedShowEntity(
    @PrimaryKey val titleId: Long,
    /** Bit per weekday, Sunday = bit 0 … Saturday = bit 6. See [com.matanjerbi.cinematheque.airing.AirSchedule]. */
    val airDays: Int,
    /** Local (phone) time of the airing, minutes since midnight. */
    val airMinuteOfDay: Int,
    val remindersOn: Boolean = true,
    val season: Int = 1,
    val airedCount: Int = 0,
    val airedCountAsOf: Long = System.currentTimeMillis(),
    /** Episodes in the current season when known (from TMDB or typed in); null = open-ended. */
    val seasonEpisodeCount: Int? = null,
    val tmdbSyncedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
