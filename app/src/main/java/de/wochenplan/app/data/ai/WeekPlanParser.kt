package de.wochenplan.app.data.ai

import kotlinx.serialization.json.Json
import java.time.LocalTime

/**
 * Liest die Antwort der KI.
 *
 * Auch mit klarer Anweisung kommt gelegentlich ein Codeblock oder ein Satz vor
 * dem JSON zurueck. Deshalb wird das Objekt aus dem Text herausgeschnitten und
 * jedes Feld einzeln geprueft, statt der Antwort zu vertrauen.
 */
object WeekPlanParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun parse(text: String): WeekPlanProposal {
        val objectText = extractJsonObject(text)
            ?: throw WeekPlanException("Die Antwort der KI war kein verwertbarer Plan.")

        val dto = runCatching { json.decodeFromString(WeekPlanDto.serializer(), objectText) }
            .getOrElse { throw WeekPlanException("Die Antwort der KI liess sich nicht lesen.", it) }

        val entries = dto.entries.mapIndexedNotNull { index, entry -> entry.toEntry(index) }
        if (entries.isEmpty() && dto.summary.isBlank()) {
            throw WeekPlanException("Die KI hat keinen Plan vorgeschlagen.")
        }

        return WeekPlanProposal(
            summary = dto.summary.trim(),
            entries = entries,
            warnings = dto.warnings.map { it.trim() }.filter { it.isNotEmpty() },
        )
    }

    /**
     * Schneidet das erste vollstaendige JSON-Objekt aus [text].
     * Klammern in Zeichenketten zaehlen dabei nicht mit.
     */
    fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null

        var depth = 0
        var inString = false
        var escaped = false

        for (index in start until text.length) {
            val char = text[index]
            when {
                escaped -> escaped = false
                char == '\\' && inString -> escaped = true
                char == '"' -> inString = !inString
                inString -> Unit
                char == '{' -> depth++
                char == '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, index + 1)
                }
            }
        }
        return null
    }

    private fun WeekPlanEntryDto.toEntry(index: Int): WeekPlanEntry? {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return null

        val day = weekday.takeIf { it in 1..7 }
        val startTime = parseTime(startTime)
        val endTime = parseTime(endTime)

        // Ein Termin braucht Tag und Uhrzeit. Fehlt etwas, wird eine Aufgabe daraus,
        // statt den Vorschlag stillschweigend wegzuwerfen.
        val wantsEvent = kind.trim().equals("event", ignoreCase = true)
        val isEvent = wantsEvent && day != null && startTime != null && endTime != null &&
            endTime.isAfter(startTime)

        return WeekPlanEntry(
            id = "entry-$index",
            title = cleanTitle,
            kind = if (isEvent) PlanEntryKind.EVENT else PlanEntryKind.TASK,
            weekday = day,
            start = if (isEvent) startTime else null,
            end = if (isEvent) endTime else null,
            plannedPomodoros = plannedPomodoros.coerceIn(0, 16),
            reason = reason.trim(),
        )
    }

    /** Nimmt `9:00`, `09:00` und `09:00:00` entgegen. */
    fun parseTime(raw: String?): LocalTime? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val parts = value.split(':')
        if (parts.size < 2) return null
        val hour = parts[0].trim().toIntOrNull() ?: return null
        val minute = parts[1].trim().toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }
}
