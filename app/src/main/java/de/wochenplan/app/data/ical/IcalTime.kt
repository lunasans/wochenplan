package de.wochenplan.app.data.ical

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Ein Zeitpunkt in iCalendar. Entweder ein reines Datum (ganztaegige Termine)
 * oder ein Zeitpunkt mit Zeitzone.
 */
sealed class IcalTime {

    data class AllDay(val date: LocalDate) : IcalTime()

    data class Timed(val value: ZonedDateTime) : IcalTime()

    val isAllDay: Boolean get() = this is AllDay

    fun toInstant(zone: ZoneId): Instant = when (this) {
        is AllDay -> date.atStartOfDay(zone).toInstant()
        is Timed -> value.toInstant()
    }

    fun toLocalDate(zone: ZoneId): LocalDate = when (this) {
        is AllDay -> date
        is Timed -> value.withZoneSameInstant(zone).toLocalDate()
    }

    fun toLocalDateTime(zone: ZoneId): LocalDateTime = when (this) {
        is AllDay -> date.atStartOfDay()
        is Timed -> value.withZoneSameInstant(zone).toLocalDateTime()
    }

    /** Der iCalendar-Wert, z.B. `20260922` oder `20260922T090000`. */
    fun render(): String = when (this) {
        is AllDay -> DATE_FORMAT.format(date)
        is Timed -> if (value.zone == ZoneOffset.UTC || value.zone.id == "Z" || value.zone.id == "UTC") {
            UTC_FORMAT.format(value.withZoneSameInstant(ZoneOffset.UTC))
        } else {
            LOCAL_FORMAT.format(value)
        }
    }

    /** Die Parameter, die zu [render] gehoeren (VALUE=DATE bzw. TZID). */
    fun renderParams(): Map<String, String> = when (this) {
        is AllDay -> mapOf("VALUE" to "DATE")
        is Timed -> {
            val zoneId = value.zone.id
            if (value.zone == ZoneOffset.UTC || zoneId == "Z" || zoneId == "UTC") {
                emptyMap()
            } else {
                mapOf("TZID" to zoneId)
            }
        }
    }

    companion object {
        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
        private val LOCAL_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.ROOT)
        private val UTC_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT)

        fun allDay(date: LocalDate): IcalTime = AllDay(date)

        fun timed(value: ZonedDateTime): IcalTime = Timed(value)

        fun utc(instant: Instant): IcalTime = Timed(instant.atZone(ZoneOffset.UTC))

        /**
         * Liest einen Zeitwert aus einer Eigenschaft.
         *
         * @param defaultZone Zeitzone fuer "floating" Werte ohne TZID und ohne `Z`.
         */
        fun parse(property: IcalProperty, defaultZone: ZoneId): IcalTime? =
            parse(property.value, property.param("TZID"), property.param("VALUE"), defaultZone)

        fun parse(
            rawValue: String,
            tzid: String?,
            valueType: String?,
            defaultZone: ZoneId,
        ): IcalTime? {
            val value = rawValue.trim()
            if (value.isEmpty()) return null

            val isDate = valueType.equals("DATE", ignoreCase = true) ||
                (value.length == 8 && !value.contains('T'))
            if (isDate) {
                return runCatching { AllDay(LocalDate.parse(value, DATE_FORMAT)) }.getOrNull()
            }

            val isUtc = value.endsWith("Z")
            val plain = if (isUtc) value.dropLast(1) else value
            val local = try {
                LocalDateTime.parse(plain, LOCAL_FORMAT)
            } catch (error: DateTimeParseException) {
                return null
            }

            val zone = when {
                isUtc -> ZoneOffset.UTC
                tzid != null -> resolveZone(tzid) ?: defaultZone
                else -> defaultZone
            }
            return Timed(local.atZone(zone))
        }

        /** Eine Liste von Zeitwerten, z.B. bei `EXDATE:20260922,20260929`. */
        fun parseList(property: IcalProperty, defaultZone: ZoneId): List<IcalTime> =
            property.value.split(',')
                .mapNotNull { parse(it, property.param("TZID"), property.param("VALUE"), defaultZone) }

        /**
         * Loest eine TZID in eine [ZoneId] auf. Neben den ueblichen Olson-Namen
         * werden die haeufigsten Windows-Bezeichnungen unterstuetzt, die manche
         * Exchange-Server ausliefern.
         */
        fun resolveZone(tzid: String): ZoneId? {
            val cleaned = tzid.trim().removeSurrounding("\"")
            zoneOrNull(cleaned)?.let { return it }
            // Praefixe wie `/freeassociation.sourceforge.net/Europe/Berlin` abschneiden.
            val tail = cleaned.trimStart('/').substringAfter('/', "")
            if (tail.isNotEmpty()) zoneOrNull(tail)?.let { return it }
            return WINDOWS_ZONES[cleaned.lowercase()]?.let { zoneOrNull(it) }
        }

        private fun zoneOrNull(id: String): ZoneId? = runCatching { ZoneId.of(id) }.getOrNull()

        private val WINDOWS_ZONES: Map<String, String> = mapOf(
            "w. europe standard time" to "Europe/Berlin",
            "central europe standard time" to "Europe/Budapest",
            "central european standard time" to "Europe/Warsaw",
            "romance standard time" to "Europe/Paris",
            "gmt standard time" to "Europe/London",
            "utc" to "UTC",
            "eastern standard time" to "America/New_York",
            "pacific standard time" to "America/Los_Angeles",
        )
    }
}

/** Erzeugt [LocalTime] aus einem Zeitpunkt, fuer ganztaegige Termine Mitternacht. */
fun IcalTime.localTimeOrMidnight(zone: ZoneId): LocalTime = when (this) {
    is IcalTime.AllDay -> LocalTime.MIDNIGHT
    is IcalTime.Timed -> value.withZoneSameInstant(zone).toLocalTime()
}
