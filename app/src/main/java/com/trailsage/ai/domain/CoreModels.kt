package com.charles.trailsage.domain

enum class SetupState {
    NOT_STARTED, CHECKING_DEVICE, DEVICE_SUPPORTED, DEVICE_LIMITED, DEVICE_FAILED,
    READY_FOR_DOWNLOADS, DOWNLOADING_AI_MODEL, DOWNLOADING_LITERT_ASSETS,
    DOWNLOADING_TTS_ENGINE, DOWNLOADING_VOICE_PACK, DOWNLOADING_SAMPLE_TOUR,
    DOWNLOADING_MAP_PACK, DOWNLOADING_RAG_PACK, VERIFYING_ASSETS, SETUP_COMPLETE, SETUP_FAILED
}

enum class AssetType {
    GEMMA_MODEL, LITERT_ASSET, TTS_ENGINE, VOICE_PACK, TOUR_PACK, MAP_PACK,
    RAG_PACK, IMAGE_PACK, SOURCE_ATTRIBUTION
}

data class RequiredAsset(
    val id: String,
    val name: String,
    val type: AssetType,
    val version: String,
    val downloadUrl: String,
    val localPath: String,
    val sizeBytes: Long,
    val checksumSha256: String,
    val required: Boolean = true,
    val installed: Boolean = false,
    val verified: Boolean = false,
    val license: String = "",
    val attribution: String = "",
    val minAndroidSdk: Int = 26,
    val recommendedRamMb: Int = 0,
    val engine: String = ""
)

data class VoicePack(
    val id: String,
    val name: String,
    val engine: String,
    val installed: Boolean,
    val verified: Boolean,
    val selected: Boolean = false
)

data class RagChunk(val id: String, val text: String, val sourceIds: List<String>)
data class StoryTrigger(
    val id: String, val latitude: Double, val longitude: Double, val radiusMeters: Double,
    val bearingStart: Double? = null, val bearingEnd: Double? = null, val cooldownMinutes: Long = 30
)

object ProductionAssets {
    const val GEMMA_REVISION = "7fa1d78473894f7e736a21d920c3aa80f950c0db"
    const val GEMMA_SIZE_BYTES = 2_583_085_056L
    const val GEMMA_SHA256 = "ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42"
    const val GEMMA_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/$GEMMA_REVISION/gemma-4-E2B-it.litertlm"
    const val SHERPA_ONNX_VERSION = "1.13.2"
}
