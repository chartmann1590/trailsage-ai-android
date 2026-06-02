package com.charles.trailsage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.charles.trailsage.BuildConfig
import com.charles.trailsage.data.local.RequiredAssetEntity
import com.charles.trailsage.domain.SetupState
import com.charles.trailsage.map.ActiveTourMap
import java.io.File

private val Forest = Color(0xFF1B3022)
private val Sandstone = Color(0xFFD9C5B2)
private val Gold = Color(0xFFFFB84D)
private val Blue = Color(0xFF4A90E2)
private val OffWhite = Color(0xFFFBF9F4)
private val Charcoal = Color(0xFF121212)

@Composable fun TrailSageApp(vm: AppViewModel = hiltViewModel()) {
    val state by vm.setup.collectAsStateWithLifecycle()
    MaterialTheme(colorScheme = lightColorScheme(primary = Forest, secondary = Sandstone, tertiary = Gold, background = OffWhite, surface = OffWhite)) {
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), color = OffWhite) {
            if (state.status.setupComplete) MainNavGraph(vm) else SetupNavGraph(vm, state)
        }
    }
}

@Composable private fun SetupNavGraph(vm: AppViewModel, state: SetupUiState) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "splash") {
        composable("splash") { SetupTemplate("TrailSage AI", "Your private road trip storyteller.", "Continue") { nav.navigate("welcome") } }
        composable("welcome") { SetupTemplate("Your guide goes offline", "TrailSage downloads a private storyteller, natural voice, map, and public-source tour notes before the drive.", "Why downloads are required") { vm.mark(SetupState.NOT_STARTED); nav.navigate("why") } }
        composable("why") { SetupTemplate("Built for places without signal", "On-device AI, offline narration, GPS triggers, and local maps keep working in remote scenic areas.", "Check this device") { nav.navigate("device"); vm.checkDevice() } }
        composable("device") { DeviceCheckScreen(state) { nav.navigate(if (state.compatibility?.supported == true) "required" else "failed") } }
        composable("required") { RequiredDownloadsScreen(state.assets, { nav.navigate("ai") }, vm::download) }
        composable("ai") { DownloadStageScreen("AI model download", "Gemma 4 E2B IT runs locally through LiteRT-LM. The verified immutable model is required.", state.assets.filterType("GEMMA_MODEL"), vm::download) { nav.navigate("tts") } }
        composable("tts") { DownloadStageScreen("Neural voice engine", "Sherpa-ONNX runtime files and a verified natural voice are required. Android system TTS cannot unlock setup.", state.assets.filter { it.type == "TTS_ENGINE" || it.type == "LITERT_ASSET" }, vm::download) { nav.navigate("voice") } }
        composable("voice") { DownloadStageScreen("Natural voice setup", "Download and verify the LibriTTS medium offline voice pack for natural narration.", state.assets.filterType("VOICE_PACK"), vm::download) { nav.navigate("sample") } }
        composable("sample") { SampleTourSetupScreen { vm.installSample(); nav.navigate("verify") } }
        composable("verify") { SetupVerificationScreen(state, { vm.verify() }, { nav.navigate("failed") }) }
        composable("complete") { SetupTemplate("Ready for the road", "All required assets are installed and SHA-256 verified.", "Explore offline tours") {} }
        composable("failed") { SetupFailedScreen(state.status.lastError ?: "Setup could not complete because required production assets are missing.") { nav.navigate("required") } }
    }
}

private fun List<RequiredAssetEntity>.filterType(type: String) = filter { it.type == type }

