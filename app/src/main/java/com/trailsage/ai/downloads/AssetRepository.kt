package com.charles.trailsage.downloads

import android.app.ActivityManager
import android.content.Context
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import androidx.work.*
import com.charles.trailsage.data.local.*
import com.charles.trailsage.domain.AssetType
import com.charles.trailsage.domain.SetupState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceCompatibility(val supported: Boolean, val limited: Boolean, val messages: List<String>)

@Singleton
class DeviceCompatibilityChecker @Inject constructor(@ApplicationContext private val context: Context) {
    fun check(): DeviceCompatibility {
        val storageMb = context.filesDir.usableSpace / 1024 / 1024
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork
        val online = network != null && connectivity.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val location = context.getSystemService(LocationManager::class.java).isLocationEnabled
        val ramMb = context.getSystemService(ActivityManager::class.java).memoryClass
        val messages = buildList {
            add("Android ${Build.VERSION.SDK_INT} on ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown ABI"}")
            add("$storageMb MB private storage available")
            add(if (online) "Network connection available" else "No active internet connection")
            add(if (location) "Location services enabled" else "Location services are disabled")
            add("$ramMb MB app memory class; LiteRT model support verified after runtime install")
            add("Sherpa neural voice support verified after native runtime install")
        }
        val failed = Build.VERSION.SDK_INT < 26 || storageMb < 512
        return DeviceCompatibility(!failed, !online || !location || storageMb < 4096, messages)
    }
}

@Singleton
class AssetRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TrailSageDao,
    private val workManager: WorkManager
) {
    val assets: Flow<List<RequiredAssetEntity>> = dao.observeAssets()
    val downloads: Flow<List<DownloadEntity>> = dao.observeDownloads()

    suspend fun seedManifests() {
        val values = listOf("manifests/core_asset_manifest.json", "manifests/voice_manifest.json", "manifests/sample_tour_manifest.json")
            .flatMap { path -> context.assets.open(path).bufferedReader().use { AssetManifestParser.parse(it.readText()) } }
            .map { asset ->
                val packagedRuntime = asset.id == "litert-lm-runtime" || asset.id == "sherpa-onnx-runtime"
                RequiredAssetEntity(asset.id, asset.name, asset.type.name, asset.version, asset.downloadUrl, asset.localPath,
                    asset.sizeBytes, asset.checksumSha256, asset.required, installed = packagedRuntime, verified = packagedRuntime, license = asset.license, attribution = asset.attribution,
                    minAndroidSdk = asset.minAndroidSdk, recommendedRamMb = asset.recommendedRamMb, engine = asset.engine)
            }
        dao.upsertAssets(values)
        if (dao.settings() == null) dao.upsertSettings(UserSettingsEntity())
        if (dao.setup() == null) dao.upsertSetup(SetupStatusEntity())
    }

    suspend fun enqueue(asset: RequiredAssetEntity) {
        if (asset.downloadUrl.startsWith("asset://")) {
            installBundledAsset(asset)
            return
        }
        require(asset.checksumSha256.isNotBlank()) { "${asset.name} requires a SHA-256 checksum before download" }
        val settings = dao.settings() ?: UserSettingsEntity()
        val constraints = Constraints.Builder().setRequiredNetworkType(if (settings.wifiOnlyDownloads) NetworkType.UNMETERED else NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf("assetId" to asset.id, "url" to asset.downloadUrl, "localPath" to asset.localPath, "sha256" to asset.checksumSha256))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        dao.upsertDownload(DownloadEntity(asset.id, asset.id, "QUEUED", totalBytes = asset.sizeBytes, workId = request.id.toString()))
        workManager.enqueueUniqueWork("asset-${asset.id}", ExistingWorkPolicy.KEEP, request)
    }

    suspend fun installSampleTour() {
        copyAssetTree("tours/adirondack-high-peaks-loop", File(context.filesDir, "tours/adirondack-high-peaks-loop"))
        listOf("adirondack-high-peaks-loop", "adirondack-map", "adirondack-rag").forEach { dao.markAsset(it, installed = true, verified = true) }
    }

    suspend fun pause(assetId: String) { workManager.cancelUniqueWork("asset-$assetId"); dao.upsertDownload(DownloadEntity(assetId, assetId, "PAUSED")) }
    suspend fun retry(asset: RequiredAssetEntity) { pause(asset.id); enqueue(asset) }

    private suspend fun installBundledAsset(asset: RequiredAssetEntity) {
        if (asset.id == "adirondack-high-peaks-loop" || asset.id == "adirondack-map" || asset.id == "adirondack-rag") installSampleTour()
    }

    private fun copyAssetTree(assetPath: String, target: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input -> target.outputStream().use(input::copyTo) }
        } else children.forEach { copyAssetTree("$assetPath/$it", File(target, it)) }
    }
}

@Singleton
class SetupRepository @Inject constructor(private val dao: TrailSageDao, private val assets: AssetRepository) {
    val setup = dao.observeSetup()
    val requiredAssets = dao.observeAssets()

    suspend fun initialize() = assets.seedManifests()
    suspend fun update(state: SetupState, step: String = state.name, error: String? = null) =
        dao.upsertSetup(SetupStatusEntity(state = state.name, setupComplete = state == SetupState.SETUP_COMPLETE, currentStep = step, lastError = error))
    suspend fun installSampleTour() = assets.installSampleTour()
    suspend fun verifyAndComplete(): Boolean {
        update(SetupState.VERIFYING_ASSETS)
        val all = dao.assets()
        val ready = all.filter { it.required }.all { it.installed && it.verified && !it.demoPlaceholder }
        return if (ready) { update(SetupState.SETUP_COMPLETE); true } else {
            update(SetupState.SETUP_FAILED, error = "Required verified production assets are still missing."); false
        }
    }
    suspend fun reset() { dao.upsertSetup(SetupStatusEntity()) }
}
