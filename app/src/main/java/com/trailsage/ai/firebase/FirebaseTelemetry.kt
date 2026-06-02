package com.charles.trailsage.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.charles.trailsage.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseTelemetry @Inject constructor(@ApplicationContext private val context: Context) {
    fun initializeRemoteConfig() {
        if (FirebaseApp.getApps(context).isEmpty()) return
        FirebaseRemoteConfig.getInstance().setDefaultsAsync(R.xml.remote_config_defaults)
        FirebaseRemoteConfig.getInstance().fetchAndActivate()
    }
    fun applyConsent(enabled: Boolean) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(enabled)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
        FirebasePerformance.getInstance().isPerformanceCollectionEnabled = enabled
    }
    fun event(name: String) {
        if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseAnalytics.getInstance(context).logEvent(name, null)
    }
}
