// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A switch with its title and a quiet line explaining both states; the whole row toggles it. As a section's
 * [heading], the title takes the section header's size and the space above it.
 */
@Composable
public fun SwitchRow(
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    heading: Boolean = false,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = if (heading) Space.xl else 0.dp)
            .heightIn(min = Sizes.touch)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = Space.s),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = if (heading) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                color = if (enabled || heading) Ed.palette.ink else Ed.palette.quiet,
                modifier = if (heading) Modifier.semantics { heading() } else Modifier,
            )
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = Ed.palette.quiet)
        }
        Spacer(Modifier.width(Space.m))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.align(Alignment.CenterVertically))
    }
}
