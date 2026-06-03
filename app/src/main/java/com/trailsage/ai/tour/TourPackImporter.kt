package com.charles.trailsage.tour

import com.charles.trailsage.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TourPackImporter @Inject constructor(private val dao: TrailSageDao) {
    suspend fun import(directory: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(TourPackValidator.isValid(directory)) { "Invalid tour pack: missing ${TourPackValidator.missingFiles(directory)}" }
            val manifest = json(directory, "manifest.json")
            val tourId = manifest.getString("id")
            val name = manifest.getString("name")
            val pois = jsonArray(directory, "pois.geojson", "features").map { feature ->
                val properties = feature.getJSONObject("properties"); val coordinates = feature.getJSONObject("geometry").getJSONArray("coordinates")
                PoiEntity(properties.getString("id"), tourId, properties.getString("name"), properties.optString("type", "landmark"), coordinates.getDouble(1), coordinates.getDouble(0), properties.optString("description"))
            }
            val stories = jsonArray(directory, "stories.json").map { item ->
                StoryEntity(item.getString("id"), tourId, item.getString("title"), item.getString("narration"), item.optString("funFact"), imageLocalPath = item.optString("image"), sourceIdsJson = item.optJSONArray("sourceIds")?.toString() ?: "[]")
            }
            val triggers = jsonArray(directory, "triggers.json").map { item ->
                StoryTriggerEntity(item.getString("id"), tourId, item.getString("id"), item.getDouble("latitude"), item.getDouble("longitude"), item.getDouble("radiusMeters"), item.optDoubleOrNull("bearingStart"), item.optDoubleOrNull("bearingEnd"), storyId = item.getString("id"))
            }
            val sources = jsonArray(directory, "sources.json").map { item ->
                StorySourceEntity(item.getString("id"), stories.firstOrNull { it.sourceIdsJson.contains(item.getString("id")) }?.id ?: "", "public-source", item.getString("title"), item.getString("url"), license = item.getString("license"))
            }
            dao.upsertDestination(DestinationEntity("adirondack-high-peaks", "Adirondack High Peaks Loop", "New York", "United States", "A scenic public-source offline tour through Adirondack mountain communities and wilderness gateways.", estimatedDriveMinutes = 95, offlineSizeBytes = directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }, downloaded = true))
            dao.upsertTour(TourPackEntity(tourId, "adirondack-high-peaks", name, manifest.getString("version"), directory.absolutePath, File(directory, "manifest.json").absolutePath, true, true, directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }))
            dao.upsertRoute(RouteEntity("$tourId-route", tourId, name, File(directory, "route.geojson").absolutePath, 95))
            dao.upsertPois(pois); dao.upsertStories(stories); dao.upsertTriggers(triggers); dao.upsertSources(sources)
        }
    }
    private fun json(directory: File, name: String) = JSONObject(File(directory, name).readText())
    private fun jsonArray(directory: File, name: String, property: String? = null): List<JSONObject> {
        val array = if (property == null) JSONArray(File(directory, name).readText()) else json(directory, name).getJSONArray(property)
        return (0 until array.length()).map { array.getJSONObject(it) }
    }
    private fun JSONObject.optDoubleOrNull(name: String) = if (has(name) && !isNull(name)) getDouble(name) else null
}

@Singleton
class SampleTourInstaller @Inject constructor(
    private val assets: com.charles.trailsage.downloads.AssetRepository,
    private val importer: TourPackImporter,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {
    suspend fun install(): Result<Unit> {
        assets.installSampleTour()
        return importer.import(File(context.filesDir, "tours/adirondack-high-peaks-loop"))
    }
}

