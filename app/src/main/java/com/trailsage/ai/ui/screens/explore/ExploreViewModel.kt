package com.charles.trailsage.ui.screens.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.trailsage.ai.AiGuideService
import com.charles.trailsage.data.local.TrailSageDao
import com.charles.trailsage.routing.ActiveTourStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val dao: TrailSageDao,
    private val aiService: AiGuideService,
    private val activeTourStore: ActiveTourStore,
) : ViewModel() {

    data class StopUi(val storyId: String, val title: String, val preview: String, val byAi: Boolean, val imageUrl: String?)
    data class SavedTour(val id: String, val name: String, val description: String, val isGenerated: Boolean, val isActive: Boolean)
    data class ExploreUi(
        val tourName: String = "TrailSage tour",
        val tourDescription: String = "",
        val driveMinutes: Int = 0,
        val stops: List<StopUi> = emptyList(),
        val heroImage: String? = null,
        val aiReady: Boolean = false,
        val voiceReady: Boolean = false,
        val isGenerated: Boolean = false,
    )

    /** Every saved adventure (AI-generated trips + the sample), newest generated first. */
    val savedTours = kotlinx.coroutines.flow.combine(
        dao.observeDestinations(),
        activeTourStore.tourId,
    ) { destinations, activeId ->
        destinations
            .map { SavedTour(it.id, it.name, it.description, it.id.startsWith("route-"), it.id == activeId) }
            .sortedWith(compareByDescending<SavedTour> { it.isGenerated }.thenByDescending { it.id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectTour(id: String) = activeTourStore.setActive(id)

    val ui = activeTourStore.tourId
        .flatMapLatest { id ->
            dao.observeStories(id).map { stories ->
                val dest = dao.destination(id)
                val assets = dao.assets()
                ExploreUi(
                    tourName = dest?.name ?: "Adirondack High Peaks Loop",
                    tourDescription = dest?.description
                        ?: "A scenic offline sample tour through the Adirondack High Peaks.",
                    driveMinutes = dest?.estimatedDriveMinutes ?: 0,
                    stops = stories.map {
                        StopUi(it.id, it.title, it.narrationText.take(200).trim(), it.generatedByAi, it.imageLocalPath.ifBlank { null })
                    },
                    heroImage = stories.firstOrNull { it.imageLocalPath.isNotBlank() }?.imageLocalPath,
                    aiReady = aiService.modelInstalled(),
                    voiceReady = assets.any { it.type == "VOICE_PACK" && it.verified },
                    isGenerated = id.startsWith("route-"),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExploreUi())
}
