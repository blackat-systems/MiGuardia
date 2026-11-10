package com.blackatsystems.miguardia.stage12

import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID

/** Observed producer inputs only; never reads stores or awards case/round credit. */
internal object Stage12ObservedFixture {
    fun fingerprint(value: String, identities: Map<String, String>): String {
        require(identities.keys.all { it.matches(Regex("[a-z0-9_]+")) })
        require(identities.values.all { UUID.fromString(it).toString() == it })
        require(identities.values.toSet().size == identities.size)
        val normalized = identities.toSortedMap().entries.fold(value) { text, (role, id) -> text.replace(id, "<role:$role>") }
        return MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    fun work(case: String, anchor: LocalDate, source: UUID, writes: List<V2ShiftWrite>,
        roles: Map<String, String>, auxiliary: Map<String, String>): Map<String, Any> {
        require(case in setOf("C090", "X015") && writes.isNotEmpty())
        val first = writes.first();val snapshot = first.snapshot
        val refs = linkedMapOf("source_id" to source.toString(), "timeline_id" to snapshot.timelineId.toString(),
            "revision_id" to snapshot.configurationRevisionId.toString(), "place_id" to snapshot.workPlaceId.toString(),
            "objective_id" to snapshot.objectiveId.toString(), "template_id" to snapshot.templateId.toString(),
            "type_id" to snapshot.workTypeId.toString())
        val semantics = linkedMapOf<String, Any>("anchor_date" to anchor.toString(),
            "dates" to writes.map { it.shift.localStartDate.toString() },
            "start_at" to writes.map { it.shift.startAt.toString() }, "end_at" to writes.map { it.shift.endAt.toString() },
            "zone_id" to first.shift.zoneId.id, "status" to writes.map { it.shift.status.name },
            "payload_sha256" to fingerprint(writes.toString(), roles), "auxiliary" to auxiliary.toSortedMap())
        return mapOf("schema" to "stage12-observed-fixture-v1", "case" to case, "memory_only" to false,
            "owned_roles" to roles.toSortedMap(), "received_references" to refs, "semantic" to semantics)
    }

    fun memory(fixture: Stage12W010Fixture, variants: Int): Map<String, Any> {
        val roles = linkedMapOf<String, String>()
        fixture.shifts.forEachIndexed { index, value ->
            roles["shift_$index"] = value.shift.id.toString()
            roles["objective_$index"] = value.snapshot.objectiveId.toString()
            roles["place_$index"] = value.snapshot.workPlaceId.toString()
            roles["template_$index"] = value.snapshot.templateId.toString()
            roles["type_$index"] = value.snapshot.workTypeId.toString()
        }
        roles["timeline"] = fixture.shifts.first().snapshot.timelineId.toString()
        roles["revision"] = fixture.shifts.first().snapshot.configurationRevisionId.toString()
        return mapOf("schema" to "stage12-observed-fixture-v1", "case" to "W010", "memory_only" to true,
            "owned_roles" to roles.toSortedMap(), "received_references" to emptyMap<String, String>(),
            "semantic" to mapOf("owner_date" to fixture.ownerDate.toString(), "day_off" to fixture.dayOff.toString(),
                "zone_id" to fixture.zone.id, "start_at" to fixture.start.toString(), "end_at" to fixture.end.toString(),
                "boundaries" to Stage12W010Fixture.Boundary.entries.associate { it.name to fixture.now(it).toString() },
                "shift_count" to fixture.shifts.size, "variants_completed" to variants,
                "payload_sha256" to fingerprint(fixture.input.toString() + fixture.weather, roles)))
    }
}
