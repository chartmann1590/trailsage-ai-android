package com.charles.trailsage.domain

object SetupGate {
    private val requiredTypes = setOf(
        AssetType.GEMMA_MODEL, AssetType.LITERT_ASSET, AssetType.TTS_ENGINE,
        AssetType.VOICE_PACK, AssetType.TOUR_PACK, AssetType.MAP_PACK, AssetType.RAG_PACK
    )

    fun canComplete(assets: List<RequiredAsset>, voices: List<VoicePack>): Boolean {
        val readyTypes = assets.filter { it.required && it.installed && it.verified }.map { it.type }.toSet()
        return readyTypes.containsAll(requiredTypes) && voices.any {
            it.engine != "android-system" && it.installed && it.verified
        }
    }
}
