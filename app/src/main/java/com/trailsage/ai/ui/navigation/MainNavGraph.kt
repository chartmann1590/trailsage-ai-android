package com.charles.trailsage.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.charles.trailsage.ui.AppViewModel
import com.charles.trailsage.ui.components.BottomNavItem
import com.charles.trailsage.ui.components.TrailSageBottomNav
import com.charles.trailsage.ui.screens.destination.DestinationDetailScreen
import com.charles.trailsage.ui.screens.downloads.DownloadsScreen
import com.charles.trailsage.ui.screens.explore.ExploreScreen
import com.charles.trailsage.ui.screens.guide.AiGuideChatScreen
import com.charles.trailsage.ui.screens.map.MapScreen
import com.charles.trailsage.ui.screens.route.CustomRouteBuilderScreen
import com.charles.trailsage.ui.screens.settings.*
import com.charles.trailsage.ui.screens.story.StoryDetailScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.charles.trailsage.ui.screens.tour.ActiveTourScreen
import com.charles.trailsage.ui.screens.tour.DrivingModeScreen
import com.charles.trailsage.ui.screens.rewards.RewardsScreen
import com.charles.trailsage.ui.components.AdmobBanner
import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext

private val tabs = listOf(
    BottomNavItem("explore", "Explore", Icons.Default.Explore),
    BottomNavItem("map", "Map", Icons.Default.Map),
    BottomNavItem("downloads", "Downloads", Icons.Default.Download),
    BottomNavItem("guide", "Guide", Icons.Default.AutoAwesome),
    BottomNavItem("settings", "Settings", Icons.Default.Settings),
)

/** Single, consistent way to switch top-level tabs so saved/restored state never corrupts. */
private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.startDestinationId) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun MainNavGraph(vm: AppViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBottomBar = route in tabs.map { it.route }

    val pendingImportId by vm.sharedTripImporter.pendingImportId.collectAsStateWithLifecycle()
    var isImporting by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(pendingImportId) {
        val id = pendingImportId ?: return@LaunchedEffect
        isImporting = true
        val result = vm.sharedTripImporter.importTrip(id)
        isImporting = false
        if (result.isSuccess) {
            nav.navigate("itinerary")
        } else {
            android.widget.Toast.makeText(
                context,
                "Failed to import trip: ${result.exceptionOrNull()?.message}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
        vm.sharedTripImporter.setPendingImport(null)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                val activity = LocalContext.current as? Activity
                Column {
                    if (route != "driving") {
                        AdmobBanner(applyNavigationInsets = !showBottomBar, vm = vm)
                    }
                    if (showBottomBar) {
                        TrailSageBottomNav(tabs, route) { target ->
                            vm.adManager.showRandomInterstitial(activity ?: return@TrailSageBottomNav)
                            nav.switchTab(target)
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(nav, startDestination = "explore", modifier = Modifier.padding(padding)) {
            composable("explore") {
                val activity = LocalContext.current as? Activity
                ExploreScreen(
                    onOpenRouteBuilder = {
                        vm.adManager.showRandomInterstitial(activity ?: return@ExploreScreen)
                        nav.navigate("route-builder")
                    },
                    onOpenMap = { nav.switchTab("map") },
                    onOpenStory = { storyId ->
                        vm.adManager.showRandomInterstitial(activity ?: return@ExploreScreen)
                        nav.navigate("story/$storyId")
                    },
                    onOpenItinerary = { nav.navigate("itinerary") },
                )
            }
            composable("map") { MapScreen(onOpenStory = { storyId -> nav.navigate("story/$storyId") }) }
            composable("downloads") { DownloadsScreen(vm) }
            composable("guide") { AiGuideChatScreen() }
            composable("settings") { SettingsScreen(vm) { nav.navigate(it) } }

            composable("destination") {
                val activity = LocalContext.current as? Activity
                DestinationDetailScreen(
                    onBack = { nav.popBackStack() },
                    onStartTour = {
                        vm.adManager.showRandomInterstitial(activity ?: return@DestinationDetailScreen)
                        nav.navigate("tour")
                    }
                )
            }
            composable("tour") {
                ActiveTourScreen(
                    vm,
                    onBack = { nav.popBackStack() },
                    onOpenDriving = { nav.switchTab("map") },
                    onOpenMap = { nav.switchTab("map") },
                )
            }
            composable("driving") { DrivingModeScreen() }
            composable("voice") { VoiceSettingsScreen(onBack = { nav.popBackStack() }) }
            composable("attribution") { AttributionScreen(vm, onBack = { nav.popBackStack() }) }
            composable(
                "story/{storyId}",
                arguments = listOf(navArgument("storyId") { type = NavType.StringType }),
            ) { StoryDetailScreen(onBack = { nav.popBackStack() }) }
            composable("route-builder") {
                CustomRouteBuilderScreen(
                    onBack = { nav.popBackStack() },
                    onStartTour = { nav.switchTab("map") },
                )
            }
            composable("itinerary") {
                com.charles.trailsage.ui.screens.itinerary.ItineraryScreen(
                    onBack = { nav.popBackStack() },
                    onOpenMap = { nav.switchTab("map") },
                    onOpenStory = { storyId -> nav.navigate("story/$storyId") },
                )
            }
            composable("notifications") { NotificationsScreen(onBack = { nav.popBackStack() }) }
            composable("privacy") { PrivacyScreen(onBack = { nav.popBackStack() }) }
            composable("rewards") { RewardsScreen(vm = vm, onBack = { nav.popBackStack() }) }
        }
    }

    if (isImporting) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                    Text("Importing shared trip…", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
}
