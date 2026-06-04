package com.charles.trailsage.map

import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import java.io.File
import kotlin.coroutines.resume

/**
 * Pre-downloads the OSM raster tiles for a route's bounding box into MapLibre's offline
 * database while the device is online, so the map renders for that route later WITHOUT a
 * network connection. The renderer ([TourMapView]) uses the exact same [OSM_RASTER_STYLE],
 * so the cached tile URLs are reused offline.
 *
 * Note: this bulk-fetches from the public OpenStreetMap raster tiles. Keep the zoom range
 * modest and prefer a tile provider that permits bulk/offline use for production.
 */
object OfflineMapCache {

    /** OpenStreetMap raster base. Shared with the renderer so cached tiles match exactly. */
    const val OSM_RASTER_STYLE = """
{
  "version": 8,
  "sources": {
    "osm": {
      "type": "raster",
      "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
      "tileSize": 256,
      "attribution": "© OpenStreetMap contributors"
    }
  },
  "layers": [{ "id": "osm", "type": "raster", "source": "osm" }]
}
"""

    // Zoom range cached for offline use: regional overview down to street level.
    private const val MIN_ZOOM = 6.0
    private const val MAX_ZOOM = 13.0
    private const val TILE_LIMIT = 80_000L

    /** A file:// style URL referencing the same OSM tiles, used to define the offline region. */
    private fun styleFileUrl(context: Context): String {
        val file = File(context.filesDir, "map/osm-raster-style.json")
        file.parentFile?.mkdirs()
        if (!file.exists()) file.writeText(OSM_RASTER_STYLE)
        return "file://${file.absolutePath}"
    }

    /**
     * Downloads (or refreshes) an offline region covering [bounds]. Best-effort: resumes
     * with `true` when the download completes, `false` on any error or tile-limit overflow.
     * Safe to call from any coroutine context (MapLibre work is marshalled to the main looper).
     */
    suspend fun cacheRegion(
        context: Context,
        bounds: LatLngBounds,
        metadataName: String,
        onProgress: (Int) -> Unit = {},
    ): Boolean = suspendCancellableCoroutine { cont ->
        var resumed = false
        fun finish(result: Boolean) {
            if (!resumed) {
                resumed = true
                if (cont.isActive) cont.resume(result)
            }
        }
        runCatching {
            MapLibre.getInstance(context)
            val manager = OfflineManager.getInstance(context)
            manager.setOfflineMapboxTileCountLimit(TILE_LIMIT)
            val pixelRatio = context.resources.displayMetrics.density
            val definition = OfflineTilePyramidRegionDefinition(
                styleFileUrl(context), bounds, MIN_ZOOM, MAX_ZOOM, pixelRatio
            )
            manager.createOfflineRegion(
                definition,
                metadataName.toByteArray(),
                object : OfflineManager.CreateOfflineRegionCallback {
                    override fun onCreate(region: OfflineRegion) {
                        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                            override fun onStatusChanged(status: OfflineRegionStatus) {
                                val total = status.requiredResourceCount
                                if (total > 0) {
                                    val pct = (100.0 * status.completedResourceCount / total)
                                        .toInt().coerceIn(0, 100)
                                    onProgress(pct)
                                }
                                if (status.isComplete) {
                                    region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                    finish(true)
                                }
                            }

                            override fun onError(error: OfflineRegionError) {
                                android.util.Log.w("OfflineMapCache", "region error: ${error.reason} ${error.message}")
                                finish(false)
                            }

                            override fun mapboxTileCountLimitExceeded(limit: Long) {
                                android.util.Log.w("OfflineMapCache", "tile limit exceeded ($limit); cached partial region")
                                region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                finish(false)
                            }
                        })
                        region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                    }

                    override fun onError(error: String) {
                        android.util.Log.w("OfflineMapCache", "createOfflineRegion error: $error")
                        finish(false)
                    }
                }
            )
        }.onFailure {
            android.util.Log.w("OfflineMapCache", "cacheRegion failed", it)
            finish(false)
        }
    }
}
