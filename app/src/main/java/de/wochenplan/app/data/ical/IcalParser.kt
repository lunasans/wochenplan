package de.wochenplan.app.data.ical

/**
 * Minimaler, aber verlustfreier Parser fuer iCalendar-Daten (RFC 5545).
 *
 * Bewusst handgeschrieben: Die verfuegbaren Java-Bibliotheken sind fuer Android
 * entweder zu schwergewichtig oder ziehen Abhaengigkeiten nach, die auf alten
 * Geraeten Probleme machen. Benoetigt wird ohnehin nur die Teilmenge fuer VEVENT.
 */
object IcalParser {

    /** Liest ein komplettes iCalendar-Objekt. Gibt die VCALENDAR-Komponente zurueck. */
    fun parse(text: String): IcalComponent? {
        val stack = ArrayDeque<IcalComponent>()
        var root: IcalComponent? = null

        for (line in unfold(text)) {
            val property = parseLine(line) ?: continue
            when {
                property.name.equals("BEGIN", ignoreCase = true) -> {
                    val component = IcalComponent(property.value.uppercase())
                    stack.lastOrNull()?.components?.add(component)
                    if (root == null) root = component
                    stack.addLast(component)
                }

                property.name.equals("END", ignoreCase = true) -> {
                    stack.removeLastOrNull()
                }

                else -> stack.lastOrNull()?.properties?.add(property)
            }
        }
        return root
    }

    /**
     * Entfernt das Zeilen-Folding: Eine Fortsetzungszeile beginnt mit Leerzeichen
     * oder Tabulator und gehoert zur vorherigen Zeile.
     */
    fun unfold(text: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var started = false

        for (rawLine in text.split("\n")) {
            val line = rawLine.removeSuffix("\r")
            if (started && (line.startsWith(" ") || line.startsWith("\t"))) {
                current.append(line.substring(1))
                continue
            }
            if (started) result.add(current.toString())
            current.setLength(0)
            current.append(line)
            started = true
        }
        if (started) result.add(current.toString())
        return result.filter { it.isNotBlank() }
    }

    /** Zerlegt eine entfaltete Zeile in Name, Parameter und Wert. */
    fun parseLine(line: String): IcalProperty? {
        var inQuotes = false
        var colonIndex = -1
        for (index in line.indices) {
            when (line[index]) {
                '"' -> inQuotes = !inQuotes
                ':' -> if (!inQuotes) {
                    colonIndex = index
                }
            }
            if (colonIndex >= 0) break
        }
        if (colonIndex <= 0) return null

        val head = line.substring(0, colonIndex)
        val value = line.substring(colonIndex + 1)
        val parts = splitParams(head)
        if (parts.isEmpty()) return null

        // Ein Gruppen-Praefix (`group.DTSTART`) ist erlaubt, wird hier aber verworfen.
        val name = parts[0].substringAfterLast('.').trim().uppercase()
        if (name.isEmpty()) return null

        val params = linkedMapOf<String, String>()
        for (part in parts.drop(1)) {
            val equalsIndex = part.indexOf('=')
            if (equalsIndex <= 0) continue
            val key = part.substring(0, equalsIndex).trim().uppercase()
            val paramValue = part.substring(equalsIndex + 1).trim().removeSurrounding("\"")
            params[key] = paramValue
        }
        return IcalProperty(name, params, value)
    }

    /** Trennt an `;`, beachtet dabei Anfuehrungszeichen. */
    private fun splitParams(head: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (char in head) {
            when {
                char == '"' -> {
                    inQuotes = !inQuotes
                    current.append(char)
                }

                char == ';' && !inQuotes -> {
                    parts.add(current.toString())
                    current.setLength(0)
                }

                else -> current.append(char)
            }
        }
        parts.add(current.toString())
        return parts
    }
}
