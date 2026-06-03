package com.charles.trailsage.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charles.trailsage.BuildConfig
import com.charles.trailsage.ui.AppViewModel
import com.charles.trailsage.ui.components.*

@Composable
fun SettingsScreen(vm: AppViewModel, onNavigate: (String) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    TrailScreen {
        ScreenTitle("Settings")
        SurfaceCard {
            SettingSwitch("Wi-Fi-only downloads", settings?.wifiOnlyDownloads != false) {
                vm.updateSettings { s -> s.copy(wifiOnlyDownloads = it) }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            SettingSwitch("Allow Android TTS emergency fallback", settings?.allowAndroidTtsFallback == true) {
                vm.updateSettings { s -> s.copy(allowAndroidTtsFallback = it) }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            SettingSwitch("Kid-friendly narration", settings?.kidFriendlyMode == true) {
                vm.updateSettings { s -> s.copy(kidFriendlyMode = it) }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            SettingSwitch("Optional telemetry (Firebase)", settings?.telemetryEnabled == true) {
                vm.updateSettings { s -> s.copy(telemetryEnabled = it) }
            }
        }
        SectionHeader("More")
        listOf(
            "voice" to "Voice settings",
            "notifications" to "Notifications",
            "attribution" to "Sources & attribution",
            "privacy" to "Privacy",
        ).forEach { (route, label) ->
            ListItem(
                headlineContent = { Text(label) },
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onNavigate(route) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            )
        }
        if (BuildConfig.DEBUG) {
            SecondaryButton("Reset setup (debug)", onClick = vm::reset)
        }
    }
}

@Composable
fun VoiceSettingsScreen(onBack: () -> Unit, vm: VoiceSettingsViewModel = hiltViewModel()) {
    val voices by vm.voices.collectAsStateWithLifecycle()
    val previewing by vm.previewing.collectAsStateWithLifecycle()
    DetailScaffold("Voice settings", onBack) {
        InfoCard(
            "Natural offline voice first",
            "TrailSage always selects a verified neural voice before any fallback. The Android system voice is a last resort only.",
        )
        if (voices.none { it.installed }) {
            EmptyStateCard("No natural voice installed", "Download a neural voice pack during setup to enable natural narration.")
        }
        voices.forEach { voice ->
            VoicePreviewCard(
                name = voice.name,
                style = if (voice.installed) voice.style else "Not installed",
                selected = voice.selected,
                previewing = previewing == voice.id,
                onPreview = { vm.preview(voice) },
                onSelect = { vm.select(voice.id) },
            )
        }
        InfoCard(
            "Emergency Android fallback",
            "Disabled by default. When enabled and used, the UI shows a warning and records the android_tts_fallback_used event.",
        )
    }
}

@Composable
fun AttributionScreen(vm: AppViewModel, onBack: () -> Unit) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    DetailScaffold("Sources & attribution", onBack) {
        if (sources.isEmpty()) {
            EmptyStateCard("No imported sources yet", "Install the sample tour to import public-source attribution.")
        } else {
            sources.forEach { source ->
                AttributionCard(source.title, "${source.license}\n${source.url}")
            }
        }
        AttributionCard("Map data", "© OpenStreetMap contributors, ODbL")
        AttributionCard("Imagery", "Bundled hero imagery is public-domain / CC0; see ATTRIBUTION.md for per-file credits.")
    }
}

@Composable
fun NotificationsScreen(onBack: () -> Unit) = DetailScaffold("Notifications", onBack) {
    InfoCard(
        "Updates only",
        "Opt in to alerts for updated tour packs, featured destinations, voice packs, and model packs. No spam, and only after permission is granted.",
    )
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) = DetailScaffold("Privacy", onBack) {
    InfoCard(
        "Private by design",
        "AI runs on-device whenever possible. Downloaded packs work offline. TrailSage does not sell location data and uses no paid AI APIs. Optional Firebase telemetry requires your consent.",
    )
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onChange)
    }
}

