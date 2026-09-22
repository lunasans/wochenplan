package de.wochenplan.app.data.caldav

/** Zugangsdaten zu einem CalDAV-Server. */
data class CalDavAccount(
    val serverUrl: String,
    val username: String,
    val password: String,
) {
    val isComplete: Boolean
        get() = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}

/** Ein Kalender auf dem Server. */
data class RemoteCalendar(
    val url: String,
    val displayName: String,
    val color: String?,
    val readOnly: Boolean,
    val ctag: String?,
)

/** Eine .ics-Ressource auf dem Server. */
data class RemoteObject(
    val href: String,
    val etag: String?,
    val data: String?,
)

/** Fehler bei der Kommunikation mit dem Server, mit Meldung fuer die Oberflaeche. */
class CalDavException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
