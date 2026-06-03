package com.charles.trailsage.ui.screens.itinerary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.charles.trailsage.data.local.TrailSageDao
import com.charles.trailsage.routing.ActiveTourStore
import com.charles.trailsage.routing.Directions
import com.charles.trailsage.routing.RouteTourGenerator
import com.charles.trailsage.firebase.TripShareService
import com.charles.trailsage.ui.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ItineraryViewModel @Inject constructor(
    private val dao: TrailSageDao,
    private val generator: RouteTourGenerator,
    private val activeTourStore: ActiveTourStore,
    private val tripShareService: TripShareService,
) : ViewModel() {
    sealed interface ShareState {
        object Idle : ShareState
        object Loading : ShareState
        data class Success(val shareUrl: String) : ShareState
        data class Error(val message: String) : ShareState
    }

    private val _shareState = MutableStateFlow<ShareState>(ShareState.Idle)
    val shareState: StateFlow<ShareState> = _shareState.asStateFlow()

    fun shareCurrentTrip() {
        if (_shareState.value is ShareState.Loading) return
        _shareState.value = ShareState.Loading
        viewModelScope.launch {
            val activeId = activeTourStore.tourId.value
            val result = tripShareService.shareTrip(activeId)
            _shareState.value = result.fold(
                onSuccess = { shareId ->
                    ShareState.Success("https://chartmann1590.github.io/trailsage-ai-android/t/?t=$shareId")
                },
                onFailure = {
                    ShareState.Error(it.message ?: "Failed to share trip")
                }
            )
        }
    }

    fun resetShareState() {
        _shareState.value = ShareState.Idle
    }

    data class Poi(val storyId: String, val title: String, val preview: String, val byAi: Boolean, val imageUrl: String?)
    data class Itinerary(
        val name: String = "",
        val driveMinutes: Int = 0,
        val directions: List<String> = emptyList(),
        val pois: List<Poi> = emptyList(),
    )

    private val refresh = MutableStateFlow(0)
    private val _regenerating = MutableStateFlow(false)
    val regenerating: StateFlow<Boolean> = _regenerating.asStateFlow()

    val itinerary = combine(activeTourStore.tourId, refresh) { id, _ -> id }
        .flatMapLatest { id ->
            dao.observeStories(id).map { stories ->
                val dest = dao.destination(id)
                val routeDir = dao.route(id)?.geoJsonPath?.let { File(it).parentFile }
                val directions = Directions.load(routeDir?.let { File(it, "directions.json") }).map { it.text }
                Itinerary(
                    name = dest?.name ?: "Trip",
                    driveMinutes = dest?.estimatedDriveMinutes ?: 0,
                    directions = directions,
                    pois = stories.map { Poi(it.id, it.title, it.narrationText.take(200).trim(), it.generatedByAi, it.imageLocalPath.ifBlank { null }) },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Itinerary())

    fun regenerateDirections() {
        if (_regenerating.value) return
        viewModelScope.launch {
            _regenerating.value = true
            generator.regenerateDirections(activeTourStore.tourId.value)
            _regenerating.value = false
            refresh.value += 1
        }
    }
}

@Composable
fun ItineraryScreen(onBack: () -> Unit, onOpenMap: () -> Unit, onOpenStory: (String) -> Unit, vm: ItineraryViewModel = hiltViewModel()) {
    val trip by vm.itinerary.collectAsStateWithLifecycle()
    val regenerating by vm.regenerating.collectAsStateWithLifecycle()
    val shareState by vm.shareState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(shareState) {
        when (val state = shareState) {
            is ItineraryViewModel.ShareState.Success -> {
                val sendIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TEXT, "Check out my TrailSage AI road trip: ${state.shareUrl}")
                    type = "text/plain"
                }
                val shareIntent = android.content.Intent.createChooser(sendIntent, "Share Trip")
                context.startActivity(shareIntent)
                vm.resetShareState()
            }
            is ItineraryViewModel.ShareState.Error -> {
                android.widget.Toast.makeText(context, state.message, android.widget.Toast.LENGTH_LONG).show()
                vm.resetShareState()
            }
            else -> {}
        }
    }

    DetailScaffold(trip.name.ifBlank { "Trip details" }, onBack) {
        InfoCard("Overview", buildString {
            append("${trip.pois.size} stops")
            if (trip.driveMinutes > 0) append(" • ~${trip.driveMinutes} min driving")
            append(". Narration plays automatically as you reach each stop, and turn directions are spoken in your selected voice.")
        })
        PrimaryButton("Open live map", onClick = onOpenMap)

        val isSharing = shareState is ItineraryViewModel.ShareState.Loading
        OutlinedButton(
            onClick = { if (!isSharing) vm.shareCurrentTrip() },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            contentPadding = PaddingValues(16.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            if (isSharing) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(if (isSharing) "Generating link…" else "Share this trip", fontWeight = FontWeight.SemiBold)
        }

        SectionHeader("All stops (${trip.pois.size})")
        if (trip.pois.isEmpty()) {
            EmptyStateCard("No stops", "Build an adventure to generate stops along your route.")
        } else {
            trip.pois.forEachIndexed { index, poi ->
                SurfaceCard(onClick = { onOpenStory(poi.storyId) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(52.dp).clip(MaterialTheme.shapes.small)) {
                            TrailImage(poi.imageUrl, poi.title, Modifier.matchParentSize(), Icons.Default.Place)
                            Box(Modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                                Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                        Text(poi.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (poi.byAi) Icon(Icons.Default.AutoAwesome, "AI", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                        Icon(Icons.Default.PlayArrow, "Play", tint = MaterialTheme.colorScheme.primary)
                    }
                    if (poi.preview.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(poi.preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                    }
                }
            }
        }

        SectionHeader("Driving directions")
        when {
            regenerating -> SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text("Fetching turn-by-turn directions…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            trip.directions.isEmpty() -> {
                EmptyStateCard("No turn-by-turn yet", "Fetch driving directions for this trip — they'll also guide and speak as you drive on the map.")
                PrimaryButton("Get turn-by-turn directions", onClick = vm::regenerateDirections)
            }
            else -> {
                SurfaceCard {
                    trip.directions.forEachIndexed { index, step ->
                        Row(Modifier.padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("${index + 1}.", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(step, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (index < trip.directions.lastIndex) HorizontalDivider(Modifier.padding(vertical = 2.dp))
                    }
                }
                SecondaryButton("Refresh directions", onClick = vm::regenerateDirections)
            }
        }
    }
}
