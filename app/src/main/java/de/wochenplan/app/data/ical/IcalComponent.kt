package de.wochenplan.app.data.ical

/**
 * Eine Eigenschaft einer iCalendar-Komponente, z.B. `DTSTART;TZID=Europe/Berlin:20260922T090000`.
 *
 * [value] ist der rohe, noch escapte Wert aus der Datei. Fuer TEXT-Werte gibt es
 * [text] zum Lesen und Schreiben mit korrektem Escaping.
 */
data class IcalProperty(
    val name: String,
    val params: MutableMap<String, String> = linkedMapOf(),
    var value: String = "",
) {
    var text: String
        get() = IcalText.unescape(value)
        set(newValue) {
            value = IcalText.escape(newValue)
        }

    fun param(key: String): String? = params[key.uppercase()]
}

/**
 * Eine iCalendar-Komponente (VCALENDAR, VEVENT, VTIMEZONE, ...).
 *
 * Der Baum ist verlustfrei: Beim Einlesen bleiben auch Eigenschaften erhalten, die
 * diese App nicht kennt (Teilnehmer, Erinnerungen, X-Properties). Beim Speichern
 * werden nur die tatsaechlich geaenderten Eigenschaften angefasst, damit Daten
 * anderer Kalender-Clients nicht verloren gehen.
 */
class IcalComponent(val name: String) {

    val properties: MutableList<IcalProperty> = mutableListOf()
    val components: MutableList<IcalComponent> = mutableListOf()

    fun findProperty(propertyName: String): IcalProperty? =
        properties.firstOrNull { it.name.equals(propertyName, ignoreCase = true) }

    fun findProperties(propertyName: String): List<IcalProperty> =
        properties.filter { it.name.equals(propertyName, ignoreCase = true) }

    fun value(propertyName: String): String? = findProperty(propertyName)?.value

    fun textValue(propertyName: String): String? = findProperty(propertyName)?.text

    fun components(componentName: String): List<IcalComponent> =
        components.filter { it.name.equals(componentName, ignoreCase = true) }

    fun removeProperty(propertyName: String) {
        properties.removeAll { it.name.equals(propertyName, ignoreCase = true) }
    }

    /** Setzt eine Eigenschaft und ersetzt dabei alle bisherigen gleichen Namens. */
    fun setProperty(
        propertyName: String,
        value: String,
        params: Map<String, String> = emptyMap(),
    ): IcalProperty {
        removeProperty(propertyName)
        val property = IcalProperty(propertyName.uppercase(), LinkedHashMap(params), value)
        properties.add(property)
        return property
    }

    /** Setzt eine TEXT-Eigenschaft (mit Escaping) oder entfernt sie bei `null`/leer. */
    fun setTextProperty(propertyName: String, value: String?) {
        removeProperty(propertyName)
        if (value.isNullOrBlank()) return
        properties.add(IcalProperty(propertyName.uppercase(), linkedMapOf(), IcalText.escape(value)))
    }

    fun addProperty(property: IcalProperty) {
        properties.add(property)
    }

    fun serialize(): String = buildString { writeTo(this) }

    private fun writeTo(builder: StringBuilder) {
        builder.append(fold("BEGIN:$name"))
        for (property in properties) {
            builder.append(fold(property.render()))
        }
        for (child in components) {
            child.writeTo(builder)
        }
        builder.append(fold("END:$name"))
    }

    companion object {
        /** Zeilen werden nach RFC 5545 bei 75 Oktetten umgebrochen. */
        internal fun fold(line: String): String {
            val bytes = line.toByteArray(Charsets.UTF_8)
            if (bytes.size <= MAX_LINE_OCTETS) return line + CRLF

            val result = StringBuilder()
            var octets = 0
            var limit = MAX_LINE_OCTETS
            var index = 0
            while (index < line.length) {
                val codePoint = line.codePointAt(index)
                val charCount = Character.charCount(codePoint)
                val size = String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8).size
                if (octets + size > limit) {
                    result.append(CRLF).append(' ')
                    octets = 1
                    limit = MAX_LINE_OCTETS
                }
                result.append(line, index, index + charCount)
                octets += size
                index += charCount
            }
            return result.append(CRLF).toString()
        }

        private const val MAX_LINE_OCTETS = 75
        internal const val CRLF = "\r\n"
    }
}

private fun IcalProperty.render(): String = buildString {
    append(name.uppercase())
    for ((key, paramValue) in params) {
        append(';').append(key.uppercase()).append('=')
        val needsQuotes = paramValue.any { it == ':' || it == ';' || it == ',' }
        if (needsQuotes) append('"').append(paramValue.replace("\"", "")).append('"') else append(paramValue)
    }
    append(':').append(value)
}

/** Escaping von TEXT-Werten nach RFC 5545 Abschnitt 3.3.11. */
object IcalText {

    fun escape(value: String): String = buildString(value.length) {
        for (char in value) {
            when (char) {
                '\\' -> append("\\\\")
                ';' -> append("\;")
                ',' -> append("\\,")
                '\n' -> append("\\n")
                '\r' -> Unit
                else -> append(char)
            }
        }
    }

    fun unescape(value: String): String {
        if (!value.contains('\\')) return value
        val result = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '\\' && index + 1 < value.length) {
                when (val next = value[index + 1]) {
                    'n', 'N' -> result.append('\n')
                    '\\' -> result.append('\\')
                    ';' -> result.append(';')
                    ',' -> result.append(',')
                    else -> result.append(next)
                }
                index += 2
            } else {
                result.append(char)
                index++
            }
        }
        return result.toString()
    }
}
