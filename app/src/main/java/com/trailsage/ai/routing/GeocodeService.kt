package com.charles.trailsage.routing

import com.charles.trailsage.net.Http
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Keyless address autocomplete via Photon (komoot) — designed for typeahead, no token. */
@Singleton
class GeocodeService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class Suggestion(val label: String, val latitude: Double, val longitude: Double)

    suspend fun autocomplete(query: String): List<Suggestion> = withContext(Dispatchers.IO) {
        if (query.trim().length < 3) return@withContext emptyList()
        runCatching {
            val json = Http.get("https://photon.komoot.io/api/?limit=5&q=${Http.encode(query)}")
            val features = JSONObject(json).optJSONArray("features") ?: return@runCatching emptyList()
            (0 until features.length()).mapNotNull { i ->
                val feature = features.getJSONObject(i)
                val coords = feature.getJSONObject("geometry").getJSONArray("coordinates")
                val props = feature.getJSONObject("properties")
                val label = listOfNotNull(
                    props.optString("name").ifBlank { null },
                    props.optString("city").ifBlank { null } ?: props.optString("county").ifBlank { null },
                    props.optString("state").ifBlank { null },
                    props.optString("country").ifBlank { null },
                ).distinct().joinToString(", ")
                if (label.isBlank()) null
                else Suggestion(label, coords.getDouble(1), coords.getDouble(0))
            }
        }.getOrDefault(emptyList())
    }
}
