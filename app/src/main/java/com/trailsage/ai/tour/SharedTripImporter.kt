package com.charles.trailsage.tour

import android.content.Context
import com.charles.trailsage.data.local.*
import com.charles.trailsage.routing.ActiveTourStore
import com.charles.trailsage.routing.Directions
import com.charles.trailsage.routing.DirectionStep
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedTripImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TrailSageDao,
    private val activeTourStore: ActiveTourStore,
) {
    private val _pendingImportId = MutableStateFlow<String?>(null)
    val pendingImportId: StateFlow<String?> = _pendingImportId.asStateFlow()

    fun setPendingImport(id: String?) {
        _pendingImportId.value = id
    }

    suspend fun importTrip(shareId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) {
                error("Firebase is not initialized.")
            }

            // 1. Fetch document from Firestore
            val firestore = FirebaseFirestore.getInstance()
            val docRef = firestore.collection("shared_trips").document(shareId)
            val docSnapshot = Tasks.await(docRef.get())
            if (!docSnapshot.exists()) {
                error("Trip not found or has been deleted.")
            }

            val name = docSnapshot.getString("name") ?: "Shared Trip"
            val region = docSnapshot.getString("region") ?: "Shared"
            val country = docSnapshot.getString("country") ?: ""
            val description = docSnapshot.getString("description") ?: ""
            val estimatedDriveMinutes = docSnapshot.getLong("estimatedDriveMinutes")?.toInt() ?: 0
            val routeGeoJson = docSnapshot.getString("routeGeoJson") ?: error("Missing route geometry.")

            @Suppress("UNCHECKED_CAST")
            val directionsData = docSnapshot.get("directions") as? List<Map<String, Any>> ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val stopsData = docSnapshot.get("stops") as? List<Map<String, Any>> ?: emptyList()

            // 2. Setup local directory
            val tourId = "shared-$shareId"
            val dir = File(context.filesDir, "tours/$tourId").apply { mkdirs() }

            // Write route.geojson and directions.json
            File(dir, "route.geojson").writeText(routeGeoJson)

            val directionsList = directionsData.map { step ->
                DirectionStep(
                    text = step["text"] as? String ?: "",
                    latitude = (step["lat"] as? Number)?.toDouble() ?: 0.0,
                    longitude = (step["lon"] as? Number)?.toDouble() ?: 0.0
                )
            }
            Directions.write(File(dir, "directions.json"), directionsList)

            // 3. Upsert Destination, Tour, and Route entities
            dao.upsertDestination(
                DestinationEntity(
                    id = tourId,
                    name = name,
                    region = region,
                    country = country,
                    description = description,
                    estimatedDriveMinutes = estimatedDriveMinutes,
                    offlineSizeBytes = 0,
                    downloaded = true
                )
            )

            dao.upsertTour(
                TourPackEntity(
                    id = tourId,
                    destinationId = tourId,
                    name = name,
                    version = "1.0",
                    localPath = dir.absolutePath,
                    manifestPath = "",
                    downloaded = true,
                    verified = true,
                    sizeBytes = 0
                )
            )

            dao.upsertRoute(
                RouteEntity(
                    id = "$tourId-route",
                    tourPackId = tourId,
                    name = name,
                    geoJsonPath = File(dir, "route.geojson").absolutePath,
                    estimatedDriveMinutes = estimatedDriveMinutes
                )
            )

            // 4. Rebuild Stop entities
            val pois = mutableListOf<PoiEntity>()
            val stories = mutableListOf<StoryEntity>()
            val triggers = mutableListOf<StoryTriggerEntity>()
            val sources = mutableListOf<StorySourceEntity>()

            stopsData.forEachIndexed { i, stop ->
                val poiId = "$tourId-poi-$i"
                val storyId = "$tourId-story-$i"
                val stopName = stop["title"] as? String ?: "Stop $i"
                val stopType = stop["type"] as? String ?: "landmark"
                val lat = (stop["latitude"] as? Number)?.toDouble() ?: 0.0
                val lon = (stop["longitude"] as? Number)?.toDouble() ?: 0.0
                val desc = stop["description"] as? String ?: ""
                val imageLocalPath = stop["imageLocalPath"] as? String ?: ""
                val narration = stop["narration"] as? String ?: ""
                val funFact = stop["funFact"] as? String ?: ""
                val generatedByAi = stop["generatedByAi"] as? Boolean ?: false

                @Suppress("UNCHECKED_CAST")
                val stopSourcesData = stop["sources"] as? List<Map<String, Any>> ?: emptyList()
                val stopSourceIds = mutableListOf<String>()

                stopSourcesData.forEachIndexed { sIdx, srcMap ->
                    val srcId = "$tourId-src-$i-$sIdx"
                    stopSourceIds.add(srcId)
                    sources.add(
                        StorySourceEntity(
                            id = srcId,
                            storyId = storyId,
                            sourceType = srcMap["sourceType"] as? String ?: "wikipedia",
                            title = srcMap["title"] as? String ?: "Wikipedia",
                            url = srcMap["url"] as? String ?: "",
                            license = srcMap["license"] as? String ?: "CC BY-SA"
                        )
                    )
                }

                val sourceIdsJson = org.json.JSONArray(stopSourceIds).toString()

                pois.add(
                    PoiEntity(
                        id = poiId,
                        tourPackId = tourId,
                        name = stopName,
                        type = stopType,
                        latitude = lat,
                        longitude = lon,
                        description = desc,
                        imageLocalPath = imageLocalPath,
                        sourceIdsJson = sourceIdsJson
                    )
                )

                stories.add(
                    StoryEntity(
                        id = storyId,
                        tourPackId = tourId,
                        title = stopName,
                        narrationText = narration,
                        funFact = funFact,
                        imageLocalPath = imageLocalPath,
                        sourceIdsJson = sourceIdsJson,
                        generatedByAi = generatedByAi
                    )
                )

                triggers.add(
                    StoryTriggerEntity(
                        id = "$tourId-trig-$i",
                        tourPackId = tourId,
                        poiId = poiId,
                        latitude = lat,
                        longitude = lon,
                        radiusMeters = 450.0,
                        priority = stopsData.size - i,
                        storyId = storyId
                    )
                )
            }

            // Always add openstreetmap attribution
            sources.add(
                StorySourceEntity(
                    id = "$tourId-osm",
                    storyId = "",
                    sourceType = "openstreetmap",
                    title = "OpenStreetMap contributors",
                    url = "https://www.openstreetmap.org/copyright",
                    license = "ODbL"
                )
            )

            dao.upsertPois(pois)
            dao.upsertStories(stories)
            dao.upsertTriggers(triggers)
            dao.upsertSources(sources)

            // Set active
            activeTourStore.setActive(tourId)

            tourId
        }
    }
}
