package com.simar.autoreply.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.produceState
import com.simar.autoreply.logic.Messages
import com.simar.autoreply.logic.ModeState
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simar.autoreply.data.AppStore
import com.simar.autoreply.data.Defaults
import com.simar.autoreply.data.Mode

@Composable
fun ModesScreen(modifier: Modifier, resumeTick: Int, openNewMode: Boolean = false, onNewModeOpened: () -> Unit = {}) {
    val modes by AppStore.modes.collectAsState()
    val settings by AppStore.settings.collectAsState()
    var editing by remember { mutableStateOf<Mode?>(null) }
    var deleting by remember { mutableStateOf<Mode?>(null) }
    val context = LocalContext.current
    // The widget's "+" asks for the new-mode editor; open it once, then clear the request.
    LaunchedEffect(openNewMode) {
        if (openNewMode) {
            editing = Mode(Defaults.newId(), "", "", "chat", modes.size % ModePalette.size)
            onNewModeOpened()
        }
    }
    val schedules by AppStore.schedules.collectAsState()
    // Re-check every so often so a schedule starting/ending shows up without reopening the app.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(20_000)
            value = System.currentTimeMillis()
        }
    }
    val current = remember(settings, modes, schedules, now, resumeTick) { ModeState.current(now) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                Text("Auto Reply", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Let callers know why you can't pick up.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(span = { GridItemSpan(2) }) { SetupCard(resumeTick) }
        item(span = { GridItemSpan(2) }) { HeroCard(current, settings.declineCalls) }
        item(span = { GridItemSpan(2) }) {
            Text(
                "MODES",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, start = 4.dp),
            )
        }
        items(modes, key = { it.id }) { mode ->
            ModeTile(
                mode = mode,
                isActive = mode.id == current?.mode?.id,
                onClick = {
                    if (mode.id == current?.mode?.id) ModeState.turnOff(context) else ModeState.turnOn(context, mode.id)
                },
                onEdit = { editing = mode },
            )
        }
        item { NewModeTile { editing = Mode(Defaults.newId(), "", "", "chat", modes.size % ModePalette.size) } }
        item(span = { GridItemSpan(2) }) {
            Text(
                "Tap a mode to turn it on. Long-press or tap the pencil to edit.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }
    }

    editing?.let { mode ->
        ModeEditor(
            initial = mode,
            isNew = modes.none { it.id == mode.id },
            onDismiss = { editing = null },
            onSave = { AppStore.upsertMode(it); editing = null },
            onDelete = { deleting = mode; editing = null },
        )
    }
    deleting?.let { mode ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(modeIcon(mode.icon), null, tint = modeColor(mode.color)) },
            title = { Text("Delete \"${mode.name}\"?") },
            confirmButton = {
                TextButton({
                    if (current?.mode?.id == mode.id) ModeState.turnOff(context)
                    AppStore.deleteMode(mode.id)
                    ModeState.refresh(context)
                    deleting = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton({ deleting = null }) { Text("Cancel") } },
        )
    }
}

// ---- Status ----

@Composable
private fun HeroCard(current: ModeState.Active?, declining: Boolean) {
    val context = LocalContext.current
    val active = current?.mode
    val accent = active?.let { modeColor(it.color) }
    val top by animateColorAsState(accent ?: MaterialTheme.colorScheme.surfaceContainerHigh, label = "heroTop")
    val bottom by animateColorAsState(accent?.deeper() ?: MaterialTheme.colorScheme.surfaceContainerHigh, label = "heroBottom")
    val onHero = if (active != null) Color.White else MaterialTheme.colorScheme.onSurface

    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(top, bottom)))
            .animateContentSize()
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    active?.let { modeIcon(it.icon) } ?: Icons.Rounded.PhoneInTalk,
                    size = 56,
                    tint = if (active != null) Color.White else MaterialTheme.colorScheme.primary,
                    background = if (active != null) Color.White.copy(alpha = 0.18f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (active != null) "ACTIVE MODE" else "NO MODE ON",
                        style = MaterialTheme.typography.labelSmall,
                        color = onHero.copy(alpha = 0.75f),
                    )
                    Text(
                        active?.name ?: "You're available",
                        style = MaterialTheme.typography.headlineSmall,
                        color = onHero,
                    )
                    if (current != null) {
                        Text(
                            when {
                                current.until == null -> "Until you turn it off"
                                current.scheduled -> "Scheduled · until ${ModeState.formatUntil(context, current.until)}"
                                else -> "Until ${ModeState.formatUntil(context, current.until)}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = onHero.copy(alpha = 0.85f),
                        )
                    }
                }
            }
            if (active != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp))
                        .background(Color.White.copy(alpha = 0.16f))
                        .padding(14.dp),
                ) {
                    Text(
                        "“${active.message}”",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = Color.White,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val now = System.currentTimeMillis()
                    HeroChip("30 min") { ModeState.setUntil(context, now + 30 * 60_000L) }
                    HeroChip("1 hour") { ModeState.setUntil(context, now + 60 * 60_000L) }
                    HeroChip("2 hours") { ModeState.setUntil(context, now + 120 * 60_000L) }
                    HeroChip("Until…") {
                        pickTime(context, minuteOfDay(current?.until ?: (now + 60 * 60_000L))) { minute ->
                            ModeState.setUntil(context, nextOccurrence(minute))
                        }
                    }
                }
                if (current?.until != null) {
                    HeroChip("Keep on until I turn it off") { ModeState.setUntil(context, null) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (declining) "Calls are declined and texted" else "Calls ring, callers get a text",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { ModeState.turnOff(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = accent!!.deeper()),
                    ) { Text("Turn off") }
                }
            } else {
                Text(
                    "Calls ring normally. Pick a mode below, or set a schedule, and callers get an automatic text.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HeroChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

// ---- Mode tiles ----

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModeTile(mode: Mode, isActive: Boolean, onClick: () -> Unit, onEdit: () -> Unit) {
    val accent = modeColor(mode.color)
    val container by animateColorAsState(
        if (isActive) accent else MaterialTheme.colorScheme.surfaceContainer,
        label = "tile",
    )
    val content = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface
    val sub = if (isActive) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        Modifier
            .fillMaxWidth()
            .height(164.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(container)
            .combinedClickable(onClick = onClick, onLongClick = onEdit)
            .padding(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(
                modeIcon(mode.icon),
                size = 46,
                tint = if (isActive) Color.White else accent,
                background = if (isActive) Color.White.copy(alpha = 0.22f) else accent.copy(alpha = 0.14f),
            )
            Spacer(Modifier.weight(1f))
            if (isActive) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Active",
                    tint = Color.White,
                    modifier = Modifier.padding(top = 4.dp, end = 8.dp),
                )
            } else {
                IconButton(onEdit, Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, "Edit", tint = sub, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(mode.name, style = MaterialTheme.typography.titleMedium, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            mode.message,
            style = MaterialTheme.typography.bodySmall,
            color = sub,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 10.dp),
        )
    }
}

@Composable
private fun NewModeTile(onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(164.dp)
            .clip(MaterialTheme.shapes.medium)
            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(10.dp))
        Text("New mode", style = MaterialTheme.typography.titleSmall)
    }
}

// ---- Editor ----

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ModeEditor(
    initial: Mode,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Mode) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    // TextFieldValue (not String) so the insert buttons know where the cursor is.
    var messageField by remember { mutableStateOf(TextFieldValue(initial.message, TextRange(initial.message.length))) }
    val message = messageField.text
    val messageFocus = remember { FocusRequester() }
    var icon by remember { mutableStateOf(initial.icon) }
    var color by remember { mutableIntStateOf(initial.color) }
    val accent = modeColor(color)
    // GSM SMS: 160 chars in one part, 153 per part once split. Emoji switch to 70/67.
    val unicode = message.any { it.code > 127 }
    val parts = when {
        message.isEmpty() -> 0
        unicode -> if (message.length <= 70) 1 else (message.length + 66) / 67
        else -> if (message.length <= 160) 1 else (message.length + 152) / 153
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(modeIcon(icon), size = 56, tint = accent)
                Spacer(Modifier.width(14.dp))
                Text(if (isNew) "New mode" else "Edit mode", style = MaterialTheme.typography.headlineSmall)
            }

            SectionLabel("ICON")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ModeIcons.forEach { (key, vector) ->
                    val selected = key == icon
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (selected) accent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(2.dp, if (selected) accent else Color.Transparent, CircleShape)
                            .clickable { icon = key },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            vector, key,
                            tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            SectionLabel("COLOUR")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ModePalette.forEachIndexed { i, c ->
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(c).clickable { color = i },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (i == color) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }

            OutlinedTextField(
                name, { name = it },
                label = { Text("Name") },
                placeholder = { Text("e.g. Gym") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                messageField, { messageField = it },
                label = { Text("Message callers get") },
                minLines = 3,
                shape = MaterialTheme.shapes.small,
                supportingText = {
                    Text(if (parts <= 1) "${message.length} characters" else "${message.length} characters · sent as $parts SMS")
                },
                modifier = Modifier.fillMaxWidth().focusRequester(messageFocus),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                /** Puts [text] at the cursor (replacing any selection), with spaces so words don't run together. */
                fun insert(text: String) {
                    val sel = messageField.selection
                    val before = message.substring(0, sel.min)
                    val after = message.substring(sel.max)
                    val lead = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
                    val trail = if (after.isNotEmpty() && !after.first().isWhitespace() && after.first() !in ".,!?") " " else ""
                    val inserted = lead + text + trail
                    messageField = TextFieldValue(before + inserted + after, TextRange(before.length + lead.length + text.length))
                    messageFocus.requestFocus()
                }
                AssistChip(
                    onClick = { insert(Messages.NAME) },
                    label = { Text("Caller's name") },
                    leadingIcon = { Icon(Icons.Rounded.Person, null, Modifier.size(18.dp)) },
                )
                AssistChip(
                    onClick = { if (Messages.VOICE_NOTE_LINE !in message) insert(Messages.VOICE_NOTE_LINE) },
                    label = { Text("Voice-note line") },
                    leadingIcon = { Icon(Icons.Rounded.Mic, null, Modifier.size(18.dp)) },
                )
            }
            Text(
                "{name} becomes the caller's first name from your contacts. Tap in the message where you want it, then tap the button.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isNew) {
                    TextButton(onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onSave(initial.copy(name = name.trim(), message = message.trim(), icon = icon, color = color)) },
                    enabled = name.isNotBlank() && message.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
                ) { Text("Save") }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ---- Setup ----

@Composable
private fun SetupCard(resumeTick: Int) {
    val context = LocalContext.current
    var permTick by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permTick++ }

    // Re-evaluated whenever the activity resumes or permissions change.
    val phoneOk = remember(resumeTick, permTick) { Setup.hasPhonePermissions(context) }
    val batteryOk = remember(resumeTick) { Setup.isIgnoringBatteryOptimizations(context) }
    val notifyOk = remember(resumeTick, permTick) { Setup.canNotify(context) }
    if (phoneOk && batteryOk && notifyOk) return

    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(10.dp))
            Text("Finish setup", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        }
        SetupRow("Phone & SMS permissions", phoneOk) { launcher.launch(Setup.phonePermissions) }
        SetupRow("Keep running in the background", batteryOk) { context.startActivity(Setup.batteryIntent(context)) }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            SetupRow("Notifications (shows the active mode)", notifyOk) {
                launcher.launch(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS))
            }
        }
    }
}

@Composable
private fun SetupRow(label: String, ok: Boolean, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            null,
            tint = if (ok) Success else MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
        if (!ok) FilledTonalButton(onFix) { Text("Fix") }
    }
}
