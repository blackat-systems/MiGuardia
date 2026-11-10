package com.blackatsystems.miguardia.stage12

import android.content.Context
import java.io.File
import java.time.Clock
import com.blackatsystems.miguardia.stage12.Stage12TemporalPreparation.Step

/** One namespace, one creation attempt, then a real close/reopen without reseeding. */
internal class Stage12TemporalCase(val run: String, val scope: Stage12TemporalScope, private val retain: (Throwable) -> Unit) {
    val preparation = Stage12TemporalPreparation(retain)
    val fixturePrefix = Stage12TemporalFixturePrefix(scope, retain)
    private var attempted = false
    lateinit var binding: Stage12TemporalPaths; private set
    lateinit var opened: Stage12TemporalStore; private set
    lateinit var fixture: Stage12TemporalFixture; private set
    val hasStore: Boolean get() = ::opened.isInitialized
    fun reopenClosedStore(context: Context) {
        try {
            check(opened.isClosed) { "TEMPORAL_REOPEN_REQUIRES_CLOSED_ORIGINAL" }
            binding.requireStored(stage12RecurringPathAccess(context))
            val original = opened
            opened = Stage12TemporalStore.create(context, binding, retain)
            check(opened !== original)
        } catch (error: Throwable) { throw preparation.outside(error) }
    }

    suspend fun prepare(context: Context, clock: Clock) {
        try {
            preparation.call(Step.CASE_REGISTERED) {
                check(!attempted) { "TEMPORAL_CASE_CREATION_REUSED" }; attempted = true
                check(context.packageName == "com.blackatsystems.miguardia.qa")
            }
            val access = stage12RecurringPathAccess(context)
            binding = Stage12TemporalPaths.admit(access, run, scope, preparation)
            for ((suffix, step) in listOf("" to Step.DATABASE_PRESENT, "-wal" to Step.WAL_PRESENT,
                "-shm" to Step.SHM_PRESENT, "-journal" to Step.JOURNAL_PRESENT))
                preparation.guard(step) { access.exists(File(binding.database.path + suffix)) }
            preparation.guard(Step.PRIVATE_PRESENT) { access.exists(binding.privateRoot) }
            preparation.call(Step.PRIVATE_BINDING) { binding.revalidate(access) }
            preparation.guard(Step.PRIVATE_MKDIR) { binding.privateRoot.mkdir() }
            preparation.call(Step.DATA_BINDING) { binding.revalidate(access) }
            preparation.guard(Step.DATA_MKDIR) { binding.dataRoot.mkdir() }
            preparation.call(Step.BINDING_BEFORE_CREATE) { binding.revalidate(access) }
            preparation.call(Step.DATABASE_CREATE) { opened = Stage12TemporalStore.create(context, binding, retain) }
            fixture = preparation.callSuspend(Step.FIXTURE_CREATE) {
                Stage12TemporalFixture.create(context, run, scope, opened, clock, binding, fixturePrefix)
            }
            preparation.guard(Step.CIVIL_DATE) { fixture.recipe.ownerDate == scope.literal.ownerDate }
            preparation.call(Step.SETUP_READBACK) {
                Stage12RecurringReadbackOracle.requireExact(
                    Stage12FullStoreReadback.captureTemporalOwned(context, run, scope, binding), fixture.initialExpected)
            }
            val original = opened
            preparation.call(Step.SETUP_STORE_CLOSE) { original.close() }
            preparation.call(Step.REOPEN_PATHS) { binding.requireStored(access) }
            preparation.call(Step.SETUP_STORE_REOPEN) {
                opened = Stage12TemporalStore.create(context, binding, retain)
                check(opened !== original && original.isClosed)
            }
            preparation.callSuspend(Step.REOPEN_VALIDATION) { fixture.requireSeedReopened(opened, context) }
            preparation.call(Step.REOPEN_READBACK) {
                Stage12RecurringReadbackOracle.requireExact(
                    Stage12FullStoreReadback.captureTemporalOwned(context, run, scope, binding), fixture.initialExpected)
            }
        } catch (error: Throwable) { throw preparation.outside(error) }
    }
}
