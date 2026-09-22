package de.wochenplan.app.data.caldav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DavXmlTest {

    @Test
    fun `Kalender werden aus der Multistatus-Antwort gelesen`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:multistatus xmlns:d="DAV:" xmlns:cs="http://calendarserver.org/ns/"
                           xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:ic="http://apple.com/ns/ical/">
              <d:response>
                <d:href>/remote.php/dav/calendars/anna/persoenlich/</d:href>
                <d:propstat>
                  <d:prop>
                    <d:resourcetype><d:collection/><c:calendar/></d:resourcetype>
                    <d:displayname>Persoenlich</d:displayname>
                    <ic:calendar-color>#FF5733FF</ic:calendar-color>
                    <cs:getctag>12345</cs:getctag>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
                <d:propstat>
                  <d:prop><d:irgendwas/></d:prop>
                  <d:status>HTTP/1.1 404 Not Found</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val responses = DavXml.parseMultiStatus(xml)
        assertEquals(1, responses.size)

        val response = responses.first()
        assertEquals("/remote.php/dav/calendars/anna/persoenlich/", response.href)
        assertEquals("Persoenlich", response.text(DavNs.DAV, "displayname"))
        assertEquals("12345", response.text(DavNs.CALENDARSERVER, "getctag"))
        assertTrue(response.hasChild(DavNs.DAV, "resourcetype", DavNs.CALDAV, "calendar"))
        // Eigenschaften aus dem 404-Block duerfen nicht uebernommen werden.
        assertEquals(null, response.element(DavNs.DAV, "irgendwas"))
    }

    @Test
    fun `Termine mit ETag werden gelesen`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <multistatus xmlns="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
              <response>
                <href>/kalender/termin-1.ics</href>
                <propstat>
                  <prop>
                    <getetag>"abc123"</getetag>
                    <c:calendar-data>BEGIN:VCALENDAR
            END:VCALENDAR</c:calendar-data>
                  </prop>
                  <status>HTTP/1.1 200 OK</status>
                </propstat>
              </response>
            </multistatus>
        """.trimIndent()

        val responses = DavXml.parseMultiStatus(xml)
        assertEquals(1, responses.size)
        assertEquals("\"abc123\"", responses[0].text(DavNs.DAV, "getetag"))
        assertNotNull(responses[0].text(DavNs.CALDAV, "calendar-data"))
    }

    @Test
    fun `Apple-Farben werden auf sechs Stellen gekuerzt`() {
        assertEquals("#FF5733", CalDavClient.normalizeColor("#FF5733FF"))
        assertEquals("#123456", CalDavClient.normalizeColor("#123456"))
        assertEquals(null, CalDavClient.normalizeColor("rot"))
        assertEquals(null, CalDavClient.normalizeColor(null))
    }
}
