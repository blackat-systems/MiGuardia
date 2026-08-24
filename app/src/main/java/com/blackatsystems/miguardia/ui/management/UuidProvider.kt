package com.blackatsystems.miguardia.ui.management

import java.util.UUID

fun interface UuidProvider {
    fun newUuid(): UUID
}
