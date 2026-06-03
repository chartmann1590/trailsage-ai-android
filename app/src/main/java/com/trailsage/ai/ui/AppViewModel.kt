package com.charles.trailsage.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.trailsage.data.local.*
import com.charles.trailsage.downloads.*
import com.charles.trailsage.domain.SetupState
import com.charles.trailsage.tour.SampleTourInstaller
import com.charles.trailsage.tour.SharedTripImporter
import com.charles.trailsage.routing.ActiveTourStore
import com.charles.trailsage.routing.RouteTourGenerator
import com.charles.trailsage.firebase.FirebaseTelemetry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.app.Activity
import com.charles.trailsage.ads.AdManager

data class SetupUiState(val status: SetupStatusEntity = SetupStatusEntity(), val assets: List<RequiredAssetEntity> = emptyList(), val downloads: List<DownloadEntity> = emptyList(), val compatibility: DeviceCompatibility? = null)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val setupRepository: SetupRepository,
    private val assetRepository: AssetRepository,
    private val sampleTourInstaller: SampleTourInstaller,
    private val compatibilityChecker: DeviceCompatibilityChecker,
    private val routeTourGenerator: RouteTourGenerator,
    private val activeTourStore: ActiveTourStore,
    private val dao: TrailSageDao,
    private val telemetry: FirebaseTelemetry,
    val sharedTripImporter: SharedTripImporter,
    val adManager: AdManager
) : ViewModel() {
    private val compatibility = MutableStateFlow<DeviceCompatibility?>(null)
    val setup: StateFlow<SetupUiState> = combine(setupRepository.setup, assetRepository.assets, assetRepository.downloads, compatibility) { status, assets, downloads, check ->
        SetupUiState(status ?: SetupStatusEntity(), assets, downloads, check)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SetupUiState())
    val destinations = dao.observeDestinations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val pois = dao.observePois("adirondack-high-peaks-loop").stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val stories = dao.observeStories("adirondack-high-peaks-loop").stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sources = dao.observeSources().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings: StateFlow<UserSettingsEntity?> = dao.observeSettings()
        .map { s ->
            if (s == null) return@map null
            val now = System.currentTimeMillis()
            if (!isSameDay(s.lastAdResetTimestamp, now)) {
                val updated = s.copy(
                    adsWatchedToday = 0,
                    creditsSpentToday = 0,
                    lastAdResetTimestamp = now
                )
                viewModelScope.launch { dao.upsertSettings(updated) }
                updated
            } else {
                s
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            runCatching { setupRepository.initialize() }
            runCatching { assetRepository.resumeIncompleteRequired() }
            // Backfill real Wikimedia photos for the active trip's stops that have none.
            runCatching { routeTourGenerator.backfillImages(activeTourStore.tourId.value) }
        }
    }
    fun mark(state: SetupState) = viewModelScope.launch { setupRepository.update(state) }
    fun checkDevice() = viewModelScope.launch {
        setupRepository.update(SetupState.CHECKING_DEVICE)
        compatibility.value = compatibilityChecker.check()
        setupRepository.update(if (compatibility.value?.supported == true) SetupState.DEVICE_SUPPORTED else SetupState.DEVICE_FAILED)
    }
    fun installSample() = viewModelScope.launch {
        setupRepository.update(SetupState.DOWNLOADING_SAMPLE_TOUR)
        sampleTourInstaller.install().onSuccess { setupRepository.update(SetupState.READY_FOR_DOWNLOADS) }
            .onFailure { setupRepository.update(SetupState.SETUP_FAILED, error = it.message) }
    }
    fun download(asset: RequiredAssetEntity) = viewModelScope.launch { runCatching { assetRepository.enqueue(asset) }.onFailure { setupRepository.update(SetupState.SETUP_FAILED, error = it.message) } }
    fun verify() = viewModelScope.launch { setupRepository.verifyAndComplete() }
    fun reset() = viewModelScope.launch { setupRepository.reset() }
    fun updateSettings(transform: (UserSettingsEntity) -> UserSettingsEntity) = viewModelScope.launch {
        val updated = transform(dao.settings() ?: UserSettingsEntity())
        dao.upsertSettings(updated)
        telemetry.applyConsent(updated.telemetryEnabled)
    }

    private fun isSameDay(time1: Long, time2: Long): Boolean {
        val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = time1 }
        val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = time2 }
        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
               cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
    }

    fun watchRewardedAd(activity: Activity, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        if (adManager.isRewardedAdReady()) {
            adManager.showRewarded(activity) {
                viewModelScope.launch {
                    val current = dao.settings() ?: UserSettingsEntity()
                    val now = System.currentTimeMillis()
                    val s = if (!isSameDay(current.lastAdResetTimestamp, now)) {
                        current.copy(adsWatchedToday = 0, creditsSpentToday = 0, lastAdResetTimestamp = now)
                    } else {
                        current
                    }
                    if (s.adsWatchedToday < 6) {
                        val updated = s.copy(
                            adsWatchedToday = s.adsWatchedToday + 1,
                            credits = s.credits + 1
                        )
                        dao.upsertSettings(updated)
                        onSuccess()
                    } else {
                        onFailure("Daily ad reward limit of 6 reached.")
                    }
                }
            }
        } else {
            onFailure("Ad is not ready yet. Please try again in a few seconds.")
            adManager.loadRewarded()
        }
    }

    fun spendCredit(onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            val current = dao.settings() ?: UserSettingsEntity()
            val now = System.currentTimeMillis()
            val s = if (!isSameDay(current.lastAdResetTimestamp, now)) {
                current.copy(adsWatchedToday = 0, creditsSpentToday = 0, lastAdResetTimestamp = now)
            } else {
                current
            }
            if (s.credits <= 0) {
                onFailure("No credits available. Watch ads to earn credits.")
                return@launch
            }
            if (s.creditsSpentToday >= 6) {
                onFailure("Daily credit spend limit of 6 reached.")
                return@launch
            }
            val currentAdFreeUntil = s.adFreeUntil
            val newAdFreeUntil = maxOf(now, currentAdFreeUntil) + (40 * 60 * 1000)
            val updated = s.copy(
                credits = s.credits - 1,
                creditsSpentToday = s.creditsSpentToday + 1,
                adFreeUntil = newAdFreeUntil
            )
            dao.upsertSettings(updated)
            onSuccess()
        }
    }
}
