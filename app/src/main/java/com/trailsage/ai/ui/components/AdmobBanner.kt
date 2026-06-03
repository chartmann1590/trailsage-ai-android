package com.charles.trailsage.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charles.trailsage.BuildConfig
import com.charles.trailsage.ui.AppViewModel
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

@Composable
fun AdmobBanner(
    modifier: Modifier = Modifier,
    applyNavigationInsets: Boolean = false,
    vm: AppViewModel = hiltViewModel()
) {
    val settings by vm.settings.collectAsStateWithLifecycle()

    val isAdFree = remember(settings) {
        val until = settings?.adFreeUntil ?: 0L
        System.currentTimeMillis() < until
    }

    if (isAdFree) {
        return
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .then(if (applyNavigationInsets) Modifier.navigationBarsPadding() else Modifier)
            .height(50.dp)
            .background(MaterialTheme.colorScheme.surface),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = BuildConfig.ADMOB_BANNER_ID.ifEmpty {
                    "ca-app-pub-3940256099942544/6300978111" // Test banner ID
                }
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
