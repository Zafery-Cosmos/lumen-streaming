package app.lumen.domain

import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

// PROPFIND renvoie un XML "multistatus" — le prefixe de namespace (d:, D:, lp1:…)
// varie selon le serveur, d'où l'usage systematique de getElementsByTagNameNS("*", …).
actual fun parseWebDavMultistatus(xml: String): List<WebDavRawEntry> {
    val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
    val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    val responses = doc.getElementsByTagNameNS("*", "response")
    val entries = mutableListOf<WebDavRawEntry>()
    for (i in 0 until responses.length) {
        val response = responses.item(i) as? Element ?: continue
        val href = response.getElementsByTagNameNS("*", "href").item(0)?.textContent?.trim() ?: continue
        val resourceType = response.getElementsByTagNameNS("*", "resourcetype").item(0) as? Element
        val isCollection = (resourceType?.getElementsByTagNameNS("*", "collection")?.length ?: 0) > 0
        val contentLength = response.getElementsByTagNameNS("*", "getcontentlength")
            .item(0)?.textContent?.trim()?.toLongOrNull()
        val displayName = response.getElementsByTagNameNS("*", "displayname").item(0)?.textContent?.trim()
        entries += WebDavRawEntry(href, isCollection, contentLength, displayName)
    }
    return entries
}
