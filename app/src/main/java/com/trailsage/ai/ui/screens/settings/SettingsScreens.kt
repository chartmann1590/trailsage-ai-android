package com.charles.trailsage.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charles.trailsage.BuildConfig
import com.charles.trailsage.ui.AppViewModel
import com.charles.trailsage.ui.components.*
import androidx.compose.ui.platform.LocalUriHandler
import coil.compose.AsyncImage
import com.charles.trailsage.data.github.SubmittedIssue

@Composable
fun StatusBadge(status: String) {
    val isOpen = status.lowercase() == "open"
    val containerColor = if (isOpen) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
    val textColor = if (isOpen) Color(0xFF2E7D32) else Color(0xFFC62828)
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = if (isOpen) "Open" else "Closed",
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    issueVm: GitHubIssueViewModel = hiltViewModel(),
    onNavigate: (String) -> Unit
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val submittedIssues by issueVm.submittedIssues.collectAsStateWithLifecycle()
    
    var showReportDialog by remember { mutableStateOf(false) }
    var showDetailDialog by remember { mutableStateOf(false) }
    var selectedIssue by remember { mutableStateOf<SubmittedIssue?>(null) }

    LaunchedEffect(Unit) {
        issueVm.refreshIssues()
    }

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

        SectionHeader("Support & Feedback")
        SurfaceCard {
            ListItem(
                headlineContent = { Text("Report a Problem", fontWeight = FontWeight.Bold) },
                supportingContent = { Text("Submit a bug or feedback directly to our GitHub repository.") },
                leadingContent = { Icon(Icons.Default.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable { showReportDialog = true },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
            )
        }

        if (submittedIssues.isNotEmpty()) {
            SectionHeader("Your Bug Reports")
            submittedIssues.forEach { issue ->
                ListItem(
                    headlineContent = { Text(issue.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("Issue #${issue.number} • ${issue.createdAt.take(10)}") },
                    trailingContent = { StatusBadge(issue.status) },
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable {
                            selectedIssue = issue
                            issueVm.loadComments(issue.number)
                            showDetailDialog = true
                        },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
                )
            }
        }

        SectionHeader("More")
        listOf(
            "rewards" to "Rewards & Ad-Free",
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
        
        val uriHandler = LocalUriHandler.current
        SectionHeader("About")
        ListItem(
            headlineContent = { Text("Project website") },
            supportingContent = { Text("chartmann1590.github.io/trailsage-ai-android/") },
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable { uriHandler.openUri("https://chartmann1590.github.io/trailsage-ai-android/") },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        )
        ListItem(
            headlineContent = { Text("Privacy policy") },
            supportingContent = { Text("chartmann1590.github.io/trailsage-ai-android/privacy.html") },
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable { uriHandler.openUri("https://chartmann1590.github.io/trailsage-ai-android/privacy.html") },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        )
        if (BuildConfig.DEBUG) {
            SecondaryButton("Reset setup (debug)", onClick = vm::reset)
        }
    }

    if (showReportDialog) {
        var title by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var includeDiagnostics by remember { mutableStateOf(true) }
        var screenshotUri by remember { mutableStateOf<Uri?>(null) }
        
        val photoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
            onResult = { uri -> screenshotUri = uri }
        )
        
        val submitStatus by issueVm.submitStatus.collectAsStateWithLifecycle()
        val configValid = BuildConfig.GITHUB_API_TOKEN.isNotBlank() &&
            BuildConfig.GITHUB_REPO_OWNER.isNotBlank() &&
            BuildConfig.GITHUB_REPO_NAME.isNotBlank()

        LaunchedEffect(submitStatus) {
            if (submitStatus is SubmitStatus.Success) {
                showReportDialog = false
                issueVm.resetSubmitStatus()
            }
        }

        AlertDialog(
            onDismissRequest = {
                if (submitStatus !is SubmitStatus.Loading) {
                    showReportDialog = false
                    issueVm.resetSubmitStatus()
                }
            },
            title = { Text("Report a Problem", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                text = "Your report will be submitted to this app's GitHub issue tracker. Do not include passwords, private keys, medical information, financial information, or anything you do not want visible to the repository maintainers. If this repository is public, your report may be publicly visible.",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (!configValid) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Text(
                                text = "GitHub configuration is missing. Submission is disabled. Check that GITHUB_API_TOKEN, GITHUB_REPO_OWNER, and GITHUB_REPO_NAME are set in local.properties or repository secrets.",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title / Subject") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description of the issue") },
                        modifier = Modifier.fillMaxWidth().height(120.dp)
                    )
                    
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = includeDiagnostics,
                            onCheckedChange = { includeDiagnostics = it }
                        )
                        Text(
                            text = "Include system and model diagnostics",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.clickable { includeDiagnostics = !includeDiagnostics }
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = MaterialTheme.shapes.small
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Attach Screenshot")
                        }
                        
                        screenshotUri?.let { uri ->
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Attached screenshot",
                                    modifier = Modifier.fillMaxSize()
                                )
                                IconButton(
                                    onClick = { screenshotUri = null },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(20.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(bottomStart = 8.dp))
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                    
                    if (submitStatus is SubmitStatus.Loading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                    
                    if (submitStatus is SubmitStatus.Error) {
                        Text(
                            text = (submitStatus as SubmitStatus.Error).message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = title.isNotBlank() && description.isNotBlank() && submitStatus !is SubmitStatus.Loading && configValid,
                    onClick = {
                        issueVm.submitIssue(title, description, name, email, includeDiagnostics, screenshotUri)
                    }
                ) {
                    Text("Submit")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = submitStatus !is SubmitStatus.Loading,
                    onClick = {
                        showReportDialog = false
                        issueVm.resetSubmitStatus()
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDetailDialog && selectedIssue != null) {
        val issue = selectedIssue!!
        val commentsStatus by issueVm.commentsStatus.collectAsStateWithLifecycle()
        val commentPostStatus by issueVm.commentPostStatus.collectAsStateWithLifecycle()
        
        var replyText by remember { mutableStateOf("") }
        var replyScreenshotUri by remember { mutableStateOf<Uri?>(null) }
        
        val replyPhotoLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
            onResult = { uri -> replyScreenshotUri = uri }
        )
        
        val uriHandler = LocalUriHandler.current
        
        LaunchedEffect(commentPostStatus) {
            if (commentPostStatus is CommentPostStatus.Success) {
                replyText = ""
                replyScreenshotUri = null
                issueVm.resetCommentPostStatus()
            }
        }
        
        AlertDialog(
            onDismissRequest = {
                showDetailDialog = false
                issueVm.resetCommentPostStatus()
            },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(issue.title, style = MaterialTheme.typography.titleLarge)
                        Text("Issue #${issue.number}", style = MaterialTheme.typography.bodySmall)
                    }
                    StatusBadge(issue.status)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { uriHandler.openUri(issue.htmlUrl) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(Icons.Default.Launch, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("View on GitHub")
                    }
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        when (commentsStatus) {
                            is CommentsStatus.Loading -> {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            }
                            is CommentsStatus.Error -> {
                                Text(
                                    text = (commentsStatus as CommentsStatus.Error).message,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                            is CommentsStatus.Success -> {
                                val comments = (commentsStatus as CommentsStatus.Success).comments
                                if (comments.isEmpty()) {
                                    Text(
                                        text = "No replies yet.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.align(Alignment.Center),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(comments) { comment ->
                                            val isUserReply = comment.body.startsWith("## Reply")
                                            val bubbleColor = if (isUserReply) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            }
                                            val alignment = if (isUserReply) Alignment.End else Alignment.Start

                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalAlignment = alignment
                                            ) {
                                                Surface(
                                                    color = bubbleColor,
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.padding(horizontal = 4.dp)
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                        Text(
                                                            text = comment.author,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Spacer(Modifier.height(4.dp))
                                                        Text(
                                                            text = if (isUserReply) comment.body.removePrefix("## Reply").trim() else comment.body,
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = comment.createdAt.take(16).replace("T", " "),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                    
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = replyText,
                                onValueChange = { replyText = it },
                                placeholder = { Text("Write a reply…") },
                                modifier = Modifier.weight(1f),
                                maxLines = 3
                            )
                            
                            IconButton(
                                onClick = {
                                    replyPhotoLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            ) {
                                Icon(Icons.Default.AttachFile, contentDescription = "Attach image")
                            }
                            
                            IconButton(
                                enabled = replyText.isNotBlank() && commentPostStatus !is CommentPostStatus.Loading,
                                onClick = {
                                    issueVm.postComment(issue.number, replyText, replyScreenshotUri)
                                }
                            ) {
                                if (commentPostStatus is CommentPostStatus.Loading) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                } else {
                                    Icon(Icons.Default.Send, contentDescription = "Send")
                                }
                            }
                        }
                        
                        replyScreenshotUri?.let { uri ->
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            ) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Reply screenshot",
                                    modifier = Modifier.fillMaxSize()
                                )
                                IconButton(
                                    onClick = { replyScreenshotUri = null },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(16.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(bottomStart = 6.dp))
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(10.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDetailDialog = false
                        issueVm.resetCommentPostStatus()
                    }
                ) {
                    Text("Close")
                }
            }
        )
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

