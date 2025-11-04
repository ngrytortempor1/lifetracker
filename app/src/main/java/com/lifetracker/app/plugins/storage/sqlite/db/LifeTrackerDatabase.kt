package com.lifetracker.app.plugins.storage.sqlite.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lifetracker.app.plugins.storage.sqlite.db.dao.LifeTrackerDao
import com.lifetracker.app.plugins.storage.sqlite.db.entities.EventEntity
import com.lifetracker.app.plugins.storage.sqlite.db.entities.HabitEntity
import com.lifetracker.app.plugins.storage.sqlite.db.entities.OutboxEntity
import com.lifetracker.app.plugins.storage.sqlite.db.entities.QuickLogTagEntity
import com.lifetracker.app.plugins.storage.sqlite.db.entities.TaskEntity
import com.lifetracker.app.plugins.storage.sqlite.db.entities.TaskListEntity

@Database(
    entities = [
        TaskListEntity::class,
        TaskEntity::class,
        HabitEntity::class,
        QuickLogTagEntity::class,
        EventEntity::class,
        OutboxEntity::class,
        com.lifetracker.app.plugins.storage.sqlite.db.entities.MoodEntryEntity::class,
        com.lifetracker.app.plugins.storage.sqlite.db.entities.SleepSessionEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(LifeTrackerTypeConverters::class)
abstract class LifeTrackerDatabase : RoomDatabase() {
    abstract fun lifeTrackerDao(): LifeTrackerDao

    companion object {
        private const val DB_NAME = "life_tracker.db"

        @Volatile
        private var INSTANCE: LifeTrackerDatabase? = null

        fun getInstance(context: Context): LifeTrackerDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context): LifeTrackerDatabase =
            Room.databaseBuilder(
                context,
                LifeTrackerDatabase::class.java,
                DB_NAME
            ).fallbackToDestructiveMigration().build()

        fun databaseFile(context: Context) = context.getDatabasePath(DB_NAME)
    }
}
