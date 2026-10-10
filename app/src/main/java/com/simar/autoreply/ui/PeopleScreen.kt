package com.simar.autoreply.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PhoneCallback
import androidx.compose.material.icons.rounded.PhoneDisabled
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simar.autoreply.data.AppStore
import com.simar.autoreply.data.ContactNames
import com.simar.autoreply.data.ContactRule
import com.simar.autoreply.data.UnknownCallers
import com.simar.autoreply.data.phoneKey

@Composable
fun PeopleScreen(modifier: Modifier, resumeTick: Int) {
    val context = LocalContext.current
    val rules by AppStore.rules.collectAsState()
    val settings by AppStore.settings.collectAsState()
    var editing by remember { mutableStateOf<ContactRule?>(null) }
    var permTick by remember { mutableIntStateOf(0) }
    val canReadContacts = remember(resumeTick, permTick) { ContactNames.canRead(context) }
    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permTick++ }

    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null) return@rememberLauncherForActivityResult
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val name = c.getString(0).orEmpty()
                val number = c.getString(1).orEmpty()
                val key = phoneKey(number)
                if (key.isEmpty()) {
                    Toast.makeText(context, "That contact has no usable number", Toast.LENGTH_SHORT).show()
                } else {
                    editing = rules.find { it.key == key } ?: ContactRule(key, name, number, vip = true)
                }
            }
        }
    }
    val vips = rules.filter { it.vip }
    val custom = rules.filter { !it.vip && it.hasMessages }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                Text("People", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Choose who always gets through, and who gets a personal message.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable {
                        pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PersonAdd, null, tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(12.dp))
                Text("Add a contact", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimary)
            }
        }

        item { Section("ALWAYS RING THROUGH", "Their calls ring normally, even when a mode is on.") }
        if (vips.isEmpty()) item { EmptyHint("No one yet. Add family or anyone you never want to miss.") }
        items(vips, key = { "vip-${it.key}" }) { r -> RuleRow(r) { editing = r } }

        item { Section("PERSONAL MESSAGES", "They get their own text instead of the mode's message.") }
        if (custom.isEmpty()) item { EmptyHint("No personal messages yet.") }
        items(custom, key = { "msg-${it.key}" }) { r -> RuleRow(r) { editing = r } }

        item { Section("NUMBERS NOT IN YOUR CONTACTS", "Spam and unknown callers, while a mode is on.") }
        item {
            Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Choice(Icons.Rounded.Sms, "Text them like everyone else", settings.unknownCallers == UnknownCallers.TEXT) {
                    AppStore.updateSettings { it.copy(unknownCallers = UnknownCallers.TEXT) }
                }
                Choice(Icons.Rounded.PhoneDisabled, "Decline without texting", settings.unknownCallers == UnknownCallers.DECLINE) {
                    AppStore.updateSettings { it.copy(unknownCallers = UnknownCallers.DECLINE) }
                }
                Choice(Icons.Rounded.PhoneCallback, "Let them ring", settings.unknownCallers == UnknownCallers.RING) {
                    AppStore.updateSettings { it.copy(unknownCallers = UnknownCallers.RING) }
                }
            }
        }
        if (!canReadContacts) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Needs access to contacts to tell who's unknown.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton({ askContacts.launch(Manifest.permission.READ_CONTACTS) }) { Text("Allow") }
                }
            }
        }
    }

    editing?.let { r ->
        RuleEditor(
            initial = r,
            isNew = rules.none { it.key == r.key },
            onDismiss = { editing = null },
            onSave = { updated ->
                if (updated.vip || updated.hasMessages) AppStore.upsertRule(updated) else AppStore.deleteRule(updated.key)
                editing = null
            },
            onDelete = { AppStore.deleteRule(r.key); editing = null },
        )
    }
}

