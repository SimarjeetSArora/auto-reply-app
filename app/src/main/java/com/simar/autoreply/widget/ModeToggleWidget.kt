package com.simar.autoreply.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.simar.autoreply.R
import com.simar.autoreply.data.AppStore
import com.simar.autoreply.data.Mode
import com.simar.autoreply.logic.ModeState
import com.simar.autoreply.ui.deeper
import com.simar.autoreply.ui.modeColor
import com.simar.autoreply.ui.modeIcon

/**
 * 1×1 round widget for one mode. Each placed widget is set to a specific mode (chosen when it's
 * added, changeable via "Widget settings"), or to "last used mode", which follows the app.
 * Tap = switch that mode on or off. On = filled with the mode's colour; off = dark, icon dimmed.
 */
class ModeToggleWidget : AppWidgetProvider() {

    companion object {
        private const val ACTION_TOGGLE = "com.simar.autoreply.widget.ROUND_TOGGLE"
        private const val PREFS = "round_widgets"

        // ---- Which mode each placed widget is for ----

        /** The mode id this widget is pinned to; null = follow the last used mode. */
        fun pinnedModeId(context: Context, widgetId: Int): String? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("w$widgetId", null)?.ifEmpty { null }

        fun setPinnedMode(context: Context, widgetId: Int, modeId: String?) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("w$widgetId", modeId.orEmpty()).apply()
            val mgr = AppWidgetManager.getInstance(context)
            mgr.updateAppWidget(widgetId, render(context, widgetId))
        }

        private fun pinnedMode(context: Context, widgetId: Int): Mode? =
            pinnedModeId(context, widgetId)?.let { id -> AppStore.modes.value.find { it.id == id } }

        // ---- Drawing ----

        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            mgr.getAppWidgetIds(ComponentName(context, ModeToggleWidget::class.java)).forEach { id ->
                mgr.updateAppWidget(id, render(context, id))
            }
        }

        private fun render(context: Context, widgetId: Int): RemoteViews {
            AppStore.init(context)
            val active = ModeState.current()
            val pinned = pinnedMode(context, widgetId)
            val modes = AppStore.modes.value
            // Pinned: always that mode. Otherwise what a tap would turn on, as ModeState.toggle() picks it.
            val mode = pinned
                ?: active?.mode
                ?: modes.find { it.id == AppStore.settings.value.lastModeId }
                ?: modes.firstOrNull()
            val on = if (pinned != null) active?.mode?.id == pinned.id else active != null
            return RemoteViews(context.packageName, R.layout.widget_toggle).apply {
                setImageViewBitmap(R.id.toggle_icon, draw(context, mode?.icon ?: "chat", mode?.color ?: 1, on))
                setContentDescription(
                    R.id.toggle_icon,
                    if (on) "${mode?.name} is on. Tap to turn off." else "Tap to turn on ${mode?.name ?: "a mode"}",
                )
                setOnClickPendingIntent(
                    R.id.toggle_icon,
                    PendingIntent.getBroadcast(
                        context, widgetId,
                        Intent(context, ModeToggleWidget::class.java)
                            .setAction(ACTION_TOGGLE)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }
        }

        private fun draw(context: Context, iconKey: String, colorIndex: Int, on: Boolean): Bitmap {
            val px = (72 * context.resources.displayMetrics.density).toInt()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val accent = modeColor(colorIndex)
            val circle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                if (on) {
                    // Same mode-colour gradient as the app's status card and the big widget.
                    shader = LinearGradient(0f, 0f, px.toFloat(), px.toFloat(), accent.toArgb(), accent.deeper().toArgb(), Shader.TileMode.CLAMP)
                } else {
                    color = 0xFF2B3040.toInt()
                }
            }
            canvas.drawCircle(px / 2f, px / 2f, px / 2f, circle)
            val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (on) 0xFFFFFFFF.toInt() else (accent.toArgb() and 0x00FFFFFF) or 0xB3000000.toInt()
            }
            val iconSize = px * 0.5f
            WidgetArt.drawIcon(canvas, modeIcon(iconKey), iconPaint, (px - iconSize) / 2f, (px - iconSize) / 2f, iconSize)
            return bmp
        }
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { mgr.updateAppWidget(it, render(context, it)) }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        ids.forEach { edit.remove("w$it") }
        edit.apply()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_TOGGLE) return
        AppStore.init(context)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val pinned = pinnedMode(context, widgetId)
        when {
            pinned == null -> ModeState.toggle(context)
            ModeState.current()?.mode?.id == pinned.id -> ModeState.turnOff(context)
            else -> ModeState.turnOn(context, pinned.id)
        }
    }
}
