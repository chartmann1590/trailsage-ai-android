package com.charles.trailsage.net

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Tiny keyless HTTP GET helper with a polite User-Agent (Nominatim/Wikimedia require one). */
object Http {
    private const val USER_AGENT = "TrailSageAI/1.0 (offline road-trip tour app; on-device AI; contact: dev@trailsage.ai)"

    fun get(url: String, accept: String = "application/json"): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", accept)
        }
        connection.connect()
        if (connection.responseCode !in 200..299) {
            throw IOException("HTTP ${connection.responseCode} for $url")
        }
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
