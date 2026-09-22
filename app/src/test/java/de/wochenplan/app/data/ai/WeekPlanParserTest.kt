package de.wochenplan.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class WeekPlanParserTest {

    @Test
    fun `sauberes JSON wird gelesen`() {
        val proposal = WeekPlanParser.parse(
            """
            {
              "summary": "Machbare Woche.",
              "entries": [
                {"title": "Steuer", "kind": "task", "weekday": 2, "plannedPomodoros": 3, "reason": "Dienstag ist frei"},
                {"title": "Sport", "kind": "event", "weekday": 4, "startTime": "18:00", "endTime": "19:00", "reason": "abends"}
              ],
              "warnings": ["Freitag ist knapp"]
            }
            """.trimIndent()
        )

        assertEquals("Machbare Woche.", proposal.summary)
        assertEquals(2, proposal.entries.size)
        assertEquals(listOf("Freitag ist knapp"), proposal.warnings)

        val task = proposal.entries[0]
        assertEquals(PlanEntryKind.TASK, task.kind)
        assertEquals(2, task.weekday)
        assertEquals(3, task.plannedPomodoros)

        val event = proposal.entries[1]
        assertEquals(PlanEntryKind.EVENT, event.kind)
        assertEquals(LocalTime.of(18, 0), event.start)
        assertEquals(LocalTime.of(19, 0), event.end)
    }

    @Test
    fun `JSON in einem Codeblock wird herausgeschnitten`() {
        val proposal = WeekPlanParser.parse(
            """
            Gern, hier ist mein Vorschlag:

            ```json
            {"summary": "Los geht es.", "entries": [{"title": "Aufraeumen", "weekday": 1}]}
            ```

            Viel Erfolg!
            """.trimIndent()
        )
        assertEquals("Los geht es.", proposal.summary)
        assertEquals("Aufraeumen", proposal.entries.single().title)
    }

    @Test
    fun `geschweifte Klammern in Texten verwirren das Ausschneiden nicht`() {
        val text = """{"summary": "Nicht }{ verwirren lassen", "entries": []}"""
        assertEquals(text, WeekPlanParser.extractJsonObject("Antwort: $text Ende"))
    }

    @Test
    fun `verschachtelte Objekte werden vollstaendig erfasst`() {
        val text = """{"a": {"b": {"c": 1}}, "d": 2}"""
        assertEquals(text, WeekPlanParser.extractJsonObject(text))
    }

    @Test
    fun `maskierte Anfuehrungszeichen beenden die Zeichenkette nicht`() {
        val text = """{"summary": "sagte \"hallo\" und }", "entries": []}"""
        assertEquals(text, WeekPlanParser.extractJsonObject(text))
    }

    @Test
    fun `unvollstaendiges JSON liefert nichts`() {
        assertNull(WeekPlanParser.extractJsonObject("""{"summary": "abgeschnitten"""))
        assertNull(WeekPlanParser.extractJsonObject("gar kein JSON"))
    }

    @Test
    fun `Termin ohne vollstaendige Zeit wird zur Aufgabe`() {
        val proposal = WeekPlanParser.parse(
            """
            {"summary": "x", "entries": [
              {"title": "Ohne Ende", "kind": "event", "weekday": 3, "startTime": "09:00"},
              {"title": "Ende vor Anfang", "kind": "event", "weekday": 3, "startTime": "10:00", "endTime": "09:00"},
              {"title": "Ohne Tag", "kind": "event", "startTime": "10:00", "endTime": "11:00"}
            ]}
            """.trimIndent()
        )
        assertEquals(3, proposal.entries.size)
        assertTrue(proposal.entries.all { it.kind == PlanEntryKind.TASK })
        assertTrue(proposal.entries.all { it.start == null && it.end == null })
    }

    @Test
    fun `ungueltige Wochentage werden zu Aufgaben ohne festen Tag`() {
        val proposal = WeekPlanParser.parse(
            """{"summary": "x", "entries": [{"title": "Irgendwann", "weekday": 9}]}"""
        )
        assertNull(proposal.entries.single().weekday)
    }

    @Test
    fun `leere Titel fallen weg und Pomodoros werden begrenzt`() {
        val proposal = WeekPlanParser.parse(
            """
            {"summary": "x", "entries": [
              {"title": "   ", "weekday": 1},
              {"title": "  Echt  ", "weekday": 1, "plannedPomodoros": 99}
            ]}
            """.trimIndent()
        )
        assertEquals(1, proposal.entries.size)
        assertEquals("Echt", proposal.entries[0].title)
        assertEquals(16, proposal.entries[0].plannedPomodoros)
    }

    @Test
    fun `unbekannte Felder stoeren nicht`() {
        val proposal = WeekPlanParser.parse(
            """{"summary": "x", "entries": [{"title": "A", "weekday": 1, "farbe": "rot"}], "extra": 5}"""
        )
        assertEquals(1, proposal.entries.size)
    }

    @Test
    fun `Antwort ohne JSON meldet einen Fehler`() {
        val error = assertThrows(WeekPlanException::class.java) {
            WeekPlanParser.parse("Tut mir leid, das kann ich nicht.")
        }
        assertTrue(error.message!!.isNotEmpty())
    }

    @Test
    fun `Uhrzeiten werden nachsichtig gelesen`() {
        assertEquals(LocalTime.of(9, 0), WeekPlanParser.parseTime("9:00"))
        assertEquals(LocalTime.of(9, 5), WeekPlanParser.parseTime("09:05"))
        assertEquals(LocalTime.of(14, 30), WeekPlanParser.parseTime("14:30:00"))
        assertNull(WeekPlanParser.parseTime("25:00"))
        assertNull(WeekPlanParser.parseTime("morgens"))
        assertNull(WeekPlanParser.parseTime(null))
        assertNull(WeekPlanParser.parseTime(""))
    }

    @Test
    fun `Kennungen der Eintraege sind eindeutig`() {
        val proposal = WeekPlanParser.parse(
            """{"summary": "x", "entries": [{"title": "A"}, {"title": "A"}, {"title": "B"}]}"""
        )
        assertEquals(3, proposal.entries.map { it.id }.toSet().size)
    }
}
