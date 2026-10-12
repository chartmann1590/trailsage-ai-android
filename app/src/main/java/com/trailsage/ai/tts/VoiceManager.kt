package com.charles.trailsage.tts

import android.content.Context
import android.speech.tts.Voice
import com.charles.trailsage.data.local.TrailSageDao
import com.charles.trailsage.data.local.UserSettingsEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every narration voice the app can use out of the box: the downloaded neural voice pack(s)
 * and the voices already installed on the device, in every language the phone has. No
 * custom or imported models. The narration language follows the device language unless the
 * user picks one, and the voice for that language is chosen automatically unless the user
 * selects one in Voice settings.
 */
@Singleton
class VoiceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TrailSageDao,
) {
    enum class Kind { NEURAL, SYSTEM }

    data class VoiceOption(
        val id: String,
        val name: String,
        val style: String,
        val installed: Boolean,
        val selected: Boolean,
        val kind: Kind,
        /** BCP-47 tag of the language this voice speaks. */
        val language: String,
        val directory: File? = null,
        val systemVoice: Voice? = null,
    )

    val systemTts: SystemTts by lazy { SystemTts(context) }

    private fun voicesRoot() = File(context.filesDir, "voices")

    /** The language narration should be spoken in: the user's choice, else the device language. */
    fun narrationLanguage(settings: UserSettingsEntity): String {
        val chosen = settings.narrationLanguage
        if (chosen.isNotBlank() && chosen != "auto") return chosen
        return deviceLanguage()
    }

    fun deviceLanguage(): String = Locale.getDefault().toLanguageTag()

    fun languageLabel(tag: String): String = Locale.forLanguageTag(tag).let { it.getDisplayLanguage(it).replaceFirstChar { c -> c.uppercase() } }

    /** Languages offered in settings: English plus every language the device has an installed voice for. */
    suspend fun availableLanguages(): List<String> {
        val tags = linkedSetOf("en")
        systemTts.voices().forEach { tags += it.locale.language }
        return tags.toList()
    }

    /** Neural voice packs (English) from the required VOICE_PACK assets. */
    suspend fun neuralOptions(): List<VoiceOption> {
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
                    kind = Kind.NEURAL,
                    language = "en",
                    directory = dir,
                )
            }
    }

    /** Voices installed on the device for [language] (all languages when null). */
    suspend fun systemOptions(language: String? = null): List<VoiceOption> {
        val settings = dao.settings() ?: UserSettingsEntity()
        val voices = if (language == null) systemTts.voices() else systemTts.voicesFor(language)
        return voices.map { voice ->
            VoiceOption(
                id = "system:" + voice.name,
                name = friendlyName(voice),
                style = "Device voice • " + voice.locale.getDisplayName(voice.locale),
                installed = true,
                selected = settings.selectedSystemVoice == voice.name,
                kind = Kind.SYSTEM,
                language = voice.locale.toLanguageTag(),
                systemVoice = voice,
            )
        }
    }

    /** All voices (neural first) usable for [language] (every voice when null). */
    suspend fun voicesFor(language: String?): List<VoiceOption> {
        val neural = neuralOptions().filter { language == null || isEnglish(language) }
        return neural + systemOptions(language)
    }

    /** The neural packs only (setup flow). */
    suspend fun options(): List<VoiceOption> = neuralOptions()

    fun engineFor(directory: File): NeuralOfflineTtsEngine = NeuralOfflineTtsEngine(directory)

    /** Engine for the selected neural voice, or the first verified installed neural voice. */
    suspend fun selectedNeuralEngine(settings: UserSettingsEntity): NeuralOfflineTtsEngine? {
        val available = neuralOptions().filter { it.installed }
        val chosen = available.firstOrNull { it.id == settings.selectedVoicePackId } ?: available.firstOrNull()
        return chosen?.directory?.let { NeuralOfflineTtsEngine(it) }
    }

    /**
     * The engine that speaks [language]: English uses the neural pack (or an English device voice
     * when no pack is installed); other languages use the user's chosen device voice for that
     * language, else the best installed device voice. Null when the device has no voice for it.
     */
    suspend fun engineFor(language: String, settings: UserSettingsEntity): TtsEngine? {
        val locale = Locale.forLanguageTag(language)
        val chosenSystem = systemTts.voiceNamed(settings.selectedSystemVoice)
            ?.takeIf { it.locale.language.equals(locale.language, ignoreCase = true) }
        if (isEnglish(language)) {
            if (chosenSystem != null) return SystemVoiceEngine(systemTts, chosenSystem, locale)
            selectedNeuralEngine(settings)?.let { return it }
        }
        if (chosenSystem != null) return SystemVoiceEngine(systemTts, chosenSystem, locale)
        val best = systemTts.voicesFor(language).firstOrNull() ?: return null
        return SystemVoiceEngine(systemTts, best, locale)
    }

    suspend fun hasVoiceFor(language: String): Boolean =
        (isEnglish(language) && neuralOptions().any { it.installed }) || systemTts.voicesFor(language).isNotEmpty()

    suspend fun select(id: String) {
        val settings = dao.settings() ?: UserSettingsEntity()
        if (id.startsWith("system:")) {
            dao.upsertSettings(settings.copy(selectedSystemVoice = id.removePrefix("system:")))
        } else {
            dao.selectVoice(id)
            dao.upsertSettings(settings.copy(selectedVoicePackId = id, selectedSystemVoice = ""))
        }
    }

    suspend fun selectLanguage(tag: String) {
        val settings = dao.settings() ?: UserSettingsEntity()
        dao.upsertSettings(settings.copy(narrationLanguage = tag, selectedSystemVoice = ""))
    }

    fun isEnglish(tag: String) = Locale.forLanguageTag(tag).language.equals("en", ignoreCase = true)

    private fun friendlyName(voice: Voice): String {
        val locale = voice.locale
        val region = locale.getDisplayCountry(locale).ifBlank { locale.country }
        val quality = when {
            voice.quality >= Voice.QUALITY_VERY_HIGH -> "premium"
            voice.quality >= Voice.QUALITY_HIGH -> "high quality"
            else -> "standard"
        }
        val short = voice.name.substringAfterLast('-').take(12)
        return listOf(locale.getDisplayLanguage(locale).replaceFirstChar { it.uppercase() }, region).filter { it.isNotBlank() }.joinToString(" ") +
            " • $quality ($short)"
    }
}
