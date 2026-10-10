package com.simar.autoreply.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneDisabled
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.simar.autoreply.data.AppStore
import com.simar.autoreply.logic.ModeState

/** Choices for "repeat callers", in minutes. 0 = text on every call. */
private val IntervalSteps = listOf(0, 5, 10, 15, 30, 45, 60, 120, 240, 480, 720, 1440)
private val UrgentSteps = listOf(1, 2, 3, 5, 10, 15)
private val DeclineDelaySteps = listOf(0, 3, 5, 10, 15, 20, 30)

private fun intervalLabel(minutes: Int) = when {
    minutes == 0 -> "Always"
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} m"
}

private fun step(steps: List<Int>, current: Int, up: Boolean): Int =
    if (up) steps.firstOrNull { it > current } ?: current
    else steps.lastOrNull { it < current } ?: steps.first()

@Composable
fun SettingsScreen(modifier: Modifier, resumeTick: Int) {
    val context = LocalContext.current
    val settings by AppStore.settings.collectAsState()
    var permTick by remember { mutableIntStateOf(0) }
    val canNotify = remember(resumeTick, permTick) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permTick++ }
    val batteryOk = remember(resumeTick) { Setup.isIgnoringBatteryOptimizations(context) }

    var pendingRestore by remember { mutableStateOf<String?>(null) }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(AppStore.exportJson().toByteArray()) }
        }.onSuccess { Toast.makeText(context, "Backup saved", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(context, "Couldn't save: ${it.message}", Toast.LENGTH_LONG).show() }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }
            .onSuccess { pendingRestore = it }
            .onFailure { Toast.makeText(context, "Couldn't read: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp)) }

        item { GroupLabel("CALLS") }
        item {
            Group {
                SettingRow(Icons.Rounded.PhoneDisabled, "Decline the call", "Hang up before texting the caller") {
                    Switch(settings.declineCalls, { v -> AppStore.updateSettings { it.copy(declineCalls = v) } })
                }
                if (settings.declineCalls) {
                    Divider()
                    SettingRow(
                        Icons.Rounded.HourglassTop, "Ring before declining",
                        if (settings.declineDelaySeconds == 0) "Decline straight away" else "Time to pick up yourself first",
                    ) {
                        Stepper(
                            value = if (settings.declineDelaySeconds == 0) "Instant" else "${settings.declineDelaySeconds} s",
                            canMinus = settings.declineDelaySeconds > 0,
                            canPlus = settings.declineDelaySeconds < DeclineDelaySteps.last(),
                            onMinus = { AppStore.updateSettings { it.copy(declineDelaySeconds = step(DeclineDelaySteps, it.declineDelaySeconds, up = false)) } },
                            onPlus = { AppStore.updateSettings { it.copy(declineDelaySeconds = step(DeclineDelaySteps, it.declineDelaySeconds, up = true)) } },
                        )
                    }
                }
                Divider()
                SettingRow(
                    Icons.Rounded.Repeat, "Repeat callers",
                    if (settings.cooldownMinutes == 0) "Text them on every call" else "Text the same number once per",
                ) {
                    Stepper(
                        value = intervalLabel(settings.cooldownMinutes),
                        canMinus = settings.cooldownMinutes > 0,
                        canPlus = settings.cooldownMinutes < IntervalSteps.last(),
                        onMinus = { AppStore.updateSettings { it.copy(cooldownMinutes = step(IntervalSteps, it.cooldownMinutes, up = false)) } },
                        onPlus = { AppStore.updateSettings { it.copy(cooldownMinutes = step(IntervalSteps, it.cooldownMinutes, up = true)) } },
                    )
                }
                Divider()
                SettingRow(Icons.Rounded.PriorityHigh, "Urgent calls ring through", "If someone calls again soon after being declined") {
                    Switch(settings.urgentOverride, { v -> AppStore.updateSettings { it.copy(urgentOverride = v) } })
                }
                if (settings.urgentOverride) {
                    Divider()
                    SettingRow(Icons.Rounded.Timer, "Second call within", "Counts as urgent") {
                        Stepper(
                            value = "${settings.urgentWindowMinutes} min",
                            canMinus = settings.urgentWindowMinutes > UrgentSteps.first(),
                            canPlus = settings.urgentWindowMinutes < UrgentSteps.last(),
                            onMinus = { AppStore.updateSettings { it.copy(urgentWindowMinutes = step(UrgentSteps, it.urgentWindowMinutes, up = false)) } },
                            onPlus = { AppStore.updateSettings { it.copy(urgentWindowMinutes = step(UrgentSteps, it.urgentWindowMinutes, up = true)) } },
                        )
                    }
                }
            }
        }

        item { GroupLabel("NOTIFICATIONS") }
        item {
            Group {
                SettingRow(
                    Icons.Rounded.NotificationsActive, "Missed-call summary",
                    if (canNotify) "When a mode ends, see who called" else "Allow notifications to get it",
                ) {
                    if (canNotify) {
                        Switch(settings.summaryNotifications, { v -> AppStore.updateSettings { it.copy(summaryNotifications = v) } })
                    } else {
                        TextButton({ askNotify.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow") }
                    }
                }
                Divider()
                SettingRow(
                    Icons.Rounded.BatteryChargingFull, "Run in the background",
                    if (batteryOk) "Battery saver won't stop schedules" else "Battery saver may stop schedules",
                ) {
                    if (!batteryOk) TextButton({ context.startActivity(Setup.batteryIntent(context)) }) { Text("Fix") }
                }
            }
        }

        item { GroupLabel("BACKUP") }
        item {
            Group {
                SettingRow(Icons.Rounded.CloudUpload, "Save a backup", "Modes, schedules, people & settings. Pick Google Drive to keep it in the cloud.") {}
                Row(Modifier.padding(start = 66.dp, bottom = 12.dp)) {
                    TextButton({ saveBackup.launch("auto-reply-backup-${DateFormat.format("yyyy-MM-dd", System.currentTimeMillis())}.json") }) {
                        Text("Save backup…")
                    }
                }
                Divider()
                SettingRow(Icons.Rounded.CloudDownload, "Restore", "From a backup file on your phone or Google Drive") {}
                Row(Modifier.padding(start = 66.dp, bottom = 12.dp)) {
                    TextButton({ openBackup.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }) {
                        Text("Choose backup…")
                    }
                }
            }
        }
        item {
            Text(
                "Android also backs up these settings to your Google account automatically, if Google backup is on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    pendingRestore?.let { json ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore this backup?") },
            text = { Text("Your current modes, schedules, people and settings will be replaced. The activity log is kept.") },
            confirmButton = {
                TextButton({
                    runCatching { AppStore.importJson(json) }
                        .onSuccess {
                            ModeState.refresh(context)
                            Toast.makeText(context, "Backup restored", Toast.LENGTH_SHORT).show()
                        }
                        .onFailure { Toast.makeText(context, it.message ?: "Not a valid backup", Toast.LENGTH_LONG).show() }
                    pendingRestore = null
                }) { Text("Restore") }
            },
            dismissButton = { TextButton({ pendingRestore = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer),
        content = content,
    )
}

@Composable
private fun Divider() {
    HorizontalDivider(Modifier.padding(start = 66.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun SettingRow(icon: ImageVector, title: String, subtitle: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        val tint = MaterialTheme.colorScheme.primary
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
private fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit, canMinus: Boolean, canPlus: Boolean) {
    Row(
        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onMinus, enabled = canMinus, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.Remove, "Less", Modifier.size(18.dp)) }
        Text(value, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, modifier = Modifier.width(60.dp))
        IconButton(onPlus, enabled = canPlus, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.Add, "More", Modifier.size(18.dp)) }
    }
}
