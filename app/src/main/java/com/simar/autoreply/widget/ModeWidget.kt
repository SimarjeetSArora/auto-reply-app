package com.simar.autoreply.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.widget.RemoteViews
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import com.simar.autoreply.R
import com.simar.autoreply.data.AppStore
import com.simar.autoreply.logic.ModeState
import com.simar.autoreply.ui.MainActivity
import com.simar.autoreply.ui.modeColor
import com.simar.autoreply.ui.modeIcon

/**
 * Home-screen widget: the current mode, then a round button per mode (tap to turn it on, tap the
 * active one to turn it off) and a "+" that opens the app on a new mode.
 */
class ModeWidget : AppWidgetProvider() {

    companion object {
        private const val ACTION_SELECT = "com.simar.autoreply.widget.SELECT"
        private const val EXTRA_MODE_ID = "modeId"
        // Kept so older pinned intents (and adb testing) still work.
        private const val ACTION_TOGGLE = "com.simar.autoreply.widget.TOGGLE"

        private const val SLOTS = 7
        private val slotIds = listOf(R.id.slot0, R.id.slot1, R.id.slot2, R.id.slot3, R.id.slot4, R.id.slot5, R.id.slot6)
        private val iconIds = listOf(
            R.id.slot0_icon, R.id.slot1_icon, R.id.slot2_icon, R.id.slot3_icon, R.id.slot4_icon, R.id.slot5_icon, R.id.slot6_icon,
        )
        /** One gradient per palette colour (ui/Theme.kt ModePalette); widgets can't build gradients at runtime. */
        private val modeBackgrounds = listOf(
            R.drawable.widget_bg_mode0, R.drawable.widget_bg_mode1, R.drawable.widget_bg_mode2, R.drawable.widget_bg_mode3,
            R.drawable.widget_bg_mode4, R.drawable.widget_bg_mode5, R.drawable.widget_bg_mode6, R.drawable.widget_bg_mode7,
        )

        private enum class Look {
            /** No mode on, dark background: icon in the mode's colour on a soft tint of it. */
            TINTED,
            /** Another mode is on, coloured background: soft white circle, white icon. */
            ON_COLOUR,
            /** The active mode: solid white circle, icon in the mode's colour. */
            SELECTED,
        }

        private val labelIds = listOf(
            R.id.slot0_label, R.id.slot1_label, R.id.slot2_label, R.id.slot3_label, R.id.slot4_label, R.id.slot5_label, R.id.slot6_label,
        )

        /** Redraws every placed Auto Reply widget (this one and the round toggle). */
        fun updateAll(context: Context) {
            ModeToggleWidget.updateAll(context)
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, ModeWidget::class.java))
            if (ids.isNotEmpty()) mgr.updateAppWidget(ids, render(context))
        }

        private fun render(context: Context): RemoteViews {
            AppStore.init(context)
            val active = ModeState.current()
            val modes = AppStore.modes.value.take(SLOTS - 1)
            return RemoteViews(context.packageName, R.layout.widget_mode).apply {
                setInt(
                    R.id.widget_root, "setBackgroundResource",
                    active?.let { modeBackgrounds[it.mode.color.mod(modeBackgrounds.size)] } ?: R.drawable.widget_bg_off,
                )
                setTextViewText(R.id.widget_title, active?.mode?.name ?: "Available")
                setTextViewText(
                    R.id.widget_subtitle,
                    when {
                        active == null -> "Tap a mode to turn it on"
                        active.until != null -> "Until ${ModeState.formatUntil(context, active.until)} · tap it again to stop"
                        else -> "Callers get a text · tap it again to stop"
                    },
                )
                setOnClickPendingIntent(R.id.widget_header, openApp(context, newMode = false))

                for (i in 0 until SLOTS) setViewVisibility(slotIds[i], View.GONE)
                modes.forEachIndexed { i, mode ->
                    val isActive = mode.id == active?.mode?.id
                    setViewVisibility(slotIds[i], View.VISIBLE)
                    val look = when {
                        isActive -> Look.SELECTED
                        active != null -> Look.ON_COLOUR
                        else -> Look.TINTED
                    }
                    setImageViewBitmap(iconIds[i], circle(context, modeIcon(mode.icon), modeColor(mode.color).toArgb(), look))
                    setTextViewText(labelIds[i], mode.name)
                    setOnClickPendingIntent(slotIds[i], select(context, i, mode.id))
                }
                val plus = modes.size
                setViewVisibility(slotIds[plus], View.VISIBLE)
                setImageViewBitmap(iconIds[plus], circle(context, Icons.Rounded.Add, 0xFFFFFFFF.toInt(), Look.ON_COLOUR))
                setTextViewText(labelIds[plus], "New")
                setOnClickPendingIntent(slotIds[plus], openApp(context, newMode = true))
            }
        }

        private fun select(context: Context, slot: Int, modeId: String) = PendingIntent.getBroadcast(
            context, 100 + slot,
            Intent(context, ModeWidget::class.java).setAction(ACTION_SELECT).putExtra(EXTRA_MODE_ID, modeId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun openApp(context: Context, newMode: Boolean) = PendingIntent.getActivity(
            context, if (newMode) 1 else 0,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_NEW_MODE, newMode)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /**
         * Draws a round button with [icon] in it. Widgets can't host Compose, so the Material icon's
         * vector paths are drawn onto a bitmap.
         */
        private fun circle(context: Context, icon: ImageVector, color: Int, look: Look): Bitmap {
            val px = (44 * context.resources.displayMetrics.density).toInt()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val white = 0xFFFFFFFF.toInt()
            val (circleColor, iconColor) = when (look) {
                Look.TINTED -> ((color and 0x00FFFFFF) or 0x38000000) to color
                Look.ON_COLOUR -> 0x2EFFFFFF to 0xE6FFFFFF.toInt()
                Look.SELECTED -> white to color
            }
            canvas.drawCircle(px / 2f, px / 2f, px / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = circleColor })
            val fg = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = iconColor }
            val iconSize = px * 0.52f
            WidgetArt.drawIcon(canvas, icon, fg, (px - iconSize) / 2f, (px - iconSize) / 2f, iconSize)
            return bmp
        }
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        mgr.updateAppWidget(ids, render(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        AppStore.init(context)
        when (intent.action) {
            ACTION_SELECT -> {
                val id = intent.getStringExtra(EXTRA_MODE_ID) ?: return
                if (ModeState.current()?.mode?.id == id) ModeState.turnOff(context) else ModeState.turnOn(context, id)
            }
            ACTION_TOGGLE -> ModeState.toggle(context)
        }
    }
}
