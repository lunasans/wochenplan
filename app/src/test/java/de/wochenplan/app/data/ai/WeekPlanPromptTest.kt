package de.wochenplan.app.data.ai

import de.wochenplan.app.core.IsoWeek
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class WeekPlanPromptTest {

    private fun context(
        goals: String = "Steuererklaerung fertig machen",
        events: List<ContextEvent> = emptyList(),
        tasks: List<ContextTask> = emptyList(),
    ) = WeekPlanContext(
        week = IsoWeek(2026, 39),
        goals = goals,
        dayStartHour = 8,
        dayEndHour = 18,
        focusMinutes = 25,
        events = events,
        openTasks = tasks,
    )

    @Test
    fun `die Anweisung verlangt reines JSON`() {
        val system = WeekPlanPrompt.systemPrompt()
        assertTrue(system.contains("ausschliesslich mit einem JSON-Objekt"))
        assertTrue(system.contains("\"weekday\""))
        assertTrue(system.contains("1 = Montag"))
    }

    @Test
    fun `Woche, Arbeitszeit und Ziele stehen in der Anfrage`() {
        val prompt = WeekPlanPrompt.userPrompt(context())
        assertTrue(prompt.contains("KW 39"))
        assertTrue(prompt.contains("21.09."))
        assertTrue(prompt.contains("8:00 bis 18:00"))
        assertTrue(prompt.contains("Steuererklaerung fertig machen"))
    }

    @Test
    fun `vorhandene Termine werden als Randbedingung genannt`() {
        val prompt = WeekPlanPrompt.userPrompt(
            context(
                events = listOf(
                    ContextEvent("Teambesprechung", 1, LocalTime.of(9, 0), LocalTime.of(10, 0), allDay = false),
                    ContextEvent("Feiertag", 3, null, null, allDay = true),
                ),
            )
        )
        assertTrue(prompt.contains("Montag, 09:00-10:00: Teambesprechung"))
        assertTrue(prompt.contains("Mittwoch, ganztaegig: Feiertag"))
    }

    @Test
    fun `offene Aufgaben werden genannt, damit sie nicht doppelt kommen`() {
        val prompt = WeekPlanPrompt.userPrompt(
            context(tasks = listOf(ContextTask("Rechnung schreiben", weekday = null, plannedPomodoros = 2)))
        )
        assertTrue(prompt.contains("Rechnung schreiben"))
        assertTrue(prompt.contains("ohne festen Tag"))
    }

    @Test
    fun `ohne Termine und Aufgaben steht das ausdruecklich da`() {
        val prompt = WeekPlanPrompt.userPrompt(context())
        assertTrue(prompt.contains("Bereits eingetragene Termine:\n- keine"))
        assertTrue(prompt.contains("Bereits vorhandene offene Aufgaben:\n- keine"))
    }

    @Test
    fun `ohne Ziele wird die KI trotzdem um einen Vorschlag gebeten`() {
        val prompt = WeekPlanPrompt.userPrompt(context(goals = "   "))
        assertTrue(prompt.contains("schlage etwas Sinnvolles vor"))
    }
}
