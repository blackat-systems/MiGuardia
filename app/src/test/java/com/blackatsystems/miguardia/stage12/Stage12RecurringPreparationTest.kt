package com.blackatsystems.miguardia.stage12

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test
import com.blackatsystems.miguardia.stage12.Stage12RecurringPreparation.Step

class Stage12RecurringPreparationTest {
    private fun advance(j:Stage12RecurringPreparation, until:Int=Step.entries.size) {
        Step.entries.take(until).forEach { step ->
            if(step.expected==null) j.call<Unit>(step) {} else j.guard(step) { checkNotNull(step.expected) }
        }
    }
    @Test fun completePrefixSeparatesDatabaseReturnValidationAndActivityMount() {
        val j=Stage12RecurringPreparation { throw AssertionError("unexpected error") };advance(j)
        val value=j.projection();assertEquals("COMPLETE",value["status"])
        @Suppress("UNCHECKED_CAST") val creation=value["creation"] as Map<String,String>
        assertEquals(mapOf("private_root" to "RETURNED_TRUE","data" to "RETURNED_TRUE","database" to "RETURNED",
            "database_validation" to "RETURNED","activity_launch" to "RETURNED","activity_mount" to "RETURNED"),creation)
    }
    @Test fun everyThrownOperationPreservesOriginalAndFirstPrefix() {
        for(step in Step.entries) {
            val errors=mutableListOf<Throwable>();val j=Stage12RecurringPreparation { errors+=it };advance(j,step.ordinal)
            val original=IOException("SYNTHETIC_PRIVATE")
            try { j.call(step) { throw original };fail("Expected original") } catch(error:IOException) { assertSame(original,error) }
            assertSame(original,errors.single());val first=j.projection()
            var later=0
            try { j.call(step) { later++ };fail("Expected original") } catch(error:IOException) { assertSame(original,error) }
            assertEquals(0,later);assertEquals(first,j.projection());assertEquals(step.name,first["first_negative_step"])
            assertFalse(first.toString().contains("SYNTHETIC_PRIVATE"))
        }
    }
    @Test fun everyFalseGuardAndExistenceShortCircuitPreservesOnlyMeasuredPrefix() {
        Step.entries.filter { it.expected!=null }.forEach { step ->
            val errors=mutableListOf<Throwable>();val j=Stage12RecurringPreparation { errors+=it };advance(j,step.ordinal)
            assertThrows(IllegalStateException::class.java) { j.guard(step) { !checkNotNull(step.expected) } }
            @Suppress("UNCHECKED_CAST") val events=j.projection()["events"] as List<Map<String,Any>>
            assertEquals(step.ordinal+1,events.size);assertEquals("THREW",events.last()["state"])
            assertEquals(!checkNotNull(step.expected),events.last()["result"]);assertEquals("GUARD_REJECTED",events.last()["error"])
            assertEquals(1,errors.size)
        }
    }
    @Test fun mkdirFalseAndThrowAndDatabaseThrowNeverPretendReturnOrValidation() {
        for(step in listOf(Step.PRIVATE_MKDIR,Step.DATA_MKDIR,Step.DATABASE_CREATE)) {
            val j=Stage12RecurringPreparation {};advance(j,step.ordinal)
            assertThrows(IOException::class.java) { j.call(step) { throw IOException() } }
            @Suppress("UNCHECKED_CAST") val states=j.projection()["creation"] as Map<String,String>
            assertEquals("THREW",states[when(step){Step.PRIVATE_MKDIR->"private_root";Step.DATA_MKDIR->"data";else->"database"}])
            assertEquals("NOT_REACHED",states["database_validation"]);assertEquals("NOT_REACHED",states["activity_launch"])
        }
    }
    @Test fun projectionIsDefensiveAndContainsNoUnmeasuredResults() {
        val j=Stage12RecurringPreparation {};j.call(Step.CASE_REGISTERED) {}
        @Suppress("UNCHECKED_CAST") val events=j.projection()["events"] as List<MutableMap<String,Any>>
        assertEquals(setOf("step","state"),events.single().keys);events.single()["result"]=true
        @Suppress("UNCHECKED_CAST") val fresh=j.projection()["events"] as List<Map<String,Any>>
        assertFalse(fresh.single().containsKey("result"));assertEquals("PREFIX",j.projection()["status"])
    }
    @Test fun impossibleOrderNeverInvokesTheOperation() {
        val j=Stage12RecurringPreparation {};var calls=0
        assertThrows(IllegalStateException::class.java) { j.call(Step.DATABASE_CREATE) { calls++ } }
        assertEquals(0,calls)
        assertThrows(IllegalStateException::class.java) { j.call(Step.CASE_REGISTERED) { calls++ } }
        assertEquals(0,calls);assertEquals("FAILED",j.projection()["status"])
    }
    @Test fun wrongBooleanCallbackTypeIsRetainedAndBlocksFollowingOperations() {
        val errors=mutableListOf<Throwable>();val j=Stage12RecurringPreparation { errors+=it };advance(j,Step.APP_DIRECTORY.ordinal)
        val first=assertThrows(IllegalStateException::class.java) { j.call(Step.APP_DIRECTORY) { Unit } }
        assertSame(first,errors.single());var calls=0
        val second=assertThrows(IllegalStateException::class.java) { j.call(Step.APP_DIRECTORY) { calls++;true } }
        assertSame(first,second);assertEquals(0,calls)
        @Suppress("UNCHECKED_CAST") val events=j.projection()["events"] as List<Map<String,Any>>
        assertEquals("THREW",events.last()["state"]);assertEquals("OTHER",events.last()["error"]);assertFalse(events.last().containsKey("result"))
    }
    @Test fun retainingCallbackFailureRemainsSecondaryToOriginalError() {
        val secondary=IOException("SYNTHETIC_SECONDARY");val original=IOException("SYNTHETIC_ORIGINAL")
        val j=Stage12RecurringPreparation { throw secondary }
        val caught=assertThrows(IOException::class.java) { j.call(Step.CASE_REGISTERED) { throw original } }
        assertSame(original,caught);assertSame(secondary,original.suppressed.single())
        assertFalse(j.projection().toString().contains("SYNTHETIC"))
    }
    @Test fun ignoredBooleanGuardsKeepTheirActualReturnValuesWithoutUnitCoercion() {
        val old=Stage12RecurringPreparation {};advance(old,Step.APP_DIRECTORY.ordinal)
        assertThrows(IllegalStateException::class.java) {
            listOf(Step.APP_DIRECTORY).forEach { old.call(it) { true } }
        }
        val corrected=Stage12RecurringPreparation {};advance(corrected,Step.APP_DIRECTORY.ordinal)
        listOf(Step.APP_DIRECTORY).forEach { corrected.guard(it) { true } }
        @Suppress("UNCHECKED_CAST") val events=corrected.projection()["events"] as List<Map<String,Any>>
        assertEquals("RETURNED",events.last()["state"]);assertEquals(true,events.last()["result"])
    }
}
