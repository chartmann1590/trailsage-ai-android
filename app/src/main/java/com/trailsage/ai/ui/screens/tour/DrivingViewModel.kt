package com.charles.trailsage.ui.screens.tour

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.trailsage.data.local.StoryEntity
import com.charles.trailsage.data.local.TrailSageDao
import com.charles.trailsage.firebase.FirebaseTelemetry
import com.charles.trailsage.gps.ActiveTourTriggerSelector
import com.charles.trailsage.gps.GpsStatus
import com.charles.trailsage.gps.LocationRepository
import com.charles.trailsage.routing.ActiveTourStore
import com.charles.trailsage.tts.NarrationPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DrivingViewModel @Inject constructor(
    private val player: NarrationPlayer,
    private val dao: TrailSageDao,
    private val location: LocationRepository,
    private val telemetry: FirebaseTelemetry,
    private val activeTourStore: ActiveTourStore,
) : ViewModel() {

    val playing: StateFlow<Boolean> = player.playing
    val usingFallback: StateFlow<Boolean> = player.usingFallback

    private val tourId: StateFlow<String> = activeTourStore.tourId
        .stateIn(viewModelScope, SharingStarted.Eagerly, ActiveTourStore.DEFAULT_TOUR_ID)

    private val stories: StateFlow<List<StoryEntity>> = tourId
        .flatMapLatest { dao.observeStories(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val index = MutableStateFlow(0)
    val currentStory: StateFlow<StoryEntity?> = combine(stories, index) { list, i ->
        list.getOrNull(i.coerceIn(0, (list.size - 1).coerceAtLeast(0)))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _gps = MutableStateFlow<GpsStatus?>(null)
    val gps: StateFlow<GpsStatus?> = _gps.asStateFlow()

    private val _noVoiceAvailable = MutableStateFlow(false)
    val noVoiceAvailable: StateFlow<Boolean> = _noVoiceAvailable.asStateFlow()

    private val selector = ActiveTourTriggerSelector()

    init {
        viewModelScope.launch { _noVoiceAvailable.value = player.resolveSource() == NarrationPlayer.Source.NONE }
        viewModelScope.launch {
            location.locations().collect { status ->
                _gps.value = status
                val loc = status.location ?: return@collect
                val match = selector.select(dao.triggers(tourId.value), loc) ?: return@collect
                val story = dao.story(match.storyId) ?: return@collect
                index.value = stories.value.indexOfFirst { it.id == story.id }.coerceAtLeast(0)
                telemetry.event("story_triggered")
                player.play(story.narrationText)
            }
        }
    }

    fun toggle() { currentStory.value?.let { player.toggle(it.narrationText) } }

    fun next() {
        player.stop()
        index.value = (index.value + 1).coerceAtMost((stories.value.size - 1).coerceAtLeast(0))
    }

    fun previous() {
        player.stop()
        index.value = (index.value - 1).coerceAtLeast(0)
    }

    override fun onCleared() { player.stop() }
}
