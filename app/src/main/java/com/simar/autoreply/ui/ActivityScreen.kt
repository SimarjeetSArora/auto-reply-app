package com.simar.autoreply.ui

import android.text.format.DateFormat
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import com.simar.autoreply.data.Mode
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhoneCallback
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.saveable.rememberSaveable
import android.Manifest
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.style.TextOverflow
import com.simar.autoreply.data.ContactNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.MarkChatRead
import androidx.compose.material.icons.rounded.PhoneDisabled
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simar.autoreply.data.AppStore
import com.simar.autoreply.data.LogEntry
import java.util.Calendar

private enum class Outcome(val label: String) {
    SENT("Texted"), RANG("Rang through"), SKIPPED("Not texted"), FAILED("Failed")
}

private fun LogEntry.outcome() = when {
    status.startsWith("Rang through") -> Outcome.RANG
    "not texted" in status.lowercase() -> Outcome.SKIPPED
    "SMS sent" in status -> Outcome.SENT
    else -> Outcome.FAILED
}

private fun startOfToday() = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun dayLabel(time: Long): String = when {
    DateUtils.isToday(time) -> "TODAY"
    DateUtils.isToday(time + DateUtils.DAY_IN_MILLIS) -> "YESTERDAY"
    else -> DateFormat.format("EEE, d MMM", time).toString().uppercase()
}

@Composable
fun ActivityScreen(modifier: Modifier, resumeTick: Int) {
    val log by AppStore.log.collectAsState()
    val modes by AppStore.modes.collectAsState()
    val modeByName = remember(modes) { modes.associateBy { it.name } }

    val today = remember(log) { startOfToday() }
    val sentToday = log.count { it.time >= today && it.outcome() == Outcome.SENT }
    val declinedToday = log.count { it.time >= today && it.status.startsWith("Declined") }
    val sentTotal = log.count { it.outcome() == Outcome.SENT }
    var selected by remember { mutableStateOf<LogEntry?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var outcomeFilter by rememberSaveable { mutableStateOf<Outcome?>(null) }
    var modeFilter by rememberSaveable { mutableStateOf<String?>(null) }

    // Contact names: re-read on resume, when permission is granted, and whenever contacts change.
    val context = LocalContext.current
    var contactsTick by remember { mutableIntStateOf(0) }
    val canReadContacts = remember(resumeTick, contactsTick) { ContactNames.canRead(context) }
    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { contactsTick++ }
    DisposableEffect(canReadContacts) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                contactsTick++
            }
        }
        if (canReadContacts) {
            runCatching {
                context.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
            }
        }
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val numbers = remember(log) { log.map { it.number } }
    val names by produceState(emptyMap<String, String>(), numbers, canReadContacts, contactsTick, resumeTick) {
        value = withContext(Dispatchers.IO) { ContactNames.lookup(context, numbers) }
    }
    val filtered = remember(log, names, query, outcomeFilter, modeFilter) {
        val q = query.trim().lowercase()
        val qDigits = q.filter { it.isDigit() }
        log.filter { e ->
            (outcomeFilter == null || e.outcome() == outcomeFilter) &&
                (modeFilter == null || e.mode == modeFilter) &&
                (q.isEmpty() || names[e.number]?.lowercase()?.contains(q) == true ||
                    (qDigits.isNotEmpty() && qDigits in e.number.filter { it.isDigit() }))
        }
    }
    val grouped = remember(filtered) { filtered.groupBy { dayLabel(it.time) } }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Activity", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(Icons.Rounded.Sms, MaterialTheme.colorScheme.primary, sentToday.toString(), "Texts today", Modifier.weight(1f))
                StatTile(Icons.Rounded.PhoneDisabled, Rose, declinedToday.toString(), "Declined today", Modifier.weight(1f))
                StatTile(Icons.Rounded.TrendingUp, Success, sentTotal.toString(), "All time", Modifier.weight(1f))
            }
        }
        if (!canReadContacts && log.isNotEmpty()) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Contacts, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Show contact names instead of numbers",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton({ askContacts.launch(Manifest.permission.READ_CONTACTS) }) { Text("Allow") }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("Recent calls", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (log.isNotEmpty()) TextButton({ AppStore.clearLog() }) { Text("Clear") }
            }
        }
        if (log.isNotEmpty()) {
            item {
                OutlinedTextField(
                    query, { query = it },
                    placeholder = { Text("Search name or number") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Rounded.Close, "Clear search") }
                    },
                    singleLine = true,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Outcome.entries) { o ->
                        FilterChip(
                            selected = outcomeFilter == o,
                            onClick = { outcomeFilter = if (outcomeFilter == o) null else o },
                            label = { Text(o.label) },
                        )
                    }
                    items(modes) { m ->
                        FilterChip(
                            selected = modeFilter == m.name,
                            onClick = { modeFilter = if (modeFilter == m.name) null else m.name },
                            label = { Text(m.name) },
                            leadingIcon = { Icon(modeIcon(m.icon), null, Modifier.size(16.dp), tint = modeColor(m.color)) },
                        )
                    }
                }
            }
        }
        if (log.isEmpty()) {
            item { EmptyState() }
        } else if (filtered.isEmpty()) {
            item {
                Text(
                    "No calls match.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(4.dp),
                )
            }
        }
        grouped.forEach { (day, entries) ->
            item(key = "day-$day") {
                Text(
                    day,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }
            items(entries, key = { "${it.time}-${it.number}" }) { e ->
                LogRow(e, names[e.number], modeByName[e.mode]) { selected = e }
            }
        }
    }

    selected?.let { e ->
        LogDetails(e, names[e.number], modes.find { it.name == e.mode }) { selected = null }
    }
}

