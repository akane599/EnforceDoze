// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.design.directions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** How one direction paints the three Doze states; each state has a fill and a pattern (SPEC 10.1). */
internal data class StripStyle(
    val active: Color,
    val light: Color,
    val deep: Color,
    val pattern: Color,
    val tick: Color,
    val notch: Color,
    val missing: Color,
    val ground: Color,
)

/** One direction's colours, mapped onto Material 3 roles so the standard components carry the world. */
internal data class StudyPalette(
    val ground: Color,
    val raised: Color,
    val hairline: Color,
    val ink: Color,
    val quiet: Color,
    val accent: Color,
    val onAccent: Color,
    val good: Color,
    val warning: Color,
    val critical: Color,
    val strip: StripStyle,
) {
    fun scheme(): ColorScheme = darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = raised,
        onPrimaryContainer = ink,
        secondaryContainer = accent.copy(alpha = 0.22f),
        onSecondaryContainer = ink,
        background = ground,
        onBackground = ink,
        surface = ground,
        onSurface = ink,
        surfaceVariant = raised,
        onSurfaceVariant = quiet,
        surfaceContainer = raised,
        surfaceContainerHigh = raised,
        outline = hairline,
        outlineVariant = hairline,
        error = critical,
    )
}

/** A type scale from one family; figures are tabular wherever a number can change (SPEC 10.1). */
internal fun studyTypography(family: FontFamily, display: TextUnit = 44.sp, tracking: Double = 0.0): Typography {
    fun style(size: TextUnit, weight: FontWeight, line: TextUnit, spacing: Double = tracking) = TextStyle(
        fontFamily = family,
        fontSize = size,
        fontWeight = weight,
        lineHeight = line,
        letterSpacing = spacing.em,
        fontFeatureSettings = "tnum",
    )
    return Typography(
        displayMedium = style(display, FontWeight.SemiBold, display * 1.05, -0.01),
        headlineMedium = style(28.sp, FontWeight.SemiBold, 34.sp),
        headlineSmall = style(24.sp, FontWeight.Medium, 30.sp),
        titleLarge = style(22.sp, FontWeight.Medium, 28.sp),
        titleMedium = style(17.sp, FontWeight.Medium, 24.sp),
        titleSmall = style(15.sp, FontWeight.Medium, 20.sp),
        bodyLarge = style(16.sp, FontWeight.Normal, 23.sp),
        bodyMedium = style(15.sp, FontWeight.Normal, 21.sp),
        bodySmall = style(13.sp, FontWeight.Normal, 18.sp),
        labelLarge = style(15.sp, FontWeight.Medium, 20.sp),
        labelMedium = style(13.sp, FontWeight.Medium, 17.sp, 0.02),
        labelSmall = style(12.sp, FontWeight.Medium, 16.sp, 0.03),
    )
}

/** The Material shell every direction shares: top app bar, one scrolling column, the navigation bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudyScreen(
    palette: StudyPalette,
    typography: Typography,
    title: String,
    back: Boolean = false,
    largeTitle: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    MaterialTheme(colorScheme = palette.scheme(), typography = typography) {
        Scaffold(
            containerColor = palette.ground,
            topBar = {
                TopAppBar(
                    title = { Text(title, style = if (largeTitle) typography.headlineSmall else typography.titleLarge) },
                    navigationIcon = {
                        if (back) {
                            IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                        }
                    },
                    actions = {
                        if (back) IconButton(onClick = {}) { Icon(Icons.Outlined.Share, contentDescription = "Export") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.ground, titleContentColor = palette.ink),
                )
            },
            bottomBar = { if (!back) StudyNavigation(palette) },
        ) { padding ->
            Box(Modifier.fillMaxSize().background(palette.ground).padding(padding)) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), content = content)
            }
        }
    }
}

@Composable
private fun StudyNavigation(palette: StudyPalette) {
    NavigationBar(containerColor = palette.raised) {
        listOf(
            "Home" to Icons.Outlined.Home,
            "Log" to Icons.Outlined.History,
            "Profiles" to Icons.Outlined.Tune,
            "Settings" to Icons.Outlined.Settings,
        ).forEachIndexed { i, (label, icon) ->
            NavigationBarItem(selected = i == 0, onClick = {}, icon = { Icon(icon, contentDescription = null) }, label = { Text(label) })
        }
    }
}

internal fun hhmm(t: java.time.LocalTime): String = "%02d:%02d".format(t.hour, t.minute)
