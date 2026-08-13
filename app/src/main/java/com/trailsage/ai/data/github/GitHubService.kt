package com.charles.trailsage.data.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubService @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    // Talks to the cloudflare-worker/ feedback relay, not api.github.com directly — the
    // Worker holds the GitHub token as a server-side secret and hardcodes this app's own
    // repo, so no owner/repo/credential ever needs to travel through this app. Previously
    // embedded BuildConfig.GITHUB_API_TOKEN client-side as a Bearer header, which shipped
    // a real repo-write PAT in every release build (extractable from the APK). See
    // cloudflare-worker/src/index.ts.
    private val baseUrl = "https://trailsage-ai-github-feedback.charles-h-hartmann1.workers.dev"

    private fun buildRequest(url: String, method: String, bodyStr: String? = null): Request {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "TrailSageAI/1.0")
            .header("X-GitHub-Api-Version", "2022-11-28")

        if (bodyStr != null) {
            val body = bodyStr.toRequestBody(mediaTypeJson)
            builder.method(method, body)
        } else {
            builder.method(method, null)
        }

        return builder.build()
    }

    private fun executeCall(request: Request): String {
        client.newCall(request).execute().use { response ->
            val bodyText = response.body?.string() ?: ""
            if (response.isSuccessful) {
                return bodyText
            } else {
                val message = runCatching {
                    JSONObject(bodyText).optString("message", "HTTP error ${response.code}")
                }.getOrDefault("HTTP error ${response.code}")
                throw IOException("GitHub API error: $message (code: ${response.code})")
            }
        }
    }

    suspend fun createIssue(title: String, body: String): JSONObject = withContext(Dispatchers.IO) {
        val url = "$baseUrl/issue"
        val payload = JSONObject().apply {
            put("title", title)
            put("body", body)
        }
        val request = buildRequest(url, "POST", payload.toString())
        JSONObject(executeCall(request))
    }

    suspend fun fetchIssueStatus(number: Int): String = withContext(Dispatchers.IO) {
        val url = "$baseUrl/issue/$number"
        val request = buildRequest(url, "GET")
        JSONObject(executeCall(request)).optString("state", "open")
    }

    suspend fun fetchComments(number: Int): JSONArray = withContext(Dispatchers.IO) {
        val url = "$baseUrl/issue/$number/comments"
        val request = buildRequest(url, "GET")
        JSONArray(executeCall(request))
    }

    suspend fun postComment(number: Int, body: String): JSONObject = withContext(Dispatchers.IO) {
        val url = "$baseUrl/issue/$number/comments"
        val payload = JSONObject().apply {
            put("body", body)
        }
        val request = buildRequest(url, "POST", payload.toString())
        JSONObject(executeCall(request))
    }

    suspend fun uploadAsset(filename: String, base64Content: String): String = withContext(Dispatchers.IO) {
        val url = "$baseUrl/upload-image"
        val payload = JSONObject().apply {
            put("filename", filename)
            put("contentBase64", base64Content)
        }
        val request = buildRequest(url, "POST", payload.toString())
        val responseStr = executeCall(request)
        val contentObj = JSONObject(responseStr).optJSONObject("content")
        contentObj?.optString("download_url") ?: throw IOException("Content upload_url not found in response")
    }
}
