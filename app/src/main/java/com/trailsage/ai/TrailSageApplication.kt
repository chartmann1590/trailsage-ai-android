package com.charles.trailsage

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp
import com.charles.trailsage.firebase.FirebaseTelemetry
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class TrailSageApplication : Application(), Configuration.Provider, ImageLoaderFactory {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var telemetry: FirebaseTelemetry

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    // Wikimedia returns 403 to requests without a descriptive User-Agent, which silently
    // breaks Coil image loads. Give Coil an OkHttp client that always sends one.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient {
            OkHttpClient.Builder().addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "TrailSageAI/1.0 (offline road-trip tour app; on-device AI)")
                        .build()
                )
            }.build()
        }
        .crossfade(true)
        .build()

    override fun onCreate() {
        super.onCreate()
        // Missing google-services.json is valid for local and CI builds.
        FirebaseApp.initializeApp(this)
        com.google.android.gms.ads.MobileAds.initialize(this) {}
        telemetry.applyConsent(false)
        telemetry.initializeRemoteConfig()
    }
}
