package com.charles.trailsage.ai

import com.charles.trailsage.domain.RagChunk
import java.io.File

interface LocalLlmEngine {
    fun isAvailable(): Boolean
    fun loadModel(): Boolean
    fun generateJson(prompt: String): String
    fun unload()
}

class DemoTemplateLlmEngine : LocalLlmEngine {
    override fun isAvailable() = true
    override fun loadModel() = true
    override fun generateJson(prompt: String) =
        """{"title":"Offline guide","narration":"This demo needs a verified Gemma model before local generation is enabled.","funFact":"Tour packs keep source context on your device.","sourceIds":[]}"""
    override fun unload() = Unit
}

class GemmaLiteRtEngine(private val modelFile: File, private val demo: LocalLlmEngine = DemoTemplateLlmEngine()) :
    LocalLlmEngine {
    override fun isAvailable() = modelFile.isFile && modelFile.length() > 1_000_000
    override fun loadModel() = isAvailable()
    override fun generateJson(prompt: String): String {
        // LiteRT-LM native binding belongs here after the verified external runtime/model is installed.
        return if (isAvailable()) demo.generateJson(prompt) else demo.generateJson(prompt)
    }
    override fun unload() = Unit
}

interface RagRetriever { fun retrieve(query: String, limit: Int = 3): List<RagChunk> }

class SimpleKeywordRagRetriever(private val chunks: List<RagChunk>) : RagRetriever {
    override fun retrieve(query: String, limit: Int): List<RagChunk> {
        val words = query.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()
        return chunks.map { chunk ->
            chunk to chunk.text.lowercase().split(Regex("\\W+")).count { it in words }
        }.filter { it.second > 0 }.sortedByDescending { it.second }.take(limit).map { it.first }
    }
}

class AiGuideRepository(private val rag: RagRetriever, private val llm: LocalLlmEngine) {
    fun answer(query: String): String {
        val context = rag.retrieve(query)
        if (context.isEmpty()) return "I do not have enough local source context. Download or update this destination pack."
        return llm.generateJson(context.joinToString("\n") { it.text })
    }
}
