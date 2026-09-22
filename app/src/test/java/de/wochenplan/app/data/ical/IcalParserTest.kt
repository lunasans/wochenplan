package de.wochenplan.app.data.ical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IcalParserTest {

    @Test
    fun `gefaltete Zeilen werden wieder zusammengefuegt`() {
        val text = "BEGIN:VCALENDAR\r\nSUMMARY:Ein sehr langer\r\n  Titel\r\nEND:VCALENDAR\r\n"
        val calendar = IcalParser.parse(text)
        assertNotNull(calendar)
        assertEquals("Ein sehr langer Titel", calendar!!.textValue("SUMMARY"))
    }

    @Test
    fun `Parameter und Werte werden getrennt`() {
        val property = IcalParser.parseLine("DTSTART;TZID=Europe/Berlin:20260922T090000")
        assertNotNull(property)
        assertEquals("DTSTART", property!!.name)
        assertEquals("Europe/Berlin", property.param("TZID"))
        assertEquals("20260922T090000", property.value)
    }

    @Test
    fun `Doppelpunkte in Anfuehrungszeichen trennen nicht`() {
        val property = IcalParser.parseLine("ATTENDEE;CN=\"Meier, Hans: Chef\":mailto:hans@example.com")
        assertNotNull(property)
        assertEquals("ATTENDEE", property!!.name)
        assertEquals("Meier, Hans: Chef", property.param("CN"))
        assertEquals("mailto:hans@example.com", property.value)
    }

    @Test
    fun `Escaping von Text funktioniert in beide Richtungen`() {
        val original = "Zeile 1\nHalbsatz; Komma, Backslash \\ Ende"
        val escaped = IcalText.escape(original)
        assertTrue(escaped.contains("\\n"))
        assertTrue(escaped.contains("\\;"))
        assertEquals(original, IcalText.unescape(escaped))
    }

    @Test
    fun `unbekannte Eigenschaften bleiben beim Serialisieren erhalten`() {
        val text = buildString {
            append("BEGIN:VCALENDAR\r\n")
            append("VERSION:2.0\r\n")
            append("BEGIN:VEVENT\r\n")
            append("UID:abc\r\n")
            append("SUMMARY:Besprechung\r\n")
            append("X-EIGENES-FELD:Wert\r\n")
            append("BEGIN:VALARM\r\n")
            append("ACTION:DISPLAY\r\n")
            append("TRIGGER:-PT15M\r\n")
            append("END:VALARM\r\n")
            append("END:VEVENT\r\n")
            append("END:VCALENDAR\r\n")
        }
        val serialized = IcalParser.parse(text)!!.serialize()
        assertTrue(serialized.contains("X-EIGENES-FELD:Wert"))
        assertTrue(serialized.contains("BEGIN:VALARM"))
        assertTrue(serialized.contains("TRIGGER:-PT15M"))
    }

    @Test
    fun `lange Zeilen werden bei 75 Oktetten umgebrochen`() {
        val component = IcalComponent("VEVENT")
        component.setTextProperty("SUMMARY", "A".repeat(200))
        val lines = component.serialize().split("\r\n")
        assertTrue(lines.all { it.toByteArray(Charsets.UTF_8).size <= 75 })
        // Nach dem Einlesen muss wieder der ganze Text da sein.
        val reparsed = IcalParser.parse("BEGIN:VCALENDAR\r\n" + component.serialize() + "END:VCALENDAR\r\n")
        val event = reparsed!!.components("VEVENT").first()
        assertEquals("A".repeat(200), event.textValue("SUMMARY"))
    }

    @Test
    fun `Dauer wird gelesen`() {
        assertEquals(90L, VEventMapper.parseDuration("PT1H30M")?.toMinutes())
        assertEquals(1440L, VEventMapper.parseDuration("P1D")?.toMinutes())
        assertEquals(-15L, VEventMapper.parseDuration("-PT15M")?.toMinutes())
    }
}
