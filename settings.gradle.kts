// SPDX-License-Identifier: GPL-3.0-or-later

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // libsu and upstream's libsuperuser are published only on JitPack.
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.topjohnwu.libsu")
                includeGroup("eu.chainfire")
            }
        }
    }
}

rootProject.name = "EnforceDoze"

include(":app")
include(":core:parse")
include(":core:profile")
include(":core:doze")
include(":core:doze-android")
include(":core:data")
include(":core:evidence")
include(":core:recommend")
include(":core:design")
include(":core:hidden-api-stubs")
include(":core:privilege-api")
include(":core:privilege-worker")
include(":core:privilege")
include(":testing:fixtures")
include(":testing:silent-audio")
