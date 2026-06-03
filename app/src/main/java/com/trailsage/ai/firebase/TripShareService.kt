package com.charles.trailsage.firebase

import android.content.Context
import com.charles.trailsage.data.local.TrailSageDao
import com.charles.trailsage.routing.Directions
import com.charles.trailsage.routing.DirectionStep
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripShareService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TrailSageDao,
) {
    suspend fun shareTrip(tourId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) {
                error("Firebase is not initialized. Please build the app with a valid google-services.json file.")
            }

            // 1. Sign in anonymously
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                Tasks.await(auth.signInAnonymously())
            }

            // 2. Fetch all components from local Room DB
            val dest = dao.destination(tourId) ?: error("Trip destination not found.")
            val route = dao.route(tourId) ?: error("Trip route not found.")
            val stories = dao.storiesOf(tourId)
            val pois = dao.pois(tourId)
            val triggers = dao.triggers(tourId)
            
            // Get all sources linked to stories
            val allSources = dao.allSources()
            val sources = allSources.filter { source -> 
                stories.any { it.id == source.storyId }
            }

            // Load and downsample GeoJSON route geometry
            val routeFile = File(route.geoJsonPath)
            if (!routeFile.isFile) error("Route GeoJSON file not found.")
            val geoJsonText = routeFile.readText()
            val downsampledGeoJson = downsampleGeoJson(geoJsonText)

            // Load turn-by-turn directions
            val directionsFile = File(routeFile.parentFile, "directions.json")
            val directionsList = Directions.load(directionsFile)

            // 3. Serialize to map
            val docData = mutableMapOf<String, Any>()
            docData["name"] = dest.name
            docData["region"] = dest.region
            docData["country"] = dest.country
            docData["description"] = dest.description
            docData["estimatedDriveMinutes"] = dest.estimatedDriveMinutes
            docData["routeGeoJson"] = downsampledGeoJson

            val directionsData = directionsList.map { step ->
                mapOf(
                    "text" to step.text,
                    "lat" to step.latitude,
                    "lon" to step.longitude
                )
            }
            docData["directions"] = directionsData

            val stopsData = pois.map { poi ->
                val story = stories.firstOrNull { it.title == poi.name }
                val trigger = triggers.firstOrNull { it.poiId == poi.id }
                val stopSources = sources.filter { it.storyId == story?.id }

                mapOf(
                    "poiId" to poi.id,
                    "title" to poi.name,
                    "type" to poi.type,
                    "latitude" to poi.latitude,
                    "longitude" to poi.longitude,
                    "description" to poi.description,
                    "imageLocalPath" to poi.imageLocalPath,
                    "narration" to (story?.narrationText ?: ""),
                    "funFact" to (story?.funFact ?: ""),
                    "generatedByAi" to (story?.generatedByAi ?: false),
                    "sources" to stopSources.map { s ->
                        mapOf(
                            "title" to s.title,
                            "url" to s.url,
                            "license" to s.license,
                            "sourceType" to s.sourceType
                        )
                    }
                )
            }
            docData["stops"] = stopsData
            docData["createdAt"] = System.currentTimeMillis()

            // 4. Save to Firestore
            val firestore = FirebaseFirestore.getInstance()
            val docRef = firestore.collection("shared_trips").document()
            Tasks.await(docRef.set(docData))

            docRef.id
        }.onFailure {
            android.util.Log.e("TripShareService", "Failed to share trip $tourId", it)
        }
    }

    private fun downsampleGeoJson(geoJsonStr: String, maxPoints: Int = 300): String {
        return try {
            val root = JSONObject(geoJsonStr)
            val features = root.getJSONArray("features")
            if (features.length() == 0) return geoJsonStr
            val feature = features.getJSONObject(0)
            val geometry = feature.getJSONObject("geometry")
            val type = geometry.getString("type")
            if (type != "LineString") return geoJsonStr
            val coordinates = geometry.getJSONArray("coordinates")
            val originalSize = coordinates.length()
            if (originalSize <= maxPoints) return geoJsonStr

            val newCoords = JSONArray()
            val step = originalSize.toDouble() / (maxPoints - 1)
            for (i in 0 until maxPoints - 1) {
                val idx = (i * step).toInt().coerceIn(0, originalSize - 1)
                newCoords.put(coordinates.getJSONArray(idx))
            }
            newCoords.put(coordinates.getJSONArray(originalSize - 1))

            geometry.put("coordinates", newCoords)
            root.toString()
        } catch (e: Exception) {
            geoJsonStr
        }
    }
}
