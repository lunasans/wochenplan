package de.wochenplan.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.wochenplan.app.core.SecretCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Alle Einstellungen der App. Das Passwort ist bewusst nicht enthalten. */
data class AppSettings(
    val serverUrl: String = "",
    val username: String = "",
    val hasPassword: Boolean = false,
    /** Erste angezeigte Stunde im Wochenraster. */
    val dayStartHour: Int = 7,
    /** Letzte angezeigte Stunde im Wochenraster. */
    val dayEndHour: Int = 21,
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val cyclesBeforeLongBreak: Int = 4,
    val autoStartNextPhase: Boolean = true,
    val vibrate: Boolean = true,
    val keepScreenOn: Boolean = false,
    val defaultCalendarUrl: String? = null,
    val lastSyncAt: Long = 0L,
    /** Ist ein Schluessel fuer die KI-Planung hinterlegt? */
    val hasAiKey: Boolean = false,
) {
    val isAccountConfigured: Boolean
        get() = serverUrl.isNotBlank() && username.isNotBlank() && hasPassword

    val visibleHours: Int get() = (dayEndHour - dayStartHour).coerceAtLeast(1)
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wochenplan_settings")

class SettingsStore(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        AppSettings(
            serverUrl = preferences[Keys.SERVER_URL].orEmpty(),
            username = preferences[Keys.USERNAME].orEmpty(),
            hasPassword = !preferences[Keys.PASSWORD].isNullOrBlank(),
            dayStartHour = preferences[Keys.DAY_START_HOUR] ?: 7,
            dayEndHour = preferences[Keys.DAY_END_HOUR] ?: 21,
            focusMinutes = preferences[Keys.FOCUS_MINUTES] ?: 25,
            shortBreakMinutes = preferences[Keys.SHORT_BREAK_MINUTES] ?: 5,
            longBreakMinutes = preferences[Keys.LONG_BREAK_MINUTES] ?: 15,
            cyclesBeforeLongBreak = preferences[Keys.CYCLES_BEFORE_LONG_BREAK] ?: 4,
            autoStartNextPhase = preferences[Keys.AUTO_START_NEXT] ?: true,
            vibrate = preferences[Keys.VIBRATE] ?: true,
            keepScreenOn = preferences[Keys.KEEP_SCREEN_ON] ?: false,
            defaultCalendarUrl = preferences[Keys.DEFAULT_CALENDAR]?.takeIf { it.isNotBlank() },
            lastSyncAt = preferences[Keys.LAST_SYNC_AT] ?: 0L,
            hasAiKey = !preferences[Keys.AI_API_KEY].isNullOrBlank(),
        )
    }

    suspend fun current(): AppSettings = settings.first()

    /** Das entschluesselte Passwort, oder `null`, wenn keines hinterlegt ist. */
    suspend fun password(): String? {
        val stored = context.dataStore.data.first()[Keys.PASSWORD] ?: return null
        return SecretCipher.decrypt(stored)
    }

    /** Der entschluesselte Schluessel fuer die KI-Planung, oder `null`. */
    suspend fun aiApiKey(): String? {
        val stored = context.dataStore.data.first()[Keys.AI_API_KEY] ?: return null
        return SecretCipher.decrypt(stored)
    }

    suspend fun saveAiApiKey(key: String) {
        context.dataStore.edit { preferences ->
            val trimmed = key.trim()
            if (trimmed.isEmpty()) {
                preferences.remove(Keys.AI_API_KEY)
            } else {
                SecretCipher.encrypt(trimmed)?.let { preferences[Keys.AI_API_KEY] = it }
            }
        }
    }

    suspend fun clearAiApiKey() {
        context.dataStore.edit { it.remove(Keys.AI_API_KEY) }
    }

    suspend fun saveAccount(serverUrl: String, username: String, password: String?) {
        context.dataStore.edit { preferences ->
            preferences[Keys.SERVER_URL] = serverUrl.trim()
            preferences[Keys.USERNAME] = username.trim()
            if (!password.isNullOrEmpty()) {
                SecretCipher.encrypt(password)?.let { preferences[Keys.PASSWORD] = it }
            }
        }
    }

    suspend fun clearAccount() {
        context.dataStore.edit { preferences ->
            preferences.remove(Keys.SERVER_URL)
            preferences.remove(Keys.USERNAME)
            preferences.remove(Keys.PASSWORD)
            preferences.remove(Keys.DEFAULT_CALENDAR)
            preferences.remove(Keys.LAST_SYNC_AT)
        }
    }

    suspend fun setDayRange(startHour: Int, endHour: Int) {
        context.dataStore.edit { preferences ->
            preferences[Keys.DAY_START_HOUR] = startHour.coerceIn(0, 22)
            preferences[Keys.DAY_END_HOUR] = endHour.coerceIn(startHour + 1, 24)
        }
    }

    suspend fun setPomodoro(
        focusMinutes: Int? = null,
        shortBreakMinutes: Int? = null,
        longBreakMinutes: Int? = null,
        cyclesBeforeLongBreak: Int? = null,
        autoStartNextPhase: Boolean? = null,
        vibrate: Boolean? = null,
        keepScreenOn: Boolean? = null,
    ) {
        context.dataStore.edit { preferences ->
            focusMinutes?.let { preferences[Keys.FOCUS_MINUTES] = it.coerceIn(1, 180) }
            shortBreakMinutes?.let { preferences[Keys.SHORT_BREAK_MINUTES] = it.coerceIn(1, 60) }
            longBreakMinutes?.let { preferences[Keys.LONG_BREAK_MINUTES] = it.coerceIn(1, 120) }
            cyclesBeforeLongBreak?.let { preferences[Keys.CYCLES_BEFORE_LONG_BREAK] = it.coerceIn(2, 12) }
            autoStartNextPhase?.let { preferences[Keys.AUTO_START_NEXT] = it }
            vibrate?.let { preferences[Keys.VIBRATE] = it }
            keepScreenOn?.let { preferences[Keys.KEEP_SCREEN_ON] = it }
        }
    }

    suspend fun setDefaultCalendar(url: String?) {
        context.dataStore.edit { preferences ->
            if (url.isNullOrBlank()) preferences.remove(Keys.DEFAULT_CALENDAR)
            else preferences[Keys.DEFAULT_CALENDAR] = url
        }
    }

    suspend fun setLastSync(timestamp: Long) {
        context.dataStore.edit { it[Keys.LAST_SYNC_AT] = timestamp }
    }

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password_encrypted")
        val DAY_START_HOUR = intPreferencesKey("day_start_hour")
        val DAY_END_HOUR = intPreferencesKey("day_end_hour")
        val FOCUS_MINUTES = intPreferencesKey("focus_minutes")
        val SHORT_BREAK_MINUTES = intPreferencesKey("short_break_minutes")
        val LONG_BREAK_MINUTES = intPreferencesKey("long_break_minutes")
        val CYCLES_BEFORE_LONG_BREAK = intPreferencesKey("cycles_before_long_break")
        val AUTO_START_NEXT = booleanPreferencesKey("auto_start_next")
        val VIBRATE = booleanPreferencesKey("vibrate")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val DEFAULT_CALENDAR = stringPreferencesKey("default_calendar")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        val AI_API_KEY = stringPreferencesKey("ai_api_key_encrypted")
    }
}
