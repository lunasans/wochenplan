package de.wochenplan.app.data.caldav

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Ein schlanker CalDAV-Client (RFC 4791) auf Basis von OkHttp.
 *
 * Deckt genau das ab, was der Wochenplan braucht: Kalender finden, Termine eines
 * Zeitraums laden sowie Termine anlegen, aendern und loeschen.
 */
class CalDavClient(
    private val account: CalDavAccount,
    private val httpClient: OkHttpClient = defaultHttpClient(),
) {

    private val authHeader = Credentials.basic(account.username, account.password)

    // --- Kalender finden -------------------------------------------------

    /**
     * Ermittelt die Kalender des Kontos.
     *
     * Angegeben werden kann sowohl die Server-Wurzel (dann wird ueber
     * `current-user-principal` und `calendar-home-set` gesucht) als auch direkt
     * die Adresse eines Kalenders.
     */
    suspend fun discoverCalendars(): List<RemoteCalendar> {
        val base = account.serverUrl.trim().toHttpUrlOrNull()
            ?: throw CalDavException("Die Server-Adresse ist ungueltig. Sie muss mit https:// beginnen.")

        val principal = findPrincipal(base)
        val homes = principal?.let { findCalendarHomes(it) }.orEmpty()
        val roots = homes.ifEmpty { listOf(base) }

        val calendars = roots.flatMap { listCalendars(it) }.distinctBy { it.url }
        if (calendars.isEmpty()) {
            throw CalDavException(
                "Unter dieser Adresse wurde kein Kalender gefunden. " +
                    "Bitte die Server-Adresse aus den Einstellungen des Anbieters verwenden."
            )
        }
        return calendars
    }

    private suspend fun findPrincipal(base: HttpUrl): HttpUrl? {
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:"><d:prop><d:current-user-principal/></d:prop></d:propfind>
        """.trimIndent()

        for (candidate in listOfNotNull(base, wellKnownUrl(base)).distinct()) {
            val responses = runCatching { propfind(candidate, depth = 0, body = body) }.getOrNull() ?: continue
            val href = responses.firstNotNullOfOrNull { response ->
                response.element(DavNs.DAV, "current-user-principal")
                    ?.childElements()
                    ?.firstOrNull { it.matches(DavNs.DAV, "href") }
                    ?.textContent
                    ?.trim()
            }
            if (!href.isNullOrEmpty()) return candidate.resolve(href)
        }
        return null
    }

    private suspend fun findCalendarHomes(principal: HttpUrl): List<HttpUrl> {
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
              <d:prop><c:calendar-home-set/></d:prop>
            </d:propfind>
        """.trimIndent()

        val responses = runCatching { propfind(principal, depth = 0, body = body) }.getOrNull().orEmpty()
        return responses.flatMap { response ->
            response.element(DavNs.CALDAV, "calendar-home-set")
                ?.childElements()
                ?.filter { it.matches(DavNs.DAV, "href") }
                ?.mapNotNull { principal.resolve(it.textContent.trim()) }
                .orEmpty()
        }
    }

    private suspend fun listCalendars(root: HttpUrl): List<RemoteCalendar> {
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav"
                        xmlns:cs="http://calendarserver.org/ns/" xmlns:ic="http://apple.com/ns/ical/">
              <d:prop>
                <d:resourcetype/>
                <d:displayname/>
                <d:current-user-privilege-set/>
                <cs:getctag/>
                <c:supported-calendar-component-set/>
                <ic:calendar-color/>
              </d:prop>
            </d:propfind>
        """.trimIndent()

        val responses = runCatching { propfind(root, depth = 1, body = body) }.getOrElse { error ->
            if (error is CalDavException) throw error else return emptyList()
        }

        return responses.mapNotNull { response ->
            if (!response.hasChild(DavNs.DAV, "resourcetype", DavNs.CALDAV, "calendar")) return@mapNotNull null
            if (!supportsEvents(response)) return@mapNotNull null

            val url = root.resolve(response.href) ?: return@mapNotNull null
            RemoteCalendar(
                url = url.toString(),
                displayName = response.text(DavNs.DAV, "displayname")
                    ?: url.pathSegments.lastOrNull { it.isNotEmpty() }
                    ?: "Kalender",
                color = normalizeColor(response.text(DavNs.APPLE, "calendar-color")),
                readOnly = !hasWritePrivilege(response),
                ctag = response.text(DavNs.CALENDARSERVER, "getctag"),
            )
        }
    }

    /** Kalender, die ausdruecklich keine Termine enthalten (z.B. reine Aufgabenlisten), fallen raus. */
    private fun supportsEvents(response: DavResponse): Boolean {
        val supported = response.element(DavNs.CALDAV, "supported-calendar-component-set") ?: return true
        val components = supported.childElements()
            .filter { it.matches(DavNs.CALDAV, "comp") }
            .map { it.getAttribute("name").orEmpty().uppercase() }
            .filter { it.isNotEmpty() }
        return components.isEmpty() || components.contains("VEVENT")
    }

    private fun hasWritePrivilege(response: DavResponse): Boolean {
        val privileges = response.element(DavNs.DAV, "current-user-privilege-set") ?: return true
        val names = privileges.childElements()
            .filter { it.matches(DavNs.DAV, "privilege") }
            .flatMap { it.childElements() }
            .map { (it.localName ?: it.nodeName).lowercase() }
        return names.isEmpty() || names.any { it == "write" || it == "write-content" || it == "all" }
    }

    // --- Termine laden ---------------------------------------------------

    /** Laedt alle Termine, die den Zeitraum [start] bis [end] beruehren. */
    suspend fun listEvents(calendarUrl: String, start: Instant, end: Instant): List<RemoteObject> {
        val url = calendarUrl.toHttpUrlOrNull()
            ?: throw CalDavException("Ungueltige Kalender-Adresse: $calendarUrl")

        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
              <d:prop>
                <d:getetag/>
                <c:calendar-data/>
              </d:prop>
              <c:filter>
                <c:comp-filter name="VCALENDAR">
                  <c:comp-filter name="VEVENT">
                    <c:time-range start="${formatUtc(start)}" end="${formatUtc(end)}"/>
                  </c:comp-filter>
                </c:comp-filter>
              </c:filter>
            </c:calendar-query>
        """.trimIndent()

        val request = newRequest(url)
            .method("REPORT", body.toRequestBody(XML_MEDIA_TYPE))
            .header("Depth", "1")
            .build()

        val xml = execute(request) { response -> response.body?.string().orEmpty() }
        return DavXml.parseMultiStatus(xml).mapNotNull { response ->
            val href = url.resolve(response.href)?.toString() ?: return@mapNotNull null
            val data = response.text(DavNs.CALDAV, "calendar-data")
            RemoteObject(
                href = href,
                etag = response.text(DavNs.DAV, "getetag")?.trim('"', ' '),
                data = data,
            )
        }.filter { !it.data.isNullOrBlank() }
    }

    /** Laedt ein einzelnes Objekt, z.B. vor dem Bearbeiten. */
    suspend fun fetchObject(href: String): RemoteObject {
        val url = href.toHttpUrlOrNull() ?: throw CalDavException("Ungueltige Termin-Adresse.")
        val request = newRequest(url).get().build()
        return execute(request) { response ->
            RemoteObject(
                href = href,
                etag = response.header("ETag")?.trim('"', ' '),
                data = response.body?.string(),
            )
        }
    }

    // --- Termine schreiben -----------------------------------------------

    /** Legt ein neues Objekt an und liefert dessen Adresse und ETag. */
    suspend fun createEvent(calendarUrl: String, fileName: String, ics: String): RemoteObject {
        val base = calendarUrl.toHttpUrlOrNull()
            ?: throw CalDavException("Ungueltige Kalender-Adresse.")
        val target = base.newBuilder().addPathSegment(fileName).build()

        val request = newRequest(target)
            .put(ics.toRequestBody(CALENDAR_MEDIA_TYPE))
            .header("If-None-Match", "*")
            .build()

        return execute(request) { response ->
            RemoteObject(target.toString(), response.header("ETag")?.trim('"', ' '), null)
        }
    }

    /** Ueberschreibt ein Objekt. [etag] schuetzt vor dem Ueberschreiben fremder Aenderungen. */
    suspend fun updateEvent(href: String, ics: String, etag: String?): RemoteObject {
        val url = href.toHttpUrlOrNull() ?: throw CalDavException("Ungueltige Termin-Adresse.")
        val builder = newRequest(url).put(ics.toRequestBody(CALENDAR_MEDIA_TYPE))
        if (!etag.isNullOrBlank()) builder.header("If-Match", "\"$etag\"")

        return execute(builder.build()) { response ->
            RemoteObject(href, response.header("ETag")?.trim('"', ' '), null)
        }
    }

    suspend fun deleteEvent(href: String, etag: String?) {
        val url = href.toHttpUrlOrNull() ?: throw CalDavException("Ungueltige Termin-Adresse.")
        val builder = newRequest(url).delete()
        if (!etag.isNullOrBlank()) builder.header("If-Match", "\"$etag\"")
        execute(builder.build()) { }
    }

    // --- HTTP-Grundlagen -------------------------------------------------

    private suspend fun propfind(url: HttpUrl, depth: Int, body: String): List<DavResponse> {
        val request = newRequest(url)
            .method("PROPFIND", body.toRequestBody(XML_MEDIA_TYPE))
            .header("Depth", depth.toString())
            .build()
        val xml = execute(request) { response -> response.body?.string().orEmpty() }
        return DavXml.parseMultiStatus(xml)
    }

    private fun newRequest(url: HttpUrl): Request.Builder = Request.Builder()
        .url(url)
        .header("Authorization", authHeader)
        .header("User-Agent", USER_AGENT)

    private suspend fun <T> execute(request: Request, handler: (Response) -> T): T =
        withContext(Dispatchers.IO) {
            try {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw toException(response)
                    handler(response)
                }
            } catch (error: IOException) {
                throw CalDavException(
                    "Der Server ist nicht erreichbar. Bitte Internetverbindung und Adresse pruefen.",
                    cause = error,
                )
            }
        }

    private fun toException(response: Response): CalDavException {
        val message = when (response.code) {
            401 -> "Anmeldung fehlgeschlagen. Benutzername oder Passwort stimmen nicht."
            403 -> "Der Server verweigert den Zugriff auf diesen Kalender."
            404 -> "Die angegebene Adresse wurde auf dem Server nicht gefunden."
            409 -> "Der Server hat die Anfrage abgelehnt (Konflikt)."
            412 -> "Der Termin wurde zwischenzeitlich geaendert. Bitte neu laden."
            507 -> "Auf dem Server ist kein Speicherplatz mehr frei."
            else -> "Der Server antwortete mit Fehler ${response.code}."
        }
        return CalDavException(message, response.code)
    }

    private fun wellKnownUrl(base: HttpUrl): HttpUrl? =
        base.newBuilder().encodedPath("/.well-known/caldav").build()

    companion object {
        private const val USER_AGENT = "Wochenplan/1.0 (Android)"
        private val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()
        private val CALENDAR_MEDIA_TYPE = "text/calendar; charset=utf-8".toMediaType()
        private val UTC_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT).withZone(ZoneOffset.UTC)

        fun formatUtc(instant: Instant): String = UTC_FORMAT.format(instant)

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        /** Wandelt `#RRGGBBAA` (Apple) in `#RRGGBB` um. */
        fun normalizeColor(raw: String?): String? {
            val value = raw?.trim()?.takeIf { it.startsWith("#") } ?: return null
            return when (value.length) {
                9 -> value.substring(0, 7)
                7 -> value
                else -> null
            }
        }
    }
}
