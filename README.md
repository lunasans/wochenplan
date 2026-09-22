# Wochenplan

Eine Android-App, mit der du deine Woche **kalenderwochenweise** planst – mit
Anbindung an einen **CalDAV-Server** und einem eingebauten **Pomodoro-Timer**
inklusive Pausen.

## Was die App kann

### Wochenansicht nach Kalenderwochen
- Montag bis Sonntag nebeneinander, Stunden untereinander, Wochenende leicht hinterlegt
- Kopfzeile zeigt Kalenderwoche und Zeitraum, z. B. `KW 39 · 21.09. – 27.09.2026`
- Blättern über Pfeile, Sprung zu „heute“ und ein Wochenwähler mit allen KW eines Jahres
- Ganztägige Termine in einem eigenen Streifen, damit sie das Zeitraster nicht auseinanderziehen
- Sich überschneidende Termine stehen nebeneinander; die aktuelle Uhrzeit wird als Linie angezeigt
- Termine außerhalb des eingestellten Tagesbereichs sind trotzdem sichtbar – das Raster wächst mit
- Tippen auf eine freie Stelle legt einen Termin zu genau dieser Zeit an

### CalDAV
- Automatische Kalendersuche über `current-user-principal` und `calendar-home-set`
  (die Server-Wurzel genügt, `/.well-known/caldav` wird ebenfalls probiert)
- Termine anlegen, ändern und löschen – auch Serientermine
- Serien (`RRULE`), gestrichene Einzeltermine (`EXDATE`) und abweichende
  Einzeltermine (`RECURRENCE-ID`) werden korrekt aufgelöst, auch wenn ein
  Einzeltermin in eine andere Woche verschoben wurde
- Einzelne Termine einer Serie lassen sich streichen, ohne die Serie zu löschen
- Uhrzeiten werden mit Zeitzone (`TZID` plus passender `VTIMEZONE`) geschrieben.
  Ein wöchentlicher Termin um 9:00 bleibt dadurch auch nach der Zeitumstellung um 9:00
- Beim Speichern bleiben Angaben anderer Programme erhalten (Teilnehmer,
  Erinnerungen, eigene Felder) – es werden nur die bearbeiteten Felder angefasst
- Mehrere Kalender, einzeln ein- und ausblendbar, mit den Farben vom Server
- Schreibgeschützte Kalender (z. B. abonnierte Feiertage) werden erkannt
- Geladene Wochen liegen lokal vor und sind auch ohne Netz sichtbar

### Aufgaben je Woche
- Aufgabenliste pro Kalenderwoche, wahlweise einem Wochentag zugeordnet
- Geplante Pomodoros je Aufgabe, erledigte werden mitgezählt
- Offene Aufgaben mit einem Tipp in die nächste Woche übernehmen

### Pomodoro-Timer mit Pausen
- Fokus, kurze Pause und lange Pause, alle Längen einstellbar
  (Vorgabe 25 / 5 / 15 Minuten, lange Pause nach 4 Abschnitten)
- Läuft als Vordergrunddienst weiter, wenn die App im Hintergrund ist
- Die Benachrichtigung zählt selbst herunter und hat Schaltflächen für
  Pause, Überspringen und Beenden
- Am Ende eines Abschnitts Meldung und Vibration; der nächste Abschnitt startet
  auf Wunsch automatisch
- Ein Timer lässt sich mit einer Aufgabe der Woche verknüpfen; abgeschlossene
  Fokusabschnitte werden dieser Aufgabe gutgeschrieben
- Tagesstatistik: Anzahl der Fokusabschnitte und Fokusminuten

## Einrichtung

1. App öffnen, unten auf **Mehr** (Einstellungen) tippen
2. Server-Adresse, Benutzername und Passwort eintragen, dann **Verbinden**
3. Unter **Kalender** auswählen, welche angezeigt werden und wo neue Termine landen

### Server-Adressen der gängigen Anbieter

