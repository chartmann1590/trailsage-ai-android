package com.charles.trailsage.tts

import android.content.Context
import com.charles.trailsage.data.local.TrailSageDao
import com.charles.trailsage.data.local.UserSettingsEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discovers installed natural (neural) voice packs and resolves the engine for the
 * user's selected voice. Voice packs are required before setup completes, and the
 * neural voice is always preferred over the Android system fallback (prompt.txt §510).
 */
@Singleton
class VoiceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TrailSageDao,
) {
    data class VoiceOption(
        val id: String,
        val name: String,
        val style: String,
        val installed: Boolean,
        val selected: Boolean,
        val directory: File,
    )

    private fun voicesRoot() = File(context.filesDir, "voices")

    /** Voice options derived from required VOICE_PACK assets + their extracted directories. */
    suspend fun options(): List<VoiceOption> {
        val settings = dao.settings() ?: UserSettingsEntity()
        return dao.assets()
            .filter { it.type == "VOICE_PACK" }
            .map { asset ->
                val dir = File(voicesRoot(), asset.id)
                VoiceOption(
                    id = asset.id,
                    name = asset.name,
                    style = asset.engine.ifBlank { "Neural offline voice" },
                    installed = asset.verified && NeuralOfflineTtsEngine(dir).available(),
                    selected = settings.selectedVoicePackId == asset.id,
                    directory = dir,
                )
            }
    }

    fun engineFor(directory: File): NeuralOfflineTtsEngine = NeuralOfflineTtsEngine(directory)

    /** Engine for the selected voice, or the first verified installed voice. */
    suspend fun selectedNeuralEngine(settings: UserSettingsEntity): NeuralOfflineTtsEngine? {
        val available = options().filter { it.installed }
        val chosen = available.firstOrNull { it.id == settings.selectedVoicePackId } ?: available.firstOrNull()
        return chosen?.let { NeuralOfflineTtsEngine(it.directory) }
    }

    suspend fun select(id: String) {
        dao.selectVoice(id)
        val settings = dao.settings() ?: UserSettingsEntity()
        dao.upsertSettings(settings.copy(selectedVoicePackId = id))
    }
}
