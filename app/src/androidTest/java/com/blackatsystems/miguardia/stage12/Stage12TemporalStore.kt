package com.blackatsystems.miguardia.stage12

import android.content.Context
import com.blackatsystems.miguardia.core.database.LocalDataStore

/** The constructor and path binding travel together; callers cannot attach a foreign store. */
internal class Stage12TemporalStore private constructor(
    val binding: Stage12TemporalPaths,
    private val value: LocalDataStore,
    private val failure: Stage12TemporalFailure,
) {
    private var closed = false
    private var closeAttempted = false
    val store: LocalDataStore get() {
        failure.requireActive()
        check(!closeAttempted) { "TEMPORAL_STORE_ALREADY_CLOSED" }
        return value
    }
    fun requireBinding(expected: Stage12TemporalPaths): LocalDataStore {
        check(binding === expected) { "TEMPORAL_STORE_FOREIGN_BINDING" }
        return store
    }
    fun close() {
        try {
            check(!closeAttempted) { "TEMPORAL_STORE_CLOSE_REUSED" }; closeAttempted = true
            value.close()
            closed = true
        } catch (error: Throwable) { failure.reject(error) }
    }
    val isClosed: Boolean get() = closed

    companion object {
        fun create(context: Context, binding: Stage12TemporalPaths, retain: (Throwable) -> Unit): Stage12TemporalStore {
            val failure = Stage12TemporalFailure(retain)
            try {
                check(context.packageName == "com.blackatsystems.miguardia.qa")
                binding.revalidate(stage12RecurringPathAccess(context))
                val store = LocalDataStore.create(context, binding.name)
                return Stage12TemporalStore(binding, store, failure)
            } catch (error: Throwable) { failure.reject(error) }
        }
    }
}
