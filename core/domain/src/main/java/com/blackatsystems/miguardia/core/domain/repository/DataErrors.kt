package com.blackatsystems.miguardia.core.domain.repository

sealed class LocalDataException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class DuplicateObjectiveAbbreviationException(
    val abbreviation: String,
    cause: Throwable? = null,
) : LocalDataException("Ya existe un objetivo con la abreviatura $abbreviation.", cause)

class DuplicateScheduleCombinationException(
    cause: Throwable? = null,
) : LocalDataException("Ya existe esa combinación exacta de objetivo y horario.", cause)

class InvalidLocalDataException(
    message: String,
    cause: Throwable? = null,
) : LocalDataException(message, cause)
