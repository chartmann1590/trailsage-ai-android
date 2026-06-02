package com.charles.trailsage.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

interface TtsEngine { val id: String; val isNeural: Boolean; fun available(): Boolean; fun speak(text: String): Boolean }

class SherpaOnnxTtsEngine(private val verified: Boolean) : TtsEngine {
    override val id = "sherpa-onnx"
    override val isNeural = true
    override fun available() = verified
    override fun speak(text: String) = verified // Native Sherpa runtime is packaged by production builds.
}

class AndroidSystemTtsFallbackEngine(context: Context, private val enabled: Boolean) : TtsEngine, TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var initialized = false
    override val id = "android-system"
    override val isNeural = false
    override fun available() = enabled && initialized
    override fun speak(text: String) = available() && tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "trailsage") == TextToSpeech.SUCCESS
    override fun onInit(status: Int) { initialized = status == TextToSpeech.SUCCESS && tts.setLanguage(Locale.US) >= TextToSpeech.LANG_AVAILABLE }
    fun shutdown() = tts.shutdown()
}

class TtsManager(private val engines: List<TtsEngine>, private val allowAndroidFallback: Boolean) {
    fun select(): TtsEngine? = engines.firstOrNull { it.isNeural && it.available() }
        ?: engines.firstOrNull { allowAndroidFallback && !it.isNeural && it.available() }
}
