package de.wochenplan.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Die SQL-Anweisungen muessen exakt dem entsprechen, was Room aus den Entitaeten
 * ableitet – sonst verweigert Room beim naechsten Start den Dienst. Der Test
 * `MigrationSqlTest` vergleicht sie deshalb mit dem erzeugten Schema.
 */
object Migrations {

    const val CREATE_TASK_TEMPLATES =
        "CREATE TABLE IF NOT EXISTS `task_templates` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`name` TEXT NOT NULL, " +
            "`description` TEXT, " +
            "`sortIndex` INTEGER NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, " +
            "`lastUsedAt` INTEGER)"

    const val CREATE_TASK_TEMPLATE_ITEMS =
        "CREATE TABLE IF NOT EXISTS `task_template_items` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`templateId` INTEGER NOT NULL, " +
            "`title` TEXT NOT NULL, " +
            "`notes` TEXT, " +
            "`weekday` INTEGER, " +
            "`plannedPomodoros` INTEGER NOT NULL, " +
            "`sortIndex` INTEGER NOT NULL, " +
            "FOREIGN KEY(`templateId`) REFERENCES `task_templates`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE )"

    const val INDEX_TASK_TEMPLATE_ITEMS =
        "CREATE INDEX IF NOT EXISTS `index_task_template_items_templateId` " +
            "ON `task_template_items` (`templateId`)"

    /** Fuegt die Tabellen fuer Aufgaben-Vorlagen hinzu. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_TASK_TEMPLATES)
            db.execSQL(CREATE_TASK_TEMPLATE_ITEMS)
            db.execSQL(INDEX_TASK_TEMPLATE_ITEMS)
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
