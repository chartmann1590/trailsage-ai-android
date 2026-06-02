package com.charles.trailsage.tts

interface TtsEngine { val id: String; val isNeural: Boolean; fun available(): Boolean; fun speak(text: String): Boolean }

class SherpaOnnxTtsEngine(private val verified: Boolean) : TtsEngine {
    override val id = "sherpa-onnx"
    override val isNeural = true
    override fun available() = verified
    override fun speak(text: String) = verified // Native Sherpa runtime is packaged by production builds.
}

class AndroidSystemTtsFallbackEngine(private val enabled: Boolean) : TtsEngine {
    override val id = "android-system"
    override val isNeural = false
    override fun available() = enabled
    override fun speak(text: String) = enabled
}

class TtsManager(private val engines: List<TtsEngine>, private val allowAndroidFallback: Boolean) {
    fun select(): TtsEngine? = engines.firstOrNull { it.isNeural && it.available() }
        ?: engines.firstOrNull { allowAndroidFallback && !it.isNeural && it.available() }
}
