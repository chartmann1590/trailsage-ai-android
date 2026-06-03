package com.charles.trailsage.ui.screens.route

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.trailsage.routing.ActiveTourStore
import com.charles.trailsage.routing.GeocodeService
import com.charles.trailsage.routing.RouteTourGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class CustomRouteViewModel @Inject constructor(
    private val generator: RouteTourGenerator,
    private val geocoder: GeocodeService,
    private val activeTourStore: ActiveTourStore,
) : ViewModel() {

    sealed interface State {
        data object Idle : State
        data class Working(val status: String) : State
        data class Done(val name: String, val stopCount: Int, val usedAi: Boolean) : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    val start = MutableStateFlow("")
    val end = MutableStateFlow("")

    private val _startSuggestions = MutableStateFlow<List<GeocodeService.Suggestion>>(emptyList())
    val startSuggestions: StateFlow<List<GeocodeService.Suggestion>> = _startSuggestions.asStateFlow()
    private val _endSuggestions = MutableStateFlow<List<GeocodeService.Suggestion>>(emptyList())
    val endSuggestions: StateFlow<List<GeocodeService.Suggestion>> = _endSuggestions.asStateFlow()

    // Labels just picked from a suggestion — don't re-query (and re-open) the dropdown for them.
    private val picked = HashSet<String>()

    init {
        start.debounce(300).onEach { q ->
            _startSuggestions.value = if (q in picked || q.trim().length < 3) emptyList() else geocoder.autocomplete(q)
        }.launchIn(viewModelScope)
        end.debounce(300).onEach { q ->
            _endSuggestions.value = if (q in picked || q.trim().length < 3) emptyList() else geocoder.autocomplete(q)
        }.launchIn(viewModelScope)
    }

    fun onStartChange(text: String) { start.value = text }
    fun onEndChange(text: String) { end.value = text }

    fun pickStart(s: GeocodeService.Suggestion) { picked.add(s.label); start.value = s.label; _startSuggestions.value = emptyList() }
    fun pickEnd(s: GeocodeService.Suggestion) { picked.add(s.label); end.value = s.label; _endSuggestions.value = emptyList() }

    fun generate() {
        val s = start.value.trim()
        val e = end.value.trim()
        if (s.isBlank() || e.isBlank()) return
        _startSuggestions.value = emptyList()
        _endSuggestions.value = emptyList()
        _state.value = State.Working("Starting…")
        viewModelScope.launch {
            generator.generate(s, e) { status -> _state.value = State.Working(status) }
                .onSuccess { tour ->
                    activeTourStore.setActive(tour.tourId)
                    _state.value = State.Done(tour.name, tour.stopCount, tour.usedAi)
                }
                .onFailure { _state.value = State.Error(it.message ?: "Could not generate the adventure.") }
        }
    }

    fun reset() { _state.value = State.Idle }
}
