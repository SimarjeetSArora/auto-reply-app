package com.simar.autoreply.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.simar.autoreply.data.AppStore
import com.simar.autoreply.ui.AutoReplyTheme
import com.simar.autoreply.ui.IconBadge
import com.simar.autoreply.ui.modeColor
import com.simar.autoreply.ui.modeIcon

/**
 * Picks which mode a round toggle widget controls. Shown when the widget is added, and again from
 * the launcher's "Widget settings" (the widget is declared reconfigurable).
 */
class ToggleWidgetConfigActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // If the picker is dismissed while adding, the launcher must not place the widget.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        AppStore.init(this)
        val current = ModeToggleWidget.pinnedModeId(this, widgetId)

        setContent {
            AutoReplyTheme {
                Picker(current, onPick = ::pick, onCancel = ::finish)
            }
        }
    }

    private fun pick(modeId: String?) {
        ModeToggleWidget.setPinnedMode(this, widgetId, modeId)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}

@Composable
private fun Picker(current: String?, onPick: (String?) -> Unit, onCancel: () -> Unit) {
    val modes = remember { AppStore.modes.value }
    // Scrim: tapping outside the card cancels, like a dialog.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {} // swallow taps
                .padding(vertical = 20.dp),
        ) {
            Text("Widget mode", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp))
            Text(
                "Tapping the widget turns this mode on or off.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Spacer(Modifier.size(8.dp))
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Option(Icons.Rounded.History, MaterialTheme.colorScheme.primary, "Last used mode", "Follows whichever mode you used last", current == null) {
                    onPick(null)
                }
                modes.forEach { m ->
                    Option(modeIcon(m.icon), modeColor(m.color), m.name, m.message, current == m.id) { onPick(m.id) }
                }
            }
            if (modes.isEmpty()) {
                Text(
                    "You don't have any modes yet. Create one in the app first.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            TextButton(onCancel, Modifier.align(Alignment.End).padding(end = 12.dp)) { Text("Cancel") }
        }
    }
}

@Composable
private fun Option(icon: ImageVector, tint: Color, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, size = 42, tint = tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) tint else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .padding(5.dp)
                .clip(CircleShape)
                .background(if (selected) tint else Color.Transparent),
        )
    }
}
