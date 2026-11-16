// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import io.github.akane599.enforcedoze.feature.profiles.SwitchOverViewModel
import io.github.akane599.enforcedoze.feature.profiles.SwitchOverRoute
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.window.core.layout.WindowSizeClass
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.design.Space
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.akylas.enforcedoze.R
import io.github.akane599.enforcedoze.feature.allowlist.AllowlistNavigation
import io.github.akane599.enforcedoze.feature.allowlist.AllowlistRoute
import io.github.akane599.enforcedoze.feature.allowlist.AllowlistViewModel
import io.github.akane599.enforcedoze.feature.home.HomeNavigation
import io.github.akane599.enforcedoze.feature.home.HomeRoute
import io.github.akane599.enforcedoze.feature.home.HomeViewModel
import io.github.akane599.enforcedoze.feature.log.LogNavigation
import io.github.akane599.enforcedoze.feature.log.LogRoute
import io.github.akane599.enforcedoze.feature.log.LogViewModel
import io.github.akane599.enforcedoze.feature.onboarding.OnboardingRoute
import io.github.akane599.enforcedoze.feature.onboarding.OnboardingStep
import io.github.akane599.enforcedoze.feature.onboarding.OnboardingViewModel
import io.github.akane599.enforcedoze.feature.plan.PlanNavigation
import io.github.akane599.enforcedoze.feature.plan.PlanRoute
import io.github.akane599.enforcedoze.feature.plan.PlanViewModel
import io.github.akane599.enforcedoze.feature.plan.RecommendedRoute
import io.github.akane599.enforcedoze.feature.profiles.ProfileDetailRoute
import io.github.akane599.enforcedoze.feature.profiles.ProfileDetailViewModel
import io.github.akane599.enforcedoze.feature.profiles.ProfileEditorRoute
import io.github.akane599.enforcedoze.feature.profiles.ProfileEditorViewModel
import io.github.akane599.enforcedoze.feature.profiles.ProfilesNavigation
import io.github.akane599.enforcedoze.feature.profiles.ProfilesRoute
import io.github.akane599.enforcedoze.feature.profiles.ProfilesViewModel
import io.github.akane599.enforcedoze.feature.report.ReportNavigation
import io.github.akane599.enforcedoze.feature.report.ReportRoute
import io.github.akane599.enforcedoze.feature.report.ReportViewModel
import io.github.akane599.enforcedoze.feature.sensors.SensorsNavigation
import io.github.akane599.enforcedoze.feature.sensors.SensorsRoute
import io.github.akane599.enforcedoze.feature.sensors.SensorsViewModel
import io.github.akane599.enforcedoze.feature.settings.R as SettingsR
import io.github.akane599.enforcedoze.feature.settings.SettingsNavigation
import io.github.akane599.enforcedoze.feature.settings.SettingsRoute
import io.github.akane599.enforcedoze.feature.settings.SettingsViewModel
import io.github.akane599.enforcedoze.ui.ActionOutcome
import io.github.akane599.enforcedoze.ui.AppPickerScreen
import io.github.akane599.enforcedoze.ui.PickApp
import io.github.akane599.enforcedoze.ui.StateNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Every screen of SPEC 9.1 as a Navigation 3 entry, each with its view model scoped to the entry. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class) // list-detail scenes, adaptive-navigation3 1.3.0 (ADR 0029)
internal fun appEntries(
    services: AppServices,
    navigator: Navigator,
    exports: ExportRequests,
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    entry<HomeKey> {
        HomeRoute(viewModel { HomeViewModel(AppHomeSource(services, services.reports)) }, homeNavigation(navigator))
    }
    entry<ReportKey>(metadata = ListDetailSceneStrategy.detailPane(LOG_SCENE)) { key ->
        ReportRoute(
            viewModel(key = "report-${key.sessionId}") { ReportViewModel(key.sessionId, AppReportSource(services, services.reports)) },
            reportNavigation(services, navigator, exports),
        )
    }
    entry<LogKey>(metadata = ListDetailSceneStrategy.listPane(LOG_SCENE) { Placeholder(R.string.ui_pick_session) }) {
        LogRoute(viewModel { LogViewModel(AppLogSource(services)) }, logNavigation(navigator, exports))
    }
    entry<ProfilesKey>(metadata = ListDetailSceneStrategy.listPane(PROFILES_SCENE) { Placeholder(R.string.ui_pick_profile) }) {
        ProfilesRoute(
            viewModel { ProfilesViewModel(AppProfilesSource(services)) },
            profilesNavigation(navigator, exports),
            selected = navigator.selectedProfile,
        )
    }
    entry<ProfileKey>(metadata = ListDetailSceneStrategy.detailPane(PROFILES_SCENE)) { key ->
        ProfileDetailRoute(
            viewModel(key = "profile-${key.profileId}") { ProfileDetailViewModel(key.profileId, AppProfilesSource(services)) },
            profilesNavigation(navigator, exports),
            showBack = !twoPanes(),
        )
    }
    entry<EditorKey> { key ->
        ProfileEditorRoute(
            viewModel(key = "editor-${key.profileId}") { ProfileEditorViewModel(key.profileId, AppProfilesSource(services)) },
            profilesNavigation(navigator, exports),
        )
    }
    entry<PlanKey> { PlanRoute(viewModel { PlanViewModel(AppPlanSource(services)) }, planNavigation(navigator)) }
    entry<RecommendedKey> { RecommendedRoute(viewModel { PlanViewModel(AppPlanSource(services)) }, planNavigation(navigator)) }
    entry<SettingsKey> {
        val context = LocalContext.current
        SettingsRoute(viewModel { SettingsViewModel(AppSettingsSource(services)) }, settingsNavigation(context, navigator, exports))
    }
    entry<AllowlistKey> { key ->
        AllowlistRoute(
            viewModel(key = "allowlist-${key.offer}") { AllowlistViewModel(AppAllowlistSource(services, key.offer)) },
            allowlistNavigation(navigator),
        )
    }
    entry<SensorsKey> { SensorsRoute(viewModel { SensorsViewModel(AppSensorsSource(services)) }, sensorsNavigation(navigator)) }
    entry<KeepAwakeKey> { KeepAwakePicker(services, navigator) }
    entry<ClientPickerKey> { ClientPicker(services, navigator) }
    entry<SwitchOverKey> { SwitchOver(services, navigator, exports) }
    entry<LicenceKey> { LicenceScreen(onBack = navigator::back) }
    entry<OnboardingKey> { key ->
        val start = if (key.fromSettings) OnboardingStep.PRIVILEGE else OnboardingStep.WELCOME
        OnboardingRoute(
            viewModel { OnboardingViewModel(AppOnboardingSource(services), createSavedStateHandle(), start) },
            navigation = { if (key.fromSettings) navigator.back() else navigator.restart(HomeKey) },
        )
    }
}

