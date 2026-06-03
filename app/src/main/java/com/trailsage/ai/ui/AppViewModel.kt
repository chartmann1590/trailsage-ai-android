package com.charles.trailsage.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.trailsage.data.local.*
import com.charles.trailsage.downloads.*
import com.charles.trailsage.domain.SetupState
import com.charles.trailsage.tour.SampleTourInstaller
import com.charles.trailsage.routing.ActiveTourStore
import com.charles.trailsage.routing.RouteTourGenerator
import com.charles.trailsage.firebase.FirebaseTelemetry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    private val telemetry: FirebaseTelemetry
) : ViewModel() {
    private val compatibility = MutableStateFlow<DeviceCompatibility?>(null)
    val setup: StateFlow<SetupUiState> = combine(setupRepository.setup, assetRepository.assets, assetRepository.downloads, compatibility) { status, assets, downloads, check ->
        SetupUiState(status ?: SetupStatusEntity(), assets, downloads, check)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SetupUiState())
    val destinations = dao.observeDestinations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val pois = dao.observePois("adirondack-high-peaks-loop").stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val stories = dao.observeStories("adirondack-high-peaks-loop").stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sources = dao.observeSources().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = dao.observeSettings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
}