@Composable
private fun Section(title: String, subtitle: String) {
    Column(Modifier.padding(start = 4.dp, top = 12.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        modifier = Modifier.padding(start = 4.dp),
    )
}

/** Initials in a circle, coloured consistently per person. */
@Composable
fun Avatar(name: String, size: Int) {
    val initials = name.split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }
        .ifEmpty { "#" }
    val c = modeColor(name.hashCode())
    Box(Modifier.size(size.dp).clip(CircleShape).background(c.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
        Text(initials, color = c, style = MaterialTheme.typography.titleSmall, fontSize = (size * 0.36f).sp)
    }
}

@Composable
private fun RuleRow(r: ContactRule, onClick: () -> Unit) {
    val modes by AppStore.modes.collectAsState()
    val perMode = modes.filter { !r.modeMessages[it.id].isNullOrBlank() }.map { it.name }
    val subtitle = when {
        r.vip -> r.number
        perMode.isEmpty() -> "“${r.message}”"
        r.message.isBlank() -> "For ${perMode.joinToString(", ")}"
        else -> "All modes + own message for ${perMode.joinToString(", ")}"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(r.name.ifBlank { r.number }, 42)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.name.ifBlank { r.number }, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (r.vip) Icon(Icons.Rounded.Star, "VIP", tint = Warn, modifier = Modifier.size(20.dp))
        else Icon(Icons.Rounded.Chat, "Personal message", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Choice(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .padding(5.dp)
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RuleEditor(
    initial: ContactRule,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (ContactRule) -> Unit,
    onDelete: () -> Unit,
) {
    val modes by AppStore.modes.collectAsState()
    var vip by remember { mutableStateOf(initial.vip) }
    /** "" = all modes; otherwise a mode id. */
    var messages by remember { mutableStateOf(initial.modeMessages + ("" to initial.message)) }
    var selected by remember { mutableStateOf("") }
    val selectedMode = modes.find { it.id == selected }
    val current = messages[selected].orEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(initial.name.ifBlank { initial.number }, 56)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(initial.name.ifBlank { initial.number }, style = MaterialTheme.typography.headlineSmall)
                    Text(initial.number, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { vip = !vip }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Star, null, tint = Warn)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Always ring through", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Never declined or texted, whatever mode is on",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(vip, { vip = it })
            }
            Text(
                "PERSONAL MESSAGE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip("All modes", null, Color.Unspecified, selected == "", !messages[""].isNullOrBlank(), enabled = !vip) {
                    selected = ""
                }
                modes.forEach { m ->
                    ModeChip(m.name, modeIcon(m.icon), modeColor(m.color), selected == m.id, !messages[m.id].isNullOrBlank(), enabled = !vip) {
                        selected = m.id
                    }
                }
            }
            OutlinedTextField(
                current, { text -> messages = messages + (selected to text) },
                enabled = !vip,
                label = { Text(if (selectedMode == null) "For every mode (optional)" else "When ${selectedMode.name} is on (optional)") },
                // Show what they'd get otherwise, so leaving it empty is an informed choice.
                placeholder = {
                    Text(
                        if (selectedMode == null) "e.g. Busy right now, will call you soon ❤️"
                        else messages[""].orEmpty().ifBlank { selectedMode.message },
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingText = {
                    Text(
                        when {
                            vip -> "Not used: their calls ring through."
                            selectedMode == null -> "Used in any mode that doesn't have its own message. Leave empty to send the mode's message."
                            else -> "Leave empty to use the \"All modes\" message, or the mode's own. {name} works here too."
                        },
                    )
                },
                minLines = 3,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isNew) TextButton(onDelete) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.weight(1f))
                TextButton(onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button({
                    val modeIds = modes.map { it.id }.toSet()
                    onSave(
                        initial.copy(
                            vip = vip,
                            message = messages[""].orEmpty().trim(),
                            modeMessages = messages
                                .filterKeys { it in modeIds }
                                .mapValues { it.value.trim() }
                                .filterValues { it.isNotEmpty() },
                        ),
                    )
                }) { Text("Save") }
            }
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    icon: ImageVector?,
    tint: Color,
    selected: Boolean,
    hasMessage: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val accent = if (tint == Color.Unspecified) MaterialTheme.colorScheme.primary else tint
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (selected && enabled) accent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.5.dp, if (selected && enabled) accent else Color.Transparent, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = if (icon != null) 8.dp else 14.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
        if (hasMessage) {
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
        }
    }
}
