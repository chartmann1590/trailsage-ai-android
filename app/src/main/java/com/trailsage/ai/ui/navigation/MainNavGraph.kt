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

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                TrailSageBottomNav(tabs, route) { target -> nav.switchTab(target) }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "explore", modifier = Modifier.padding(padding)) {
            composable("explore") {
                ExploreScreen(
                    onOpenRouteBuilder = { nav.navigate("route-builder") },
                    onOpenMap = { nav.switchTab("map") },
                    onOpenStory = { storyId -> nav.navigate("story/$storyId") },
                    onOpenItinerary = { nav.navigate("itinerary") },
                )
            }
            composable("map") { MapScreen(onOpenStory = { storyId -> nav.navigate("story/$storyId") }) }
            composable("downloads") { DownloadsScreen(vm) }
            composable("guide") { AiGuideChatScreen() }
            composable("settings") { SettingsScreen(vm) { nav.navigate(it) } }

            composable("destination") {
                DestinationDetailScreen(onBack = { nav.popBackStack() }, onStartTour = { nav.navigate("tour") })
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
        }
    }
}
