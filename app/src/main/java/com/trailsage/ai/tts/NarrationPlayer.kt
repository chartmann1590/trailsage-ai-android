package com.charles.trailsage.tts

import android.content.Context
import com.charles.trailsage.data.local.TrailSageDao
import com.charles.trailsage.data.local.UserSettingsEntity
import com.charles.trailsage.firebase.FirebaseTelemetry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays narration with neural-first voice priority. The Android system voice is only
 * used as a last resort and only when the user has explicitly enabled the fallback;
 * when it is used we surface a UI warning and log the analytics event (prompt.txt §510-516).
 */
@Singleton
class NarrationPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TrailSageDao,
    private val voiceManager: VoiceManager,
    private val telemetry: FirebaseTelemetry,
) {
    enum class Source { NEURAL, ANDROID_FALLBACK, NONE }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private val _usingFallback = MutableStateFlow(false)
    /** True when narration is currently coming from the basic Android system voice. */
    val usingFallback: StateFlow<Boolean> = _usingFallback.asStateFlow()

    /** Selects the best available engine without speaking — used to drive UI state. */
    suspend fun resolveSource(): Source {
        val settings = dao.settings() ?: UserSettingsEntity()
        if (voiceManager.selectedNeuralEngine(settings)?.available() == true) return Source.NEURAL
        return if (settings.allowAndroidTtsFallback) Source.ANDROID_FALLBACK else Source.NONE
    }

    fun toggle(text: String) {
        if (_playing.value) stop() else play(text)
    }

    fun play(text: String) {
        stop()
        job = scope.launch {
            val settings = dao.settings() ?: UserSettingsEntity()
            val neural = voiceManager.selectedNeuralEngine(settings)
            val android = AndroidSystemTtsFallbackEngine(context, settings.allowAndroidTtsFallback)
            val engine = TtsManager(listOfNotNull(neural, android), settings.allowAndroidTtsFallback).select()
            when {
                engine == null -> { _playing.value = false; _usingFallback.value = false }
                engine.isNeural -> {
                    _usingFallback.value = false
                    _playing.value = true
                    engine.speak(text)
                    _playing.value = false
                }
                else -> {
                    _usingFallback.value = true
                    telemetry.event("android_tts_fallback_used")
                    _playing.value = true
                    engine.speak(text)
                    _playing.value = false
                }
            }
            neural?.release()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _playing.value = false
    }
}