private const val LOG_SCENE = "log"
private const val PROFILES_SCENE = "profiles"

/** The width where the list-detail strategy shows two panes (SPEC 9: from the medium width class up). */
@Composable
private fun twoPanes(): Boolean =
    currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

/** The empty detail pane beside a list: what choosing an item will show. */
@Composable
private fun Placeholder(text: Int) {
    Box(Modifier.fillMaxSize().background(Ed.palette.ground), contentAlignment = Alignment.Center) {
        Text(
            stringResource(text),
            style = MaterialTheme.typography.bodyLarge,
            color = Ed.palette.quiet,
            modifier = Modifier.padding(Space.xl),
        )
    }
}

private fun homeNavigation(navigator: Navigator) = object : HomeNavigation {
    override fun changePlan() = navigator.go(PlanKey)
    override fun openReport(sessionId: Long) = navigator.go(ReportKey(sessionId))
    override fun notice(notice: StateNotice) = navigator.notice(notice)
}

private fun reportNavigation(services: AppServices, navigator: Navigator, exports: ExportRequests) = object : ReportNavigation {
    override fun back() = navigator.back()
    override fun export(sessionId: Long) {
        services.graph.scope.launch {
            val share = services.exports.shareReport(sessionId)
            if (share == null) {
                exports.outcome(ActionOutcome.Unavailable(services.graph.app.getString(R.string.ui_report_gone)))
            } else {
                services.graph.app.startActivity(share.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
    override fun allowlist(packageName: String?) = navigator.go(AllowlistKey(packageName))
    override fun notice(notice: StateNotice) = navigator.notice(notice)
}

private fun allowlistNavigation(navigator: Navigator) = object : AllowlistNavigation {
    override fun notice(notice: StateNotice) = navigator.notice(notice)
}

private fun logNavigation(navigator: Navigator, exports: ExportRequests) = object : LogNavigation {
    override fun open(sessionId: Long) = navigator.go(ReportKey(sessionId))
    override fun export(from: LocalDate, to: LocalDate) = exports.days(from, to)
    override fun notice(notice: StateNotice) = navigator.notice(notice)
}

private fun profilesNavigation(navigator: Navigator, exports: ExportRequests) = object : ProfilesNavigation {
    override fun detail(profileId: String) = navigator.go(ProfileKey(profileId))
    override fun edit(profileId: String) = navigator.go(EditorKey(profileId))
    override fun export(profileId: String) = exports.profile(profileId)
    override fun notice(notice: StateNotice) = navigator.notice(notice)
    override fun back() = navigator.back()
}

private fun planNavigation(navigator: Navigator) = object : PlanNavigation {
    override fun recommended() = navigator.go(RecommendedKey)
    override fun profiles() = navigator.tab(ProfilesKey)
    override fun notice(notice: StateNotice) = navigator.notice(notice)
    override fun back() = navigator.back()
}

private fun sensorsNavigation(navigator: Navigator) = object : SensorsNavigation {
    override fun snapshot(sessionId: Long) = navigator.go(ReportKey(sessionId))
    override fun notice(notice: StateNotice) = navigator.notice(notice)
}

private fun settingsNavigation(context: Context, navigator: Navigator, exports: ExportRequests) = object : SettingsNavigation {
    override fun privilege() = navigator.go(OnboardingKey(fromSettings = true))
    override fun addKeepAwake() = navigator.go(KeepAwakeKey)
    override fun addClient() = navigator.go(ClientPickerKey)
    override fun switchOver() = navigator.go(SwitchOverKey)
    override fun exportAll() = exports.all()

    /** Android's per-app language page (API 33+). */
    override fun language() {
        context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }

    override fun licence() = navigator.go(LicenceKey)
    override fun allowlist() = navigator.go(AllowlistKey())
    override fun sensors() = navigator.go(SensorsKey)
    override fun notice(notice: StateNotice) = navigator.notice(notice)
}

/** The app picker for the keep-awake list (SPEC 5.5); the app itself and apps already chosen are left out. */
@Composable
private fun KeepAwakePicker(services: AppServices, navigator: Navigator) {
    val apps = produceState(emptyList<PickApp>()) {
        value = withContext(Dispatchers.IO) { services.labels.apps(exclude = services.prefs.keepAwake + services.graph.app.packageName) }
    }.value
    AppPickerScreen(
        title = stringResource(SettingsR.string.settings_keep_awake),
        apps = apps,
        onPick = { app ->
            services.prefs.keepAwake = services.prefs.keepAwake + app.packageName
            navigator.back()
        },
        onBack = navigator::back,
    )
}

/** The app picker for a new automation client: the chosen app gets a token, shown once back in Settings (ADR 0033). */
@Composable
private fun ClientPicker(services: AppServices, navigator: Navigator) {
    val apps = produceState(emptyList<PickApp>()) {
        value = withContext(Dispatchers.IO) {
            val clients = services.automation.clients.clients().map { it.packageName }
            services.labels.apps(exclude = clients.toSet() + services.graph.app.packageName)
        }
    }.value
    AppPickerScreen(
        title = stringResource(SettingsR.string.settings_client_picker),
        apps = apps,
        onPick = { app ->
            services.automation.issue(app.packageName)
            navigator.back()
        },
        onBack = navigator::back,
    )
}

/** The switch-over from another setup (SPEC 11.4); it opens profiles the same way the profile screens do. */
@Composable
private fun SwitchOver(services: AppServices, navigator: Navigator, exports: ExportRequests) {
    SwitchOverRoute(viewModel { SwitchOverViewModel(AppSwitchOverSource(services)) }, profilesNavigation(navigator, exports))
}
