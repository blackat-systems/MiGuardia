package com.blackatsystems.miguardia.core.domain.repository

import java.time.LocalDate

sealed class LocalDataException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class DuplicateObjectiveAbbreviationException(
    val abbreviation: String,
    cause: Throwable? = null,
) : LocalDataException("Ya existe un objetivo con la abreviatura $abbreviation.", cause)

class InvalidLocalDataException(
    message: String,
    cause: Throwable? = null,
) : LocalDataException(message, cause)

class DuplicateHolidayDateException(cause: Throwable? = null) :
    LocalDataException("Ya existe un feriado en esa fecha.", cause)

class EmptyShiftNoteException : LocalDataException("La nota no puede estar vacía.")

class ConflictingLocalWriteException(message: String) : LocalDataException(message)

class InvalidVacationRangeException :
    LocalDataException("La fecha final de vacaciones no puede ser anterior a la inicial.")

class OverlappingVacationException :
    LocalDataException("Ese período se superpone con otras vacaciones existentes.")

class VacationMedicalLeaveConflictException :
    LocalDataException("Las vacaciones no pueden superponerse con una carpeta médica.")

class DuplicateWorkPlaceException(cause: Throwable? = null) :
    LocalDataException("Ese lugar ya fue agregado para esta forma de trabajar.", cause)

class DuplicateWorkTypeNameException(
    val name: String,
    cause: Throwable? = null,
) : LocalDataException("Ya existe un tipo de trabajo llamado $name.", cause)

class DuplicateWorkTemplateException(cause: Throwable? = null) :
    LocalDataException("Ya existe ese lugar, tipo de trabajo y horario.", cause)

class MissingWorkplaceRuleException(
    val date: LocalDate,
    cause: Throwable? = null,
) : LocalDataException("El lugar no tiene reglas vigentes para el $date.", cause)

class RetroactiveWorkplaceRuleException(
    val effectiveFrom: LocalDate,
    cause: Throwable? = null,
) : LocalDataException(
    "No se pueden cambiar desde $effectiveFrom las reglas de una jornada que ya comenzó.",
    cause,
)

class InvalidV2SelectionException(message: String) : LocalDataException(message)
