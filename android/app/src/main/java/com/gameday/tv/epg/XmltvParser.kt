package com.gameday.tv.epg

import com.gameday.tv.core.Diag
import com.gameday.tv.model.Program
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

/**
 * Streaming XMLTV reader. SAX keeps memory flat on a 1.5 GB Fire TV compared with DOM,
 * and caps the number of programmes retained defensively.
 */
object XmltvParser {

    fun parse(
        xml: String,
        @Suppress("UNUSED_PARAMETER") sourceId: String,
        allowedChannelIds: Set<String> = emptySet(),
        maxPrograms: Int = 20_000,
    ): List<Program> {
        val handler = Handler(allowedChannelIds, maxPrograms)
        try {
            val factory = SAXParserFactory.newInstance()
            factory.isNamespaceAware = false
            factory.isValidating = false
            val reader = factory.newSAXParser().xmlReader
            reader.contentHandler = handler
            reader.parse(InputSource(StringReader(xml.removePrefix("\uFEFF"))))
        } catch (e: SAXException) {
            Diag.warn("XmltvParser", "Malformed XMLTV: ${e.message}")
            throw IllegalArgumentException("Invalid XMLTV document: ${e.message}")
        }
        return handler.programs
    }

    private class Handler(
        private val allowedChannelIds: Set<String>,
        private val maxPrograms: Int,
    ) : DefaultHandler() {

        val programs = ArrayList<Program>()
        private var current: Builder? = null
        private var captureTag: String? = null
        private val text = StringBuilder()

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
            if (attributes == null) return
            when (qName) {
                "programme" -> {
                    val channelId = attributes.getValue("channel")?.trim().orEmpty()
                    if (channelId.isEmpty()) return
                    if (allowedChannelIds.isNotEmpty() && !allowedChannelIds.contains(channelId)) return
                    val start = XmltvTime.parse(attributes.getValue("start")) ?: return
                    val stop = XmltvTime.parse(attributes.getValue("stop")) ?: return
                    if (stop <= start) return
                    current = Builder(channelId, start, stop)
                }
                "title", "desc", "category" -> {
                    if (current != null) {
                        captureTag = qName
                        text.setLength(0)
                    }
                }
                "icon" -> {
                    val builder = current ?: return
                    if (builder.iconUrl.isEmpty()) {
                        builder.iconUrl = attributes.getValue("src").orEmpty()
                    }
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (captureTag != null) text.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val builder = current
            when (qName) {
                "title" -> if (builder != null && captureTag == "title") builder.title = text.toString().trim()
                "desc" -> if (builder != null && captureTag == "desc") builder.description = text.toString().trim()
                "category" -> if (builder != null && captureTag == "category" && builder.category.isEmpty()) {
                    builder.category = text.toString().trim()
                }
                "programme" -> {
                    if (builder != null && builder.title.isNotBlank() && programs.size < maxPrograms) {
                        programs += Program(
                            channelId = builder.channelId,
                            title = builder.title,
                            description = builder.description,
                            startMs = builder.startMs,
                            stopMs = builder.stopMs,
                            category = builder.category,
                            iconUrl = builder.iconUrl,
                        )
                    }
                    current = null
                }
            }
            if (qName == captureTag) captureTag = null
        }

        private class Builder(val channelId: String, val startMs: Long, val stopMs: Long) {
            var title: String = ""
            var description: String = ""
            var category: String = ""
            var iconUrl: String = ""
        }
    }
}
