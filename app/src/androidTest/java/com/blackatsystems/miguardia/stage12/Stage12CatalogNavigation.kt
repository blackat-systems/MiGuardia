package com.blackatsystems.miguardia.stage12

/** Only the owned manual catalog widget. Callbacks perform the real Compose observations/actions. */
internal class Stage12CatalogNavigation {
    enum class Kind { CATALOG, PLACE }
    enum class Child { CREATE_PLACE, PLACE, TEMPLATE, ADD_TEMPLATE }
    data class Header(val count: Int, val enabled: Boolean?, val description: String?)
    private val observations = mutableListOf<MutableMap<String, Any>>()
    private var halted = false

    fun navigate(kind: Kind, child: Child, context: () -> Boolean, header: () -> Header,
        interact: () -> Unit, expanded: () -> Header, catalog: () -> Int, requiredChild: () -> Int) {
        check(!halted)
        check(if (kind == Kind.CATALOG) child in setOf(Child.CREATE_PLACE, Child.PLACE)
            else child in setOf(Child.TEMPLATE, Child.ADD_TEMPLATE))
        val event = linkedMapOf<String, Any>("kind" to kind.name, "child" to child.name, "phase" to "CONTEXT")
        observations += event
        try {
            val compatible = context()
            event["context_compatible"] = compatible
            check(compatible)
            event["phase"] = "HEADER"
            val before = header()
            observe(event, "header", kind, before)
            checkValid(kind, before)
            val decision = if (kind == Kind.PLACE) "SELECT" else if (state(kind, before) == "COLLAPSED") "EXPAND" else "VERIFY"
            event["decision"] = decision
            if (decision != "VERIFY") {
                event["phase"] = "ACTION"
                event["action_started"] = true
                interact()
                event["action_returned"] = true
            }
            event["phase"] = "POST"
            val after = expanded()
            observe(event, "post", kind, after)
            checkValid(kind, after)
            check(state(kind, after) == "EXPANDED")
            event["phase"] = "CATALOG"
            val catalogCount = catalog()
            event["catalog_count"] = catalogCount
            check(catalogCount == 1)
            event["phase"] = "CHILD"
            val childCount = requiredChild()
            event["child_count"] = childCount
            check(childCount == 1)
            event["phase"] = "COMPLETE"
        } catch (error: Throwable) {
            halted = true
            throw error
        }
    }

    private fun state(kind: Kind, header: Header): String = when (header.description) {
        if (kind == Kind.CATALOG) "Instituciones desplegadas" else "Horarios desplegados" -> "EXPANDED"
        if (kind == Kind.CATALOG) "Instituciones contraídas" else "Horarios contraídos" -> "COLLAPSED"
        null -> "MISSING"
        else -> "UNKNOWN"
    }
    private fun observe(event: MutableMap<String, Any>, prefix: String, kind: Kind, header: Header) {
        event[prefix + "_count"] = header.count
        if (header.count == 1) {
            header.enabled?.let { event[prefix + "_enabled"] = it }
            event[prefix + "_state"] = state(kind, header)
        }
    }
    private fun checkValid(kind: Kind, header: Header) {
        check(header.count == 1 && header.enabled == true && state(kind, header) in setOf("EXPANDED", "COLLAPSED"))
    }
    fun projection(): Map<String, Any> = linkedMapOf("schema" to "stage12-selection-catalog-navigation-v1",
        "observations" to observations.map { it.toMap() })
}
