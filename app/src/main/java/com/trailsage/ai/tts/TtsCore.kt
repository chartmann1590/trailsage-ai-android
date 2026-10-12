package com.charles.trailsage.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/** A narration voice: either the bundled neural voice pack or a voice installed on the device. */
interface TtsEngine {
    val id: String
    val isNeural: Boolean
    fun available(): Boolean
    /** Speaks [text] and returns once playback finished (or false when nothing was spoken). */
    suspend fun speak(text: String): Boolean
}

class SherpaOnnxTtsEngine(private val verified: Boolean) : TtsEngine {
    override val id = "sherpa-onnx"
    override val isNeural = true
    override fun available() = verified
    override suspend fun speak(text: String) = verified // Native Sherpa runtime is packaged by production builds.
}

/**
 * The voices already installed on the device (Google TTS, Samsung TTS, ...) in every language
 * the phone has. One shared engine instance per process; [ready] waits for its initialisation.
 */
class SystemTts(context: Context) {
    private val initResult = CompletableDeferred<Boolean>()
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        initResult.complete(status == TextToSpeech.SUCCESS)
    }

    suspend fun ready(): Boolean = withTimeoutOrNull(8_000) { initResult.await() } ?: false

    /** Installed, non-network voices, best quality first. */
    suspend fun voices(): List<Voice> {
        if (!ready()) return emptyList()
        val all = runCatching { tts.voices?.toList() }.getOrNull() ?: emptyList()
        return all
            .filter { !it.isNetworkConnectionRequired && !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.name })
    }

    suspend fun voicesFor(language: String): List<Voice> =
        voices().filter { it.locale.language.equals(Locale.forLanguageTag(language).language, ignoreCase = true) }

    suspend fun voiceNamed(name: String): Voice? = if (name.isBlank()) null else voices().firstOrNull { it.name == name }

    /** Speaks and suspends until the utterance finished. */
    suspend fun speak(text: String, voice: Voice?, locale: Locale): Boolean {
        if (!ready() || text.isBlank()) return false
        if (voice != null) tts.voice = voice else if (tts.setLanguage(locale) < TextToSpeech.LANG_AVAILABLE) return false
        val utteranceId = UUID.randomUUID().toString()
        return suspendCancellableCoroutine { cont ->
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { if (id == utteranceId && cont.isActive) cont.resume(true) }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) { if (id == utteranceId && cont.isActive) cont.resume(false) }
                override fun onError(id: String?, errorCode: Int) { if (id == utteranceId && cont.isActive) cont.resume(false) }
            })
            cont.invokeOnCancellation { runCatching { tts.stop() } }
            val params = Bundle()
            val queued = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (queued != TextToSpeech.SUCCESS && cont.isActive) cont.resume(false)
        }
    }

    fun stop() { runCatching { tts.stop() } }
}

/** A device voice wrapped as a [TtsEngine] for one language. */
class SystemVoiceEngine(private val system: SystemTts, private val voice: Voice?, private val locale: Locale) : TtsEngine {
    override val id = "android-system:" + (voice?.name ?: locale.toLanguageTag())
    override val isNeural = false
    override fun available() = true
    override suspend fun speak(text: String): Boolean = system.speak(text, voice, locale)
}
