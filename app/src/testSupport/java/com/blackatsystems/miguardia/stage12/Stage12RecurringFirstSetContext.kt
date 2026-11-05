package com.blackatsystems.miguardia.stage12

import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRuleRevision

/** The synthetic first set belongs to the same historical date as its first rule. */
internal object Stage12RecurringFirstSetContext {
    fun resolve(history: WorkConfigurationHistory, rule: WorkplaceRuleRevision): ResolvedWorkConfigurationRevision =
        ResolvedWorkConfigurationRevision.resolve(history, rule.effectiveFrom)
}
