package com.charles.trailsage.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.trailsage.tts.NarrationPlayer
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
) : ViewModel() {

    private val _voices = MutableStateFlow<List<VoiceManager.VoiceOption>>(emptyList())
    val voices: StateFlow<List<VoiceManager.VoiceOption>> = _voices.asStateFlow()

    private val _previewing = MutableStateFlow<String?>(null)
    val previewing: StateFlow<String?> = _previewing.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch { _voices.value = voiceManager.options() }
    }

    fun select(id: String) {
        viewModelScope.launch {
            voiceManager.select(id)
            refresh()
        }
    }

    fun preview(option: VoiceManager.VoiceOption) {
        _previewing.value = option.id
        player.play("This is a preview of the ${option.name} natural offline voice from TrailSage AI.")
    }

    override fun onCleared() {
        player.stop()
    }
}
