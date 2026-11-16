// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.akane599.enforcedoze.R
import io.github.akane599.enforcedoze.design.Ed
import io.github.akane599.enforcedoze.design.EdTopBar
import io.github.akane599.enforcedoze.design.RawLine
import io.github.akane599.enforcedoze.design.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The GPL-3.0 text and the notice exactly as shipped (SPEC 0.3 rule 6), set as raw lines. */
@Composable
internal fun LicenceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lines = produceState(emptyList<String>()) {
        value = withContext(Dispatchers.IO) {
            listOf(NOTICE, LICENCE).flatMap { name -> context.assets.open(name).bufferedReader().use { it.readLines() } + "" }
        }
    }.value
    Scaffold(containerColor = Ed.palette.ground, topBar = { EdTopBar(stringResource(R.string.ui_licence), onBack = onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = Space.gutter)) {
            items(lines) { Text(it, style = RawLine, color = Ed.palette.ink) }
        }
    }
}

private const val LICENCE = "LICENSE.txt"
private const val NOTICE = "NOTICE.txt"
