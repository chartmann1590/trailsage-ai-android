package com.charles.trailsage.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import com.charles.trailsage.BuildConfig
import com.charles.trailsage.data.local.TrailSageDao
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TrailSageDao
) {
    private val scope = CoroutineScope(Dispatchers.Main)
    
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var isInterstitialLoading = false
    private var isRewardedLoading = false

    init {
        // Pre-load ads
        loadInterstitial()
        loadRewarded()
    }

    fun loadInterstitial() {
        if (interstitialAd != null || isInterstitialLoading) return
        
        val adUnitId = BuildConfig.ADMOB_INTERSTITIAL_ID.ifEmpty {
            "ca-app-pub-3940256099942544/1033173712" // Test interstitial ID
        }
        
        isInterstitialLoading = true
        InterstitialAd.load(context, adUnitId, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isInterstitialLoading = false
                    Log.d("AdManager", "Interstitial ad loaded successfully")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isInterstitialLoading = false
                    Log.e("AdManager", "Failed to load interstitial ad: ${error.message}")
                }
            })
    }

    fun showInterstitial(activity: Activity) {
        scope.launch {
            val settings = dao.settings()
            val adFreeUntil = settings?.adFreeUntil ?: 0L
            if (System.currentTimeMillis() < adFreeUntil) {
                Log.d("AdManager", "Ads are disabled, not showing interstitial")
                return@launch
            }

            val ad = interstitialAd
            if (ad != null) {
                ad.show(activity)
                interstitialAd = null
                loadInterstitial()
            } else {
                Log.d("AdManager", "Interstitial ad not ready, reloading")
                loadInterstitial()
            }
        }
    }

    fun loadRewarded() {
        if (rewardedAd != null || isRewardedLoading) return
        
        val adUnitId = BuildConfig.ADMOB_REWARD_ID.ifEmpty {
            "ca-app-pub-3940256099942544/5224354917" // Test rewarded ID
        }
        
        isRewardedLoading = true
        RewardedAd.load(context, adUnitId, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isRewardedLoading = false
                    Log.d("AdManager", "Rewarded ad loaded successfully")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    isRewardedLoading = false
                    Log.e("AdManager", "Failed to load rewarded ad: ${error.message}")
                }
            })
    }

    fun showRewarded(activity: Activity, onRewardEarned: () -> Unit) {
        val ad = rewardedAd
        if (ad != null) {
            ad.show(activity) { rewardItem ->
                Log.d("AdManager", "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                onRewardEarned()
            }
            rewardedAd = null
            loadRewarded()
        } else {
            Log.e("AdManager", "Rewarded ad not ready, reloading")
            loadRewarded()
        }
    }
    
    fun isRewardedAdReady(): Boolean = rewardedAd != null

    fun showRandomInterstitial(activity: Activity, probability: Double = 0.3) {
        scope.launch {
            val settings = dao.settings()
            val adFreeUntil = settings?.adFreeUntil ?: 0L
            if (System.currentTimeMillis() < adFreeUntil) {
                return@launch
            }
            if (Math.random() < probability) {
                showInterstitial(activity)
            }
        }
    }
}

/** Bulletproof helper to find the Activity from a Context */
fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
