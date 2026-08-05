package com.minion.scaffold.feature.weather.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * This feature's own Room database — not the app's, since nothing else needs these tables yet.
 *
 * `forecast_cache` is disposable (it refetches), but `saved_locations` is user data, so schema
 * changes get a real [MIGRATION_1_2]-style migration rather than
 * `fallbackToDestructiveMigration()`: a destructive fallback would silently wipe a user's saved
 * cities on any future version bump, which is exactly the kind of data loss that is invisible
 * until someone complains.
 */
@Database(
    entities = [ForecastCacheEntity::class, SavedLocationEntity::class],
    version = 2,
    exportSchema = false,
)
internal abstract class WeatherDatabase : RoomDatabase() {
    abstract fun forecastCacheDao(): ForecastCacheDao
    abstract fun savedLocationDao(): SavedLocationDao
}

/** v1 shipped with only `forecast_cache`; v2 adds the saved-locations list (SPEC.md §6). */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `saved_locations` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `latitude` REAL NOT NULL,
                `longitude` REAL NOT NULL,
                `sortOrder` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
    }
}
