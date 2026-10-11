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

/** Which downloads honour the "Wi-Fi-only downloads" setting. Pure so it can be unit tested. */
object DownloadNetworkPolicy {
    /** Assets at or above this size (the AI model) wait for Wi-Fi when Wi-Fi-only is on. */
    const val LARGE_DOWNLOAD_BYTES = 200L * 1024 * 1024

    fun requiresUnmetered(wifiOnlySetting: Boolean, sizeBytes: Long, userOverride: Boolean): Boolean =
        wifiOnlySetting && !userOverride && sizeBytes >= LARGE_DOWNLOAD_BYTES
}

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
        // Preserve already-installed/verified state across launches — re-seeding must NOT
        // reset verified flags, or completed downloads would be re-queued and re-downloaded.
        val existing = dao.assets().associateBy { it.id }
        val values = listOf("manifests/core_asset_manifest.json", "manifests/voice_manifest.json", "manifests/sample_tour_manifest.json")
            .flatMap { path -> context.assets.open(path).bufferedReader().use { AssetManifestParser.parse(it.readText()) } }
            .map { asset ->
                val packagedRuntime = asset.id == "litert-lm-runtime" || asset.id == "sherpa-onnx-runtime"
                val prior = existing[asset.id]
                RequiredAssetEntity(asset.id, asset.name, asset.type.name, asset.version, asset.downloadUrl, asset.localPath,
                    asset.sizeBytes, asset.checksumSha256, asset.required,
                    installed = prior?.installed ?: packagedRuntime,
                    verified = prior?.verified ?: packagedRuntime,
                    demoPlaceholder = prior?.demoPlaceholder ?: false,
                    license = asset.license, attribution = asset.attribution,
                    minAndroidSdk = asset.minAndroidSdk, recommendedRamMb = asset.recommendedRamMb, engine = asset.engine)
            }
        dao.upsertAssets(values)
        if (dao.settings() == null) dao.upsertSettings(UserSettingsEntity())
        if (dao.setup() == null) dao.upsertSetup(SetupStatusEntity())
    }

    /**
     * Queues a download.
     *
     * Wi-Fi-only downloads (on by default) used to put EVERY asset behind an UNMETERED
     * network constraint. On mobile data or a metered Wi-Fi (hotspots, many Samsung/OEM
     * defaults) WorkManager then never started anything: the AI model and the voice both sat at
     * 0% "Queued" forever, and tapping Resume just re-queued them behind the same constraint.
     *
     * Now the Wi-Fi-only preference only applies to large assets (the multi-GB AI model). Small
     * assets such as the ~22 MB voice always download on any connection. When a large asset is
     * held for Wi-Fi it is marked [WAITING_FOR_WIFI] so the UI can say so, and a tap on its
     * "Download now" button ([userInitiated]) downloads it over the current connection.
     */
    suspend fun enqueue(asset: RequiredAssetEntity, userInitiated: Boolean = false) {
        if (asset.downloadUrl.startsWith("asset://")) {
            installBundledAsset(asset)
            return
        }
        require(asset.checksumSha256.isNotBlank()) { "${asset.name} requires a SHA-256 checksum before download" }
        val settings = dao.settings() ?: UserSettingsEntity()
        val previous = dao.download(asset.id)
        val userOverride = userInitiated && previous?.status == "QUEUED" && previous.error == WAITING_FOR_WIFI
        val holdForWifi = DownloadNetworkPolicy.requiresUnmetered(settings.wifiOnlyDownloads, asset.sizeBytes, userOverride)
        val waitingForWifi = holdForWifi && !isOnUnmeteredNetwork()
        val constraints = Constraints.Builder().setRequiredNetworkType(if (holdForWifi) NetworkType.UNMETERED else NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf("assetId" to asset.id, "url" to asset.downloadUrl, "localPath" to asset.localPath, "sha256" to asset.checksumSha256))
            .setBackoffCriteria(BackoffPolicy.LINEAR, 20, TimeUnit.SECONDS).build()
        val now = System.currentTimeMillis()
        val existing = dao.download(asset.id)
        // Only keep work that is actively running; re-tapping a QUEUED/RETRYING/PAUSED/FAILED
        // item must REPLACE so it re-enqueues with current constraints (and a live worker).
        val policy = if (existing?.status in setOf("CONNECTING", "DOWNLOADING")) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE
        val startedAt = existing?.startedAt?.takeIf { it > 0 && policy == ExistingWorkPolicy.KEEP } ?: now
        if (policy == ExistingWorkPolicy.REPLACE) {
            dao.upsertDownload(DownloadEntity(asset.id, asset.id, "QUEUED", bytesDownloaded = existing?.bytesDownloaded ?: 0,
                totalBytes = asset.sizeBytes, workId = request.id.toString(),
                error = if (waitingForWifi) WAITING_FOR_WIFI else null, startedAt = startedAt, updatedAt = now))
        }
        workManager.enqueueUniqueWork("asset-${asset.id}", policy, request)
    }

    private fun isOnUnmeteredNetwork(): Boolean = runCatching {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
                (Build.VERSION.SDK_INT >= 30 && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED)))
    }.getOrDefault(false)

    companion object {
        /** Marker stored in [DownloadEntity.error] while a QUEUED download waits for Wi-Fi. */
        const val WAITING_FOR_WIFI = "WAITING_FOR_WIFI"
    }

    /**
     * Re-enqueues required web downloads that are not actively running (QUEUED/RETRYING/
     * PAUSED/FAILED or never started). Called on launch so a stalled download — e.g. one
     * whose WorkManager backoff grew after transient DNS failures — resumes promptly from
     * its .partial file with a fresh attempt instead of waiting out a long backoff.
     */
    suspend fun resumeIncompleteRequired() {
        dao.assets()
            .filter { it.required && !it.verified && it.downloadUrl.startsWith("http") }
            .forEach { asset ->
                val status = dao.download(asset.id)?.status
                if (status !in setOf("CONNECTING", "DOWNLOADING")) {
                    runCatching { enqueue(asset) }
                }
            }
    }

    suspend fun installSampleTour() {
        copyAssetTree("tours/adirondack-high-peaks-loop", File(context.filesDir, "tours/adirondack-high-peaks-loop"))
        listOf("adirondack-high-peaks-loop", "adirondack-map", "adirondack-rag").forEach { dao.markAsset(it, installed = true, verified = true) }
    }

    suspend fun pause(assetId: String) {
        workManager.cancelUniqueWork("asset-$assetId")
        val existing = dao.download(assetId)
        dao.upsertDownload(DownloadEntity(assetId, assetId, "PAUSED", existing?.bytesDownloaded ?: 0, existing?.totalBytes ?: 0, existing?.workId ?: "", startedAt = existing?.startedAt ?: System.currentTimeMillis()))
    }
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
