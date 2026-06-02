package com.charles.trailsage.gps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.charles.trailsage.data.local.StoryTriggerEntity
import com.charles.trailsage.domain.StoryTrigger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

data class GpsStatus(val enabled: Boolean, val weakSignal: Boolean, val location: Location? = null)

@Singleton
class LocationRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)
    @SuppressLint("MissingPermission")
    fun locations(): Flow<GpsStatus> = callbackFlow {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            trySend(GpsStatus(manager.isLocationEnabled, true)); close(); return@callbackFlow
        }
        val listener = LocationListener { location -> trySend(GpsStatus(manager.isLocationEnabled, location.accuracy > 50f, location)) }
        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2_000, 5f, listener)
        trySend(GpsStatus(manager.isLocationEnabled, true))
        awaitClose { manager.removeUpdates(listener) }
    }
}

class ActiveTourTriggerSelector(private val engine: StoryTriggerEngine = StoryTriggerEngine(), private val cooldown: TriggerCooldownManager = TriggerCooldownManager()) {
    fun select(triggers: List<StoryTriggerEntity>, location: Location): StoryTriggerEntity? = triggers.sortedByDescending { it.priority }.firstOrNull { entity ->
        val trigger = StoryTrigger(entity.id, entity.latitude, entity.longitude, entity.radiusMeters, entity.bearingStart, entity.bearingEnd, entity.cooldownMinutes)
        cooldown.canPlay(trigger) && engine.shouldTrigger(trigger, location.latitude, location.longitude, location.bearing.toDouble())
    }?.also { cooldown.markPlayed(StoryTrigger(it.id, it.latitude, it.longitude, it.radiusMeters, it.bearingStart, it.bearingEnd, it.cooldownMinutes)) }
}

