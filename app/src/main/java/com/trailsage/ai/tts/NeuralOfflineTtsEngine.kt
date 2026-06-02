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
    override fun available() = requiredFiles().all(File::isFile)
    override fun speak(text: String): Boolean = runCatching {
        val generated = engine().generate(text, 0, 1.0f)
        val track = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(generated.sampleRate).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(generated.samples.size * 4).setTransferMode(AudioTrack.MODE_STATIC).build()
        track.write(generated.samples, 0, generated.samples.size, AudioTrack.WRITE_BLOCKING); track.play(); true
    }.getOrDefault(false)
    fun release() { tts?.release(); tts = null }
    private fun engine(): OfflineTts = tts ?: OfflineTts(null, OfflineTtsConfig(model = OfflineTtsModelConfig(vits = OfflineTtsVitsModelConfig(model = model().absolutePath, tokens = File(voiceDirectory, "tokens.txt").absolutePath, dataDir = File(voiceDirectory, "espeak-ng-data").absolutePath), numThreads = 2, provider = "cpu"))).also { tts = it }
    private fun model() = voiceDirectory.walkTopDown().firstOrNull { it.isFile && it.extension == "onnx" } ?: File(voiceDirectory, "model.onnx")
    private fun requiredFiles() = listOf(model(), File(voiceDirectory, "tokens.txt"))
}

