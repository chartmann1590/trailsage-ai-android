package com.charles.trailsage.ui.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.trailsage.data.local.TrailSageDao
import com.charles.trailsage.firebase.FirebaseTelemetry
import com.charles.trailsage.gps.ActiveTourTriggerSelector
import com.charles.trailsage.gps.GpsStatus
import com.charles.trailsage.gps.LocationRepository
import com.charles.trailsage.gps.StoryTriggerEngine
import com.charles.trailsage.map.MapStop
import com.charles.trailsage.routing.ActiveTourStore
import com.charles.trailsage.routing.DirectionStep
import com.charles.trailsage.routing.Directions
import com.charles.trailsage.tts.NarrationPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Drives the live journey on the map: shows the active tour's route + stops + current
 * location, tracks real GPS speed and the distance to the next stop, and auto-plays each
 * stop's narration when the vehicle enters its trigger radius (returning to the map view
 * when playback ends).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    private val dao: TrailSageDao,
    private val location: LocationRepository,
    private val player: NarrationPlayer,
    private val telemetry: FirebaseTelemetry,
    activeTourStore: ActiveTourStore,
) : ViewModel() {

    data class MapData(val routeGeoJson: String?, val stops: List<MapStop>, val tourName: String)
    data class NextStop(val name: String, val distanceMeters: Int)

    private val tourId = activeTourStore.tourId
        .stateIn(viewModelScope, SharingStarted.Eagerly, ActiveTourStore.DEFAULT_TOUR_ID)

    val data = tourId.map { id ->
        val pois = dao.pois(id)
        val storyIdByTitle = dao.storiesOf(id).associateBy({ it.title }, { it.id })
        val routePath = dao.route(id)?.geoJsonPath
        val geoJson = routePath?.let { runCatching { File(it).readText() }.getOrNull() }
        MapData(
            geoJson,
            pois.map { MapStop(it.name, it.latitude, it.longitude, storyIdByTitle[it.name] ?: "") },
            dao.destination(id)?.name ?: "Offline map",
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapData(null, emptyList(), "Offline map"))

    val playing: StateFlow<Boolean> = player.playing
    val usingFallback: StateFlow<Boolean> = player.usingFallback

    private val _nowPlaying = MutableStateFlow<String?>(null)
    val nowPlaying: StateFlow<String?> = _nowPlaying.asStateFlow()
    private val _speedMph = MutableStateFlow(0)
    val speedMph: StateFlow<Int> = _speedMph.asStateFlow()
    private val _nextStop = MutableStateFlow<NextStop?>(null)
    val nextStop: StateFlow<NextStop?> = _nextStop.asStateFlow()
    private val _gps = MutableStateFlow<GpsStatus?>(null)
    val gps: StateFlow<GpsStatus?> = _gps.asStateFlow()
    private val _currentDirection = MutableStateFlow<String?>(null)
    val currentDirection: StateFlow<String?> = _currentDirection.asStateFlow()

    private val selector = ActiveTourTriggerSelector()
    private val triggerEngine = StoryTriggerEngine()
    @Volatile private var steps: List<DirectionStep> = emptyList()
    private var announcedStep: String? = null

    init {
        // Keep the active tour's turn-by-turn steps loaded for the live guidance overlay.
        viewModelScope.launch {
            tourId.collect { id ->
                steps = Directions.load(dao.route(id)?.geoJsonPath?.let { File(File(it).parentFile, "directions.json") })
                announcedStep = null
            }
        }
        viewModelScope.launch { player.playing.collect { if (!it) _nowPlaying.value = null } }
        viewModelScope.launch {
            location.locations().collect { status ->
                _gps.value = status
                val loc = status.location ?: return@collect
                _speedMph.value = (loc.speed * 2.2369363).roundToInt().coerceAtLeast(0)

                val triggers = dao.triggers(tourId.value)
                triggers.minByOrNull { triggerEngine.distanceMeters(it.latitude, it.longitude, loc.latitude, loc.longitude) }
                    ?.let { nearest ->
                        val meters = triggerEngine.distanceMeters(nearest.latitude, nearest.longitude, loc.latitude, loc.longitude)
                        _nextStop.value = NextStop(dao.story(nearest.storyId)?.title ?: "Next stop", meters.roundToInt())
                    }

                // Live turn-by-turn: surface the nearest upcoming maneuver and speak it once
                // (in the selected voice) as we approach, without interrupting a story.
                updateDirection(loc.latitude, loc.longitude)

                selector.select(triggers, loc)?.let { match ->
                    dao.story(match.storyId)?.let { story ->
                        telemetry.event("story_triggered")
                        _nowPlaying.value = story.title
                        player.play(story.narrationText)
                    }
                }
            }
        }
    }

    private fun updateDirection(lat: Double, lon: Double) {
        val current = steps.minByOrNull { triggerEngine.distanceMeters(it.latitude, it.longitude, lat, lon) } ?: return
        val meters = triggerEngine.distanceMeters(current.latitude, current.longitude, lat, lon)
        _currentDirection.value = if (meters >= 80) "${formatDistance(meters)} — ${current.text}" else current.text
        if (meters < 250 && announcedStep != current.text && !player.playing.value) {
            announcedStep = current.text
            player.play(current.text)
        }
    }

    private fun formatDistance(meters: Double): String {
        val miles = meters / 1609.34
        return if (miles >= 0.2) String.format(java.util.Locale.US, "%.1f mi", miles) else "${meters.roundToInt()} m"
    }

    fun stopPlayback() = player.stop()
    override fun onCleared() { player.stop() }
}
