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
        TaskTemplateEntity::class,
        TaskTemplateItemEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class WochenplanDatabase : RoomDatabase() {

    abstract fun calendarDao(): CalendarDao
    abstract fun eventCacheDao(): EventCacheDao
    abstract fun taskDao(): TaskDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun taskTemplateDao(): TaskTemplateDao

    companion object {
        fun create(context: Context): WochenplanDatabase =
            Room.databaseBuilder(context, WochenplanDatabase::class.java, "wochenplan.db")
                .addMigrations(*Migrations.ALL)
                // Nur als letzte Rettung: Ohne diesen Fallback liesse sich die App
                // nach einem Schemafehler gar nicht mehr starten.
                .fallbackToDestructiveMigration()
                .build()
    }
}
