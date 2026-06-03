package com.charles.trailsage.ui.screens.downloads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charles.trailsage.ui.AppViewModel
import com.charles.trailsage.ui.components.*

@Composable
fun DownloadsScreen(vm: AppViewModel) {
    val state by vm.setup.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val files = remember { context.filesDir }
    val used = remember(state.assets) { files.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
    val free = remember { files.usableSpace }
    val byAsset = state.downloads.associateBy { it.assetId }

    TrailScreen {
        ScreenTitle("Downloads")
        StorageUsageMeter(usedBytes = used, freeBytes = free)
        SectionHeader("Required assets")
        state.assets.forEach { asset ->
            DownloadAssetCard(asset, byAsset[asset.id]) { vm.download(asset) }
        }
    }
}
