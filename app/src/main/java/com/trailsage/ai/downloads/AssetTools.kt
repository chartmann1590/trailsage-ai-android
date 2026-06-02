package com.charles.trailsage.downloads

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.charles.trailsage.domain.AssetType
import com.charles.trailsage.domain.RequiredAsset
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object AssetVerificationManager {
    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun verify(file: File, checksumSha256: String) =
        file.exists() && file.inputStream().use(::sha256).equals(checksumSha256, ignoreCase = true)
}

object AssetManifestParser {
    fun parse(json: String): List<RequiredAsset> {
        val root = org.json.JSONObject(json)
        val array = root.optJSONArray("assets") ?: JSONArray()
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            RequiredAsset(
                id = item.getString("id"), name = item.getString("name"),
                type = AssetType.valueOf(item.getString("type")), version = item.getString("version"),
                downloadUrl = item.getString("downloadUrl"), localPath = item.getString("localPath"),
                sizeBytes = item.getLong("sizeBytes"), checksumSha256 = item.getString("checksumSha256"),
                required = item.optBoolean("required", true), license = item.optString("license"),
                attribution = item.optString("attribution"), minAndroidSdk = item.optInt("minAndroidSdk", 26),
                recommendedRamMb = item.optInt("recommendedRamMb"), engine = item.optString("engine")
            )
        }
    }
}

class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val url = inputData.getString("url") ?: return Result.failure()
        val relativePath = inputData.getString("localPath") ?: return Result.failure()
        val checksum = inputData.getString("sha256") ?: return Result.failure()
        val target = File(applicationContext.filesDir, relativePath).apply { parentFile?.mkdirs() }
        val partial = File(target.path + ".partial")
        val offset = if (partial.exists()) partial.length() else 0L
        val connection = URL(url).openConnection() as HttpURLConnection
        if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
        connection.connect()
        if (connection.responseCode !in listOf(200, 206)) return Result.retry()
        FileOutputStream(partial, offset > 0 && connection.responseCode == 206).buffered().use { output ->
            connection.inputStream.use { it.copyTo(output) }
        }
        if (!AssetVerificationManager.verify(partial, checksum)) return Result.failure()
        if (!partial.renameTo(target)) return Result.failure()
        return Result.success()
    }
}
