package com.example.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ServerProfile
import com.example.ui.screens.AddServerDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ServerDetailDialog
import com.example.ui.screens.ServersScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SubscriptionsScreen
import com.example.ui.screens.TestingRankingScreen
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val inspectingProfile by viewModel.inspectingProfile.collectAsStateWithLifecycle()
    var showAddServerDialog by remember { mutableStateOf(false) }

    // Pending profile to connect after VPN permission is granted
    var pendingProfileForConnect by remember { mutableStateOf<ServerProfile?>(null) }

    // Android VpnService.prepare launcher
    val vpnPrepareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingProfileForConnect?.let { profile ->
                viewModel.requestConnect(context, profile) {}
            }
        }
        pendingProfileForConnect = null
    }

    // Back button handling: return to Dashboard if on another tab
    BackHandler(enabled = currentTab != AppTab.DASHBOARD) {
        viewModel.setTab(AppTab.DASHBOARD)
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("main_navigation_bar")
            ) {
                AppTab.values().forEach { tab ->
                    val isSelected = currentTab == tab
                    val icon = when (tab) {
                        AppTab.DASHBOARD -> Icons.Default.Dashboard
                        AppTab.SERVERS -> Icons.Default.Dns
                        AppTab.SUBSCRIPTIONS -> Icons.Default.RssFeed
                        AppTab.TESTING -> Icons.Default.Speed
                        AppTab.SETTINGS -> Icons.Default.Settings
                    }

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { viewModel.setTab(tab) },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = DarkBg,
                            selectedTextColor = CyanAccent,
                            indicatorColor = CyanAccent,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                    )
                }
            }
        },
        containerColor = DarkBg,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        when (currentTab) {
            AppTab.DASHBOARD -> DashboardScreen(
                viewModel = viewModel,
                onPrepareNeeded = { intent ->
                    pendingProfileForConnect = viewModel.currentProfile.value
                        ?: viewModel.mainSubServers.value.firstOrNull()
                        ?: viewModel.allServers.value.firstOrNull()
                    vpnPrepareLauncher.launch(intent)
                },
                modifier = Modifier.padding(innerPadding)
            )

            AppTab.SERVERS -> ServersScreen(
                viewModel = viewModel,
                onPrepareNeeded = { intent ->
                    vpnPrepareLauncher.launch(intent)
                },
                onAddServerClick = { showAddServerDialog = true },
                modifier = Modifier.padding(innerPadding)
            )

            AppTab.SUBSCRIPTIONS -> SubscriptionsScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding)
            )

            AppTab.TESTING -> TestingRankingScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding)
            )

            AppTab.SETTINGS -> SettingsScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }

    // Server Details Inspector Dialog
    inspectingProfile?.let { profile ->
        ServerDetailDialog(
            profile = profile,
            onDismiss = { viewModel.setInspectingProfile(null) },
            onDelete = { viewModel.deleteProfile(it) }
        )
    }

    // Add / Paste URI Dialog
    if (showAddServerDialog) {
        AddServerDialog(
            onDismiss = { showAddServerDialog = false },
            onImport = { uri -> viewModel.importConfig(uri) }
        )
    }
}
