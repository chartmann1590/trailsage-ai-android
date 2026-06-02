package com.charles.trailsage

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp
import com.charles.trailsage.firebase.FirebaseTelemetry
import javax.inject.Inject

@HiltAndroidApp
class TrailSageApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var telemetry: FirebaseTelemetry

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Missing google-services.json is valid for local and CI builds.
        FirebaseApp.initializeApp(this)
        telemetry.applyConsent(false)
        telemetry.initializeRemoteConfig()
    }
}
