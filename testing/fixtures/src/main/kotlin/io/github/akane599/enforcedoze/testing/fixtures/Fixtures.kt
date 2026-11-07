// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.testing.fixtures

import java.io.File

/** One capture directory, `fixtures/<model>/api<SDK>_<build id>/` (see `fixtures/README.md`). */
public data class Capture(val model: String, val build: String, val dir: File) {
    /** SDK level from the directory name, for example 37 for `api37_CE2A.260420.051.A3`. */
    public val sdk: Int get() = build.substringAfter("api").substringBefore('_').toInt()

    /** True for dumps from a real phone rather than an emulator. */
    public val isPhone: Boolean get() = !model.startsWith("sdk_")

    public fun has(name: String): Boolean = File(dir, name).isFile

    public fun text(name: String): String = File(dir, name).readText()

    override fun toString(): String = "$model/$build"
}

public object Fixtures {
    private const val PROPERTY = "enforcedoze.fixtures"

    public val root: File by lazy {
        val path = System.getProperty(PROPERTY)
            ?: error("System property $PROPERTY is not set; run the tests through Gradle")
        File(path).also { check(it.isDirectory) { "Fixture directory $it does not exist" } }
    }

    /** Every capture, emulators and phones, in a stable order. */
    public val captures: List<Capture> by lazy {
        root.listFiles().orEmpty().filter { it.isDirectory }.sortedBy { it.name }.flatMap { model ->
            model.listFiles().orEmpty()
                .filter { File(it, "meta.txt").isFile }
                .sortedBy { it.name }
                .map { Capture(model.name, it.name, it) }
        }
    }

    /** Captures that contain [name]. */
    public fun with(name: String): List<Capture> = captures.filter { it.has(name) }

    public fun capture(model: String, build: String): Capture = captures.single { it.model == model && it.build == build }
}
