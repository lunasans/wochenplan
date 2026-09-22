package de.wochenplan.app.data.repo

import de.wochenplan.app.data.db.TaskEntity
import de.wochenplan.app.data.db.TaskTemplateItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskTemplateMapperTest {

    private fun item(
        title: String,
        weekday: Int? = null,
        pomodoros: Int = 1,
        sortIndex: Int = 0,
        notes: String? = null,
    ) = TaskTemplateItemEntity(
        templateId = 1,
        title = title,
        notes = notes,
        weekday = weekday,
        plannedPomodoros = pomodoros,
        sortIndex = sortIndex,
    )

    private fun task(
        title: String,
        weekday: Int? = null,
        done: Boolean = false,
        planned: Int = 1,
        donePomodoros: Int = 0,
        sortIndex: Int = 0,
    ) = TaskEntity(
        id = 0,
        weekKey = "2026-W39",
        title = title,
        weekday = weekday,
        done = done,
        plannedPomodoros = planned,
        donePomodoros = donePomodoros,
        sortIndex = sortIndex,
    )

    @Test
    fun `Vorlage wird zu Aufgaben der Woche`() {
        val tasks = TaskTemplateMapper.toTasks(
            items = listOf(
                item("Wocheneinkauf", weekday = 6, pomodoros = 2, sortIndex = 1),
                item("Buchhaltung", weekday = 1, pomodoros = 3, sortIndex = 0),
            ),
            weekKey = "2026-W40",
            now = 42L,
        )

        // Die Reihenfolge der Vorlage bleibt erhalten.
        assertEquals(listOf("Buchhaltung", "Wocheneinkauf"), tasks.map { it.title })
        assertTrue(tasks.all { it.weekKey == "2026-W40" })
        assertEquals(listOf(1, 6), tasks.map { it.weekday })
        assertEquals(listOf(3, 2), tasks.map { it.plannedPomodoros })
        assertTrue(tasks.all { it.createdAt == 42L })
    }

    @Test
    fun `eingefuegte Aufgaben sind offen und ohne geleistete Pomodoros`() {
        val tasks = TaskTemplateMapper.toTasks(
            items = listOf(item("Sport", pomodoros = 2)),
            weekKey = "2026-W40",
        )
        assertEquals(1, tasks.size)
        assertTrue(!tasks[0].done)
        assertEquals(0, tasks[0].donePomodoros)
        assertEquals(0L, tasks[0].id)
    }

    @Test
    fun `neue Aufgaben werden hinter die vorhandenen einsortiert`() {
        val tasks = TaskTemplateMapper.toTasks(
            items = listOf(item("A", sortIndex = 0), item("B", sortIndex = 1)),
            weekKey = "2026-W40",
            startSortIndex = 7,
        )
        assertEquals(listOf(8, 9), tasks.map { it.sortIndex })
    }

    @Test
    fun `leere Eintraege werden uebersprungen und Werte begrenzt`() {
        val tasks = TaskTemplateMapper.toTasks(
            items = listOf(
                item("   ", sortIndex = 0),
                item("  Aufraeumen  ", pomodoros = 99, sortIndex = 1),
                item("Falscher Tag", weekday = 9, sortIndex = 2),
            ),
            weekKey = "2026-W40",
        )
        assertEquals(2, tasks.size)
        assertEquals("Aufraeumen", tasks[0].title)
        assertEquals(16, tasks[0].plannedPomodoros)
        assertEquals(null, tasks[1].weekday)
    }

    @Test
    fun `Woche wird zur Vorlage, nach Wochentag sortiert`() {
        val items = TaskTemplateMapper.toItems(
            tasks = listOf(
                task("Ohne Tag", weekday = null, sortIndex = 0),
                task("Freitag", weekday = 5, sortIndex = 1),
                task("Montag", weekday = 1, sortIndex = 2),
            ),
        )
        // Aufgaben ohne festen Tag stehen am Ende.
        assertEquals(listOf("Montag", "Freitag", "Ohne Tag"), items.map { it.title })
        assertEquals(listOf(0, 1, 2), items.map { it.sortIndex })
    }

    @Test
    fun `erledigte Aufgaben kommen ohne Fortschritt in die Vorlage`() {
        val items = TaskTemplateMapper.toItems(
            tasks = listOf(task("Erledigt", done = true, planned = 4, donePomodoros = 4)),
        )
        assertEquals(1, items.size)
        assertEquals(4, items[0].plannedPomodoros)
        assertEquals(0L, items[0].id)
    }

    @Test
    fun `Hin und Rueckweg bleibt stabil`() {
        val original = listOf(
            item("Buchhaltung", weekday = 1, pomodoros = 3, sortIndex = 0),
            item("Wocheneinkauf", weekday = 6, pomodoros = 2, sortIndex = 1),
        )
        val roundTrip = TaskTemplateMapper.toItems(
            TaskTemplateMapper.toTasks(original, weekKey = "2026-W40"),
        )
        assertEquals(original.map { it.title }, roundTrip.map { it.title })
        assertEquals(original.map { it.weekday }, roundTrip.map { it.weekday })
        assertEquals(original.map { it.plannedPomodoros }, roundTrip.map { it.plannedPomodoros })
    }
}
