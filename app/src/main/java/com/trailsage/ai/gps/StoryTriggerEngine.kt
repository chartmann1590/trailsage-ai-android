package com.charles.trailsage.gps

import com.charles.trailsage.domain.StoryTrigger
import kotlin.math.*

object BearingCalculator {
    fun matches(bearing: Double, start: Double?, end: Double?): Boolean {
        if (start == null || end == null) return true
        val normalized = (bearing + 360) % 360
        return if (start <= end) normalized in start..end else normalized >= start || normalized <= end
    }
}

class StoryTriggerEngine {
    fun shouldTrigger(trigger: StoryTrigger, latitude: Double, longitude: Double, bearing: Double): Boolean =
        distanceMeters(trigger.latitude, trigger.longitude, latitude, longitude) <= trigger.radiusMeters &&
            BearingCalculator.matches(bearing, trigger.bearingStart, trigger.bearingEnd)

    fun distanceMeters(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val radius = 6_371_000.0
        val dLat = Math.toRadians(bLat - aLat)
        val dLon = Math.toRadians(bLon - aLon)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLon / 2).pow(2)
        return radius * 2 * atan2(sqrt(h), sqrt(1 - h))
    }
}

class TriggerCooldownManager(private val now: () -> Long = System::currentTimeMillis) {
    private val lastPlayed = mutableMapOf<String, Long>()
    fun canPlay(trigger: StoryTrigger): Boolean =
        now() - (lastPlayed[trigger.id] ?: Long.MIN_VALUE) >= trigger.cooldownMinutes * 60_000
    fun markPlayed(trigger: StoryTrigger) { lastPlayed[trigger.id] = now() }
}
