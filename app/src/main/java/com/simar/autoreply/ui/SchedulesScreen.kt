package com.simar.autoreply.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.simar.autoreply.data.AppStore
import com.simar.autoreply.data.Defaults
import com.simar.autoreply.data.Mode
import com.simar.autoreply.data.Schedule
import com.simar.autoreply.logic.ModeState
import java.util.Calendar

/** Monday-first, as people read a week. Values are Calendar.DAY_OF_WEEK. */
private val WeekOrder = listOf(
    Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
    Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY,
)
private val DayLetter = mapOf(
    Calendar.MONDAY to "M", Calendar.TUESDAY to "T", Calendar.WEDNESDAY to "W", Calendar.THURSDAY to "T",
    Calendar.FRIDAY to "F", Calendar.SATURDAY to "S", Calendar.SUNDAY to "S",
)
private val Weekdays = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)

private fun daysLabel(days: Set<Int>) = when (days) {
    WeekOrder.toSet() -> "Every day"
    Weekdays -> "Weekdays"
    setOf(Calendar.SATURDAY, Calendar.SUNDAY) -> "Weekends"
    else -> WeekOrder.filter { it in days }.joinToString(" ") {
        Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, it) }
            .getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, java.util.Locale.getDefault()) ?: ""
    }
}

@Composable
fun SchedulesScreen(modifier: Modifier) {
    val context = LocalContext.current
    val schedules by AppStore.schedules.collectAsState()
    val modes by AppStore.modes.collectAsState()
    var editing by remember { mutableStateOf<Schedule?>(null) }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                Text("Schedules", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Turn modes on and off automatically, like Sleeping every night or In class from your timetable.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(schedules, key = { it.id }) { schedule ->
            val mode = modes.find { it.id == schedule.modeId } ?: return@items
            ScheduleCard(
                schedule, mode,
                onToggle = { on -> AppStore.upsertSchedule(schedule.copy(enabled = on)); ModeState.refresh(context) },
                onClick = { editing = schedule },
            )
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                    .clickable(enabled = modes.isNotEmpty()) {
                        editing = Schedule(Defaults.newId(), modes.first().id, 23 * 60, 7 * 60, WeekOrder.toSet())
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(Icons.Rounded.Add, size = 40, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("New schedule", style = MaterialTheme.typography.titleSmall)
            }
        }
        if (schedules.isEmpty()) {
            item {
                Text(
                    "Tip: a schedule only turns a mode on during its hours. You can still switch modes by hand any time, and turning a scheduled mode off keeps it off until the schedule's end.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }

    editing?.let { s ->
        ScheduleEditor(
            initial = s,
            modes = modes,
            isNew = schedules.none { it.id == s.id },
            onDismiss = { editing = null },
            onSave = { AppStore.upsertSchedule(it); ModeState.refresh(context); editing = null },
            onDelete = { AppStore.deleteSchedule(s.id); ModeState.refresh(context); editing = null },
        )
    }
}

@Composable
private fun ScheduleCard(schedule: Schedule, mode: Mode, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val context = LocalContext.current
    val accent = modeColor(mode.color)
    val dim = if (schedule.enabled) 1f else 0.45f
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(modeIcon(mode.icon), size = 46, tint = accent.copy(alpha = dim))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${ModeState.formatMinute(context, schedule.startMinute)} – ${ModeState.formatMinute(context, schedule.endMinute)}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim),
            )
            Text(
                "${mode.name} · ${daysLabel(schedule.days)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim),
            )
        }
        Switch(schedule.enabled, onToggle)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleEditor(
    initial: Schedule,
    modes: List<Mode>,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Schedule) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var modeId by remember { mutableStateOf(initial.modeId) }
    var start by remember { mutableIntStateOf(initial.startMinute) }
    var end by remember { mutableIntStateOf(initial.endMinute) }
    var days by remember { mutableStateOf(initial.days) }
    val accent = modeColor(modes.find { it.id == modeId }?.color ?: 0)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Schedule, size = 52, tint = accent)
                Spacer(Modifier.width(14.dp))
                Text(if (isNew) "New schedule" else "Edit schedule", style = MaterialTheme.typography.headlineSmall)
            }

            Label("MODE")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                modes.forEach { m ->
                    val selected = m.id == modeId
                    val c = modeColor(m.color)
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(if (selected) c.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(1.5.dp, if (selected) c else Color.Transparent, CircleShape)
                            .clickable { modeId = m.id }
                            .padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(modeIcon(m.icon), null, tint = c, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(m.name, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Label("TIME")
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimeBox("Starts", ModeState.formatMinute(context, start), Modifier.weight(1f)) {
                    pickTime(context, start) { start = it }
                }
                Icon(
                    Icons.Rounded.ArrowForward, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
                TimeBox("Ends", ModeState.formatMinute(context, end), Modifier.weight(1f)) {
                    pickTime(context, end) { end = it }
                }
            }
            if (end <= start) {
                Text(
                    "Ends the next morning.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Label("DAYS")
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                WeekOrder.forEach { d ->
                    val on = d in days
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (on) accent else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { days = if (on) days - d else days + d },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            DayLetter.getValue(d),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickDays("Every day") { days = WeekOrder.toSet() }
                QuickDays("Weekdays") { days = Weekdays }
                QuickDays("Weekends") { days = setOf(Calendar.SATURDAY, Calendar.SUNDAY) }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isNew) TextButton(onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.weight(1f))
                TextButton(onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onSave(initial.copy(modeId = modeId, startMinute = start, endMinute = end, days = days)) },
                    enabled = days.isNotEmpty() && start != end,
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
                ) { Text("Save") }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun TimeBox(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun QuickDays(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
