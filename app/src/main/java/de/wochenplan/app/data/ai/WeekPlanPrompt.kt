package de.wochenplan.app.data.ai

import de.wochenplan.app.ui.common.Formatters
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Baut die Anfrage an die KI und liest ihre Antwort.
 *
 * Bewusst ohne Android- und ohne Netzwerkbezug: So laesst sich genau der Teil
 * pruefen, der bei einer KI-Anbindung erfahrungsgemaess schiefgeht – was
 * hineingeht und wie das Ergebnis ausgewertet wird.
 */
object WeekPlanPrompt {

    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    /** Die Rolle und die Regeln, an die sich die Antwort halten muss. */
    fun systemPrompt(): String = """
        Du hilfst beim Planen einer Arbeitswoche. Der Benutzer nennt dir seine Ziele;
        du verteilst sie sinnvoll auf die Tage der Kalenderwoche.

        Halte dich an diese Regeln:
        - Beruecksichtige die bereits eingetragenen Termine. Plane nichts in belegte Zeiten.
        - Bleibe innerhalb der angegebenen Arbeitszeiten.
        - Verplane den Tag nicht vollstaendig. Lass Puffer zwischen den Bloecken.
        - Schaetze den Aufwand ehrlich. Lieber weniger vornehmen als eine Woche,
          die nicht zu schaffen ist.
        - Bereits vorhandene offene Aufgaben nimmst du nicht erneut auf; du darfst
          ihnen aber einen Tag zuordnen, wenn sie noch keinen haben.
        - Wenn die Ziele zu umfangreich fuer die Woche sind, sag das in "warnings".

        Antworte ausschliesslich mit einem JSON-Objekt in genau dieser Form,
        ohne Text davor oder danach und ohne Markdown-Codeblock:

        {
          "summary": "ein bis zwei Saetze zur Einschaetzung der Woche",
          "entries": [
            {
              "title": "kurzer Titel",
              "kind": "task oder event",
              "weekday": 1,
              "startTime": "09:00",
              "endTime": "10:30",
              "plannedPomodoros": 2,
              "reason": "knappe Begruendung fuer diesen Tag und diese Zeit"
            }
          ],
          "warnings": ["was nicht aufgeht"]
        }

        Zu den Feldern:
        - "kind": "task" fuer etwas, das irgendwann am Tag erledigt wird,
          "event" fuer einen festen Block mit Uhrzeit.
        - "weekday": 1 = Montag bis 7 = Sonntag.
        - "startTime"/"endTime": nur bei "event", im Format HH:MM.
        - "plannedPomodoros": nur bei "task", geschaetzte Anzahl Fokusabschnitte.
        - Alle Texte auf Deutsch.
    """.trimIndent()

    /** Der Zustand der Woche und die Ziele des Benutzers. */
    fun userPrompt(context: WeekPlanContext): String = buildString {
        appendLine("Kalenderwoche: ${context.week.label} (${context.week.rangeLabel()})")
        appendLine("Arbeitszeit: ${context.dayStartHour}:00 bis ${context.dayEndHour}:00 Uhr")
        appendLine("Laenge eines Fokusabschnitts: ${context.focusMinutes} Minuten")
        appendLine()

        appendLine("Bereits eingetragene Termine:")
        if (context.events.isEmpty()) {
            appendLine("- keine")
        } else {
            for (event in context.events.sortedWith(compareBy({ it.weekday }, { it.start }))) {
                val day = Formatters.weekdayName(event.weekday)
                val time = when {
                    event.allDay -> "ganztaegig"
                    event.start != null && event.end != null ->
                        "${TIME.format(event.start)}-${TIME.format(event.end)}"
                    else -> "ohne Uhrzeit"
                }
                appendLine("- $day, $time: ${event.title}")
            }
        }
        appendLine()

        appendLine("Bereits vorhandene offene Aufgaben:")
        if (context.openTasks.isEmpty()) {
            appendLine("- keine")
        } else {
            for (task in context.openTasks) {
                val day = task.weekday?.let { Formatters.weekdayName(it) } ?: "ohne festen Tag"
                appendLine("- ${task.title} ($day, ${task.plannedPomodoros} Pomodoros geplant)")
            }
        }
        appendLine()

        appendLine("Das nehme ich mir fuer die Woche vor:")
        appendLine(context.goals.trim().ifEmpty { "(nichts angegeben - schlage etwas Sinnvolles vor)" })
    }
}
