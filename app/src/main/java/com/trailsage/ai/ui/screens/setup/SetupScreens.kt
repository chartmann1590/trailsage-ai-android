package com.charles.trailsage.ui.screens.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.charles.trailsage.data.local.DownloadEntity
import com.charles.trailsage.data.local.RequiredAssetEntity
import com.charles.trailsage.ui.SetupUiState
import com.charles.trailsage.ui.components.*
import com.charles.trailsage.ui.theme.ForestContainer
import com.charles.trailsage.ui.theme.SunriseGold

/* ---------- shared scaffolds ---------- */

@Composable
private fun SetupScaffold(
    title: String,
    intro: String,
    primaryLabel: String,
    primaryEnabled: Boolean = true,
    secondary: (@Composable () -> Unit)? = null,
    onPrimary: () -> Unit,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BrandLockup()
            Spacer(Modifier.height(4.dp))
            ScreenTitle(title)
            Text(intro, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content?.invoke(this)
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(primaryLabel, enabled = primaryEnabled, onClick = onPrimary)
        secondary?.invoke()
    }
}

/* ---------- hero / brand setup pages ---------- */

@Composable
fun SplashScreen(onContinue: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(ForestContainer, MaterialTheme.colorScheme.background)))) {
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().statusBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Box(
                    Modifier.size(72.dp).clip(MaterialTheme.shapes.large).background(SunriseGold),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Explore, null, tint = ForestContainer, modifier = Modifier.size(40.dp)) }
                Spacer(Modifier.height(24.dp))
                Text("TrailSage AI", style = MaterialTheme.typography.displayMedium, color = Color.White)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Your private road trip storyteller.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            PrimaryButton("Get started", onClick = onContinue)
        }
    }
}

@Composable
fun WelcomeSetupScreen(onNext: () -> Unit) = SetupScaffold(
    title = "Your guide goes offline",
    intro = "TrailSage downloads a private on-device storyteller, a natural voice, an offline map, and public-source tour notes before you drive — so it keeps working with no signal.",
    primaryLabel = "Why are downloads required?",
    onPrimary = onNext,
) {
    FeatureRow(Icons.Default.AutoAwesome, "On-device AI", "Gemma runs locally — your trips never leave the device.")
    FeatureRow(Icons.Default.RecordVoiceOver, "Natural narration", "Neural offline voice, not robotic system speech.")
    FeatureRow(Icons.Default.Map, "Offline maps & GPS", "Local map packs and GPS-triggered stories without a connection.")
}

@Composable
fun WhySetupScreen(onNext: () -> Unit) = SetupScaffold(
    title = "Built for places without signal",
    intro = "Scenic byways and national parks lose connectivity. TrailSage verifies every required asset up front so narration, maps, and the AI guide are ready in remote areas.",
    primaryLabel = "Check this device",
    onPrimary = onNext,
) {
    FeatureRow(Icons.Default.Lock, "Required, not optional", "Main tours stay locked until all required assets verify with SHA-256.")
    FeatureRow(Icons.Default.Shield, "Private by design", "No paid AI APIs and no hidden location selling.")
}

/* ---------- device & downloads ---------- */

@Composable
fun DeviceCheckScreen(state: SetupUiState, onNext: () -> Unit) = SetupScaffold(
    title = "Device check",
    intro = "TrailSage inspects this device locally to confirm it can run the offline AI and neural voice.",
    primaryLabel = if (state.compatibility?.supported == true) "Review required downloads" else "View setup issue",
    onPrimary = onNext,
) {
    val messages = state.compatibility?.messages
    if (messages == null) {
        SetupChecklistItem("Checking device capabilities…", false)
    } else {
        messages.forEach { SetupChecklistItem(it, state.compatibility.supported) }
        if (state.compatibility.limited) {
            ErrorStateCard("This device is supported but limited (low storage or no network). Downloads may be slow.")
        }
    }
}

@Composable
fun RequiredDownloadsScreen(
    state: SetupUiState,
    onDownload: (RequiredAssetEntity) -> Unit,
    onNext: () -> Unit,
) {
    val byAsset = state.downloads.associateBy { it.assetId }
    val verified = state.assets.count { it.verified }
    SetupScaffold(
        title = "Required downloads",
        intro = "Every required production asset must install and verify before the main app unlocks. $verified of ${state.assets.size} ready.",
        primaryLabel = "Set up the AI model",
        onPrimary = onNext,
    ) {
        state.assets.forEach { asset ->
            DownloadAssetCard(asset, byAsset[asset.id]) { onDownload(asset) }
        }
    }
}

