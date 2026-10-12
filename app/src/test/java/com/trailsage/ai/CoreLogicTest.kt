package com.charles.trailsage

import com.charles.trailsage.ai.SimpleKeywordRagRetriever
import com.charles.trailsage.domain.*
import com.charles.trailsage.downloads.AssetManifestParser
import com.charles.trailsage.downloads.AssetVerificationManager
import com.charles.trailsage.downloads.DownloadNetworkPolicy
import com.charles.trailsage.gps.BearingCalculator
import com.charles.trailsage.gps.StoryTriggerEngine
import com.charles.trailsage.tts.*
import com.charles.trailsage.tour.TourPackValidator
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class CoreLogicTest {
    private fun asset(type: AssetType) = RequiredAsset(type.name, type.name, type, "1", "", "", 0, "", installed = true, verified = true)
    @Test fun setupRequiresEveryAssetAndNaturalVoice() {
        val assets = listOf(AssetType.GEMMA_MODEL, AssetType.LITERT_ASSET, AssetType.TTS_ENGINE, AssetType.VOICE_PACK, AssetType.TOUR_PACK, AssetType.MAP_PACK, AssetType.RAG_PACK).map(::asset)
        assertFalse(SetupGate.canComplete(assets, listOf(VoicePack("android", "Android", "android-system", true, true))))
        assertTrue(SetupGate.canComplete(assets, listOf(VoicePack("voice", "Natural", "sherpa-onnx", true, true))))
        assertFalse(SetupGate.canComplete(assets.filterNot { it.type == AssetType.GEMMA_MODEL }, listOf(VoicePack("voice", "Natural", "sherpa-onnx", true, true))))
    }
    @Test fun checksumMatches() = assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", AssetVerificationManager.sha256(ByteArrayInputStream("abc".toByteArray())))
    @Test fun manifestParses() = assertEquals(AssetType.RAG_PACK, AssetManifestParser.parse("""{"assets":[{"id":"r","name":"R","type":"RAG_PACK","version":"1","downloadUrl":"x","localPath":"r","sizeBytes":1,"checksumSha256":"a"}]}""").single().type)
    @Test fun triggerChecksRadiusAndWrappedBearing() {
        val trigger = StoryTrigger("x", 44.0, -73.0, 100.0, 350.0, 10.0)
        assertTrue(StoryTriggerEngine().shouldTrigger(trigger, 44.0001, -73.0, 5.0))
        assertFalse(BearingCalculator.matches(180.0, 350.0, 10.0))
    }
    @Test fun ragRanksKeywordContext() {
        val chunks = listOf(RagChunk("a", "Cascade Lakes scenic corridor", listOf("s1")), RagChunk("b", "Keene Valley trailheads", listOf("s2")))
        assertEquals("a", SimpleKeywordRagRetriever(chunks).retrieve("Tell me about Cascade Lakes").first().id)
    }
    @Test fun neuralEngineReportsVerifiedState() {
        assertTrue(SherpaOnnxTtsEngine(true).available())
        assertFalse(SherpaOnnxTtsEngine(false).available())
        assertTrue(SherpaOnnxTtsEngine(true).isNeural)
    }
    @Test fun tourPackValidatorFindsMissingFiles() {
        val dir = kotlin.io.path.createTempDirectory().toFile()
        assertFalse(TourPackValidator.isValid(dir))
        assertTrue(TourPackValidator.missingFiles(dir).contains("manifest.json"))
    }
    @Test fun setupRejectsDemoPlaceholderMap() {
        val assets = listOf(AssetType.GEMMA_MODEL, AssetType.LITERT_ASSET, AssetType.TTS_ENGINE, AssetType.VOICE_PACK, AssetType.TOUR_PACK, AssetType.MAP_PACK, AssetType.RAG_PACK).map(::asset)
        val demoMap = assets.map { if (it.type == AssetType.MAP_PACK) it.copy(verified = false) else it }
        assertFalse(SetupGate.canComplete(demoMap, listOf(VoicePack("voice", "Natural", "sherpa-onnx", true, true))))
    }

    @Test fun wifiOnlyHoldsOnlyLargeDownloads() {
        val model = 2_583_085_056L
        val voice = 23_398_348L
        // The ~22 MB voice must never wait for Wi-Fi, or setup stalls on mobile data.
        assertFalse(DownloadNetworkPolicy.requiresUnmetered(wifiOnlySetting = true, sizeBytes = voice, userOverride = false))
        assertTrue(DownloadNetworkPolicy.requiresUnmetered(wifiOnlySetting = true, sizeBytes = model, userOverride = false))
        // "Download now" on the card, or the setting turned off, lets the model start now.
        assertFalse(DownloadNetworkPolicy.requiresUnmetered(wifiOnlySetting = true, sizeBytes = model, userOverride = true))
        assertFalse(DownloadNetworkPolicy.requiresUnmetered(wifiOnlySetting = false, sizeBytes = model, userOverride = false))
    }
}
