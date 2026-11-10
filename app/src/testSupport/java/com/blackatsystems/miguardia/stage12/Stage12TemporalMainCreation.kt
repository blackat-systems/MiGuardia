package com.blackatsystems.miguardia.stage12

/** Method terminal state never turns a late owned receiver into an ordinary MainActivity. */
internal object Stage12TemporalMainCreation {
    fun <T> construct(isMain: Boolean, temporalSelected: Boolean, exclusiveSelection: Boolean,
        selectorActive: Boolean, finished: Boolean, graphPresent: Boolean, create: () -> T): T {
        if (isMain && temporalSelected) check(exclusiveSelection && selectorActive && !finished && graphPresent) {
            "TEMPORAL_MAIN_CREATION_OUTSIDE_ACTIVE_OWNED_METHOD"
        }
        return create()
    }
}