@Composable
fun AiModelDownloadScreen(
    state: SetupUiState,
    onDownload: (RequiredAssetEntity) -> Unit,
    onNext: () -> Unit,
) = DownloadStage(
    title = "AI model download",
    intro = "Gemma 4 E2B IT runs locally through LiteRT-LM. The verified, immutable model is required for offline narration.",
    assets = state.assets.filter { it.type == "GEMMA_MODEL" || it.type == "LITERT_ASSET" },
    downloads = state.downloads,
    onDownload = onDownload,
    onNext = onNext,
)

@Composable
fun TtsEngineDownloadScreen(
    state: SetupUiState,
    onDownload: (RequiredAssetEntity) -> Unit,
    onNext: () -> Unit,
) = DownloadStage(
    title = "Neural voice engine",
    intro = "Sherpa-ONNX runtime files power natural narration. Android's built-in voice cannot unlock setup.",
    assets = state.assets.filter { it.type == "TTS_ENGINE" },
    downloads = state.downloads,
    onDownload = onDownload,
    onNext = onNext,
)

@Composable
fun NaturalVoiceSetupScreen(
    state: SetupUiState,
    onDownload: (RequiredAssetEntity) -> Unit,
    onPreview: () -> Unit,
    onNext: () -> Unit,
) {
    val byAsset = state.downloads.associateBy { it.assetId }
    val voices = state.assets.filter { it.type == "VOICE_PACK" }
    SetupScaffold(
        title = "Natural voice setup",
        intro = "Download and verify a natural offline voice. At least one verified neural voice is required before setup completes.",
        primaryLabel = "Continue",
        onPrimary = onNext,
    ) {
        voices.forEach { asset ->
            DownloadAssetCard(asset, byAsset[asset.id]) { onDownload(asset) }
            if (asset.verified) {
                VoicePreviewCard(
                    name = asset.name,
                    style = "Tap to hear the neural voice",
                    selected = false,
                    onPreview = onPreview,
                    onSelect = onPreview,
                )
            }
        }
    }
}

@Composable
private fun DownloadStage(
    title: String,
    intro: String,
    assets: List<RequiredAssetEntity>,
    downloads: List<DownloadEntity>,
    onDownload: (RequiredAssetEntity) -> Unit,
    onNext: () -> Unit,
) {
    val byAsset = downloads.associateBy { it.assetId }
    SetupScaffold(title = title, intro = intro, primaryLabel = "Continue", onPrimary = onNext) {
        assets.forEach { asset -> DownloadAssetCard(asset, byAsset[asset.id]) { onDownload(asset) } }
    }
}

@Composable
fun SampleTourSetupScreen(onInstall: () -> Unit) = SetupScaffold(
    title = "Sample tour setup",
    intro = "Install the public-source Adirondack High Peaks Loop into private storage: POIs, GPS triggers, local source notes, and an offline map fallback.",
    primaryLabel = "Install sample tour",
    onPrimary = onInstall,
) {
    InfoCard(
        "Adirondack High Peaks Loop",
        "4 GPS-triggered stories • Wikipedia & OpenStreetMap attribution • offline route • PMTiles map slot",
    )
}

@Composable
fun SetupVerificationScreen(state: SetupUiState, onVerify: () -> Unit, onFailed: () -> Unit) {
    SetupScaffold(
        title = "Verify offline setup",
        intro = "TrailSage never silently skips a required asset. Each one is confirmed installed and SHA-256 verified.",
        primaryLabel = "Verify required assets",
        secondary = { TextButton(onFailed, Modifier.fillMaxWidth()) { Text("Review missing assets") } },
        onPrimary = onVerify,
    ) {
        state.assets.filter { it.required }.forEach { asset ->
            SetupChecklistItem(asset.name, asset.installed && asset.verified && !asset.demoPlaceholder)
        }
        state.status.lastError?.let { ErrorStateCard(it) }
    }
}

@Composable
fun SetupCompleteScreen(onEnter: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(ForestContainer, MaterialTheme.colorScheme.background)))) {
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().statusBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(96.dp).clip(CircleShape).background(SunriseGold), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.CheckCircle, null, tint = ForestContainer, modifier = Modifier.size(56.dp))
                }
                Spacer(Modifier.height(24.dp))
                Text("Ready for the road", style = MaterialTheme.typography.displayMedium, color = Color.White)
                Spacer(Modifier.height(10.dp))
                Text(
                    "All required assets are installed and SHA-256 verified.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            PrimaryButton("Explore offline tours", onClick = onEnter)
        }
    }
}

@Composable
fun SetupFailedScreen(error: String, onRetry: () -> Unit) = SetupScaffold(
    title = "Setup needs attention",
    intro = "TrailSage stays locked until the required offline assets are ready.",
    primaryLabel = "Return to downloads",
    onPrimary = onRetry,
) {
    ErrorStateCard(error)
}

/* ---------- small helper ---------- */

@Composable
private fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier.size(44.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