@Composable private fun SetupTemplate(title: String, body: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.padding(top = 36.dp)) { Brand(); Spacer(Modifier.height(24.dp)); Heading(title); Spacer(Modifier.height(14.dp)); Text(body, fontSize = 18.sp, lineHeight = 28.sp) }
        PrimaryButton(action, onAction)
    }
}
@Composable private fun DeviceCheckScreen(state: SetupUiState, next: () -> Unit) = SetupListPage("Device check", "TrailSage checks the device locally.") {
    state.compatibility?.messages?.forEach { SetupChecklistItem(it, true) } ?: SetupChecklistItem("Checking device capabilities...", false)
    Spacer(Modifier.weight(1f)); PrimaryButton(if (state.compatibility?.supported == true) "Review required downloads" else "View setup issue", next)
}
@Composable private fun RequiredDownloadsScreen(assets: List<RequiredAssetEntity>, next: () -> Unit, download: (RequiredAssetEntity) -> Unit) = SetupListPage("Required downloads", "Main tours stay locked until every required production asset verifies.") {
    assets.forEach { DownloadAssetCard(it) { download(it) } }; Spacer(Modifier.weight(1f)); PrimaryButton("Set up AI model", next)
}
@Composable private fun DownloadStageScreen(title: String, text: String, assets: List<RequiredAssetEntity>, download: (RequiredAssetEntity) -> Unit, next: () -> Unit) = SetupListPage(title, text) {
    assets.forEach { DownloadAssetCard(it) { download(it) } }; Spacer(Modifier.weight(1f)); PrimaryButton("Continue", next)
}
@Composable private fun SampleTourSetupScreen(install: () -> Unit) = SetupListPage("Sample tour setup", "Install the public-source Adirondack High Peaks Loop into private storage, including POIs, triggers, local source notes, and map fallback.") {
    InfoCard("Adirondack High Peaks Loop", "4 GPS-triggered stories • public-source attribution • offline route • PMTiles replacement point")
    Spacer(Modifier.weight(1f)); PrimaryButton("Install sample tour", install)
}
@Composable private fun SetupVerificationScreen(state: SetupUiState, verify: () -> Unit, failed: () -> Unit) = SetupListPage("Verify offline setup", "TrailSage never silently skips a required asset.") {
    state.assets.filter { it.required }.forEach { SetupChecklistItem(it.name, it.installed && it.verified && !it.demoPlaceholder) }
    state.status.lastError?.let { ErrorCard(it) }
    Spacer(Modifier.weight(1f)); PrimaryButton("Verify required assets") { verify() }; TextButton(failed) { Text("Review missing assets") }
}
@Composable private fun SetupFailedScreen(error: String, retry: () -> Unit) = SetupListPage("Setup needs attention", "TrailSage remains locked until the required offline assets are ready.") {
    ErrorCard(error); Spacer(Modifier.weight(1f)); PrimaryButton("Return to downloads", retry)
}
@Composable private fun SetupListPage(title: String, intro: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(24.dp)) { Brand(); Spacer(Modifier.height(18.dp)); Heading(title); Text(intro, Modifier.padding(vertical = 12.dp), lineHeight = 22.sp); content() }
}

@Composable private fun MainNavGraph(vm: AppViewModel) {
    val nav = rememberNavController()
    val tabs = listOf("explore" to Icons.Default.Explore, "tour" to Icons.Default.Navigation, "guide" to Icons.Default.AutoAwesome, "downloads" to Icons.Default.Download, "settings" to Icons.Default.Settings)
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing, bottomBar = {
        NavigationBar(Modifier.navigationBarsPadding()) { tabs.forEach { (route, icon) -> NavigationBarItem(currentRoute(nav) == route, { nav.navigate(route) { launchSingleTop = true } }, { Icon(icon, route) }, label = { Text(route.replaceFirstChar(Char::uppercase), fontSize = 10.sp) }) } }
    }) { padding ->
        NavHost(nav, "explore", Modifier.padding(padding)) {
            composable("explore") { ExploreScreen(nav, vm) }; composable("destination") { DestinationDetailScreen(nav) }
            composable("tour") { ActiveTourScreen(nav, vm) }; composable("driving") { DrivingModeScreen() }; composable("guide") { AiGuideChatScreen() }
            composable("map") { MapScreen(vm) }; composable("downloads") { DownloadsScreen(vm) }; composable("voice") { VoiceSettingsScreen() }
            composable("settings") { SettingsScreen(nav, vm) }; composable("attribution") { AttributionScreen(vm) }; composable("story") { StoryDetailScreen() }
            composable("route-builder") { CustomRouteBuilderScreen() }; composable("notifications") { NotificationsScreen() }; composable("privacy") { PrivacyScreen() }
        }
    }
}
@Composable private fun currentRoute(nav: NavHostController) = nav.currentBackStackEntryAsState().value?.destination?.route

