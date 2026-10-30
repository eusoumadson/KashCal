package org.onekash.kashcal.ui.components

import android.location.Geocoder
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.onekash.kashcal.R
import org.onekash.kashcal.data.tasks_org.TasksOrgLocation
import org.onekash.kashcal.data.tasks_org.TasksOrgPlace
import org.onekash.kashcal.data.tasks_org.TasksOrgPlaceDraft
import org.onekash.kashcal.data.tasks_org.TasksOrgRepeatFrom
import org.onekash.kashcal.domain.tasks.RecurrenceChoice
import org.onekash.kashcal.domain.tasks.SimpleRecurrence
import org.onekash.kashcal.util.DateTimeUtils
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

// ---------------------------------------------------------------------------------------------
// Date field (due date and start date share it)
// ---------------------------------------------------------------------------------------------

/**
 * A date, optional time and all-day switch, for the due date and the start date of a task. The
 * all-day flag and the instant are always changed together, so they can't disagree (report
 * §4.1). [onChange] gets the new instant (null clears the date) and the all-day flag.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TaskDateField(
    label: String,
    emptyLabel: String,
    ms: Long?,
    allDay: Boolean,
    timeFormat: String,
    is24Hour: Boolean,
    zone: ZoneId,
    onChange: (ms: Long?, allDay: Boolean) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Text(label, style = MaterialTheme.typography.labelLarge)
    Row(verticalAlignment = Alignment.CenterVertically) {
        AssistChip(
            onClick = { showDatePicker = true },
            label = { Text(ms?.let { DateTimeUtils.formatEventDateShort(it, allDay) } ?: emptyLabel) }
        )
        if (ms != null && !allDay) {
            Spacer(Modifier.width(8.dp))
            AssistChip(
                onClick = { showTimePicker = true },
                label = {
                    Text(DateTimeUtils.formatEventTime(ms, false, DateTimeUtils.getTimePattern(timeFormat, is24Hour)))
                }
            )
        }
        if (ms != null) {
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { onChange(null, allDay) }) { Text("\u2715") }
        }
    }
    if (ms != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.task_editor_all_day), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = allDay,
                onCheckedChange = { checked ->
                    // Snap to local midnight so the flag and the value agree.
                    onChange(adjustForAllDay(ms, checked, zone), checked)
                }
            )
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            // The picker works in UTC days; hand it the local calendar day.
            initialSelectedDateMillis = localDayAsUtcMidnight(ms ?: System.currentTimeMillis(), zone)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { picked ->
                        onChange(combineDateWithExistingTime(picked, ms, allDay, zone), allDay)
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.task_editor_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.task_editor_cancel)) }
            }
        ) { DatePicker(state = pickerState) }
    }

    if (showTimePicker && ms != null) {
        val initialTime = remember { Instant.ofEpochMilli(ms).atZone(zone).toLocalTime() }
        val timeState = rememberTimePickerState(
            initialHour = initialTime.hour,
            initialMinute = initialTime.minute,
            is24Hour = is24Hour
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(setTimeOfDay(ms, timeState.hour, timeState.minute, zone), allDay)
                    showTimePicker = false
                }) { Text(stringResource(R.string.task_editor_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.task_editor_cancel)) }
            },
            text = { TimePicker(state = timeState) }
        )
    }
}

/** The local calendar day of [ms] as UTC midnight, which is what a Material date picker takes. */
internal fun localDayAsUtcMidnight(ms: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        .atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()

/**
 * Keeps the calendar day of [utcMidnightMs] (as a date picker returns it) while preserving the
 * time of day of [existing] for a timed task, or snapping to local midnight for an all-day one.
 */
internal fun combineDateWithExistingTime(
    utcMidnightMs: Long,
    existing: Long?,
    allDay: Boolean,
    zone: ZoneId
): Long {
    val date = Instant.ofEpochMilli(utcMidnightMs).atZone(ZoneId.of("UTC")).toLocalDate()
    val time = if (allDay || existing == null) {
        LocalTime.MIDNIGHT
    } else {
        Instant.ofEpochMilli(existing).atZone(zone).toLocalTime()
    }
    return date.atTime(time).atZone(zone).toInstant().toEpochMilli()
}

/** Replaces the time of day of [ms] with [hour]:[minute] in [zone]. */
internal fun setTimeOfDay(ms: Long, hour: Int, minute: Int, zone: ZoneId): Long {
    val date = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    return date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
}

/** Snaps [ms] to local midnight when turning all-day on; leaves it otherwise. */
internal fun adjustForAllDay(ms: Long, allDay: Boolean, zone: ZoneId): Long {
    if (!allDay) return ms
    val date: LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    return date.atStartOfDay(zone).toInstant().toEpochMilli()
}

// ---------------------------------------------------------------------------------------------
// Recurrence
// ---------------------------------------------------------------------------------------------

/**
 * The repeat section: a frequency, how often, the weekdays of a weekly rule, an optional total
 * count, and what the next date is measured from. A rule set in Tasks.org that this editor
 * can't build shows as a custom chip and is written back unchanged unless the user picks
 * another option.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskRecurrenceSection(
    choice: RecurrenceChoice,
    onChoice: (RecurrenceChoice) -> Unit,
    repeatFrom: TasksOrgRepeatFrom,
    onRepeatFrom: (TasksOrgRepeatFrom) -> Unit
) {
    Text(stringResource(R.string.task_editor_repeat_label), style = MaterialTheme.typography.labelLarge)
    val simple = (choice as? RecurrenceChoice.Simple)?.rule

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = choice == RecurrenceChoice.None,
            onClick = { onChoice(RecurrenceChoice.None) },
            label = { Text(stringResource(R.string.task_repeat_none)) }
        )
        SimpleRecurrence.Frequency.entries.forEach { freq ->
            FilterChip(
                selected = simple?.frequency == freq,
                onClick = {
                    val base = simple ?: SimpleRecurrence(freq)
                    onChoice(
                        RecurrenceChoice.Simple(
                            base.copy(
                                frequency = freq,
                                weekdays = if (freq == SimpleRecurrence.Frequency.WEEKLY) base.weekdays else emptySet()
                            )
                        )
                    )
                },
                label = { Text(stringResource(frequencyLabel(freq))) }
            )
        }
        if (choice is RecurrenceChoice.Custom) {
            FilterChip(
                selected = true,
                onClick = {},
                label = { Text(stringResource(R.string.task_repeat_custom, choice.raw)) }
            )
        }
    }
    if (choice is RecurrenceChoice.Custom) {
        Text(
            stringResource(R.string.task_repeat_custom_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (simple != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.task_repeat_every), style = MaterialTheme.typography.bodyLarge)
            Stepper(
                value = simple.interval,
                min = 1,
                onChange = { onChoice(RecurrenceChoice.Simple(simple.copy(interval = it))) }
            )
            Text(stringResource(unitLabel(simple.frequency)), style = MaterialTheme.typography.bodyLarge)
        }
        if (simple.frequency == SimpleRecurrence.Frequency.WEEKLY) {
            Text(stringResource(R.string.task_repeat_on_days), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = day in simple.weekdays,
                        onClick = {
                            val days = if (day in simple.weekdays) simple.weekdays - day else simple.weekdays + day
                            onChoice(RecurrenceChoice.Simple(simple.copy(weekdays = days)))
                        },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) }
                    )
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
            FilterChip(
                selected = simple.count == null,
                onClick = { onChoice(RecurrenceChoice.Simple(simple.copy(count = null))) },
                label = { Text(stringResource(R.string.task_repeat_ends_never)) }
            )
            FilterChip(
                selected = simple.count != null,
                onClick = { onChoice(RecurrenceChoice.Simple(simple.copy(count = simple.count ?: 5))) },
                label = { Text(stringResource(R.string.task_repeat_ends_after, simple.count ?: 5)) }
            )
        }
        if (simple.count != null) {
            Stepper(
                value = simple.count,
                min = 1,
                onChange = { onChoice(RecurrenceChoice.Simple(simple.copy(count = it))) }
            )
        }
    }

    if (choice != RecurrenceChoice.None) {
        Text(stringResource(R.string.task_repeat_from_label), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = repeatFrom == TasksOrgRepeatFrom.DUE_DATE,
                onClick = { onRepeatFrom(TasksOrgRepeatFrom.DUE_DATE) },
                label = { Text(stringResource(R.string.task_repeat_from_due)) }
            )
            FilterChip(
                selected = repeatFrom == TasksOrgRepeatFrom.COMPLETION_DATE,
                onClick = { onRepeatFrom(TasksOrgRepeatFrom.COMPLETION_DATE) },
                label = { Text(stringResource(R.string.task_repeat_from_completion)) }
            )
        }
    }
}

@Composable
private fun Stepper(value: Int, min: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { if (value > min) onChange(value - 1) }, enabled = value > min) { Text("\u2212") }
        Text(value.toString(), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = { onChange(value + 1) }) { Text("+") }
    }
}

private fun frequencyLabel(f: SimpleRecurrence.Frequency): Int = when (f) {
    SimpleRecurrence.Frequency.DAILY -> R.string.task_repeat_daily
    SimpleRecurrence.Frequency.WEEKLY -> R.string.task_repeat_weekly
    SimpleRecurrence.Frequency.MONTHLY -> R.string.task_repeat_monthly
    SimpleRecurrence.Frequency.YEARLY -> R.string.task_repeat_yearly
}

private fun unitLabel(f: SimpleRecurrence.Frequency): Int = when (f) {
    SimpleRecurrence.Frequency.DAILY -> R.string.task_repeat_unit_days
    SimpleRecurrence.Frequency.WEEKLY -> R.string.task_repeat_unit_weeks
    SimpleRecurrence.Frequency.MONTHLY -> R.string.task_repeat_unit_months
    SimpleRecurrence.Frequency.YEARLY -> R.string.task_repeat_unit_years
}

// ---------------------------------------------------------------------------------------------
// Place
// ---------------------------------------------------------------------------------------------

/**
 * The place section: pick one saved place and whether to be reminded on arriving, on leaving,
 * or both. A task has at most one place in Tasks.org. "New place" opens [NewPlaceDialog].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskPlaceSection(
    places: List<TasksOrgPlace>,
    location: TasksOrgLocation?,
    onLocation: (TasksOrgLocation?) -> Unit,
    onNewPlace: () -> Unit
) {
    Text(stringResource(R.string.task_editor_place_label), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = location == null,
            onClick = { onLocation(null) },
            label = { Text(stringResource(R.string.task_place_none)) }
        )
        places.forEach { place ->
            FilterChip(
                selected = location?.placeId == place.id,
                onClick = {
                    // A first pick reminds on arrival, the common case.
                    onLocation(
                        TasksOrgLocation(
                            placeId = place.id,
                            arrival = location?.arrival ?: true,
                            departure = location?.departure ?: false
                        )
                    )
                },
                label = { Text(place.displayName.ifBlank { place.name }) }
            )
        }
        AssistChip(onClick = onNewPlace, label = { Text("+ " + stringResource(R.string.task_place_new)) })
    }
    if (location != null) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = location.arrival,
                onClick = { onLocation(location.copy(arrival = !location.arrival)) },
                label = { Text(stringResource(R.string.task_place_on_arrival)) }
            )
            FilterChip(
                selected = location.departure,
                onClick = { onLocation(location.copy(departure = !location.departure)) },
                label = { Text(stringResource(R.string.task_place_on_leaving)) }
            )
        }
    }
}

/**
 * Validates the new-place form into a [TasksOrgPlaceDraft], or null when it can't be saved.
 * Latitude and longitude accept a comma as the decimal mark.
 */
internal fun parsePlaceDraft(
    name: String,
    address: String,
    latitude: String,
    longitude: String,
    radius: String
): TasksOrgPlaceDraft? {
    val lat = latitude.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in -90.0..90.0 } ?: return null
    val lon = longitude.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in -180.0..180.0 } ?: return null
    val meters = radius.trim().toIntOrNull()?.takeIf { it > 0 } ?: return null
    if (name.isBlank()) return null
    return TasksOrgPlaceDraft(name.trim(), address.trim(), lat, lon, meters)
}

