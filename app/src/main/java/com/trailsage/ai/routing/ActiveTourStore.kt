package com.charles.trailsage.routing

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the currently selected tour (sample or AI-generated) for map + driving playback.
 * Persisted so a generated trip stays selected across app restarts — the map must never
 * silently fall back to the sample tour once the user has built their own.
 */
@Singleton
class ActiveTourStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("active_tour", Context.MODE_PRIVATE)
    private val _tourId = MutableStateFlow(prefs.getString(KEY, DEFAULT_TOUR_ID) ?: DEFAULT_TOUR_ID)
    val tourId: StateFlow<String> = _tourId.asStateFlow()

    fun setActive(id: String) {
        _tourId.value = id
        prefs.edit().putString(KEY, id).apply()
    }

    companion object {
        const val DEFAULT_TOUR_ID = "adirondack-high-peaks-loop"
        private const val KEY = "active_tour_id"
    }
}
