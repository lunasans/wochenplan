package de.wochenplan.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CalendarEntity::class,
        CachedEventEntity::class,
        TaskEntity::class,
        FocusSessionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class WochenplanDatabase : RoomDatabase() {

    abstract fun calendarDao(): CalendarDao
    abstract fun eventCacheDao(): EventCacheDao
    abstract fun taskDao(): TaskDao
    abstract fun focusSessionDao(): FocusSessionDao

    companion object {
        fun create(context: Context): WochenplanDatabase =
            Room.databaseBuilder(context, WochenplanDatabase::class.java, "wochenplan.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
