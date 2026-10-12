package com.charles.trailsage.tts

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device translation of the English guide text into the narration language, using
 * ML Kit's offline translation packs (about 30 MB per language, downloaded on demand and
 * kept on the device). Nothing leaves the phone.
 */
@Singleton
class TranslationManager @Inject constructor(@ApplicationContext private val context: Context) {

    enum class PackState { NOT_NEEDED, NOT_DOWNLOADED, DOWNLOADING, READY, FAILED, UNSUPPORTED }
    data class PackStatus(val language: String, val state: PackState, val progress: Int = 0, val detail: String = "")

    private val _packStatus = MutableStateFlow(PackStatus("en", PackState.NOT_NEEDED, 100))
    val packStatus: StateFlow<PackStatus> = _packStatus.asStateFlow()

    private val modelManager by lazy { RemoteModelManager.getInstance() }
    private val translators = mutableMapOf<String, Translator>()

    /** ML Kit language code for a BCP-47 tag, or null when the language is not supported. */
    fun supportedCode(languageTag: String): String? {
        val language = Locale.forLanguageTag(languageTag).language.ifBlank { languageTag }
        return TranslateLanguage.fromLanguageTag(language)
    }

    fun isEnglish(languageTag: String) = Locale.forLanguageTag(languageTag).language.equals("en", ignoreCase = true)

    suspend fun isPackInstalled(languageTag: String): Boolean {
        if (isEnglish(languageTag)) return true
        val code = supportedCode(languageTag) ?: return false
        return runCatching { modelManager.isModelDownloaded(TranslateRemoteModel.Builder(code).build()).await() }.getOrDefault(false)
    }

    /** Refreshes [packStatus] for [languageTag] without downloading anything. */
    suspend fun refreshStatus(languageTag: String): PackStatus {
        val status = when {
            isEnglish(languageTag) -> PackStatus(languageTag, PackState.NOT_NEEDED, 100)
            supportedCode(languageTag) == null -> PackStatus(languageTag, PackState.UNSUPPORTED, 0, "No offline translation pack exists for this language; narration stays in English.")
            isPackInstalled(languageTag) -> PackStatus(languageTag, PackState.READY, 100)
            else -> PackStatus(languageTag, PackState.NOT_DOWNLOADED, 0)
        }
        _packStatus.value = status
        return status
    }

    /**
     * Makes sure the translation pack for [languageTag] is on the device, downloading it when
     * needed (about 30 MB, any connection). Returns true when translation is possible.
     */
    suspend fun ensurePack(languageTag: String): Boolean {
        if (isEnglish(languageTag)) { _packStatus.value = PackStatus(languageTag, PackState.NOT_NEEDED, 100); return true }
        val code = supportedCode(languageTag)
        if (code == null) { refreshStatus(languageTag); return false }
        if (isPackInstalled(languageTag)) { _packStatus.value = PackStatus(languageTag, PackState.READY, 100); return true }
        _packStatus.value = PackStatus(languageTag, PackState.DOWNLOADING, 0, "Downloading the offline translation pack…")
        val ok = runCatching {
            modelManager.download(TranslateRemoteModel.Builder(code).build(), DownloadConditions.Builder().build()).await()
            true
        }.getOrElse { error ->
            android.util.Log.w("TranslationManager", "translation pack download failed for $code", error)
            false
        }
        _packStatus.value = if (ok) PackStatus(languageTag, PackState.READY, 100)
        else PackStatus(languageTag, PackState.FAILED, 0, "The translation pack could not be downloaded. Check the connection and try again.")
        return ok
    }

    /** Translates English [text] into [languageTag]; null when the pack is missing or translation fails. */
    suspend fun translateFromEnglish(text: String, languageTag: String): String? {
        if (text.isBlank() || isEnglish(languageTag)) return text
        val code = supportedCode(languageTag) ?: return null
        if (!isPackInstalled(languageTag)) return null
        val translator = translators.getOrPut(code) {
            Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.ENGLISH).setTargetLanguage(code).build())
        }
        return withTimeoutOrNull(60_000) {
            runCatching { translator.translate(text).await() }.getOrElse { error ->
                android.util.Log.w("TranslationManager", "translate failed for $code", error)
                null
            }
        }
    }

    fun displayName(languageTag: String): String = Locale.forLanguageTag(languageTag).let { it.getDisplayLanguage(it).replaceFirstChar { c -> c.uppercase() } }
}
