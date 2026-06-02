package com.charles.trailsage.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Forest = Color(0xFF1B3022)
private val Sandstone = Color(0xFFD9C5B2)
private val Gold = Color(0xFFFFB84D)
private val Blue = Color(0xFF4A90E2)
private val OffWhite = Color(0xFFFBF9F4)
private val Context.trailSageDataStore by preferencesDataStore("trailsage_settings")
private val SetupCompleteKey = booleanPreferencesKey("setup_complete")

@Composable fun TrailSageApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val setupComplete by context.trailSageDataStore.data.map { it[SetupCompleteKey] ?: false }.collectAsState(initial = false)
    val saveSetup: (Boolean) -> Unit = { value -> scope.launch { context.trailSageDataStore.edit { it[SetupCompleteKey] = value } } }
    MaterialTheme(colorScheme = lightColorScheme(primary = Forest, secondary = Sandstone, tertiary = Gold, background = OffWhite)) {
        Surface(Modifier.fillMaxSize(), color = OffWhite) {
            if (setupComplete) MainNavGraph { saveSetup(false) } else SetupNavGraph { saveSetup(true) }
        }
    }
}

@Composable private fun SetupNavGraph(onComplete: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    when (step) {
        0 -> SetupPage("TrailSage AI", "Your private road trip storyteller.", "Set up offline guide") { step++ }
        1 -> SetupPage("Works where signal doesn't", "Download the guide, natural voice, sample High Peaks tour, map, and local source notes before you go.", "Check this device") { step++ }
        2 -> SetupPage("Pixel-ready", "Android version, storage, network, location services, notifications, ABI, memory, LiteRT, and neural voice support are checked locally.", "Review downloads") { step++ }
        3 -> RequiredDownloadsScreen { step++ }
        4 -> SetupPage("Natural offline voice", "TrailSage uses a verified Sherpa-ONNX voice first. Android system speech is an optional emergency fallback only.", "Verify sample assets") { step++ }
        else -> SetupPage("Ready for the road", "Your offline sample tour is installed. Large production model and voice files remain manifest-driven downloads.", "Explore TrailSage") { onComplete() }
    }
}

@Composable private fun SetupPage(title: String, body: String, button: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.padding(top = 64.dp)) {
            Text("TRAILSAGE AI", color = Forest, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(20.dp))
            Text(title, fontSize = 38.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, color = Forest)
            Spacer(Modifier.height(18.dp))
            Text(body, fontSize = 18.sp, lineHeight = 28.sp)
        }
        Button(onClick, Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) { Text(button) }
    }
}

@Composable private fun RequiredDownloadsScreen(onClick: () -> Unit) {
    val items = listOf("Gemma 4 E2B local model" to "2.41 GB", "LiteRT-LM runtime assets" to "Verified", "Sherpa neural voice" to "Required", "High Peaks sample tour + map" to "Offline")
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Spacer(Modifier.height(32.dp)); Text("Required downloads", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Forest)
        Text("TrailSage verifies every required asset before unlocking tours.", Modifier.padding(vertical = 12.dp))
        items.forEach { (name, detail) ->
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Download, null, tint = Blue); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.SemiBold); Text(detail, color = Color.DarkGray, fontSize = 13.sp) }
                }
            }
        }
        Spacer(Modifier.weight(1f)); Button(onClick, Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) { Text("Download demo pack") }
    }
}

@Composable private fun MainNavGraph(onReset: () -> Unit) {
    var route by remember { mutableStateOf("Explore") }
    Scaffold(bottomBar = {
        NavigationBar {
            listOf("Explore" to Icons.Default.Explore, "Tour" to Icons.Default.Navigation, "Guide" to Icons.Default.AutoAwesome, "Downloads" to Icons.Default.Download, "Settings" to Icons.Default.Settings).forEach { (label, icon) ->
                NavigationBarItem(route == label, { route = label }, { Icon(icon, label) }, label = { Text(label, fontSize = 10.sp) })
            }
        }
    }) { padding -> Box(Modifier.padding(padding)) { when (route) {
        "Explore" -> ExploreScreen { route = "Tour" }; "Tour" -> ActiveTourScreen(); "Guide" -> GuideScreen()
        "Downloads" -> DownloadsScreen(); else -> SettingsScreen(onReset)
    } } }
}

@Composable private fun Page(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Spacer(Modifier.height(12.dp)); Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Forest); Spacer(Modifier.height(16.dp)); content()
    }
}
@Composable private fun InfoCard(title: String, text: String) { Card(Modifier.fillMaxWidth().padding(vertical = 7.dp), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(Color.White)) { Column(Modifier.padding(18.dp)) { Text(title, fontWeight = FontWeight.Bold, fontSize = 19.sp); Spacer(Modifier.height(5.dp)); Text(text, lineHeight = 22.sp) } } }

@Composable private fun ExploreScreen(onTour: () -> Unit) = Page("Explore offline") {
    Text("PRIVATE • ON-DEVICE • ROAD-READY", color = Forest, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    InfoCard("Adirondack High Peaks Loop", "A public-source sample drive through mountain gateways, historic communities, and scenic pull-offs. 4 stories • offline map fallback")
    Button(onTour, Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)) { Text("Start sample tour") }
    Text("Featured stops", Modifier.padding(top = 24.dp), fontWeight = FontWeight.Bold, fontSize = 20.sp)
    listOf("Lake Placid", "Keene Valley", "Cascade Lakes", "High Peaks Wilderness").forEach { InfoCard(it, "Local story notes with Wikipedia, Wikimedia Commons, and OpenStreetMap attribution.") }
}
@Composable private fun ActiveTourScreen() = Page("High Peaks Loop") {
    AssistChip({ }, { Text("GPS ready • offline") }, leadingIcon = { Icon(Icons.Default.LocationOn, null, tint = Blue) })
    Box(Modifier.fillMaxWidth().height(190.dp).background(Color(0xFFDDE7DD), RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) { Text("Offline PMTiles map\nRoute and POI overlay fallback", color = Forest, fontWeight = FontWeight.Bold) }
    InfoCard("Next story: Cascade Lakes", "Narration starts automatically when you enter the trigger radius. Controls stay large and driving-safe.")
    Button({}, Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(18.dp)) { Icon(Icons.Default.PlayArrow, null); Text(" Play narration", fontSize = 18.sp) }
}
@Composable private fun GuideScreen() = Page("Ask your offline guide") { InfoCard("RAG-first answers", "Questions use downloaded destination notes only. When context is missing, TrailSage asks you to update the pack instead of inventing an answer."); OutlinedTextField("", {}, Modifier.fillMaxWidth(), placeholder = { Text("Ask about a nearby place") }); Button({}, Modifier.padding(top = 10.dp)) { Text("Ask locally") } }
@Composable private fun DownloadsScreen() = Page("Downloads") { listOf("Gemma 4 E2B model", "LiteRT-LM runtime", "Sherpa neural voice", "High Peaks tour pack", "Offline map + source notes").forEach { InfoCard(it, "Manifest-driven • SHA-256 verified • private app storage") } }
@Composable private fun SettingsScreen(onReset: () -> Unit) = Page("Settings") { InfoCard("Privacy", "AI runs on device whenever possible. No paid AI API. No location selling. Telemetry is opt-in."); InfoCard("Voice", "Natural Sherpa-ONNX voice first. Android system fallback remains disabled until you opt in."); OutlinedButton(onReset) { Text("Reset setup (debug)") } }
