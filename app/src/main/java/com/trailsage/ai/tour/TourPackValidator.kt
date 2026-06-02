package com.charles.trailsage.tour

import java.io.File

object TourPackValidator {
    private val required = listOf(
        "manifest.json", "route.geojson", "pois.geojson", "triggers.json", "stories.json",
        "sources.json", "rag_chunks.json", "attribution.json"
    )
    fun missingFiles(directory: File) = required.filterNot { File(directory, it).isFile }
    fun isValid(directory: File) = missingFiles(directory).isEmpty()
}
