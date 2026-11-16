// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The back stack's keys (ADR 0029: route keys stay in `:app`, so swapping the library touches only `:app`). */
@Serializable
internal sealed interface Route : NavKey

/** The four destinations of the navigation bar, Home first (ADR 0028 study). */
@Serializable
internal sealed interface Top : Route

@Serializable internal data object HomeKey : Top

@Serializable internal data object LogKey : Top

@Serializable internal data object ProfilesKey : Top

@Serializable internal data object SettingsKey : Top

@Serializable internal data class ReportKey(val sessionId: Long) : Route

@Serializable internal data class ProfileKey(val profileId: String) : Route

@Serializable internal data class EditorKey(val profileId: String) : Route

@Serializable internal data object PlanKey : Route

@Serializable internal data object RecommendedKey : Route

@Serializable internal data class AllowlistKey(val offer: String? = null) : Route

@Serializable internal data object SensorsKey : Route

@Serializable internal data object KeepAwakeKey : Route

@Serializable internal data object LicenceKey : Route

/** Onboarding from its first step; [fromSettings] when the owner came to check privilege again. */
@Serializable internal data class OnboardingKey(val fromSettings: Boolean = false) : Route
