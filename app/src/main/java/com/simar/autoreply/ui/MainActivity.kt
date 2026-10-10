package com.simar.autoreply.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.simar.autoreply.data.AppStore
import com.simar.autoreply.logic.ModeState

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_ACTIVITY = 3
        /** Open the Modes tab with the "New mode" editor showing (from the widget's "+"). */
        const val EXTRA_NEW_MODE = "newMode"
    }

    /** Bumped on every resume so permission/setup status is re-checked after visiting Settings. */
    private var resumeTick by mutableIntStateOf(0)
    private var tab by mutableIntStateOf(0)
    /** Set by the widget's "+"; ModesScreen opens the editor and clears it. */
    private var openNewMode by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        tab = savedInstanceState?.getInt(EXTRA_TAB) ?: intent.getIntExtra(EXTRA_TAB, 0)
        if (savedInstanceState == null) handleNewMode(intent)
        setContent {
            AutoReplyTheme {
                MainScreen(tab, { tab = it }, resumeTick, openNewMode) { openNewMode = false }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasExtra(EXTRA_TAB)) tab = intent.getIntExtra(EXTRA_TAB, tab)
        handleNewMode(intent)
    }

    private fun handleNewMode(intent: Intent) {
        if (!intent.getBooleanExtra(EXTRA_NEW_MODE, false)) return
        tab = 0
        openNewMode = true
    }

    override fun onResume() {
        super.onResume()
        AppStore.init(this)
        ModeState.refresh(this)
        resumeTick++
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(EXTRA_TAB, tab)
    }
}

private data class Tab(val label: String, val icon: ImageVector)

private val Tabs = listOf(
    Tab("Modes", Icons.Rounded.Home),
    Tab("Schedules", Icons.Rounded.Schedule),
    Tab("People", Icons.Rounded.People),
    Tab("Activity", Icons.Rounded.History),
    Tab("Settings", Icons.Rounded.Settings),
)

@Composable
private fun MainScreen(tab: Int, onTab: (Int) -> Unit, resumeTick: Int, openNewMode: Boolean, onNewModeOpened: () -> Unit) {
    val itemColors = NavigationBarItemDefaults.colors(
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { onTab(i) },
                        icon = { Icon(t.icon, null) },
                        label = { Text(t.label, maxLines = 1) },
                        colors = itemColors,
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            0 -> ModesScreen(modifier, resumeTick, openNewMode, onNewModeOpened)
            1 -> SchedulesScreen(modifier)
            2 -> PeopleScreen(modifier, resumeTick)
            3 -> ActivityScreen(modifier, resumeTick)
            else -> SettingsScreen(modifier, resumeTick)
        }
    }
}
