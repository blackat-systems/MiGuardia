package com.blackatsystems.miguardia.stage12

import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException

/** Parse in memory without the default parser's stderr messages or external entities. */
internal object Stage12PrivatePreferencesXml {
    fun parse(bytes: ByteArray): Document = try {
        val xml = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        check("<!DOCTYPE" !in xml && "<!ENTITY" !in xml && '\u0000' !in xml) { "READBACK_XML_DECLARATION_REJECTED" }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
        }
        val builder = factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> throw SAXException("READBACK_EXTERNAL_ENTITY_REJECTED") }
            setErrorHandler(object : ErrorHandler {
                override fun warning(error: SAXParseException) { throw SAXException("READBACK_XML_PARSE_FAILED") }
                override fun error(error: SAXParseException) { throw SAXException("READBACK_XML_PARSE_FAILED") }
                override fun fatalError(error: SAXParseException) { throw SAXException("READBACK_XML_PARSE_FAILED") }
            })
        }
        builder.parse(InputSource(StringReader(xml))).also {
            check(it.doctype == null && it.documentElement?.tagName == "map") { "READBACK_INVALID_SHARED_PREFERENCES" }
        }
    } catch (error: Exception) {
        val code = error.message?.takeIf { it.matches(Regex("READBACK_[A-Z0-9_]+")) } ?: "READBACK_XML_PARSE_FAILED"
        throw IllegalStateException(code)
    }
}