| Anbieter | Adresse |
| --- | --- |
| Nextcloud / ownCloud | `https://cloud.example.com/remote.php/dav` |
| Radicale | `https://radicale.example.com/` |
| Baïkal | `https://baikal.example.com/dav.php` |
| mailbox.org / Open-Xchange | `https://dav.mailbox.org/` |
| Fastmail | `https://caldav.fastmail.com/dav/` |
| Synology Calendar | `https://nas.example.com:5001/caldav.php` |

Bei Nextcloud, ownCloud und Fastmail bitte ein **App-Passwort** anlegen und nicht
das Kontopasswort verwenden. Das Passwort wird mit einem Schlüssel aus dem
Android-Keystore verschlüsselt gespeichert; der Schlüssel verlässt das Gerät nie.

**Nur HTTPS:** Unverschlüsselte Verbindungen sind bewusst abgeschaltet, weil das
Passwort bei CalDAV mit jeder Anfrage mitgeschickt wird. Wer zu Hause einen Server
ohne Zertifikat betreibt, stellt ihm am besten einen Reverse Proxy mit
Let's-Encrypt-Zertifikat voran.

## Bauen

Voraussetzung ist ein JDK 17 und das Android SDK (API 35).

```bash
./gradlew assembleDebug        # APK unter app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # Unit-Tests
./gradlew installDebug         # auf ein angeschlossenes Gerät spielen
```

Alternativ das Projekt einfach in Android Studio öffnen.

Jeder Push baut die App über GitHub Actions und hängt das Debug-APK als Artefakt
an den Lauf – praktisch, wenn gerade kein Android Studio zur Hand ist.

## Aufbau

Die App kommt ohne Backend aus: Android spricht direkt mit dem CalDAV-Server.

```
app/src/main/java/de/wochenplan/app/
├── core/          Kalenderwochen (ISO 8601) und Verschlüsselung
├── data/
│   ├── ical/      iCalendar: Parser, Writer, Serientermine, VTIMEZONE
│   ├── caldav/    CalDAV über OkHttp, Auswertung der WebDAV-Antworten
│   ├── db/        Room: Kalender, Terminzwischenspeicher, Aufgaben, Fokuszeiten
│   ├── prefs/     Einstellungen (DataStore)
│   └── repo/      Bindeglied zwischen Server, Datenbank und Oberfläche
├── pomodoro/      Timer, Vordergrunddienst, Benachrichtigungen
├── ui/            Jetpack Compose: Woche, Termin, Aufgaben, Fokus, Einstellungen
└── work/          Hintergrundabgleich (WorkManager)
```

Technik: Kotlin, Jetpack Compose (Material 3), Room, DataStore, WorkManager,
OkHttp. Mindestens Android 8.0 (API 26).

Der iCalendar-Teil ist bewusst selbst geschrieben statt als Bibliothek eingebunden:
Er umfasst nur die für VEVENT nötige Teilmenge, behält beim Schreiben unbekannte
Felder bei und ist ohne Gerät testbar. Die kniffligen Stellen – Kalenderwochen über
den Jahreswechsel, Wiederholungsregeln, Zeitumstellung, Terminüberschneidungen –
sind mit Unit-Tests abgedeckt.

## Bekannte Grenzen

- Serientermine werden immer als Ganzes bearbeitet. Einzelne Termine lassen sich
  streichen, aber nicht abweichend ändern (das Anzeigen solcher Termine
  funktioniert dagegen).
- Wiederholungsregeln, die diese App nicht kennt (z. B. `BYSETPOS`), werden
  angezeigt und beim Speichern unverändert übernommen, aber nicht zum Bearbeiten
  angeboten.
- Änderungen brauchen eine Verbindung zum Server; ein Änderungsspeicher für den
  Offline-Betrieb ist nicht eingebaut. Das Ansehen geladener Wochen geht offline.
- Aufgaben und Fokuszeiten bleiben auf dem Gerät und werden nicht als
  CalDAV-Aufgaben (`VTODO`) abgeglichen.
