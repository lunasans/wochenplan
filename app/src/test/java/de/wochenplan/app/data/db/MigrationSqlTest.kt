package de.wochenplan.app.data.db

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Room prueft beim Start, ob die Tabellen exakt zu den Entitaeten passen, und
 * verweigert sonst den Dienst. Eine handgeschriebene Migration ist damit eine
 * stille Gefahr: Ein Tippfehler faellt erst auf dem Geraet auf.
 *
 * Dieser Test vergleicht die Anweisungen der Migration mit dem Schema, das Room
 * beim Uebersetzen erzeugt – ohne Emulator.
 */
class MigrationSqlTest {

    @Test
    fun `Migration 1 auf 2 legt die Tabellen genau so an, wie Room sie erwartet`() {
        val schema = readSchema(version = 2)

        assertEquals(
            "CREATE TABLE fuer task_templates weicht vom erzeugten Schema ab",
            normalize(createSqlOf("task_templates", schema).withTableName("task_templates")),
            normalize(Migrations.CREATE_TASK_TEMPLATES),
        )
        assertEquals(
            "CREATE TABLE fuer task_template_items weicht vom erzeugten Schema ab",
            normalize(createSqlOf("task_template_items", schema).withTableName("task_template_items")),
            normalize(Migrations.CREATE_TASK_TEMPLATE_ITEMS),
        )
        assertEquals(
            "CREATE INDEX fuer task_template_items weicht vom erzeugten Schema ab",
            normalize(
                indexSqlOf("index_task_template_items_templateId", schema)
                    .withTableName("task_template_items")
            ),
            normalize(Migrations.INDEX_TASK_TEMPLATE_ITEMS),
        )
    }

    @Test
    fun `die Datenbankversion passt zum erzeugten Schema`() {
        val schema = readSchema(version = 2)
        val version = Regex("\"version\"\\s*:\\s*(\\d+)").find(schema)?.groupValues?.get(1)
        assertEquals("2", version)
    }

    private fun readSchema(version: Int): String {
        val relative = "schemas/de.wochenplan.app.data.db.WochenplanDatabase/$version.json"
        val file = listOf(File(relative), File("app/$relative")).firstOrNull { it.exists() }
            ?: error(
                "Das Room-Schema $relative fehlt. Es entsteht beim Uebersetzen; " +
                    "bitte zuerst ./gradlew assembleDebug ausfuehren."
            )
        return file.readText()
    }

    /** Der Abschnitt einer Tabelle beginnt im Schema mit ihrem Namen. */
    private fun createSqlOf(tableName: String, schema: String): String {
        val start = Regex("\"tableName\"\\s*:\\s*\"$tableName\"").find(schema)?.range?.first
            ?: error("Die Tabelle $tableName steht nicht im erzeugten Schema.")
        return Regex("\"createSql\"\\s*:\\s*\"(.*?)\"").find(schema, start)?.groupValues?.get(1)
            ?: error("Zu $tableName gibt es kein createSql im Schema.")
    }

    private fun indexSqlOf(indexName: String, schema: String): String {
        val start = Regex("\"name\"\\s*:\\s*\"$indexName\"").find(schema)?.range?.first
            ?: error("Der Index $indexName steht nicht im erzeugten Schema.")
        return Regex("\"createSql\"\\s*:\\s*\"(.*?)\"").find(schema, start)?.groupValues?.get(1)
            ?: error("Zu $indexName gibt es kein createSql im Schema.")
    }

    /** Room setzt im Schema den Platzhalter `${'$'}{TABLE_NAME}` ein. */
    private fun String.withTableName(tableName: String): String =
        replace("\${TABLE_NAME}", tableName)

    private fun normalize(sql: String): String =
        sql.replace(Regex("\\s+"), " ").trim()
}
