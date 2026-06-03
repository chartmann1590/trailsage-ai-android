package com.charles.trailsage.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.charles.trailsage.data.local.DownloadEntity
import com.charles.trailsage.data.local.RequiredAssetEntity
import com.charles.trailsage.ui.theme.RoadTripBlue
import java.util.Locale

private val runningStates = setOf("QUEUED", "CONNECTING", "DOWNLOADING", "RETRYING")

/** Dual-layer download bar: sandstone track, forest fill (DESIGN.md "Progress Bars"). */
@Composable
fun DownloadProgressBar(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.secondaryContainer,
    )
}

@Composable
fun DownloadAssetCard(asset: RequiredAssetEntity, download: DownloadEntity?, onAction: () -> Unit) {
    val total = download?.totalBytes?.takeIf { it > 0 } ?: asset.sizeBytes
    val downloaded = download?.bytesDownloaded ?: if (asset.verified) total else 0
    val progress = if (total > 0) (downloaded.toDouble() / total).coerceIn(0.0, 1.0) else 0.0
    val running = download?.status in runningStates

    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when {
                    asset.verified -> Icons.Default.CheckCircle
                    running -> Icons.Default.Downloading
                    else -> Icons.Default.Download
                },
                null,
                tint = if (asset.verified) MaterialTheme.colorScheme.primary else RoadTripBlue,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(asset.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    downloadStatus(asset, download, downloaded, total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Show an action on anything not actively transferring, so a stalled
            // QUEUED/RETRYING/FAILED/PAUSED download can always be kicked by hand.
            val transferring = download?.status in setOf("CONNECTING", "DOWNLOADING")
            if (!asset.verified && !transferring) {
                TextButton(onAction) {
                    Text(
                        when (download?.status) {
                            "RETRYING", "QUEUED" -> "Resume"
                            "FAILED", "PAUSED" -> "Retry"
                            else -> "Get"
                        }
                    )
                }
            }
        }
        if (!asset.verified && (download != null || total > 0)) {
            Spacer(Modifier.height(10.dp))
            DownloadProgressBar(progress.toFloat())
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                Text(downloadEta(download, downloaded, total), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun SetupChecklistItem(text: String, complete: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            if (complete) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            null,
            tint = if (complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Segmented storage usage bar with used / free breakdown. */
@Composable
fun StorageUsageMeter(usedBytes: Long, freeBytes: Long, modifier: Modifier = Modifier) {
    val total = (usedBytes + freeBytes).coerceAtLeast(1L)
    val usedFraction = (usedBytes.toFloat() / total).coerceIn(0f, 1f)
    SurfaceCard(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("On-device storage", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("${formatBytes(usedBytes)} used", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        DownloadProgressBar(usedFraction)
        Spacer(Modifier.height(4.dp))
        Text("${formatBytes(freeBytes)} free", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// --- formatting (ported from the previous monolith) ---

private fun downloadStatus(asset: RequiredAssetEntity, download: DownloadEntity?, downloaded: Long, total: Long): String {
    if (asset.verified) return "Verified and installed"
    return when (download?.status) {
        "FAILED" -> "Failed: ${download.error ?: "download error"}"
        "QUEUED" -> "Queued - waiting for network constraints"
        "CONNECTING" -> "Connecting to download server"
        "RETRYING" -> "Retrying: ${download.error ?: "temporary download error"}"
        "PAUSED" -> "Paused at ${formatBytes(downloaded)} of ${formatBytes(total)}"
        "DOWNLOADING" -> "Downloading ${formatBytes(downloaded)} of ${formatBytes(total)}"
        else -> "${asset.type} - ${formatBytes(total)}"
    }
}

private fun downloadEta(download: DownloadEntity?, downloaded: Long, total: Long): String {
    when (download?.status) {
        "QUEUED" -> return "ETA after start"
        "CONNECTING" -> return "ETA after connection"
        "RETRYING" -> return "Retry scheduled"
        "DOWNLOADING" -> {}
        else -> return ""
    }
    val elapsedSeconds = (System.currentTimeMillis() - download.startedAt).coerceAtLeast(1L) / 1000.0
    if (downloaded <= 0 || total <= 0 || elapsedSeconds < 1.0) return "ETA calculating"
    val bytesPerSecond = downloaded / elapsedSeconds
    if (bytesPerSecond <= 1.0) return "ETA calculating"
    val remainingSeconds = ((total - downloaded).coerceAtLeast(0L) / bytesPerSecond).toLong()
    return "ETA ${formatDuration(remainingSeconds)} - ${formatBytes(bytesPerSecond.toLong())}/s"
}

fun formatBytes(value: Long): String {
    if (value <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var size = value.toDouble()
    var unit = 0
    while (size >= 1024 && unit < units.lastIndex) { size /= 1024; unit++ }
    return String.format(Locale.US, if (unit == 0) "%.0f %s" else "%.1f %s", size, units[unit])
}

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "now"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${secs}s"
        else -> "${secs}s"
    }
}
