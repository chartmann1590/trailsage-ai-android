package com.charles.trailsage.routing

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A single turn-by-turn maneuver with the location where it happens. */
data class DirectionStep(val text: String, val latitude: Double, val longitude: Double)

object Directions {
    fun load(file: File?): List<DirectionStep> {
        if (file == null || !file.isFile) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).map {
                val o = array.getJSONObject(it)
                DirectionStep(o.getString("t"), o.optDouble("lat"), o.optDouble("lon"))
            }
        }.getOrDefault(emptyList())
    }

    fun write(file: File, steps: List<DirectionStep>) {
        val array = JSONArray()
        steps.forEach { array.put(JSONObject().put("t", it.text).put("lat", it.latitude).put("lon", it.longitude)) }
        file.writeText(array.toString())
    }
}
