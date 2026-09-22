package de.wochenplan.app.data.caldav

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** Die XML-Namensraeume, die CalDAV-Server verwenden. */
object DavNs {
    const val DAV = "DAV:"
    const val CALDAV = "urn:ietf:params:xml:ns:caldav"
    const val CALENDARSERVER = "http://calendarserver.org/ns/"
    const val APPLE = "http://apple.com/ns/ical/"
}

/** Eine `<response>`-Einheit einer Multistatus-Antwort. */
data class DavResponse(
    val href: String,
    private val properties: Map<String, Element>,
) {
    fun element(namespace: String, name: String): Element? = properties["$namespace:$name"]

    fun text(namespace: String, name: String): String? =
        element(namespace, name)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    /** Prueft, ob ein Property ein Kindelement mit diesem Namen enthaelt. */
    fun hasChild(namespace: String, name: String, childNamespace: String, childName: String): Boolean =
        element(namespace, name)?.let { parent ->
            parent.childElements().any { it.matches(childNamespace, childName) }
        } ?: false

    fun childNames(namespace: String, name: String, childNamespace: String, childName: String): List<Element> =
        element(namespace, name)?.childElements()?.filter { it.matches(childNamespace, childName) }.orEmpty()
}

object DavXml {

    /**
     * Liest eine `multistatus`-Antwort. Eigenschaften aus `propstat`-Bloecken mit
     * einem Fehlerstatus (404, 403, ...) werden dabei uebersprungen.
     */
    fun parseMultiStatus(xml: String): List<DavResponse> {
        val document = runCatching {
            newDocumentBuilderFactory().newDocumentBuilder()
                .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        }.getOrNull() ?: return emptyList()

        val root = document.documentElement ?: return emptyList()
        return root.childElements()
            .filter { it.matches(DavNs.DAV, "response") }
            .mapNotNull { parseResponse(it) }
    }

    private fun parseResponse(response: Element): DavResponse? {
        val href = response.childElements()
            .firstOrNull { it.matches(DavNs.DAV, "href") }
            ?.textContent?.trim()
            ?: return null

        val properties = mutableMapOf<String, Element>()
        for (propstat in response.childElements().filter { it.matches(DavNs.DAV, "propstat") }) {
            val status = propstat.childElements()
                .firstOrNull { it.matches(DavNs.DAV, "status") }
                ?.textContent
                .orEmpty()
            if (status.isNotEmpty() && !status.contains(" 200 ")) continue

            val prop = propstat.childElements().firstOrNull { it.matches(DavNs.DAV, "prop") } ?: continue
            for (element in prop.childElements()) {
                val namespace = element.namespaceURI ?: DavNs.DAV
                properties["$namespace:${element.localName ?: element.nodeName}"] = element
            }
        }
        return DavResponse(href, properties)
    }

    private fun newDocumentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // Externe Entitaeten sind fuer CalDAV nie noetig und waeren ein Risiko.
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            isExpandEntityReferences = false
        }
}

internal fun Element.childElements(): List<Element> {
    val result = mutableListOf<Element>()
    val nodes = childNodes
    for (index in 0 until nodes.length) {
        val node = nodes.item(index)
        if (node.nodeType == Node.ELEMENT_NODE) result.add(node as Element)
    }
    return result
}

internal fun Element.matches(namespace: String, name: String): Boolean =
    (localName ?: nodeName).equals(name, ignoreCase = true) &&
        (namespaceURI == namespace || namespaceURI == null)
