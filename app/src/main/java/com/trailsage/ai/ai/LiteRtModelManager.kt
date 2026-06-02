package com.charles.trailsage.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

interface ModelManager {
    fun isInstalled(): Boolean
    suspend fun loadModel(): Result<Unit>
    suspend fun generate(prompt: String): Result<String>
    fun unload()
}

@Singleton
class LiteRtModelManager @Inject constructor(@ApplicationContext private val context: Context) : ModelManager {
    private val modelFile = File(context.filesDir, "models/gemma-4-e2b-it/model.litertlm")
    private var engine: Engine? = null
    override fun isInstalled() = modelFile.isFile && modelFile.length() > 1_000_000_000
    override suspend fun loadModel(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(isInstalled()) { "Download and verify the Gemma 4 E2B LiteRT-LM model first." }
            unload()
            engine = Engine(EngineConfig(modelPath = modelFile.absolutePath, backend = Backend.CPU(), cacheDir = context.cacheDir.absolutePath)).also { it.initialize() }
        }
    }
    override suspend fun generate(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val active = engine ?: error("Gemma model is not loaded.")
            active.createConversation().use { it.sendMessage(prompt).toString() }
        }
    }
    override fun unload() { engine?.close(); engine = null }
}

