package com.blackatsystems.miguardia.updates
import com.blackatsystems.miguardia.ui.shouldConsumeCalendarNavigation
import com.blackatsystems.miguardia.nextCalendarNavigationRequest
import java.time.Instant
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
class UpdateClockRegressionTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun consumedCalendarRequestMustNotReplaceHelpOrSummaryAfterRemount() {
        assertFalse(shouldConsumeCalendarNavigation(request = 1, lastConsumed = 1))
    }

    @Test fun restoredProducerIssuesFreshRequestsAndClosedGatesDoNotConsumeThem() {
        val savedProducer = nextCalendarNavigationRequest(0)
        val savedConsumer = savedProducer
        assertFalse(shouldConsumeCalendarNavigation(0, savedConsumer))
        assertFalse(shouldConsumeCalendarNavigation(savedProducer, savedConsumer))
        val afterRecreation = nextCalendarNavigationRequest(savedProducer)
        assertEquals(2L, afterRecreation)
        assertFalse(shouldConsumeCalendarNavigation(afterRecreation, savedConsumer, allowed = false))
        assertTrue(shouldConsumeCalendarNavigation(afterRecreation, savedConsumer, allowed = true))
        assertFalse(shouldConsumeCalendarNavigation(afterRecreation, afterRecreation))
        assertTrue(shouldConsumeCalendarNavigation(nextCalendarNavigationRequest(afterRecreation), afterRecreation))
        assertThrows(ArithmeticException::class.java) { nextCalendarNavigationRequest(Long.MAX_VALUE) }
    }


    @Test fun civilSkewNeverDecidesPublicationDateOrExtendsBudget(): Unit = runBlocking {
        for (offset in listOf(-86400L, -30L, 30L, 86400L)) {
            val time = UpdateTestClock(updateNow.plusSeconds(offset))
            assertNotNull(UpdateClient(FixtureUpdateTransport(), {time.nanos}, time::instant).release())
            val budget = UpdateTimeBudget.start { time.nanos }
            time.advance(299); time.civil = Instant.EPOCH
            assertEquals(1000L, budget.remainingMillis())
            time.advance(1); assertThrows(Exception::class.java) { budget.requireRemaining() }
        }
    }
    @Test fun metadataLatencyConsumesBudgetFromRequestStart(): Unit = runBlocking {
        val time = UpdateTestClock()
        val transport = FixtureUpdateTransport().apply { transform = { _, response -> time.advance(300); response } }
        assertThrows(Exception::class.java) { runBlocking { UpdateClient(transport, {time.nanos}, time::instant).release() } }
    }
    @Test fun slowGetIsRejectedAndOnlyItsPartialIsDiscarded(): Unit = runBlocking {
        val time = UpdateTestClock()
        val transport = FixtureUpdateTransport().apply { transform = { _, response -> time.advance(300); response } }
        val directory = temporary.newFolder()
        val service = UpdateService(transport, FixtureUpdateInspector(), UpdateFiles(directory), time) { time.nanos }
        assertThrows(Exception::class.java) { runBlocking {
            service.download(service.select(updateRelease(), updateInstalled), {true}, {}, { fail("Expired transfer") })
        } }
        assertTrue(directory.listFiles()!!.isEmpty())
    }
    @Test fun retryAfterBoundsSyntaxAndFallbackAreExact() {
        for ((header, wait) in listOf(null to 60000L, "bad" to 60000L, "0" to 0L, "2" to 2000L,
            "9".repeat(128) to 86400000L, "9".repeat(129) to 60000L,
            "Sun, 06 Sep 2026 12:01:00 GMT" to 60000L, "Sun, 06 Sep 2026 11:59:00 GMT" to 0L)) {
            assertEquals(header, wait, updateRetryMillis(header, updateNow))
        }
    }
    @Test fun apkInspectionCannotReturnVerifiedArtifactAfterTotalBudgetExpires(): Unit = runBlocking {
        for (seconds in listOf(299L, 300L)) {
            val time = UpdateTestClock()
            val fixture = FixtureUpdateInspector()
            val inspector = object : UpdateApkInspector by fixture {
                override fun inspect(file: java.io.File): UpdateApkInfo {
                    time.advance(seconds)
                    return fixture.inspect(file)
                }
            }
            val directory = temporary.newFolder()
            val service = UpdateService(FixtureUpdateTransport(), inspector, UpdateFiles(directory), time) { time.nanos }
            val selection = service.select(updateRelease(), updateInstalled)
            if (seconds < 300) {
                val artifact = service.download(selection, {true}, {}, {})
                assertTrue(artifact.file.isFile)
                service.discard(artifact)
            } else {
                assertThrows(IllegalArgumentException::class.java) { runBlocking {
                    service.download(selection, {true}, {}, {})
                } }
            }
            assertTrue(directory.listFiles()!!.isEmpty())
        }
    }
    @Test fun cooldownSharedAcrossEntriesCannotBeResetByClockOrNewSelectionAndNeverAutoRequests(): Unit = runBlocking {
        val time = UpdateTestClock()
        val transport = FixtureUpdateTransport().apply { transform = { _, _ ->
            UpdateResponse(429, "broken".byteInputStream(), "text/html", null, "60")
        } }
        val service = UpdateService(transport, FixtureUpdateInspector(), UpdateFiles(temporary.newFolder()), time) {time.nanos}
        assertThrows(UpdateServiceFailure::class.java) { runBlocking { service.release() } }
        assertEquals(60000L, service.retryRemainingMillis())
        time.advance(59); time.civil = updateNow.minusSeconds(86400)
        assertThrows(UpdateServiceFailure::class.java) { runBlocking { service.release() } }
        assertThrows(Exception::class.java) { runBlocking { service.select(updateRelease(), updateInstalled) } }
        assertEquals(1000L, service.retryRemainingMillis()); assertEquals(1, transport.paths.size)
        time.advance(1); assertEquals(0L, service.retryRemainingMillis()); assertEquals(1, transport.paths.size)
        transport.transform = { _, response -> response }
        assertNotNull(service.release()); assertEquals(2, transport.paths.size)
    }
    @Test fun slowErrorBodyNeverRestartsOrExtendsReceivedRetryDeadline(): Unit = runBlocking {
        for (wait in listOf(1, 60)) {
            val time = UpdateTestClock()
            val transport = FixtureUpdateTransport().apply { transform = { _, _ ->
                UpdateResponse(429, object : java.io.InputStream() {
                    override fun read(): Int { time.advance(5); return -1 }
                }, "application/json", null, wait.toString())
            } }
            val service = UpdateService(transport, FixtureUpdateInspector(), UpdateFiles(temporary.newFolder()), time) { time.nanos }
            val failure = assertThrows(UpdateServiceFailure::class.java) { runBlocking { service.release() } }
            assertEquals((wait - 5).coerceAtLeast(0) * 1000L, failure.retryMillis)
            assertEquals(failure.retryMillis, service.retryRemainingMillis())
            assertEquals(updateNow.plusSeconds(wait.toLong()), failure.retryAt)
            time.advance(100)
            assertEquals(0L, service.retryRemainingMillis())
            assertEquals(1, transport.paths.size)
        }
    }
    @Test fun releaseChangedRequiresNewConsciousSelection(): Unit = runBlocking {
        val transport = FixtureUpdateTransport().apply { transform = { _, _ -> jsonResponse(
            linkedMapOf("contract_version" to 1, "error" to linkedMapOf("code" to "RELEASE_CHANGED")), 409) } }
        val service = UpdateService(transport, FixtureUpdateInspector(), UpdateFiles(temporary.newFolder()))
        val selection = service.select(updateRelease(), updateInstalled)
        assertThrows(UpdateServiceFailure::class.java) { runBlocking { service.download(selection,{true},{},{}) } }
        transport.transform = { _, response -> response }
        assertThrows(Exception::class.java) { runBlocking { service.download(selection,{true},{},{}) } }
        assertEquals(1, transport.paths.size)
    }
}