/**
 * Creates a saved place. Tasks.org does no geocoding, so the user gives coordinates; the
 * address button asks the device's geocoder to fill them in, and the fields stay editable when
 * it has nothing (no network, or no geocoder on the device).
 */
@Composable
internal fun NewPlaceDialog(onDismiss: () -> Unit, onSave: (TasksOrgPlaceDraft) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf(TasksOrgPlaceDraft.DEFAULT_RADIUS_M.toString()) }
    var message by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.task_place_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.task_place_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(address, { address = it }, label = { Text(stringResource(R.string.task_place_address)) }, modifier = Modifier.fillMaxWidth())
                TextButton(
                    enabled = address.isNotBlank(),
                    onClick = {
                        scope.launch {
                            val found = withContext(Dispatchers.IO) { geocode(context, address) }
                            if (found == null) {
                                message = R.string.task_place_not_found
                            } else {
                                latitude = found.first.toString()
                                longitude = found.second.toString()
                                message = null
                            }
                        }
                    }
                ) { Text(stringResource(R.string.task_place_find_coordinates)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(latitude, { latitude = it }, label = { Text(stringResource(R.string.task_place_latitude)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(longitude, { longitude = it }, label = { Text(stringResource(R.string.task_place_longitude)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                OutlinedTextField(radius, { radius = it }, label = { Text(stringResource(R.string.task_place_radius)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                message?.let {
                    Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val draft = parsePlaceDraft(name, address, latitude, longitude, radius)
                if (draft == null) message = R.string.task_place_invalid else onSave(draft)
            }) { Text(stringResource(R.string.task_place_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.task_editor_cancel)) } }
    )
}

/** Looks up [address]; null when the device has no geocoder, no network, or no match. */
@Suppress("DEPRECATION")
private fun geocode(context: android.content.Context, address: String): Pair<Double, Double>? = try {
    if (!Geocoder.isPresent()) null
    else Geocoder(context).getFromLocationName(address, 1)?.firstOrNull()?.let { it.latitude to it.longitude }
} catch (_: Exception) {
    null
}
