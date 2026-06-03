package com.charles.trailsage.ai

import com.charles.trailsage.data.local.TrailSageDao
import com.charles.trailsage.domain.RagChunk
import com.charles.trailsage.routing.ActiveTourStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device AI orchestration. Runs the real Gemma 4 / LiteRT-LM model through
 * [LiteRtModelManager] (no cloud, no token) and is the single entry point for both the
 * guide chat and AI tour-narration generation. Loads the model once, lazily.
 */
@Singleton
class AiGuideService @Inject constructor(
    private val modelManager: LiteRtModelManager,
    private val dao: TrailSageDao,
    private val activeTourStore: ActiveTourStore,
) {
    data class GuideAnswer(
        val title: String,
        val narration: String,
        val funFact: String,
        val sourceIds: List<String>,
        val grounded: Boolean,
        val demo: Boolean,
    )

    private val loadMutex = Mutex()
    @Volatile private var loaded = false

    fun modelInstalled(): Boolean = modelManager.isInstalled()

    /** Ensures the on-device model is initialized. Returns false if it isn't installed/loadable. */
    suspend fun ensureLoaded(): Boolean {
        if (!modelManager.isInstalled()) return false
        if (loaded) return true
        return loadMutex.withLock {
            if (loaded) return@withLock true
            modelManager.loadModel().onSuccess { loaded = true }
            loaded
        }
    }

    /** Raw on-device generation. Returns null when the model is unavailable. */
    suspend fun generate(prompt: String): String? {
        if (!ensureLoaded()) return null
        return modelManager.generate(prompt).getOrNull()?.takeIf { it.isNotBlank() }
    }

    /** RAG-first chat answer: grounded ONLY in the currently-active trip's stops. */
    suspend fun answer(query: String): GuideAnswer = withContext(Dispatchers.Default) {
        val chunks = loadActiveTourChunks()
        val retrieved = SimpleKeywordRagRetriever(chunks).retrieve(query)
        if (retrieved.isEmpty()) {
            return@withContext GuideAnswer(
                title = "Not on this trip",
                narration = if (chunks.isEmpty())
                    "Pick or build a trip first, then ask me about the stops on it."
                else
                    "I can only talk about your current trip's stops, and I don't have anything on that. " +
                        "Try asking about one of the stops on this route.",
                funFact = "", sourceIds = emptyList(), grounded = false, demo = false,
            )
        }
        val ids = retrieved.flatMap { it.sourceIds }.distinct()
        val generated = generate(chatPrompt(query, retrieved))
        if (generated != null) {
            parseJsonAnswer(generated, ids).copy(demo = false)
        } else {
            GuideAnswer(
                title = "Offline guide (demo)",
                narration = retrieved.first().text,
                funFact = "",
                sourceIds = ids,
                grounded = true,
                demo = true,
            )
        }
    }

    /** Vivid spoken narration for a route stop, generated on-device from web-sourced notes. */
    suspend fun narrateStop(name: String, sourceNotes: String, audience: String = "road-trip travelers"): String {
        val prompt = """
            SYSTEM: You are TrailSage AI, a warm, vivid road-trip storyteller. Using ONLY the facts in the
            notes below, tell an engaging spoken story about this place for $audience as they approach it by car.
            Open with a hook, weave in the most interesting history or detail, and end with something memorable.
            Write 4 to 6 natural sentences. Do not invent facts beyond the notes. Do not give driving
            instructions and never tell the driver to look at the screen. No preamble, no headings, no JSON.
            PLACE: $name
            NOTES: ${sourceNotes.take(1500)}
            STORY:
        """.trimIndent()
        val generated = generate(prompt)?.trim()?.removePrefix("STORY:")?.trim()
        // Honest fallback when the model isn't installed yet: use the source notes directly.
        return generated?.takeIf { it.length > 40 } ?: sourceNotes.take(700)
    }

    private fun chatPrompt(query: String, context: List<RagChunk>): String = """
        SYSTEM: You are TrailSage AI, a concise, friendly road-trip narrator. Use only the provided
        source context. Do not invent facts. Keep narration under 90 seconds. Never give unsafe driving
        instructions and never tell the driver to look at the screen.
        QUESTION: $query
        SOURCES:
        ${context.joinToString("\n") { "- (${it.id}) ${it.text}" }}
        Respond as JSON: {"title":"...","narration":"...","funFact":"...","sourceIds":["..."]}
    """.trimIndent()

    private fun parseJsonAnswer(text: String, fallbackIds: List<String>): GuideAnswer {
        // The model may wrap JSON in prose; extract the first {...} block if present.
        val json = text.substringAfter('{', "").substringBeforeLast('}', "").let { if (it.isBlank()) "" else "{$it}" }
        return runCatching {
            val obj = JSONObject(json)
            val ids = obj.optJSONArray("sourceIds")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: fallbackIds
            GuideAnswer(
                title = obj.optString("title", "Offline guide"),
                narration = obj.optString("narration").ifBlank { text.trim() },
                funFact = obj.optString("funFact"),
                sourceIds = ids.ifEmpty { fallbackIds },
                grounded = true, demo = false,
            )
        }.getOrElse {
            GuideAnswer("Offline guide", text.trim(), "", fallbackIds, grounded = true, demo = false)
        }
    }

    /** RAG context built from the active trip's stops only, so the guide stays on-trip. */
    private suspend fun loadActiveTourChunks(): List<RagChunk> {
        val tourId = activeTourStore.tourId.value
        return dao.storiesOf(tourId).map { story ->
            RagChunk(
                id = story.id,
                text = "${story.title}. ${story.narrationText}",
                sourceIds = parseSourceIds(story.sourceIdsJson),
            )
        }
    }

    private fun parseSourceIds(json: String): List<String> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).map { array.getString(it) }
    }.getOrDefault(emptyList())
}
