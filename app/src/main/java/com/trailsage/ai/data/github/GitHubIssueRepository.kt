package com.charles.trailsage.data.github

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class SubmittedIssue(
    val number: Int,
    val title: String,
    val status: String, // "open" or "closed"
    val createdAt: String,
    val htmlUrl: String
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "github_issues")

@Singleton
class GitHubIssueRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gitHubService: GitHubService,
    private val diagnosticsHelper: DiagnosticsHelper
) {
    private val SUBMITTED_ISSUES_KEY = stringPreferencesKey("submitted_issues")

    val submittedIssues: Flow<List<SubmittedIssue>> = context.dataStore.data.map { preferences ->
        val json = preferences[SUBMITTED_ISSUES_KEY] ?: ""
        deserializeList(json)
    }

    private fun serializeList(list: List<SubmittedIssue>): String {
        val array = JSONArray()
        for (issue in list) {
            val obj = JSONObject().apply {
                put("number", issue.number)
                put("title", issue.title)
                put("status", issue.status)
                put("createdAt", issue.createdAt)
                put("htmlUrl", issue.htmlUrl)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializeList(jsonStr: String): List<SubmittedIssue> {
        if (jsonStr.isBlank()) return emptyList()
        val list = mutableListOf<SubmittedIssue>()
        runCatching {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    SubmittedIssue(
                        number = obj.getInt("number"),
                        title = obj.getString("title"),
                        status = obj.getString("status"),
                        createdAt = obj.getString("createdAt"),
                        htmlUrl = obj.getString("htmlUrl")
                    )
                )
            }
        }
        return list
    }

    suspend fun refreshStatuses() {
        val currentList = submittedIssues.first()
        if (currentList.isEmpty()) return
        val updatedList = currentList.map { issue ->
            val status = runCatching { gitHubService.fetchIssueStatus(issue.number) }.getOrNull()
            if (status != null) {
                issue.copy(status = status)
            } else {
                issue
            }
        }
        context.dataStore.edit { preferences ->
            preferences[SUBMITTED_ISSUES_KEY] = serializeList(updatedList)
        }
    }

    suspend fun submitIssue(
        title: String,
        description: String,
        name: String,
        email: String,
        includeDiagnostics: Boolean,
        screenshotUri: Uri?
    ): Result<SubmittedIssue> {
        val finalBody = StringBuilder()
        
        // Metadata / reporter info
        if (name.isNotBlank() || email.isNotBlank()) {
            finalBody.append("**Reported by**:")
            if (name.isNotBlank()) finalBody.append(" $name")
            if (email.isNotBlank()) finalBody.append(" ($email)")
            finalBody.append("\n\n")
        }

        finalBody.append(description)

        // Upload screenshot if present
        if (screenshotUri != null) {
            val uploadResult = uploadScreenshot(screenshotUri)
            if (uploadResult.isSuccess) {
                val downloadUrl = uploadResult.getOrThrow()
                finalBody.append("\n\n### Attached Screenshot\n")
                finalBody.append("![Screenshot]($downloadUrl)")
            } else {
                return Result.failure(uploadResult.exceptionOrNull() ?: Exception("Failed to upload screenshot"))
            }
        }

        // Diagnostics
        if (includeDiagnostics) {
            val diagnostics = diagnosticsHelper.gatherDiagnostics(includeModels = true)
            finalBody.append("\n\n---\n")
            finalBody.append(diagnostics)
        }

        return runCatching {
            val responseObj = gitHubService.createIssue(title, finalBody.toString())
            val number = responseObj.getInt("number")
            val issueTitle = responseObj.getString("title")
            val state = responseObj.optString("state", "open")
            val createdAt = responseObj.optString("created_at", "")
            val htmlUrl = responseObj.optString("html_url", "")

            val newIssue = SubmittedIssue(
                number = number,
                title = issueTitle,
                status = state,
                createdAt = createdAt,
                htmlUrl = htmlUrl
            )

            // Save to local storage
            val currentList = submittedIssues.first()
            val updatedList = currentList + newIssue
            context.dataStore.edit { preferences ->
                preferences[SUBMITTED_ISSUES_KEY] = serializeList(updatedList)
            }

            newIssue
        }
    }

    suspend fun postComment(
        issueNumber: Int,
        bodyText: String,
        screenshotUri: Uri?
    ): Result<JSONObject> {
        val finalBody = StringBuilder()
        finalBody.append("**[User Reply from App]**\n\n")
        finalBody.append(bodyText)

        if (screenshotUri != null) {
            val uploadResult = uploadScreenshot(screenshotUri)
            if (uploadResult.isSuccess) {
                val downloadUrl = uploadResult.getOrThrow()
                finalBody.append("\n\n### Attached Screenshot\n")
                finalBody.append("![Screenshot]($downloadUrl)")
            } else {
                return Result.failure(uploadResult.exceptionOrNull() ?: Exception("Failed to upload comment screenshot"))
            }
        }

        return runCatching {
            gitHubService.postComment(issueNumber, finalBody.toString())
        }
    }

    private suspend fun uploadScreenshot(uri: Uri): Result<String> {
        return runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IOException("Could not open InputStream for screenshot URI")
            
            val base64Content = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            val filename = "screenshot_${UUID.randomUUID()}.png"
            gitHubService.uploadAsset(filename, base64Content)
        }
    }
}
