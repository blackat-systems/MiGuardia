package com.blackatsystems.miguardia.stage12

import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Stage12C097InstitutionAdmissionTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun admitsOnlyTheExactSamsungApi37Singleton() {
        val selector = Stage12C097InstitutionContract.SELECTOR
        val target = Stage12RuntimeTarget.Target.SAMSUNG_TEMPORAL_API37
        Stage12RuntimeTarget.requireSelection(target, listOf(selector), "REAL", false, true)
        for (other in Stage12RuntimeTarget.Target.entries.filter { it != target }) {
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(other, listOf(selector), "REAL", false, true)
            }
        }
        for (selectors in listOf(listOf(selector.substringBefore('#')), listOf(selector, selector),
            listOf(selector, Stage12CalendarDetailContract.SELECTOR))) {
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(target, selectors, "REAL", false, true)
            }
        }
        assertThrows(IllegalStateException::class.java) {
            Stage12RuntimeTarget.requireSelection(target, listOf(selector), "FIXED", false, true)
        }
        assertThrows(IllegalStateException::class.java) {
            Stage12RuntimeTarget.requireSelection(target, listOf(selector), "REAL", false, false)
        }
        assertThrows(IllegalStateException::class.java) {
            Stage12RuntimeTarget.requireSelection(target, listOf(selector), "REAL", true, true)
        }
    }

    @Test fun acceptsOnlyTheOwnedInstitutionReceiptSlot() {
        val files = temporary.newFolder("files")
        val run = UUID.randomUUID().toString()
        val slot = Stage12C097InstitutionContract.SLOT
        assertThrows(IllegalArgumentException::class.java) {
            Stage12NativeReceiptFiles.prepareOutput(files, run, "owner", "$slot-alias")
        }
        assertFalse(File(files, "reports").exists())
        val output = Stage12NativeReceiptFiles.prepareOutput(files, run, "owner", slot)
        assertEquals(File(files.canonicalFile, "reports/artifacts/stage12-normal-$run/$slot.json"), output)
        Stage12NativeReceiptFiles.publish(output, "fixture-only", null)
        assertThrows(IllegalStateException::class.java) {
            Stage12NativeReceiptFiles.prepareOutput(files, run, "owner", slot)
        }
    }
}
