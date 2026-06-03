package com.charles.trailsage.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File

class NeuralOfflineTtsEngine(private val voiceDirectory: File) : TtsEngine {
    override val id = "sherpa-onnx-vits"
    override val isNeural = true
    private var tts: OfflineTts? = null

    // The .tar.bz2 voice extracts into a nested folder, so resolve every required file
    // relative to wherever the .onnx model actually lives — not the top-level dir.
    private fun modelFile(): File? = voiceDirectory.walkTopDown().firstOrNull { it.isFile && it.extension == "onnx" }
    private fun modelDir(): File? = modelFile()?.parentFile
    private fun tokensFile(): File? = modelDir()?.let { File(it, "tokens.txt") }
    private fun dataDir(): File? = modelDir()?.let { File(it, "espeak-ng-data") }

    override fun available(): Boolean {
        val tokens = tokensFile()
        return modelFile()?.isFile == true && tokens?.isFile == true
    }

    override fun speak(text: String): Boolean = runCatching {
        val generated = engine().generate(text, 0, 1.0f)
        val track = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(generated.sampleRate).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(generated.samples.size * 4).setTransferMode(AudioTrack.MODE_STATIC).build()
        track.write(generated.samples, 0, generated.samples.size, AudioTrack.WRITE_BLOCKING); track.play(); true
    }.getOrDefault(false)

    fun release() { tts?.release(); tts = null }

    private fun engine(): OfflineTts = tts ?: run {
        val model = requireNotNull(modelFile()) { "No .onnx voice model found under $voiceDirectory" }
        val tokens = requireNotNull(tokensFile()) { "tokens.txt missing next to $model" }
        val espeak = dataDir()
        OfflineTts(
            null,
            OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = model.absolutePath,
                        tokens = tokens.absolutePath,
                        dataDir = if (espeak?.isDirectory == true) espeak.absolutePath else "",
                    ),
                    numThreads = 2,
                    provider = "cpu",
                ),
            ),
        ).also { tts = it }
    }
}

