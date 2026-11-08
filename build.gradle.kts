// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.licensee) apply false
}

// Quality gates (SPEC 12.5): detekt and ktlint run from their CLI jars over the Kotlin modules, so
// they work with AGP 9's built-in Kotlin and need no per-module plugin (ADR 0010). Upstream's Java
// code is not checked; it is replaced, not restyled (SPEC 12.1).
val detektCli: Configuration by configurations.creating
val ktlintCli: Configuration by configurations.creating {
    // ktlint-cli publishes a plain and a shadowed variant; only the shadowed one bundles its CLI parser.
    attributes { attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.SHADOWED)) }
}

dependencies {
    detektCli(libs.detekt.cli)
    ktlintCli(libs.ktlint.cli)
}

val kotlinRoots = listOf("core", "feature", "testing")
val kotlinSourceDirs = provider {
    kotlinRoots.map(::file).filter { it.isDirectory }.flatMap { root ->
        root.walkTopDown().maxDepth(3).filter { it.name == "src" && it.isDirectory }.toList()
    }
}

tasks.register<JavaExec>("detekt") {
    group = "verification"
    description = "Runs detekt over the Kotlin modules."
    classpath = detektCli
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    val config = file("config/detekt/detekt.yml")
    val report = layout.buildDirectory.file("reports/detekt/detekt.html")
    inputs.files(kotlinSourceDirs).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(config)
    outputs.file(report)
    argumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                "--input", kotlinSourceDirs.get().joinToString(",") { it.path },
                "--config", config.path,
                "--build-upon-default-config",
                "--excludes", "**/build/**",
                "--report", "html:${report.get().asFile.path}",
            )
        },
    )
}

val ktlintPatterns = listOf("core/**/src/**/*.kt", "testing/**/src/**/*.kt", "feature/**/src/**/*.kt", "**/*.gradle.kts", "!**/build/**")

tasks.register<JavaExec>("ktlintCheck") {
    group = "verification"
    description = "Checks Kotlin style with ktlint (.editorconfig)."
    classpath = ktlintCli
    mainClass.set("com.pinterest.ktlint.Main")
    workingDir = rootDir
    inputs.files(kotlinSourceDirs).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(".editorconfig")
    args(listOf("--relative") + ktlintPatterns)
}

tasks.register<JavaExec>("ktlintFormat") {
    group = "formatting"
    description = "Formats Kotlin sources with ktlint."
    classpath = ktlintCli
    mainClass.set("com.pinterest.ktlint.Main")
    workingDir = rootDir
    args(listOf("--relative", "--format") + ktlintPatterns)
}
