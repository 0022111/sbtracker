package com.sbtracker.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SBTrackerApp(vm: AppViewModel = viewModel()) {
    val screen by vm.screen.collectAsStateWithLifecycle()

    // Full-screen detail has no bottom bar — keeps focus on the session.
    if (screen is Screen.Detail) {
        SessionDetailScreen(vm)
        return
    }

    Scaffold(
        bottomBar = { BottomBar(screen, onSelect = vm::navigate) },
    ) { pad ->
        val inner = Modifier.fillMaxSize().padding(pad)
        when (screen) {
            Screen.Now      -> NowScreen(vm, inner)
            Screen.History  -> HistoryScreen(vm, inner)
            Screen.Settings -> SettingsScreen(vm, inner)
            is Screen.Detail -> Unit
        }
    }
}

@Composable
private fun BottomBar(current: Screen, onSelect: (Screen) -> Unit) {
    NavigationBar {
        tab(current, Screen.Now,      "Now",      Icons.Filled.Bolt,     onSelect)
        tab(current, Screen.History,  "History",  Icons.Filled.History,  onSelect)
        tab(current, Screen.Settings, "Settings", Icons.Filled.Settings, onSelect)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.tab(
    current: Screen,
    target:  Screen,
    label:   String,
    icon:    ImageVector,
    onSelect: (Screen) -> Unit,
) {
    NavigationBarItem(
        selected = current == target,
        onClick  = { onSelect(target) },
        icon     = { Icon(icon, contentDescription = label) },
        label    = { Text(label) },
    )
}
