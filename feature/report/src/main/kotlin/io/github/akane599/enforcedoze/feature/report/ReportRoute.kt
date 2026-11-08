// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.report

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.design.EdTopBar
import io.github.akane599.enforcedoze.design.Space
import io.github.akane599.enforcedoze.ui.StateNotice

/** Where the app's navigation lands for a report: the ViewModel's state, the screen and the late sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun ReportRoute(viewModel: ReportViewModel, navigation: ReportNavigation, modifier: Modifier = Modifier) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    when (val current = ui) {
        ReportUi.Loading -> Placeholder(null, navigation, modifier)

        ReportUi.Missing -> Placeholder(stringResource(R.string.report_missing), navigation, modifier)

        is ReportUi.Ready -> {
            val actions = object : ReportActions {
                override fun back() = navigation.back()
                override fun export() = navigation.export(current.state.summary.sessionId)
                override fun late() = viewModel.openLate()
                override fun allowlist() = navigation.allowlist(null)
                override fun notice(notice: StateNotice) = navigation.notice(notice)
                override fun seen(notice: StateNotice) = viewModel.seen(notice.id)
            }
            ReportScreen(current.state, actions, modifier)
            sheet?.let { open ->
                ModalBottomSheet(onDismissRequest = viewModel::closeLate, containerColor = Ed.palette.raised) {
                    LateSheetContent(
                        current.state.lateCandidates,
                        open,
                        onRecord = viewModel::recordLate,
                        onAllowlist = { packageName ->
                            viewModel.closeLate()
                            navigation.allowlist(packageName)
                        },
                        onClose = viewModel::closeLate,
                    )
                }
            }
        }
    }
}

/** Where the report sends the owner; the app's navigation implements it (ADR 0029). */
public interface ReportNavigation {
    public fun back()

    /** The system share sheet with the session's ZIP (SPEC 8.7). */
    public fun export(sessionId: Long)

    /** The allowlist, with [packageName] offered first when the owner came from a late arrival. */
    public fun allowlist(packageName: String?)

    public fun notice(notice: StateNotice)
}

/** The report's frame before it has a session to show: back only, and a line when the session is gone. */
@Composable
private fun Placeholder(text: String?, navigation: ReportNavigation, modifier: Modifier) {
    Scaffold(modifier, containerColor = Ed.palette.ground, topBar = { EdTopBar("", onBack = navigation::back) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            text?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = Ed.palette.quiet, modifier = Modifier.padding(Space.xl))
            }
        }
    }
}
