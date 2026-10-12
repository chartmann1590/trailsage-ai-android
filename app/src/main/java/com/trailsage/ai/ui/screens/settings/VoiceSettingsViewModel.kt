package com.charles.trailsage.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.trailsage.data.local.TrailSageDao
import com.charles.trailsage.data.local.UserSettingsEntity
import com.charles.trailsage.tts.NarrationPlayer
import com.charles.trailsage.tts.TranslationManager
import com.charles.trailsage.tts.VoiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VoiceSettingsViewModel @Inject constructor(
    private val voiceManager: VoiceManager,
    private val player: NarrationPlayer,
    private val translation: TranslationManager,
    private val dao: TrailSageDao,
) : ViewModel() {

    data class LanguageChoice(val tag: String, val label: String)

    /** Neural voice packs (used by setup). */
    private val _voices = MutableStateFlow<List<VoiceManager.VoiceOption>>(emptyList())
    val voices: StateFlow<List<VoiceManager.VoiceOption>> = _voices.asStateFlow()

    /** Every voice (neural + device) for the current narration language. */
    private val _languageVoices = MutableStateFlow<List<VoiceManager.VoiceOption>>(emptyList())
    val languageVoices: StateFlow<List<VoiceManager.VoiceOption>> = _languageVoices.asStateFlow()

    private val _languages = MutableStateFlow<List<LanguageChoice>>(emptyList())
    val languages: StateFlow<List<LanguageChoice>> = _languages.asStateFlow()

    /** "auto" or a BCP-47 tag, as stored. */
    private val _languageSetting = MutableStateFlow("auto")
    val languageSetting: StateFlow<String> = _languageSetting.asStateFlow()

    /** The language narration actually uses right now. */
    private val _effectiveLanguage = MutableStateFlow("en")
    val effectiveLanguage: StateFlow<String> = _effectiveLanguage.asStateFlow()

    val packStatus: StateFlow<TranslationManager.PackStatus> = translation.packStatus
    val playerStatus: StateFlow<String> = player.status
    val spoken: StateFlow<NarrationPlayer.Spoken?> = player.spoken

    private val _previewing = MutableStateFlow<String?>(null)
    val previewing: StateFlow<String?> = _previewing.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val settings = dao.settings() ?: UserSettingsEntity()
            _languageSetting.value = settings.narrationLanguage
            val effective = voiceManager.narrationLanguage(settings)
            _effectiveLanguage.value = effective
            _voices.value = voiceManager.options()
            _languages.value = listOf(LanguageChoice("auto", "Automatic (device language: ${voiceManager.languageLabel(voiceManager.deviceLanguage())})")) +
                voiceManager.availableLanguages().map { LanguageChoice(it, voiceManager.languageLabel(it)) }
            _languageVoices.value = voiceManager.voicesFor(effective)
            translation.refreshStatus(effective)
        }
    }

    fun languageLabel(tag: String) = voiceManager.languageLabel(tag)

    fun selectLanguage(tag: String) {
        viewModelScope.launch {
            voiceManager.selectLanguage(tag)
            refresh()
            val settings = dao.settings() ?: UserSettingsEntity()
            val effective = voiceManager.narrationLanguage(settings)
            // Fetch the offline translation pack right away so the first narration is instant.
            translation.ensurePack(effective)
        }
    }

    fun downloadPack() {
        viewModelScope.launch { translation.ensurePack(_effectiveLanguage.value) }
    }

    fun select(id: String) {
        viewModelScope.launch {
            voiceManager.select(id)
            refresh()
        }
    }

    fun preview(option: VoiceManager.VoiceOption) {
        _previewing.value = option.id
        if (option.kind == VoiceManager.Kind.SYSTEM) {
            player.preview("This is a preview of the TrailSage AI narration voice. Welcome, and enjoy your road trip.", option)
        } else {
            player.preview("This is a preview of the ${option.name} natural offline voice from TrailSage AI.", option)
        }
    }

    override fun onCleared() {
        player.stop()
    }
}
