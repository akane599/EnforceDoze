// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.log

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.akane599.enforcedoze.design.Ed
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Picks the days to export (SPEC 9.1 "export a range"), [first] to [last] being the days the log holds. It opens on
 * the last week the log holds. The picker counts days in UTC; they are handed on as plain dates.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun ExportRangeDialog(first: LocalDate, last: LocalDate, onExport: (from: LocalDate, to: LocalDate) -> Unit, onDismiss: () -> Unit) {
    fun millis(day: LocalDate) = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = millis(maxOf(first, last.minusDays(WEEK_DAYS))),
        initialSelectedEndDateMillis = millis(last),
        initialDisplayedMonthMillis = millis(last),
        yearRange = first.year..last.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = day(utcTimeMillis) in first..last
        },
    )
    val from = state.selectedStartDateMillis
    val to = state.selectedEndDateMillis
    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = DatePickerDefaults.colors(containerColor = Ed.palette.raised),
        confirmButton = {
            TextButton(onClick = { if (from != null && to != null) onExport(day(from), day(to)) }, enabled = from != null && to != null) {
                Text(stringResource(R.string.log_export_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.log_cancel)) } },
    ) {
        DateRangePicker(
            state,
            title = { Text(stringResource(R.string.log_export_title), modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
            showModeToggle = true,
        )
    }
}

private const val WEEK_DAYS = 6L
