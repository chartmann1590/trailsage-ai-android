package com.charles.trailsage.data.github

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.charles.trailsage.BuildConfig
import com.charles.trailsage.data.local.TrailSageDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiagnosticsHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TrailSageDao
) {
    suspend fun gatherDiagnostics(includeModels: Boolean): String = withContext(Dispatchers.IO) {
        val brand = Build.BRAND
        val model = Build.MODEL
        val manufacturer = Build.MANUFACTURER
        val androidVersion = Build.VERSION.RELEASE
        val sdkInt = Build.VERSION.SDK_INT
        
        val appVersionName = BuildConfig.VERSION_NAME
        val appVersionCode = BuildConfig.VERSION_CODE
        val locale = Locale.getDefault().toString()

        val filesDir = context.filesDir
        val freeStorageMb = filesDir.usableSpace / (1024 * 1024)
        val totalStorageMb = filesDir.totalSpace / (1024 * 1024)

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val freeRamMb = memoryInfo.availMem / (1024 * 1024)
        val totalRamMb = memoryInfo.totalMem / (1024 * 1024)

        val builder = java.lang.StringBuilder()
        builder.append("### System Diagnostics\n")
        builder.append("- **Device**: $manufacturer $brand $model\n")
        builder.append("- **Android OS**: version $androidVersion (API $sdkInt)\n")
        builder.append("- **App Version**: $appVersionName ($appVersionCode)\n")
        builder.append("- **System Locale**: $locale\n")
        builder.append("- **Storage Info**: ${freeStorageMb}MB free of ${totalStorageMb}MB total\n")
        builder.append("- **Memory Info**: ${freeRamMb}MB available of ${totalRamMb}MB physical RAM\n")

        if (includeModels) {
            builder.append("\n### Configured Models & Engines\n")
            runCatching {
                val assets = dao.assets()
                val modelAssets = assets.filter {
                    it.type == "GEMMA_MODEL" || it.type == "VOICE_PACK" || it.type == "TTS_ENGINE"
                }
                if (modelAssets.isEmpty()) {
                    builder.append("_No models configured in database._\n")
                } else {
                    for (asset in modelAssets) {
                        val location = if (asset.installed) "On-device" else "Remote"
                        builder.append("- **${asset.name}** (${asset.type}): $location\n")
                    }
                }
            }.onFailure {
                builder.append("_Error loading model statuses: ${it.localizedMessage}_\n")
            }
        }
        builder.toString()
    }
}
