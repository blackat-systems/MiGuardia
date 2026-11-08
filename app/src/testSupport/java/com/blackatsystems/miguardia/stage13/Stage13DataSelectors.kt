package com.blackatsystems.miguardia.stage13

/** Métodos de la etapa 13 (Datos y archivos) que el ejecutor QA normal admite juntos. */
object Stage13DataSelectors {
    private const val NOTES = "com.blackatsystems.miguardia.stage13.Stage13NotesDeviceTest"
    private const val PHOTOS = "com.blackatsystems.miguardia.stage13.Stage13PhotosDeviceTest"
    private const val BACKUP = "com.blackatsystems.miguardia.stage13.Stage13BackupDeviceTest"
    private const val LEGACY = "com.blackatsystems.miguardia.stage13.Stage13LegacyCopiesDeviceTest"

    /** Sesión humana: sólo se corre sola y con Joaquín frente al teléfono. */
    const val HUMAN: String =
        "com.blackatsystems.miguardia.stage13.Stage13DataHumanTest#realPickersSaveOpenAndImportWithTheHumanHand"

    val family: Set<String> = setOf(HUMAN) + listOf(
        "notesFollowTheExactShiftAndEveryEditIsConscious",
        "noteImagesAreChosenOpenedRemovedAndSavedByteForByte",
        "savingANoteRemovedElsewhereNeverRecreatesItAndKeepsTheDraft",
    ).map { "$NOTES#$it" }.toSet() + listOf(
        "photosOpenOnTheVisibleMonthChangeMonthAndReturnToTheCalendar",
        "importAssociateReplaceAndDeleteKeepBytesAndOrientation",
        "missingPhotoShowsItsOwnErrorAndRetryNeverSubstitutesAnother",
    ).map { "$PHOTOS#$it" } + listOf(
        "createEncryptedAndPlainCopiesAndCancelTheDestination",
        "openingRejectsWrongPasswordAndDamagedFilesWithoutWriting",
        "mergeAppliesEachConsciousResolution",
        "replaceAllNeedsTheExactPhraseAndRestoresTheCopyExactly",
        "otherTimelineBlocksMergeButKeepsConsciousReplacement",
        "applyingCannotBeLeftAndPendingRecoveryNeedsARealRetry",
    ).map { "$BACKUP#$it" } + listOf(
        "version5CopyIsReadAndRestoredExactly",
        "version6CopyIsReadAndRestoredExactly",
        "version7CopyIsReadAndRestoredExactly",
    ).map { "$LEGACY#$it" }
}
