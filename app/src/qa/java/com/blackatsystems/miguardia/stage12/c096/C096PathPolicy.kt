package com.blackatsystems.miguardia.stage12.c096

import java.io.File

/** Canonical filesystem fence; this does not claim to sandbox arbitrary Android code. */
internal class C096PathPolicy(root: File) {
    val root: File = root.canonicalFile

    fun resolve(relative: String): File {
        require(relative.isNotEmpty() && relative.length <= 240)
        require(!relative.contains('\\') && !relative.contains(':'))
        require(relative.none { it.code < 32 || it.code == 127 })
        val parts = relative.split('/')
        require(parts.all { it.isNotEmpty() && it != "." && it != ".." })
        check(root.canonicalFile == root) { "Root identity changed" }
        val candidate = File(root, relative)
        val canonical = candidate.canonicalFile
        require(canonical.toPath().startsWith(root.toPath()) && canonical != root)
        require(candidate.absoluteFile == canonical) { "Alias or symbolic link rejected" }
        return canonical
    }

    fun segment(name: String): File {
        require('/' !in name)
        return resolve(name)
    }

    fun assertOwned(file: File): File {
        val relative = root.toPath().relativize(file.absoluteFile.toPath()).toString().replace('\\', '/')
        return resolve(relative).also { require(it == file.absoluteFile) }
    }
}
