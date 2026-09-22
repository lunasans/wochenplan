package de.wochenplan.app.data.repo

import de.wochenplan.app.core.IsoWeek
import de.wochenplan.app.data.caldav.CalDavAccount
import de.wochenplan.app.data.caldav.CalDavClient
import de.wochenplan.app.data.caldav.CalDavException
import de.wochenplan.app.data.db.CachedEventEntity
import de.wochenplan.app.data.db.CalendarDao
import de.wochenplan.app.data.db.CalendarEntity
import de.wochenplan.app.data.db.EventCacheDao
import de.wochenplan.app.data.ical.EventDraft
import de.wochenplan.app.data.ical.EventExpander
import de.wochenplan.app.data.ical.IcalParser
import de.wochenplan.app.data.ical.IcalWriter
import de.wochenplan.app.data.ical.VEventMapper
import de.wochenplan.app.data.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.time.ZoneId

/**
 * Bindeglied zwischen CalDAV-Server, lokalem Zwischenspeicher und Oberflaeche.
 *
 * Die Anzeige liest immer aus der Datenbank; der Server wird nur beim
 * Aktualisieren und beim Speichern angefasst. So bleibt die Woche auch ohne
 * Netz sichtbar.
 */
class CalendarRepository(
    private val settingsStore: SettingsStore,
    private val calendarDao: CalendarDao,
    private val eventCacheDao: EventCacheDao,
    private val httpClient: OkHttpClient = CalDavClient.defaultHttpClient(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    fun observeCalendars(): Flow<List<CalendarEntity>> = calendarDao.observeAll()

    /** Prueft die Zugangsdaten, speichert sie bei Erfolg und laedt die Kalenderliste. */
    suspend fun connect(serverUrl: String, username: String, password: String): Result<List<CalendarEntity>> =
        runCatching {
            val client = CalDavClient(CalDavAccount(serverUrl.trim(), username.trim(), password), httpClient)
            val remote = client.discoverCalendars()

            settingsStore.saveAccount(serverUrl, username, password)
            val entities = remote.mapIndexed { index, calendar ->
                CalendarEntity(
                    url = calendar.url,
                    displayName = calendar.displayName,
                    color = calendar.color,
                    readOnly = calendar.readOnly,
                    ctag = calendar.ctag,
                    sortIndex = index,
                )
            }
            calendarDao.replaceAll(entities)
            if (settingsStore.current().defaultCalendarUrl == null) {
                entities.firstOrNull { !it.readOnly }?.let { settingsStore.setDefaultCalendar(it.url) }
            }
            entities
        }

    /** Laedt die Kalenderliste neu, ohne die Zugangsdaten zu aendern. */
    suspend fun refreshCalendars(): Result<Unit> = runCatching {
        val client = requireClient()
        val remote = client.discoverCalendars()
        val entities = remote.mapIndexed { index, calendar ->
            CalendarEntity(
                url = calendar.url,
                displayName = calendar.displayName,
                color = calendar.color,
                readOnly = calendar.readOnly,
                ctag = calendar.ctag,
                sortIndex = index,
            )
        }
        calendarDao.replaceAll(entities)
        // Eine leere Liste wuerde "NOT IN ()" ergeben, was SQLite nicht kennt.
        if (entities.isNotEmpty()) eventCacheDao.deleteOrphans(entities.map { it.url })
    }

    suspend fun setCalendarVisible(url: String, visible: Boolean) {
        calendarDao.setVisible(url, visible)
    }

    suspend fun disconnect() {
        settingsStore.clearAccount()
        calendarDao.deleteAll()
        eventCacheDao.deleteAll()
    }

    /** Holt die Termine einer Kalenderwoche vom Server. */
    suspend fun syncWeek(week: IsoWeek): Result<Unit> = runCatching {
        val client = requireClient()
        val calendars = calendarDao.visibleCalendars()
        if (calendars.isEmpty()) {
            throw CalDavException("Es ist kein Kalender ausgewaehlt. Bitte in den Einstellungen einen Kalender aktivieren.")
        }

        val start = week.monday.atStartOfDay(zone).toInstant()
        val end = week.monday.plusDays(7).atStartOfDay(zone).toInstant()
        val now = System.currentTimeMillis()

        for (calendar in calendars) {
            val objects = client.listEvents(calendar.url, start, end)
            eventCacheDao.replaceWeek(
                weekKey = week.key,
                calendarUrl = calendar.url,
                events = objects.mapNotNull { remote ->
                    val data = remote.data ?: return@mapNotNull null
                    CachedEventEntity(
                        weekKey = week.key,
                        href = remote.href,
                        calendarUrl = calendar.url,
                        etag = remote.etag,
                        ics = data,
                        fetchedAt = now,
                    )
                },
            )
        }
        settingsStore.setLastSync(now)
    }

    /** Die Termine einer Woche aus dem Zwischenspeicher, bereits aufgeloest. */
    fun observeWeek(week: IsoWeek): Flow<List<PlannedEvent>> =
        combine(eventCacheDao.observeWeek(week.key), calendarDao.observeAll()) { cached, calendars ->
            val byUrl = calendars.associateBy { it.url }
            cached.filter { byUrl[it.calendarUrl]?.visible == true }
                .flatMap { entity ->
                    val calendar = byUrl.getValue(entity.calendarUrl)
                    toPlannedEvents(entity, calendar, week)
                }
                .sortedWith(compareBy({ !it.allDay }, { it.occurrence.startInstant }, { it.title }))
        }.flowOn(Dispatchers.Default)

    private fun toPlannedEvents(
        entity: CachedEventEntity,
        calendar: CalendarEntity,
        week: IsoWeek,
    ): List<PlannedEvent> {
        val calendarComponent = IcalParser.parse(entity.ics) ?: return emptyList()
        val events = VEventMapper.allEvents(calendarComponent, zone)
        if (events.isEmpty()) return emptyList()

        return EventExpander.occurrences(
            events = events,
            rangeStart = week.monday,
            rangeEndExclusive = week.monday.plusDays(7),
            zone = zone,
        ).map { occurrence ->
            PlannedEvent(
                occurrence = occurrence,
                calendarUrl = calendar.url,
                calendarName = calendar.displayName,
                colorHex = calendar.color,
                href = entity.href,
                etag = entity.etag,
                readOnly = calendar.readOnly,
            )
        }
    }

    /** Legt einen neuen Termin an. */
    suspend fun createEvent(calendarUrl: String, draft: EventDraft, week: IsoWeek): Result<Unit> = runCatching {
        val client = requireClient()
        client.createEvent(calendarUrl, fileNameFor(draft), IcalWriter.newEvent(draft))
        syncWeek(week).getOrThrow()
    }

    /**
     * Speichert Aenderungen an einem Termin.
     *
     * Bei einer Serie wird immer die ganze Serie geaendert; einzelne Termine
     * lassen sich streichen, aber nicht getrennt bearbeiten.
     */
    suspend fun updateEvent(
        href: String,
        sourceCalendarUrl: String,
        targetCalendarUrl: String,
        draft: EventDraft,
        week: IsoWeek,
    ): Result<Unit> = runCatching {
        val client = requireClient()
        val current = loadIcs(href)
        val updated = IcalWriter.updateEvent(current.ics, draft)
            ?: throw CalDavException("Der Termin konnte nicht gelesen werden.")

        if (targetCalendarUrl == sourceCalendarUrl) {
            client.updateEvent(href, updated, current.etag)
            // Der Termin kann in eine andere Woche gewandert sein; der alte
            // Eintrag im Zwischenspeicher waere dann falsch.
            eventCacheDao.deleteByHref(href)
        } else {
            // Ein Wechsel des Kalenders ist in CalDAV ein Neuanlegen plus Loeschen.
            client.createEvent(targetCalendarUrl, fileNameFor(draft), updated)
            client.deleteEvent(href, current.etag)
            eventCacheDao.deleteByHref(href)
        }
        syncWeek(week).getOrThrow()
    }

    /** Loescht einen Termin oder streicht einen einzelnen Termin aus einer Serie. */
    suspend fun deleteEvent(event: PlannedEvent, scope: DeleteScope, week: IsoWeek): Result<Unit> = runCatching {
        val client = requireClient()
        val current = loadIcs(event.href)
        val recurrenceId = event.occurrence.recurrenceId

        if (scope == DeleteScope.SINGLE_OCCURRENCE && recurrenceId != null) {
            val updated = IcalWriter.excludeOccurrence(current.ics, recurrenceId, zone)
                ?: throw CalDavException("Dieser Einzeltermin konnte nicht gestrichen werden.")
            client.updateEvent(event.href, updated, current.etag)
        } else {
            client.deleteEvent(event.href, current.etag)
            eventCacheDao.deleteByHref(event.href)
        }
        syncWeek(week).getOrThrow()
    }

    /** Laedt einen vorhandenen Termin zum Bearbeiten. */
    suspend fun loadForEditing(href: String): EditableEvent? = withContext(Dispatchers.IO) {
        val cached = eventCacheDao.byHref(href) ?: return@withContext null
        val calendar = calendarDao.byUrl(cached.calendarUrl)
        val component = IcalParser.parse(cached.ics) ?: return@withContext null
        val master = VEventMapper.allEvents(component, zone).firstOrNull { !it.isOverride }
            ?: return@withContext null

        val allDay = master.allDay
        val startLocal = master.start.toLocalDateTime(zone)
        val endLocal = master.end.toLocalDateTime(zone)

        EditableEvent(
            draft = EventDraft(
                uid = master.uid.ifBlank { "${java.util.UUID.randomUUID()}@wochenplan" },
                summary = master.summary,
                description = master.description,
                location = master.location,
                allDay = allDay,
                startDate = startLocal.toLocalDate(),
                startTime = startLocal.toLocalTime(),
                // DTEND ist exklusiv, in der Oberflaeche zaehlt der letzte Tag.
                endDate = if (allDay) endLocal.toLocalDate().minusDays(1) else endLocal.toLocalDate(),
                endTime = endLocal.toLocalTime(),
                recurrence = master.rrule,
                zone = zone,
            ),
            calendarUrl = cached.calendarUrl,
            readOnly = calendar?.readOnly ?: false,
            isSeries = master.isRecurring,
        )
    }

    /** Der Dateiname der .ics-Ressource wird aus der UID abgeleitet. */
    private fun fileNameFor(draft: EventDraft): String =
        draft.uid.substringBefore('@').replace(Regex("[^A-Za-z0-9._-]"), "-") + ".ics"

    /** Das aktuelle iCalendar-Objekt, bevorzugt aus dem Zwischenspeicher. */
    private suspend fun loadIcs(href: String): LoadedIcs = withContext(Dispatchers.IO) {
        eventCacheDao.byHref(href)?.let { return@withContext LoadedIcs(it.ics, it.etag) }
        val remote = requireClient().fetchObject(href)
        LoadedIcs(
            ics = remote.data ?: throw CalDavException("Der Termin ist auf dem Server nicht mehr vorhanden."),
            etag = remote.etag,
        )
    }

    private suspend fun requireClient(): CalDavClient {
        val settings = settingsStore.current()
        val password = settingsStore.password()
        if (!settings.isAccountConfigured || password.isNullOrEmpty()) {
            throw CalDavException("Es ist noch kein CalDAV-Konto eingerichtet.")
        }
        return CalDavClient(CalDavAccount(settings.serverUrl, settings.username, password), httpClient)
    }

    private data class LoadedIcs(val ics: String, val etag: String?)

    /** Ein Termin in der Form, die die Bearbeitung braucht. */
    data class EditableEvent(
        val draft: EventDraft,
        val calendarUrl: String,
        val readOnly: Boolean,
        val isSeries: Boolean,
    )
}
