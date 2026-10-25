package com.matanjerbi.cinematheque.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.matanjerbi.cinematheque.data.local.dao.CollectionDao
import com.matanjerbi.cinematheque.data.local.dao.FollowedShowDao
import com.matanjerbi.cinematheque.data.local.dao.StatsDao
import com.matanjerbi.cinematheque.data.local.dao.TagDao
import com.matanjerbi.cinematheque.data.local.dao.TitleDao
import com.matanjerbi.cinematheque.data.local.dao.WatchEntryDao
import com.matanjerbi.cinematheque.data.local.dao.WatchlistDao
import com.matanjerbi.cinematheque.data.local.entity.CollectionEntity
import com.matanjerbi.cinematheque.data.local.entity.CollectionItemEntity
import com.matanjerbi.cinematheque.data.local.entity.EntryTagEntity
import com.matanjerbi.cinematheque.data.local.entity.FollowedShowEntity
import com.matanjerbi.cinematheque.data.local.entity.PersonEntity
import com.matanjerbi.cinematheque.data.local.entity.TagEntity
import com.matanjerbi.cinematheque.data.local.entity.TitleEntity
import com.matanjerbi.cinematheque.data.local.entity.TitleGenreEntity
import com.matanjerbi.cinematheque.data.local.entity.TitlePersonEntity
import com.matanjerbi.cinematheque.data.local.entity.WatchEntryEntity
import com.matanjerbi.cinematheque.data.local.entity.WatchlistItemEntity

@Database(
    entities = [
        TitleEntity::class,
        TitleGenreEntity::class,
        WatchEntryEntity::class,
        TagEntity::class,
        EntryTagEntity::class,
        PersonEntity::class,
        TitlePersonEntity::class,
        CollectionEntity::class,
        CollectionItemEntity::class,
        WatchlistItemEntity::class,
        FollowedShowEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CinemathequeDatabase : RoomDatabase() {
    abstract fun titleDao(): TitleDao
    abstract fun watchEntryDao(): WatchEntryDao
    abstract fun tagDao(): TagDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun collectionDao(): CollectionDao
    abstract fun statsDao(): StatsDao
    abstract fun followedShowDao(): FollowedShowDao

    companion object {
        /** The general tags every watch can carry; the user adds their own on top. */
        val DEFAULT_TAGS = listOf(
            "סוחף", "מעורר מחשבה", "פסיכולוגי", "עמוק",
            "מותח", "מרגש", "מצחיק", "אפל", "יפה ויזואלית", "איטי",
        )

        /** v2: series airing now, with a weekly schedule and episode reminders. Keeps all existing data. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `followed_shows` (
                        `titleId` INTEGER NOT NULL,
                        `airDays` INTEGER NOT NULL,
                        `airMinuteOfDay` INTEGER NOT NULL,
                        `remindersOn` INTEGER NOT NULL,
                        `season` INTEGER NOT NULL,
                        `airedCount` INTEGER NOT NULL,
                        `airedCountAsOf` INTEGER NOT NULL,
                        `seasonEpisodeCount` INTEGER,
                        `tmdbSyncedAt` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`titleId`),
                        FOREIGN KEY(`titleId`) REFERENCES `titles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }
        }

        fun build(context: Context): CinemathequeDatabase =
            Room.databaseBuilder(context, CinemathequeDatabase::class.java, "cinematheque.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        DEFAULT_TAGS.forEach { name ->
                            db.execSQL("INSERT OR IGNORE INTO tags (name, isDefault) VALUES (?, 1)", arrayOf(name))
                        }
                    }
                })
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
