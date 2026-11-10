package com.blackatsystems.miguardia.stage12

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import org.junit.Assert.*
import org.junit.Test

class Stage12PrivatePreferencesXmlTest {
    @Test fun validTypedPreferencesRemainComplete() {
        val document = Stage12PrivatePreferencesXml.parse(("<map><string name=\"private-key\">private-value &amp; more</string>" +
            "<long name=\"counter\" value=\"42\"/><set name=\"set\"><string>member</string></set></map>").toByteArray())
        assertEquals(3, document.documentElement.childNodes.length)
        assertEquals("private-value & more", document.getElementsByTagName("string").item(0).textContent)
        assertEquals("42", document.getElementsByTagName("long").item(0).attributes.getNamedItem("value").nodeValue)
    }

    @Test fun malformedPrivateElementNeverAppearsInStderrOrThrowable() {
        val prior = System.err
        val output = ByteArrayOutputStream()
        try {
            System.setErr(PrintStream(output, true, "UTF-8"))
            val error = assertThrows(IllegalStateException::class.java) {
                Stage12PrivatePreferencesXml.parse("<map><private-canary-name></map>".toByteArray())
            }
            assertEquals("READBACK_XML_PARSE_FAILED", error.message)
            assertNull(error.cause)
            assertTrue(error.suppressed.isEmpty())
            assertFalse(error.stackTraceToString().contains("private-canary-name"))
            assertEquals("", output.toString("UTF-8"))
        } finally { System.setErr(prior) }
    }

    @Test fun doctypeEntityAndNulAreRejectedBeforeResolvingAnything() {
        listOf("<!DOCTYPE map SYSTEM 'file:///private-canary-path'><map/>",
            "<!DOCTYPE map [<!ENTITY private SYSTEM 'https://invalid.example/canary'>]><map>&private;</map>",
            "<map>\u0000</map>").forEach { xml ->
            val error = assertThrows(IllegalStateException::class.java) { Stage12PrivatePreferencesXml.parse(xml.toByteArray()) }
            assertEquals("READBACK_XML_DECLARATION_REJECTED", error.message)
            assertNull(error.cause)
        }
    }

    @Test fun invalidUtf8AndNonPreferenceRootAreNotAcceptedAsEmpty() {
        val malformed = assertThrows(IllegalStateException::class.java) {
            Stage12PrivatePreferencesXml.parse(byteArrayOf(0xc3.toByte(), 0x28))
        }
        assertEquals("READBACK_XML_PARSE_FAILED", malformed.message)
        val wrong = assertThrows(IllegalStateException::class.java) { Stage12PrivatePreferencesXml.parse("<other/>".toByteArray()) }
        assertEquals("READBACK_INVALID_SHARED_PREFERENCES", wrong.message)
    }
}
