// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.design

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * The scopes a shared-element transition needs (SPEC 10.3: the Home verdict travels into the report). The app
 * provides them around its navigation; screenshot tests and previews leave them null, and nothing moves.
 */
public val LocalSharedTransitionScope: androidx.compose.runtime.ProvidableCompositionLocal<SharedTransitionScope?> =
    staticCompositionLocalOf { null }

/** The animated scope of the navigation entry being shown. */
public val LocalEntryAnimationScope: androidx.compose.runtime.ProvidableCompositionLocal<AnimatedVisibilityScope?> =
    compositionLocalOf { null }

/** Marks the element that stands for [key] on two screens, so it moves between them; a no-op without the scopes. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
public fun Modifier.sharedElement(key: Any?): Modifier {
    if (key == null) return this
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalEntryAnimationScope.current ?: return this
    return with(shared) { this@sharedElement.sharedBounds(rememberSharedContentState(key), animated) }
}

/** The verdict of one session (or the night it starts), as Home and the report both show it. */
public fun verdictKey(sessionId: Long): String = "verdict-$sessionId"