@Composable
private fun StatTile(icon: ImageVector, tint: Color, value: String, label: String, modifier: Modifier) {
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
    ) {
        IconBadge(icon, size = 34, tint = tint)
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun LogEntry.icon(): Pair<ImageVector, Color> = when (outcome()) {
    Outcome.SENT -> Icons.Rounded.MarkChatRead to Success
    Outcome.RANG -> Icons.Rounded.PhoneCallback to MaterialTheme.colorScheme.primary
    Outcome.SKIPPED -> Icons.Rounded.Repeat to MaterialTheme.colorScheme.onSurfaceVariant
    Outcome.FAILED -> Icons.Rounded.ErrorOutline to MaterialTheme.colorScheme.error
}

private fun LogEntry.headline() = when (outcome()) {
    Outcome.SENT -> when {
        status.startsWith("Declined") -> "Declined & texted"
        status.startsWith("Missed") -> "Hung up · texted"
        else -> "Texted"
    }
    Outcome.RANG -> when {
        "VIP" in status -> "Rang through · VIP"
        "called again" in status -> "Rang through · called twice"
        else -> "Rang through · unknown number"
    }
    Outcome.SKIPPED -> when {
        status.startsWith("Answered") -> "You answered"
        "unknown number" in status -> "Declined · unknown number"
        else -> "Not texted again"
    }
    Outcome.FAILED -> "Couldn't send"
}

/** Anything beyond the standard outcome (e.g. "couldn't decline the call") is worth showing. */
private fun LogEntry.note() = status.substringAfter(" (", "").removeSuffix(")").takeIf { it != "unknown number" && it != "you picked up" }.orEmpty().ifEmpty {
    if (outcome() == Outcome.FAILED) status else ""
}

@Composable
private fun LogRow(e: LogEntry, name: String?, mode: Mode?, onClick: () -> Unit) {
    val (icon, tint) = e.icon()
    val headline = e.headline()
    val detail = e.note()

    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, size = 40, tint = tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name ?: e.number, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(headline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = Warn)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                DateFormat.format("h:mm a", e.time).toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            ModeChip(e.mode, mode)
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(Icons.Rounded.Inbox, size = 64, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Spacer(Modifier.height(8.dp))
        Text("No calls yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Turn on a mode and calls you miss will show up here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogDetails(e: LogEntry, name: String?, mode: Mode?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val (icon, tint) = e.icon()
    val accent = mode?.let { modeColor(it.color) } ?: MaterialTheme.colorScheme.primary
    val digits = e.number.filter { it.isDigit() || it == '+' }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, size = 52, tint = tint)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(name ?: e.number, style = MaterialTheme.typography.headlineSmall)
                    if (name != null) {
                        Text(e.number, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        e.headline() + " · " + DateFormat.format("EEE d MMM yyyy, h:mm a", e.time),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton(Icons.Rounded.Call, "Call back", Modifier.weight(1f)) {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits")))
                }
                ActionButton(Icons.Rounded.Sms, "Message", Modifier.weight(1f)) {
                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$digits")))
                }
                ActionButton(Icons.Rounded.ContentCopy, "Copy", Modifier.weight(1f)) {
                    context.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("Phone number", e.number))
                    Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
                }
            }

            DetailBlock("MODE") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(modeIcon(mode?.icon ?: "chat"), size = 32, tint = accent)
                    Spacer(Modifier.width(10.dp))
                    Text(e.mode, style = MaterialTheme.typography.bodyLarge)
                }
            }

            if (e.outcome() == Outcome.SENT) {
                DetailBlock("MESSAGE SENT") {
                    val text = e.message.ifEmpty { mode?.message.orEmpty() }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp))
                            .background(accent)
                            .padding(14.dp),
                    ) {
                        Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    }
                    if (e.message.isEmpty() && text.isNotEmpty()) {
                        Text(
                            "Older entry: showing the mode's current message.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            DetailBlock("WHAT HAPPENED") {
                Text(e.status, style = MaterialTheme.typography.bodyMedium)
                if (e.note().isNotEmpty()) {
                    Text(e.note(), style = MaterialTheme.typography.bodySmall, color = Warn)
                }
            }

            TextButton(
                onClick = { AppStore.deleteLog(e); onDismiss() },
                modifier = Modifier.align(Alignment.End),
            ) { Text("Delete entry", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun DetailBlock(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun ModeChip(name: String, mode: Mode?) {
    val accent = mode?.let { modeColor(it.color) } ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.14f))
            .padding(start = 6.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(modeIcon(mode?.icon ?: "chat"), null, tint = accent, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(name, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp), color = accent)
    }
}
