package com.charles.trailsage.routing

import android.content.Context
import com.charles.trailsage.ai.AiGuideService
import com.charles.trailsage.data.local.*
import com.charles.trailsage.map.OfflineMapCache
import com.charles.trailsage.net.Http
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Builds a road-trip audio tour on demand from a start + end location, using only free,
 * keyless web sources, then narrates each stop with the on-device Gemma model:
 *   Nominatim (geocode) -> OSRM (driving route) -> Wikipedia geosearch + summaries (stops)
 *   -> on-device AI narration -> persisted as a playable tour pack (route + POIs + triggers).
 */
@Singleton
class RouteTourGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TrailSageDao,
    private val ai: AiGuideService,
) {
    data class GeneratedTour(val tourId: String, val name: String, val stopCount: Int, val usedAi: Boolean)
    private data class GeoPoint(val lat: Double, val lon: Double)
    private data class Route(val coords: List<DoubleArray>, val distanceMeters: Double, val durationSeconds: Double, val directions: List<DirectionStep> = emptyList())
    private data class Stop(val title: String, val lat: Double, val lon: Double, val url: String, val summary: String, val imageUrl: String, var narration: String = "")
    private data class WikiPage(val title: String, val extract: String, val pageUrl: String, val imageUrl: String)
    private data class TempCandidate(val pageId: Int, val title: String, val lat: Double, val lon: Double, val pointIndex: Int)

    suspend fun generate(startQuery: String, endQuery: String, onStatus: (String) -> Unit): Result<GeneratedTour> =
        withContext(Dispatchers.IO) {
            runCatching {
                onStatus("Finding start & destination…")
                val start = geocode(startQuery) ?: error("Could not find \"$startQuery\".")
                delay(1100)
                val end = geocode(endQuery) ?: error("Could not find \"$endQuery\".")

                onStatus("Calculating driving route…")
                val route = drivingRoute(start, end) ?: run {
                    onStatus("Routing service busy — using a direct corridor…")
                    straightCorridor(start, end)
                }

                onStatus("Scouting points of interest along the route…")
                val stops = collectStops(route.coords)
                if (stops.isEmpty()) error("No notable public-source stops found along this route.")

                val aiReady = ai.modelInstalled()
                stops.forEachIndexed { index, stop ->
                    onStatus("${if (aiReady) "AI narrating" else "Preparing"} stop ${index + 1}/${stops.size}: ${stop.title}")
                    stop.narration = ai.narrateStop(stop.title, stop.summary)
                }

                onStatus("Caching offline map for this route…")
                cacheRouteTiles(route.coords) { onStatus(it) }

                onStatus("Saving offline tour…")
                persist(startQuery, endQuery, route, stops, aiReady)
            }.onFailure {
                android.util.Log.e("RouteTourGenerator", "Generation failed", it)
            }
        }

    // --- web sources ---

    private fun geocode(query: String): GeoPoint? {
        val url = "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=${Http.encode(query)}"
        val arr = JSONArray(Http.get(url))
        if (arr.length() == 0) return null
        val o = arr.getJSONObject(0)
        return GeoPoint(o.getString("lat").toDouble(), o.getString("lon").toDouble())
    }

    private fun drivingRoute(start: GeoPoint, end: GeoPoint): Route? {
        val url = "https://router.project-osrm.org/route/v1/driving/" +
            "${start.lon},${start.lat};${end.lon},${end.lat}?overview=full&geometries=geojson&steps=true"
        val root = JSONObject(Http.get(url))
        val routes = root.optJSONArray("routes") ?: return null
        if (routes.length() == 0) return null
        val route = routes.getJSONObject(0)
        val coordsJson = route.getJSONObject("geometry").getJSONArray("coordinates")
        val coords = (0 until coordsJson.length()).map {
            val c = coordsJson.getJSONArray(it)
            doubleArrayOf(c.getDouble(0), c.getDouble(1)) // [lon, lat]
        }
        return Route(coords, route.optDouble("distance"), route.optDouble("duration"), parseDirections(route))
    }

    /** Build readable turn-by-turn directions (with maneuver locations) from OSRM steps. */
    private fun parseDirections(route: JSONObject): List<DirectionStep> = runCatching {
        val out = mutableListOf<DirectionStep>()
        val legs = route.optJSONArray("legs") ?: return@runCatching emptyList()
        for (l in 0 until legs.length()) {
            val steps = legs.getJSONObject(l).optJSONArray("steps") ?: continue
            for (s in 0 until steps.length()) {
                val step = steps.getJSONObject(s)
                val road = step.optString("name")
                val m = step.optJSONObject("maneuver") ?: continue
                val type = m.optString("type")
                val modifier = m.optString("modifier")
                val onto = if (road.isNotBlank()) " onto $road" else ""
                val on = if (road.isNotBlank()) " on $road" else ""
                val text = when (type) {
                    "depart" -> "Start out$on"
                    "arrive" -> "Arrive at your destination"
                    "turn" -> "Turn ${modifier.ifBlank { "ahead" }}$onto"
                    "merge" -> "Merge ${modifier}$onto".trim()
                    "on ramp", "off ramp" -> "Take the ramp ${modifier}$onto".trim()
                    "fork" -> "Keep ${modifier.ifBlank { "ahead" }}$onto"
                    "roundabout", "rotary" -> "Take the roundabout$onto"
                    "continue", "new name" -> "Continue$on"
                    "end of road" -> "Turn ${modifier.ifBlank { "ahead" }}$onto"
                    else -> (listOf(type, modifier).filter { it.isNotBlank() }.joinToString(" ") + onto).trim()
                }
                val loc = m.optJSONArray("location")
                val lon = loc?.optDouble(0) ?: 0.0
                val lat = loc?.optDouble(1) ?: 0.0
                if (text.isNotBlank()) out += DirectionStep(text, lat, lon)
            }
        }
        out
    }.getOrDefault(emptyList())

    /**
     * Re-route a previously-saved trip's geometry through OSRM to (re)build turn-by-turn
     * directions — used for older trips and the sample that lack a directions.json.
     */
    suspend fun regenerateDirections(tourId: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val routeFile = dao.route(tourId)?.geoJsonPath?.let { File(it) }
                ?: error("This trip has no saved route.")
            val coords = readRouteCoords(routeFile)
            require(coords.size >= 2) { "Route has too few points." }
            val waypoints = sampleEvenly(coords, 6) // OSRM follows these to match the saved route
            val path = waypoints.joinToString(";") { "${it[0]},${it[1]}" }
            val url = "https://router.project-osrm.org/route/v1/driving/$path?overview=false&steps=true"
            val routeJson = JSONObject(Http.get(url)).getJSONArray("routes").let {
                require(it.length() > 0) { "No route found." }; it.getJSONObject(0)
            }
            val steps = parseDirections(routeJson)
            require(steps.isNotEmpty()) { "Routing returned no directions." }
            Directions.write(File(routeFile.parentFile, "directions.json"), steps)
            steps.size
        }
    }

    /**
     * Backfill real Wikipedia/Wikimedia photos (from the web) for a tour's stops that have
     * none — used for trips generated before image support and the sample. Updates the DB
     * as each image is found so the UI fills in progressively.
     */
    suspend fun backfillImages(tourId: String) = withContext(Dispatchers.IO) {
        val storiesByTitle = dao.storiesOf(tourId).associateBy { it.title }
        dao.pois(tourId).forEach { poi ->
            val story = storiesByTitle[poi.name]
            val current = story?.imageLocalPath.orEmpty()
            if (story != null && !current.startsWith("http")) {
                // A real geotagged Commons photo near the stop (reliable); else the page image.
                val commons = runCatching { commonsImageNear(poi.latitude, poi.longitude) }.getOrNull()
                val image = commons ?: runCatching { wikiSummary(poi.name)?.imageUrl?.ifBlank { null } }.getOrNull()
                if (image != null) {
                    dao.setStoryImage(story.id, image)
                    dao.setPoiImageByName(tourId, poi.name, image)
                }
            }
            delay(120)
        }
    }

    private fun readRouteCoords(routeFile: File): List<DoubleArray> = runCatching {
        val fc = JSONObject(routeFile.readText())
        val coordsJson = fc.getJSONArray("features").getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
        (0 until coordsJson.length()).map { val c = coordsJson.getJSONArray(it); doubleArrayOf(c.getDouble(0), c.getDouble(1)) }
    }.getOrDefault(emptyList())

    /**
     * Pre-download the OSM map tiles covering this route's bounding box while we still have a
     * connection, so the route's map renders later with no internet. Best-effort: never fails
     * tour generation. coords are [lon, lat] pairs.
     */
    private suspend fun cacheRouteTiles(coords: List<DoubleArray>, onStatus: (String) -> Unit) {
        if (coords.size < 2) return
        runCatching {
            val builder = LatLngBounds.Builder()
            coords.forEach { builder.include(LatLng(it[1], it[0])) }
            val bounds = builder.build()
            withContext(Dispatchers.Main) {
                OfflineMapCache.cacheRegion(context, bounds, "route-${System.currentTimeMillis()}") { pct ->
                    onStatus("Caching offline map… $pct%")
                }
            }
        }.onFailure {
            android.util.Log.w("RouteTourGenerator", "Offline map caching skipped", it)
        }
    }

    private suspend fun collectStops(coords: List<DoubleArray>): List<Stop> {
        if (coords.isEmpty()) return emptyList()
        android.util.Log.i("RouteTourGenerator", "collectStops started with ${coords.size} coords")
        val samples = sampleEvenly(coords, SAMPLE_POINTS)
        android.util.Log.i("RouteTourGenerator", "sampled ${samples.size} points")
        val candidatesBySample = List(samples.size) { mutableListOf<TempCandidate>() }
        val seenPageIds = mutableSetOf<Int>()

        // 1. Gather candidates from each sample point
        for ((pointIndex, point) in samples.withIndex()) {
            var url = ""
            runCatching {
                url = "https://en.wikipedia.org/w/api.php?format=json&action=query&list=geosearch" +
                    "&gscoord=${point[1]}%7C${point[0]}&gsradius=10000&gslimit=5"
                val responseStr = Http.get(url)
                val queryObj = JSONObject(responseStr).optJSONObject("query") ?: return@runCatching
                val results = queryObj.optJSONArray("geosearch") ?: return@runCatching
                android.util.Log.i("RouteTourGenerator", "point $pointIndex returned ${results.length()} geosearch results")
                for (i in 0 until results.length()) {
                    val r = results.getJSONObject(i)
                    val pageId = r.getInt("pageid")
                    if (seenPageIds.add(pageId)) {
                        candidatesBySample[pointIndex].add(
                            TempCandidate(
                                pageId = pageId,
                                title = r.getString("title"),
                                lat = r.getDouble("lat"),
                                lon = r.getDouble("lon"),
                                pointIndex = pointIndex
                            )
                        )
                    }
                }
            }.onFailure {
                android.util.Log.e("RouteTourGenerator", "Geosearch failed for point $pointIndex ($url)", it)
            }
            delay(800) // Polite delay between geosearch requests (comply with MediaWiki's rate limits)
        }

        // 2. Distribute selection across the route using round-robin
        val selectedCandidates = mutableListOf<TempCandidate>()
        var addedInRound: Boolean
        var pass = 0
        do {
            addedInRound = false
            for (pointIndex in 0 until samples.size) {
                if (selectedCandidates.size >= MAX_STOPS) break
                val list = candidatesBySample[pointIndex]
                if (pass < list.size) {
                    selectedCandidates.add(list[pass])
                    addedInRound = true
                }
            }
            pass++
        } while (addedInRound && selectedCandidates.size < MAX_STOPS)

        android.util.Log.i("RouteTourGenerator", "Selected ${selectedCandidates.size} candidates via round-robin")

        // Sort by pointIndex to ensure POIs are ordered along the trip
        val orderedCandidates = selectedCandidates.sortedBy { it.pointIndex }

        // 3. Batch query Wikipedia details (summaries, images, urls) in chunks of 50
        val finalStops = mutableListOf<Stop>()
        val chunks = orderedCandidates.chunked(50)
        for (chunk in chunks) {
            var url = ""
            runCatching {
                val pageidsStr = chunk.joinToString("%7C") { it.pageId.toString() }
                url = "https://en.wikipedia.org/w/api.php?action=query&format=json" +
                    "&prop=extracts%7Cpageimages%7Cinfo&exintro=1&explaintext=1&exchars=300" +
                    "&piprop=original%7Cthumbnail&pithumbsize=1000&inprop=url&pageids=$pageidsStr"
                
                val responseStr = Http.get(url)
                val queryObj = JSONObject(responseStr).optJSONObject("query") ?: return@runCatching
                val pagesJson = queryObj.optJSONObject("pages") ?: return@runCatching
                
                for (candidate in chunk) {
                    val pageObj = pagesJson.optJSONObject(candidate.pageId.toString()) ?: continue
                    val extract = pageObj.optString("extract").trim()
                    if (extract.isBlank()) {
                        android.util.Log.i("RouteTourGenerator", "Skip ${candidate.title}: blank extract")
                        continue
                    }
                    
                    val title = pageObj.optString("title", candidate.title)
                    val pageUrl = pageObj.optString("fullurl", "https://en.wikipedia.org/wiki/${Http.encode(title).replace("+", "%20")}")
                    
                    val originalImage = pageObj.optJSONObject("original")?.optString("source")
                    val thumbnailImage = pageObj.optJSONObject("thumbnail")?.optString("source")
                    var imageUrl = originalImage?.ifBlank { null } ?: thumbnailImage?.ifBlank { null } ?: ""
                    
                    if (imageUrl.isBlank()) {
                        // Fallback to commons image search
                        imageUrl = runCatching { commonsImageNear(candidate.lat, candidate.lon) }
                            .onFailure { android.util.Log.w("RouteTourGenerator", "commonsImageNear failed for ${candidate.title}", it) }
                            .getOrNull() ?: ""
                        delay(100) // Polite delay after fallback call
                    }
                    
                    finalStops.add(
                        Stop(
                            title = title,
                            lat = candidate.lat,
                            lon = candidate.lon,
                            url = pageUrl,
                            summary = extract,
                            imageUrl = imageUrl
                        )
                    )
                }
            }.onFailure {
                android.util.Log.e("RouteTourGenerator", "Batch detail query failed ($url)", it)
            }
            delay(200) // Polite delay between batch detail requests
        }

        android.util.Log.i("RouteTourGenerator", "collectStops finished. Returning ${finalStops.size} stops")
        return finalStops
    }

    /** A real geotagged Wikimedia Commons photo near a coordinate (works even when the
     *  Wikipedia page itself has no lead image — e.g. small local landmarks). */
    private fun commonsImageNear(lat: Double, lon: Double): String? = runCatching {
        val url = "https://commons.wikimedia.org/w/api.php?format=json&action=query&generator=geosearch" +
            "&ggscoord=$lat%7C$lon&ggsradius=10000&ggslimit=1&ggsnamespace=6&prop=imageinfo&iiprop=url&iiurlwidth=1000"
        val pages = JSONObject(Http.get(url)).optJSONObject("query")?.optJSONObject("pages") ?: return null
        val key = pages.keys().asSequence().firstOrNull() ?: return null
        val info = pages.getJSONObject(key).optJSONArray("imageinfo")?.optJSONObject(0) ?: return null
        info.optString("thumburl").ifBlank { null } ?: info.optString("url").ifBlank { null }
    }.getOrNull()

    /** Wikipedia summary incl. a representative image (Wikimedia Commons) when available. */
    private fun wikiSummary(title: String): WikiPage? = runCatching {
        val path = Http.encode(title).replace("+", "%20")
        val o = JSONObject(Http.get("https://en.wikipedia.org/api/rest_v1/page/summary/$path"))
        val extract = o.optString("extract")
        if (extract.isBlank()) return null
        val pageUrl = o.optJSONObject("content_urls")?.optJSONObject("desktop")?.optString("page")
            ?: "https://en.wikipedia.org/wiki/$path"
        val image = o.optJSONObject("originalimage")?.optString("source")?.ifBlank { null }
            ?: o.optJSONObject("thumbnail")?.optString("source") ?: ""
        WikiPage(o.optString("title", title), extract, pageUrl, image)
    }.getOrNull()

    /** Fallback when the OSRM demo server is unavailable: a straight corridor of points. */
    private fun straightCorridor(start: GeoPoint, end: GeoPoint): Route {
        val n = 12
        val coords = (0..n).map { i ->
            val t = i.toDouble() / n
            doubleArrayOf(start.lon + (end.lon - start.lon) * t, start.lat + (end.lat - start.lat) * t)
        }
        val meters = haversine(start.lat, start.lon, end.lat, end.lon)
        return Route(coords, meters, meters / 13.4) // ~30 mph estimate
    }

    private fun haversine(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(bLat - aLat)
        val dLon = Math.toRadians(bLon - aLon)
        val h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(aLat)) * Math.cos(Math.toRadians(bLat)) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return r * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h))
    }

    private fun sampleEvenly(coords: List<DoubleArray>, points: Int): List<DoubleArray> {
        if (coords.size <= points) return coords
        val step = (coords.size - 1).toDouble() / (points - 1)
        return (0 until points).map { coords[(it * step).roundToInt().coerceIn(0, coords.size - 1)] }
    }

    // --- persistence ---

    private suspend fun persist(startQuery: String, endQuery: String, route: Route, stops: List<Stop>, usedAi: Boolean): GeneratedTour {
        val tourId = "route-${System.currentTimeMillis()}"
        val name = "$startQuery → $endQuery"
        val minutes = (route.durationSeconds / 60).roundToInt()
        val dir = File(context.filesDir, "tours/$tourId").apply { mkdirs() }
        File(dir, "route.geojson").writeText(routeGeoJson(route.coords))
        Directions.write(File(dir, "directions.json"), route.directions)

        dao.upsertDestination(
            DestinationEntity(
                id = tourId, name = name, region = "Custom route", country = "",
                description = "AI-generated road trip • ${stops.size} stops • ~$minutes min driving.",
                estimatedDriveMinutes = minutes, offlineSizeBytes = 0, downloaded = true,
            )
        )
        dao.upsertTour(TourPackEntity(tourId, tourId, name, "1.0", dir.absolutePath, "", downloaded = true, verified = true, sizeBytes = 0))
        dao.upsertRoute(RouteEntity("$tourId-route", tourId, name, File(dir, "route.geojson").absolutePath, minutes))

        val pois = mutableListOf<PoiEntity>()
        val stories = mutableListOf<StoryEntity>()
        val triggers = mutableListOf<StoryTriggerEntity>()
        val sources = mutableListOf<StorySourceEntity>()
        stops.forEachIndexed { i, stop ->
            val storyId = "$tourId-story-$i"
            val sourceId = "$tourId-src-$i"
            pois += PoiEntity("$tourId-poi-$i", tourId, stop.title, "landmark", stop.lat, stop.lon, stop.summary, imageLocalPath = stop.imageUrl, sourceIdsJson = "[\"$sourceId\"]")
            stories += StoryEntity(storyId, tourId, stop.title, stop.narration, imageLocalPath = stop.imageUrl, sourceIdsJson = "[\"$sourceId\"]", generatedByAi = usedAi)
            triggers += StoryTriggerEntity("$tourId-trig-$i", tourId, "$tourId-poi-$i", stop.lat, stop.lon, TRIGGER_RADIUS_M, priority = stops.size - i, storyId = storyId)
            sources += StorySourceEntity(sourceId, storyId, "wikipedia", stop.title, stop.url, license = "CC BY-SA")
        }
        sources += StorySourceEntity("$tourId-osm", "", "openstreetmap", "OpenStreetMap contributors", "https://www.openstreetmap.org/copyright", license = "ODbL")

        dao.upsertPois(pois); dao.upsertStories(stories); dao.upsertTriggers(triggers); dao.upsertSources(sources)
        return GeneratedTour(tourId, name, stops.size, usedAi)
    }

    private fun routeGeoJson(coords: List<DoubleArray>): String {
        val line = coords.joinToString(",", prefix = "[", postfix = "]") { "[${it[0]},${it[1]}]" }
        return """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":$line}}]}"""
    }

    companion object {
        // Scout the route densely with a generous ceiling rather than a hard "6 stops" cap.
        private const val MAX_STOPS = 30
        private const val SAMPLE_POINTS = 12
        private const val TRIGGER_RADIUS_M = 450.0
    }
}
