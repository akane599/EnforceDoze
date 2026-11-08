// SPDX-License-Identifier: GPL-3.0-or-later

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

/**
 * A `:feature:x` module (ADR 0029): an Android library with Compose, its screens rendered by Roborazzi on
 * Robolectric's native graphics, depending on `:core:design` and on pure-JVM core modules only.
 */
class FeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        pluginManager.apply("io.github.takahirom.roborazzi")
        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        extensions.configure<LibraryExtension> {
            namespace = "io.github.akane599.enforcedoze.feature." + path.removePrefix(":feature:").replace('-', '.')
            compileSdk = 37
            defaultConfig { minSdk = 34 }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_21
                targetCompatibility = JavaVersion.VERSION_21
            }
            buildFeatures { compose = true }
            testOptions { unitTests.isIncludeAndroidResources = true }
        }
        dependencies {
            val bom = platform(libs.findLibrary("compose-bom").get())
            add("implementation", project(":core:design"))
            add("implementation", bom)
            add("implementation", libs.findLibrary("compose-material-icons-extended").get())
            add("implementation", libs.findLibrary("compose-ui-tooling-preview").get())
            add("implementation", libs.findLibrary("lifecycle-viewmodel-compose").get())
            add("implementation", libs.findLibrary("lifecycle-runtime-compose").get())
            add("implementation", libs.findLibrary("kotlinx-coroutines-core").get())
            add("testImplementation", project(":core:design"))
            add("testImplementation", project(":testing:screenshots"))
            add("testImplementation", bom)
            listOf(
                "junit", "truth", "robolectric", "roborazzi", "roborazzi-compose", "roborazzi-junit-rule",
                "compose-ui-test-junit4", "androidx-test-core", "kotlinx-coroutines-test", "espresso-core",
            ).forEach { add("testImplementation", libs.findLibrary(it).get()) }
            add("debugImplementation", bom)
            add("debugImplementation", libs.findLibrary("compose-ui-test-manifest").get())
        }
        tasks.withType<Test>().configureEach {
            systemProperty("robolectric.graphicsMode", "NATIVE")
            systemProperty("enforcedoze.root", rootProject.layout.projectDirectory.asFile.absolutePath)
            // Robolectric's FileDescriptor interceptor (SDK 36) reaches jdk.internal.access on JDK 17+ (ADR 0028).
            jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
        }
    }
}
