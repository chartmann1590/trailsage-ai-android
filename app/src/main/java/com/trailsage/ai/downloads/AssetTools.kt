package com.charles.trailsage.downloads

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.hilt.work.HiltWorker
import androidx.work.workDataOf
import com.charles.trailsage.data.local.DownloadEntity
import com.charles.trailsage.data.local.TrailSageDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.charles.trailsage.domain.AssetType
import com.charles.trailsage.domain.RequiredAsset
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.IOException
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

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: TrailSageDao
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        downloadAsset()
    } catch (error: Throwable) {
        val assetId = inputData.getString("assetId")
        if (assetId != null) {
            val existing = dao.download(assetId)
            val retryable = error is IOException
            dao.upsertDownload(existing.toRetryState(assetId, id.toString(), error.visibleMessage(), retryable))
            Log.e(TAG, "Download failed for $assetId", error)
            if (retryable) Result.retry() else Result.failure()
        } else {
            Log.e(TAG, "Download failed without an asset id", error)
            Result.failure()
        }
    }

    private suspend fun downloadAsset(): Result {
        val assetId = inputData.getString("assetId") ?: return Result.failure()
        val url = inputData.getString("url") ?: return Result.failure()
        val relativePath = inputData.getString("localPath") ?: return Result.failure()
        val checksum = inputData.getString("sha256") ?: return Result.failure()
        val target = File(applicationContext.filesDir, relativePath).apply { parentFile?.mkdirs() }
        val partial = File(target.path + ".partial")
        // Already fully downloaded & valid? Never re-download — just (re)install it.
        if (target.isFile && AssetVerificationManager.verify(target, checksum)) {
            partial.delete()
            val startedNow = dao.download(assetId)?.startedAt?.takeIf { it > 0 } ?: System.currentTimeMillis()
            return finishInstall(assetId, target, startedNow)
        }
        val offset = if (partial.exists()) partial.length() else 0L
        val existing = dao.download(assetId)
        val startedAt = existing?.startedAt?.takeIf { it > 0 } ?: System.currentTimeMillis()
        dao.upsertDownload(DownloadEntity(assetId, assetId, "CONNECTING", bytesDownloaded = offset, totalBytes = existing?.totalBytes ?: 0, workId = id.toString(), startedAt = startedAt))

        val connection = openFollowingRedirects(url, offset)
        if (connection.responseCode == HTTP_RANGE_NOT_SATISFIABLE) {
            if (AssetVerificationManager.verify(partial, checksum) && partial.renameTo(target)) {
                return finishInstall(assetId, target, startedAt)
            }
            partial.delete()
            dao.upsertDownload(DownloadEntity(assetId, assetId, "RETRYING", error = "Server rejected resume; restarting download", workId = id.toString(), startedAt = startedAt))
            return Result.retry()
        }
        if (connection.responseCode !in listOf(HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_PARTIAL)) {
            val message = "HTTP ${connection.responseCode} ${connection.responseMessage ?: ""}".trim()
            dao.upsertDownload(DownloadEntity(assetId, assetId, "RETRYING", bytesDownloaded = offset, totalBytes = existing?.totalBytes ?: 0, workId = id.toString(), error = message, startedAt = startedAt))
            Log.w(TAG, "Download retry for $assetId: $message")
            return Result.retry()
        }
        val totalBytes = if (connection.contentLengthLong > 0) connection.contentLengthLong + offset else existing?.totalBytes ?: 0
        dao.upsertDownload(DownloadEntity(assetId, assetId, "DOWNLOADING", bytesDownloaded = offset, totalBytes = totalBytes, workId = id.toString(), startedAt = startedAt))
        FileOutputStream(partial, offset > 0 && connection.responseCode == 206).buffered().use { output ->
            connection.inputStream.use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = offset
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    total += count
                    setProgress(workDataOf("bytesDownloaded" to total))
                    dao.upsertDownload(DownloadEntity(assetId, assetId, "DOWNLOADING", bytesDownloaded = total, totalBytes = totalBytes, workId = id.toString(), startedAt = startedAt))
                }
            }
        }
        if (!AssetVerificationManager.verify(partial, checksum)) {
            dao.upsertDownload(DownloadEntity(assetId, assetId, "FAILED", error = "SHA-256 verification failed", startedAt = startedAt))
            return Result.failure()
        }
        if (!partial.renameTo(target)) {
            dao.upsertDownload(DownloadEntity(assetId, assetId, "FAILED", bytesDownloaded = partial.length(), totalBytes = totalBytes, workId = id.toString(), error = "Could not move verified download into private storage", startedAt = startedAt))
            return Result.failure()
        }
        return finishInstall(assetId, target, startedAt)
    }

    private suspend fun finishInstall(assetId: String, target: File, startedAt: Long): Result {
        if (assetId == "en-us-libritts-r-medium") ArchiveExtractor.extractTarBz2(target, File(applicationContext.filesDir, "voices/en-us-libritts-r-medium"))
        dao.markAsset(assetId, installed = true, verified = true)
        dao.upsertDownload(DownloadEntity(assetId, assetId, "COMPLETE", bytesDownloaded = target.length(), totalBytes = target.length(), workId = id.toString(), startedAt = startedAt))
        Log.i(TAG, "Download complete for $assetId: ${target.length()} bytes")
        return Result.success()
    }

    private fun DownloadEntity?.toRetryState(assetId: String, workId: String, message: String, retryable: Boolean): DownloadEntity {
        val status = if (retryable) "RETRYING" else "FAILED"
        return DownloadEntity(
            id = assetId,
            assetId = assetId,
            status = status,
            bytesDownloaded = this?.bytesDownloaded ?: 0,
            totalBytes = this?.totalBytes ?: 0,
            workId = workId,
            error = message,
            startedAt = this?.startedAt?.takeIf { it > 0 } ?: System.currentTimeMillis()
        )
    }

    private fun Throwable.visibleMessage() = "${javaClass.simpleName}: ${message ?: "download failed"}"

    /**
     * Follows HTTP redirects manually, re-attaching the Range header on every hop. Java's
     * HttpURLConnection drops custom headers across cross-host redirects (e.g. HuggingFace ->
     * its CDN), which would silently restart a multi-GB download from zero instead of resuming.
     */
    private fun openFollowingRedirects(initialUrl: String, offset: Long): HttpURLConnection {
        var current = initialUrl
        repeat(MAX_REDIRECTS) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "TrailSageAI/${applicationContext.packageName}")
                setRequestProperty("Accept-Encoding", "identity")
                if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
            }
            connection.connect()
            if (connection.responseCode in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                requireNotNull(location) { "Redirect ${connection.responseCode} without Location header" }
                current = URL(URL(current), location).toString()
                return@repeat
            }
            return connection
        }
        throw IOException("Too many redirects while downloading $initialUrl")
    }

    companion object {
        private const val TAG = "TrailSageDownload"
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
        private const val MAX_REDIRECTS = 8
    }
}