@Composable private fun ExploreScreen(nav: NavHostController, vm: AppViewModel) = Page("Explore offline") {
    Text("PRIVATE • ON-DEVICE • ROAD-READY", color = Forest, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    HeroDestinationCard { nav.navigate("destination") }; Section("Featured stops")
    val pois by vm.pois.collectAsStateWithLifecycle(); pois.forEach { StoryCard(it.name, "Downloaded public-source stop") { nav.navigate("story") } }
    OutlinedButton({ nav.navigate("route-builder") }) { Text("Build a custom route") }
}
@Composable private fun DestinationDetailScreen(nav: NavHostController) = Page("Adirondack High Peaks Loop") { InfoCard("Offline scenic drive", "A 95-minute loop through Lake Placid, Keene Valley, Cascade Lakes, and a High Peaks Wilderness gateway."); InfoCard("Sources included", "Wikipedia contributors and OpenStreetMap contributors are retained in the downloaded pack."); PrimaryButton("Start tour") { nav.navigate("tour") } }
@Composable private fun ActiveTourScreen(nav: NavHostController, vm: AppViewModel) = Page("High Peaks Loop") { GpsStatusChip(); OfflineStatusChip(); Box(Modifier.fillMaxWidth().height(190.dp).background(Color(0xFFDDE7DD), RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) { Text("Offline map ready\nRoute and POI overlay", color = Forest, fontWeight = FontWeight.Bold) }; InfoCard("Next story: Cascade Lakes", "Narration starts when the vehicle enters the trigger radius."); PrimaryButton("Open driving mode") { nav.navigate("driving") }; OutlinedButton({ nav.navigate("map") }) { Text("Open map") } }
@Composable private fun DrivingModeScreen() = Page("Driving mode") { Text("Cascade Lakes", fontSize = 34.sp, fontWeight = FontWeight.Bold); Text("Narration controls stay large and glanceable.", Modifier.padding(vertical = 12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { DrivingControlButton("Back"); DrivingControlButton("Play"); DrivingControlButton("Next") } }
@Composable private fun AiGuideChatScreen() = Page("Ask your offline guide") { var query by remember { mutableStateOf("") }; var answer by remember { mutableStateOf("Answers use downloaded source context only.") }; InfoCard("RAG-first answers", answer); OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Ask about a nearby place") }); PrimaryButton("Ask locally") { answer = "I do not have enough local source context for that question. Download or update this destination pack." } }
@Composable private fun MapScreen(vm: AppViewModel) = Page("Offline map") { val context = androidx.compose.ui.platform.LocalContext.current; Box(Modifier.fillMaxWidth().height(420.dp)) { ActiveTourMap(File(context.filesDir, "tours/adirondack-high-peaks-loop/map.pmtiles")) { Column { InfoCard("Map data unavailable", "Install a valid PMTiles extract to render MapLibre tiles. Your route and stops remain available offline."); val pois by vm.pois.collectAsStateWithLifecycle(); pois.forEach { Text("• ${it.name}", Modifier.padding(5.dp)) } } } }; Text("Map data © OpenStreetMap contributors", fontSize = 12.sp) }
@Composable private fun DownloadsScreen(vm: AppViewModel) = Page("Downloads") { val state by vm.setup.collectAsStateWithLifecycle(); state.assets.forEach { DownloadAssetCard(it) { vm.download(it) } } }
@Composable private fun VoiceSettingsScreen() = Page("Voice settings") { InfoCard("Natural offline voice first", "TrailSage selects a verified Sherpa-ONNX neural voice before any fallback."); InfoCard("Emergency Android fallback", "Disabled by default. When enabled and used, the UI shows a warning and records the fallback event.") }
@Composable private fun SettingsScreen(nav: NavHostController, vm: AppViewModel) = Page("Settings") { val settings by vm.settings.collectAsStateWithLifecycle(); SettingsSwitch("Wi-Fi-only downloads", settings?.wifiOnlyDownloads != false) { vm.updateSettings { s -> s.copy(wifiOnlyDownloads = it) } }; SettingsSwitch("Allow Android TTS emergency fallback", settings?.allowAndroidTtsFallback == true) { vm.updateSettings { s -> s.copy(allowAndroidTtsFallback = it) } }; SettingsSwitch("Optional telemetry", settings?.telemetryEnabled == true) { vm.updateSettings { s -> s.copy(telemetryEnabled = it) } }; listOf("voice" to "Voice settings", "notifications" to "Notifications", "privacy" to "Privacy", "attribution" to "Attribution").forEach { (route, label) -> TextButton({ nav.navigate(route) }) { Text(label) } }; if (BuildConfig.DEBUG) OutlinedButton(vm::reset) { Text("Reset setup (debug)") } }
@Composable private fun AttributionScreen(vm: AppViewModel) = Page("Sources & attribution") { val sources by vm.sources.collectAsStateWithLifecycle(); if (sources.isEmpty()) InfoCard("No imported sources yet", "Install the sample tour to import public-source attribution.") else sources.forEach { InfoCard(it.title, "${it.license}\n${it.url}") }; InfoCard("Map data", "© OpenStreetMap contributors, ODbL") }
@Composable private fun StoryDetailScreen() = Page("Cascade Lakes") { InfoCard("Scenic corridor", "The downloaded sample narration is stored locally and played when the corresponding GPS trigger matches."); SourceChip("OpenStreetMap contributors") }
@Composable private fun CustomRouteBuilderScreen() = Page("Custom route builder") { InfoCard("Offline-first route packs", "Import a reviewed route GeoJSON and user-supplied OSM-derived local extract with the included builder utility."); OutlinedTextField("", {}, Modifier.fillMaxWidth(), placeholder = { Text("Destination name") }); PrimaryButton("Prepare route pack") {} }
@Composable private fun NotificationsScreen() = Page("Notifications") { InfoCard("Updates only", "Opt in to alerts for updated tour packs, featured destinations, voice packs, and model packs. No spam.") }
@Composable private fun PrivacyScreen() = Page("Privacy") { InfoCard("Private by design", "AI runs on-device whenever possible. Downloaded packs work offline. TrailSage does not sell location data or use paid AI APIs. Optional Firebase telemetry requires consent.") }

@Composable private fun Page(title: String, content: @Composable ColumnScope.() -> Unit) { Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp)) { Heading(title); Spacer(Modifier.height(14.dp)); content() } }
@Composable private fun Brand() = Text("TRAILSAGE AI", color = Forest, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
@Composable private fun Heading(text: String) = Text(text, fontSize = 31.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, color = Forest)
@Composable private fun Section(text: String) = Text(text, Modifier.padding(top = 22.dp, bottom = 8.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold)
@Composable private fun PrimaryButton(text: String, action: () -> Unit) = Button(action, Modifier.fillMaxWidth().padding(top = 8.dp).height(56.dp), shape = RoundedCornerShape(15.dp)) { Text(text) }
@Composable private fun InfoCard(title: String, text: String) = Card(Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(Color.White)) { Column(Modifier.padding(18.dp)) { Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp); Spacer(Modifier.height(5.dp)); Text(text, lineHeight = 21.sp) } }
@Composable private fun ErrorCard(text: String) = Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer)) { Text(text, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer) }
@Composable private fun SetupChecklistItem(text: String, complete: Boolean) = Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { Icon(if (complete) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (complete) Forest else Sandstone); Spacer(Modifier.width(10.dp)); Text(text) }
@Composable private fun DownloadAssetCard(asset: RequiredAssetEntity, action: () -> Unit) = Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(Color.White)) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(if (asset.verified) Icons.Default.CheckCircle else Icons.Default.Download, null, tint = if (asset.verified) Forest else Blue); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(asset.name, fontWeight = FontWeight.SemiBold); Text(if (asset.verified) "Verified" else "${asset.type} • ${asset.sizeBytes / 1024 / 1024} MB", fontSize = 12.sp) }; if (!asset.verified) TextButton(action) { Text("Get") } } }
@Composable private fun HeroDestinationCard(action: () -> Unit) = Card(action, Modifier.fillMaxWidth().padding(vertical = 8.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(Forest)) { Column(Modifier.padding(22.dp)) { Text("ADIRONDACK SAMPLE TOUR", color = Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp); Text("High Peaks Loop", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp); Text("95 min • downloaded public-source stories", color = Color.White) } }
@Composable private fun StoryCard(title: String, text: String, action: () -> Unit) = Card(action, Modifier.fillMaxWidth().padding(vertical = 5.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(Color.White)) { Column(Modifier.padding(16.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(text, fontSize = 13.sp) } }
@Composable private fun OfflineStatusChip() = AssistChip({}, { Text("Offline ready") }, leadingIcon = { Icon(Icons.Default.CloudDone, null) })
@Composable private fun GpsStatusChip() = AssistChip({}, { Text("GPS monitoring") }, leadingIcon = { Icon(Icons.Default.LocationOn, null, tint = Blue) })
@Composable private fun SourceChip(text: String) = AssistChip({}, { Text(text) })
@Composable private fun DrivingControlButton(text: String) = Button({}, Modifier.size(92.dp), shape = RoundedCornerShape(46.dp)) { Text(text) }
@Composable private fun SettingsSwitch(text: String, checked: Boolean, change: (Boolean) -> Unit) = Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { Text(text, Modifier.weight(1f)); Switch(checked, change) }
