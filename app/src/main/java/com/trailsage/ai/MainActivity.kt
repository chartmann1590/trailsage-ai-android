package com.charles.trailsage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.charles.trailsage.ui.TrailSageApp
import com.charles.trailsage.tour.SharedTripImporter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var sharedTripImporter: SharedTripImporter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent { TrailSageApp() }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "trailsage" && data.host == "trip") {
            val tripId = data.getQueryParameter("t")
            if (!tripId.isNullOrBlank()) {
                sharedTripImporter.setPendingImport(tripId)
            }
        }
    }
}
