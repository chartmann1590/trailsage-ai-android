package com.charles.trailsage.ui.screens.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.trailsage.ai.AiGuideService
import com.charles.trailsage.data.local.AiChatMessageEntity
import com.charles.trailsage.data.local.TrailSageDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ChatMessage(
    val id: String,
    val role: String,
    val content: String,
    val sources: List<String> = emptyList(),
    val demo: Boolean = false,
)

@HiltViewModel
class GuideChatViewModel @Inject constructor(
    private val guide: AiGuideService,
    private val dao: TrailSageDao,
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _thinking = MutableStateFlow(false)
    val thinking: StateFlow<Boolean> = _thinking.asStateFlow()

    init {
        viewModelScope.launch {
            dao.observeChatMessages().collect { stored ->
                if (_messages.value.size <= stored.size) {
                    _messages.value = stored.map { ChatMessage(it.id, it.role, it.content) }
                }
            }
        }
    }

    fun ask(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || _thinking.value) return
        viewModelScope.launch {
            val userMessage = ChatMessage(UUID.randomUUID().toString(), "user", trimmed)
            _messages.value = _messages.value + userMessage
            dao.insertChatMessage(AiChatMessageEntity(userMessage.id, "user", trimmed))

            _thinking.value = true
            val answer = guide.answer(trimmed)
            val body = buildString {
                append(answer.narration)
                if (answer.funFact.isNotBlank()) append("\n\nFun fact: ${answer.funFact}")
            }
            val assistant = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = "assistant",
                content = body,
                sources = answer.sourceIds,
                demo = answer.demo,
            )
            _messages.value = _messages.value + assistant
            dao.insertChatMessage(AiChatMessageEntity(assistant.id, "assistant", body))
            _thinking.value = false
        }
    }

    fun clear() {
        viewModelScope.launch {
            dao.clearChat()
            _messages.value = emptyList()
        }
    }
}
