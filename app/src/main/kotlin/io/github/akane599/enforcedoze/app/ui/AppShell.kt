// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import io.github.akane599.enforcedoze.design.LocalSharedTransitionScope
import io.github.akane599.enforcedoze.design.LocalEntryAnimationScope
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.SharedTransitionLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.akane599.enforcedoze.R
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.ui.ActionOutcome
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The app: the navigation bar (a rail on wide screens) around one Navigation 3 back stack (ADR 0029). */
@Composable
@OptIn(ExperimentalMaterial3AdaptiveApi::class) // list-detail scenes, adaptive-navigation3 1.3.0 (ADR 0029)
internal fun EnforceDozeApp(services: AppServices, opening: Route? = null, opened: () -> Unit = {}) {
    val backStack = rememberNavBackStack(if (services.prefs.onboarded) HomeKey else OnboardingKey())
    val navigator = remember(backStack) { Navigator(backStack) }
    // A notification or a tile's long press names a screen; before onboarding is done, onboarding stays.
    LaunchedEffect(opening) {
        if (opening != null && services.prefs.onboarded) navigator.open(opening)
        if (opening != null) opened()
    }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val saved = stringResource(R.string.ui_saved)
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val show: (ActionOutcome) -> Unit = { outcome ->
        val text = when (outcome) {
            ActionOutcome.Done, is ActionOutcome.Applied -> saved
            is ActionOutcome.Unavailable -> outcome.reason
            is ActionOutcome.Failed -> outcome.raw
        }
        scope.launch { snackbar.showSnackbar(text) }
    }
    val write: (android.net.Uri?) -> Unit = { uri ->
        val job = pending
        pending = null
        if (uri != null && job != null) scope.launch { show(run(services.exports, job, uri)) }
    }
    val zip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ZIP), write)
    val json = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON), write)
    val exports = remember(zip, json) {
        object : ExportRequests {
            override fun days(from: LocalDate, to: LocalDate) {
                pending = "days:$from:$to"
                zip.launch("enforcedoze-$from-$to.zip")
            }

            override fun all() {
                pending = "all"
                zip.launch("enforcedoze-all.zip")
            }

            override fun profile(profileId: String) {
                pending = "profile:$profileId"
                json.launch(services.exports.profileFileName(profileId))
            }

            override fun outcome(outcome: ActionOutcome) = show(outcome)
        }
    }
    val onboarding = backStack.lastOrNull() is OnboardingKey
    val layout = if (onboarding) {
        NavigationSuiteType.None
    } else {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    }
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TABS.forEach { (key, icon, label) ->
                item(
                    selected = navigator.tab == key,
                    onClick = { navigator.tab(key) },
                    icon = { Icon(icon, contentDescription = null) },
                    label = { Text(stringResource(label)) },
                )
            }
        },
        layoutType = layout,
        containerColor = Ed.palette.ground,
    ) {
        Box(Modifier.fillMaxSize()) {
            // SPEC 10.3: the Home verdict moves into the report; each entry gets its animated scope for that.
            SharedTransitionLayout(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                    NavDisplay(
                        backStack = backStack,
                        onBack = navigator::back,
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                            remember { animatedScopeDecorator() },
                        ),
                        // Two panes from the medium width class up (SPEC 9): Log beside its report, Profiles beside a profile.
                        sceneStrategies = listOf(rememberListDetailSceneStrategy()),
                        sharedTransitionScope = this,
                        entryProvider = appEntries(services, navigator, exports),
                    )
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** Gives each entry the animated scope NavDisplay runs it in, for shared elements in feature screens. */
private fun animatedScopeDecorator(): NavEntryDecorator<NavKey> = NavEntryDecorator(
    decorate = { entry ->
        CompositionLocalProvider(LocalEntryAnimationScope provides LocalNavAnimatedContentScope.current) { entry.Content() }
    },
)

private val TABS = listOf(
    Triple(HomeKey, Icons.Outlined.Home, R.string.ui_tab_home),
    Triple(LogKey, Icons.Outlined.History, R.string.ui_tab_log),
    Triple(ProfilesKey, Icons.Outlined.Tune, R.string.ui_tab_profiles),
    Triple(SettingsKey, Icons.Outlined.Settings, R.string.ui_tab_settings),
)

private const val ZIP = "application/zip"
private const val JSON = "application/json"

/** Runs a pending export job, `days:<from>:<to>`, `all` or `profile:<id>`, into the picked file. */
private suspend fun run(exports: Exports, job: String, uri: android.net.Uri): ActionOutcome {
    val parts = job.split(':', limit = 3)
    return when (parts[0]) {
        "days" -> exports.writeDays(uri, LocalDate.parse(parts[1]), LocalDate.parse(parts[2]))
        "all" -> exports.writeAll(uri)
        else -> exports.writeProfile(uri, parts[1])
    }
}
