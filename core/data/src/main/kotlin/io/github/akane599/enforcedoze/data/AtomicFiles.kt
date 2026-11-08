// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Writes [text] to a temporary file that is synced and then renamed over [name] in [dir], so a crash leaves
 * either the old file or the new one. Blocking: call it on an IO dispatcher.
 */
internal fun writeAtomically(dir: File, name: String, text: String) {
    if (!dir.isDirectory && !dir.mkdirs()) throw IOException("cannot create $dir")
    val target = File(dir, name)
    val temporary = File(dir, "$name.tmp")
    FileOutputStream(temporary).use { out ->
        out.write(text.toByteArray())
        out.fd.sync()
    }
    Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
}

/** Moves a file that no longer reads out of the way, kept for bug reports, so it is treated as missing. */
internal fun setAside(file: File, cause: Exception) {
    val aside = File(file.parentFile, "${file.name}.unreadable-${System.currentTimeMillis()}")
    if (!file.renameTo(aside)) throw IOException("${file.name} cannot be read and cannot be moved aside", cause)
}
