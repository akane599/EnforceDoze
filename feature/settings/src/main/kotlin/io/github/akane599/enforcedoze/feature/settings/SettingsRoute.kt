// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.settings

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.ui.StateNotice

/** The confirmation or picker Settings has open. */
private enum class SettingsDialog { RETENTION, RESTORE, RESET, FLAG_SYNC }

@Composable
public fun SettingsRoute(viewModel: SettingsViewModel, navigation: SettingsNavigation, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    val current = state ?: return Box(modifier.fillMaxSize().background(Ed.palette.ground))
    val context = LocalContext.current
    val actions = object : SettingsActions {
        override fun privilege() = navigation.privilege()
        override fun addKeepAwake() = navigation.addKeepAwake()
        override fun removeKeepAwake(app: AppRef) = viewModel.removeKeepAwake(app)
        override fun reapplyOnDrift(on: Boolean) = viewModel.reapplyOnDrift(on)

        override fun wallpaperAccent(on: Boolean) = viewModel.wallpaperAccent(on)
        override fun automation(on: Boolean) = viewModel.automation(on)
        override fun addClient() = navigation.addClient()
        override fun makeToken(packageName: String) = viewModel.makeToken(packageName)
        override fun removeClient(packageName: String) = viewModel.removeClient(packageName)
        override fun copyToken(token: String) = copySensitive(context, token)
        override fun tokenShown() = viewModel.tokenShown()
        override fun retention() = run { dialog = SettingsDialog.RETENTION }
        override fun exportAll() = navigation.exportAll()
        override fun language() = navigation.language()

        // Turning sync back on needs no warning; turning it off does (SPEC 5.6: advanced, explained).
        override fun flagSync(disabled: Boolean) =
            if (disabled) dialog = SettingsDialog.FLAG_SYNC else viewModel.run(SettingsCommand.FLAG_SYNC, flagSyncDisabled = false)
        override fun restoreStock() = run { dialog = SettingsDialog.RESTORE }
        override fun reset() = run { dialog = SettingsDialog.RESET }
        override fun licence() = navigation.licence()
        override fun allowlist() = navigation.allowlist()
        override fun sensors() = navigation.sensors()
        override fun notice(notice: StateNotice) = navigation.notice(notice)
        override fun seen(notice: StateNotice) = viewModel.seen(notice)
    }
    SettingsScreen(current, actions, modifier, status)
    val close = { dialog = null }
    when (dialog) {
        SettingsDialog.RETENTION -> RetentionDialog(current.retentionDays, onPick = {
            close()
            if (it != current.retentionDays) viewModel.retention(it)
        }, onDismiss = close)

        SettingsDialog.RESTORE -> ConfirmDialog(
            stringResource(R.string.settings_restore_title),
            stringResource(R.string.settings_restore_body),
            stringResource(R.string.settings_restore_stock),
            onConfirm = {
                close()
                viewModel.run(SettingsCommand.RESTORE_STOCK)
            },
            onDismiss = close,
        )

        SettingsDialog.RESET -> ConfirmDialog(
            stringResource(R.string.settings_reset_title),
            stringResource(R.string.settings_reset_body),
            stringResource(R.string.settings_reset),
            onConfirm = {
                close()
                viewModel.run(SettingsCommand.RESET)
            },
            onDismiss = close,
            critical = true,
        )

        SettingsDialog.FLAG_SYNC -> ConfirmDialog(
            stringResource(R.string.settings_flag_sync_title),
            stringResource(R.string.settings_flag_sync_body),
            stringResource(R.string.settings_flag_sync_confirm),
            onConfirm = {
                close()
                viewModel.run(SettingsCommand.FLAG_SYNC, flagSyncDisabled = true)
            },
            onDismiss = close,
        )

        null -> Unit
    }
}

/** The token goes to the clipboard marked sensitive (API 33+), so keyboards and the preview hide it. */
private fun copySensitive(context: Context, token: String) {
    val clip = ClipData.newPlainText(context.getString(R.string.settings_token_clip), token)
    clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}
