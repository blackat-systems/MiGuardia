package com.blackatsystems.miguardia.stage12

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.util.UUID

/** Owns the explicit functional and technical receipts below trusted Android files. */
internal object Stage12NativeReceiptFiles {
    fun prepareOutput(filesDir: File, runId: String, identity: String, name: String): File {
        require(UUID.fromString(runId).toString() == runId)
        require(name in setOf("month-navigation", "today-detail", "fixed-date-weather", "activity-admission", "summary-observation",
            "c090-identical-shifts", "w010-applied-widget", "x015-query-conservation", "aosp-work-fixture", "b1-owned-load-edit",
            "owned-work-private-security", "owned-work-police", "owned-work-nursing", "owned-work-medicine",
            "selection-batch-draft", "selection-batch-policies", "selection-batch-clear-cas", "selection-batch-recovery",
            "recurring-policies", "recurring-occurrences", "recurring-lifecycle",
            "calendar-medical", "calendar-situations", "calendar-holidays", "calendar-vacations",
            "calendar-cancellation-pending", "calendar-medical-privacy",
            "temporal-shift-endpoints", "temporal-availability-endpoints", "temporal-resumption-widget",
            "availability-history", "availability-reviews", "availability-conflicts", Stage12CalendarDetailContract.SLOT,
            Stage12CalendarDetailContract.ACTIVATION_SLOT,
            Stage12C097InstitutionContract.SLOT, Stage12B1HumanContract.SLOT))
        require(identity.isNotEmpty() && identity.length <= 4096)
        val files = filesDir.canonicalFile
        check(files.isDirectory) { "QA files base is unavailable" }
        val relative = "reports/artifacts/stage12-normal-$runId"
        var cursor = files
        relative.split('/').forEach { part ->
            cursor = File(cursor, part)
            check(!Files.isSymbolicLink(cursor.toPath())) { "Symbolic link below the trusted QA base" }
        }
        val root = Stage12PathPolicy(files).resolve(relative)
        val existed = Files.exists(root.toPath(), NOFOLLOW_LINKS)
        check(root.isDirectory || root.mkdirs()) { "Owned QA artifact directory could not be created" }
        val fence = Stage12PathPolicy(root)
        val owner = fence.segment("owner.json")
        check(!Files.isSymbolicLink(owner.toPath())) { "Owner must be a regular owned file" }
        if (existed) {
            check(owner.isFile && owner.length() <= 4096 && owner.readText() == identity) { "Artifact owner differs" }
        } else {
            check(owner.createNewFile()) { "Owner already exists" }
            owner.writeText(identity)
        }
        val output = fence.segment("$name.json")
        check(!Files.exists(output.toPath(), NOFOLLOW_LINKS)) { "Receipt entry already exists, including a dangling link" }
        return output
    }

    fun publish(output: File, content: String, primaryFailure: Throwable?) {
        try {
            // Claim and write through one handle; never reopen an existing entry.
            Files.newOutputStream(output.toPath(), CREATE_NEW, WRITE).bufferedWriter(Charsets.UTF_8).use {
                it.write(content)
            }
        } catch (publicationFailure: Throwable) {
            if (primaryFailure == null) throw publicationFailure
            if (publicationFailure !== primaryFailure) primaryFailure.addSuppressed(publicationFailure)
            throw primaryFailure
        }
    }
}
