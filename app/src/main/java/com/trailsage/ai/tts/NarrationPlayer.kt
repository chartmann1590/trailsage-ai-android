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
 * Plays narration in the user's language with the voices the app has out of the box.
 * English text is translated on the device (ML Kit) into the narration language, then spoken
 * by the bundled neural voice (English) or the device's own voice for that language. When no
 * voice or translation pack exists for the language, narration falls back to English cleanly.
 */
@Singleton
class NarrationPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TrailSageDao,
    private val voiceManager: VoiceManager,
    private val translation: TranslationManager,
    private val telemetry: FirebaseTelemetry,
) {
    enum class Source { NEURAL, ANDROID_FALLBACK, NONE }

    /** What is (or was last) spoken: the text after translation, its language and the voice used. */
    data class Spoken(val text: String, val language: String, val languageLabel: String, val voiceId: String, val translated: Boolean, val fellBackToEnglish: Boolean)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private val _status = MutableStateFlow("")
    /** Short progress line while narration is being prepared ("Translating…", pack download). */
    val status: StateFlow<String> = _status.asStateFlow()

    private val _spoken = MutableStateFlow<Spoken?>(null)
    val spoken: StateFlow<Spoken?> = _spoken.asStateFlow()

    private val _usingFallback = MutableStateFlow(false)
    /** True when the narration language has no device voice or translation pack and English was used instead. */
    val usingFallback: StateFlow<Boolean> = _usingFallback.asStateFlow()

    /** Selects the best available engine without speaking — used to drive UI state. */
    suspend fun resolveSource(): Source {
        val settings = dao.settings() ?: UserSettingsEntity()
        val language = voiceManager.narrationLanguage(settings)
        val engine = voiceManager.engineFor(language, settings) ?: voiceManager.engineFor("en", settings)
        return when {
            engine == null -> Source.NONE
            engine.isNeural -> Source.NEURAL
            else -> Source.ANDROID_FALLBACK
        }
    }

    fun toggle(text: String) {
        if (_playing.value) stop() else play(text)
    }

    fun play(text: String) {
        stop()
        if (text.isBlank()) return
        job = scope.launch {
            _playing.value = true
            try {
                val settings = dao.settings() ?: UserSettingsEntity()
                val language = voiceManager.narrationLanguage(settings)
                val plan = prepare(text, language, settings)
                _spoken.value = plan.first
                _usingFallback.value = plan.first.fellBackToEnglish
                _status.value = ""
                val engine = plan.second
                if (engine == null) {
                    _status.value = "No narration voice is available."
                } else {
                    if (!engine.isNeural) telemetry.event("device_voice_used")
                    engine.speak(plan.first.text)
                }
                (engine as? NeuralOfflineTtsEngine)?.release()
            } finally {
                _playing.value = false
            }
        }
    }

    /** Speaks a short preview with exactly [option] (translated into its language when needed). */
    fun preview(text: String, option: VoiceManager.VoiceOption) {
        stop()
        job = scope.launch {
            _playing.value = true
            try {
                val engine: TtsEngine? = when (option.kind) {
                    VoiceManager.Kind.NEURAL -> option.directory?.let { NeuralOfflineTtsEngine(it) }
                    VoiceManager.Kind.SYSTEM -> SystemVoiceEngine(voiceManager.systemTts, option.systemVoice, java.util.Locale.forLanguageTag(option.language))
                }
                val language = option.language
                var spokenText = text
                var translated = false
                if (!voiceManager.isEnglish(language)) {
                    _status.value = "Preparing ${translation.displayName(language)} preview…"
                    if (translation.ensurePack(language)) {
                        translation.translateFromEnglish(text, language)?.let { spokenText = it; translated = true }
                    }
                }
                _status.value = ""
                _spoken.value = Spoken(spokenText, if (translated) language else "en", if (translated) translation.displayName(language) else "English", engine?.id ?: "", translated, fellBackToEnglish = false)
                engine?.speak(spokenText)
                (engine as? NeuralOfflineTtsEngine)?.release()
            } finally {
                _playing.value = false
            }
        }
    }

    /** Translates [text] when needed and picks the engine. Never throws; falls back to English. */
    private suspend fun prepare(text: String, language: String, settings: UserSettingsEntity): Pair<Spoken, TtsEngine?> {
        val english = voiceManager.isEnglish(language)
        if (!english) {
            val voiceAvailable = voiceManager.hasVoiceFor(language)
            if (voiceAvailable) {
                _status.value = "Preparing ${translation.displayName(language)} narration…"
                val packReady = translation.ensurePack(language)
                val translated = if (packReady) {
                    _status.value = "Translating…"
                    translation.translateFromEnglish(text, language)
                } else null
                if (!translated.isNullOrBlank()) {
                    val engine = voiceManager.engineFor(language, settings)
                    if (engine != null) {
                        return Spoken(translated, language, translation.displayName(language), engine.id, translated = true, fellBackToEnglish = false) to engine
                    }
                }
            }
        }
        val engine = voiceManager.engineFor("en", settings)
        return Spoken(text, "en", "English", engine?.id ?: "", translated = false, fellBackToEnglish = !english) to engine
    }

    fun stop() {
        job?.cancel()
        job = null
        voiceManager.systemTts.stop()
        _playing.value = false
        _status.value = ""
    }
}
