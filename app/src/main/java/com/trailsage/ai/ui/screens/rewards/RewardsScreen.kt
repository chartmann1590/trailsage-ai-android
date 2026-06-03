package com.charles.trailsage.ui.screens.rewards

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charles.trailsage.ui.AppViewModel
import com.charles.trailsage.ui.components.*
import kotlinx.coroutines.delay

@Composable
fun RewardsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val settings by vm.settings.collectAsStateWithLifecycle()

    var remainingTimeStr by remember(settings) {
        mutableStateOf(getRemainingTimeStr(settings?.adFreeUntil ?: 0L))
    }

    LaunchedEffect(settings?.adFreeUntil) {
        while (true) {
            delay(1000L)
            remainingTimeStr = getRemainingTimeStr(settings?.adFreeUntil ?: 0L)
        }
    }

    DetailScaffold(title = "Rewards & Ad-Free", onBack = onBack) {
        // --- Status Gradient Card ---
        val adFreeUntil = settings?.adFreeUntil ?: 0L
        val isCurrentlyAdFree = System.currentTimeMillis() < adFreeUntil
        
        val gradientBrush = if (isCurrentlyAdFree) {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF0D321A), // Dark Forest
                    Color(0xFF1B5E20)  // Active Green
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF2C1E12), // Charcoal Brown
                    Color(0xFF4E342E)  // Warm Sandstone/Brown
                )
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .background(gradientBrush)
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isCurrentlyAdFree) "AD-FREE ACTIVE" else "ADS ACTIVE",
                        color = if (isCurrentlyAdFree) Color(0xFFFFB84D) else Color(0xFFFFDDB3),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = if (isCurrentlyAdFree) remainingTimeStr else "Enable ad-free browsing by spending credits below.",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // --- Credits Counter Card ---
        val credits = settings?.credits ?: 0
        SurfaceCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Your Credits",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "1 credit = 40 minutes of ad-free time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "$credits",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // --- Earn Credits Section ---
        val adsWatched = settings?.adsWatchedToday ?: 0
        SectionHeader("1. Earn Credits")
        SurfaceCard {
            Text(
                "Watch rewarded interstitial ads to earn credits. You can watch up to 6 ads per day.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            
            // Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Daily Limit Progress", style = MaterialTheme.typography.bodySmall)
                Text("$adsWatched / 6 ads", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { adsWatched / 6f },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            Spacer(Modifier.height(16.dp))

            PrimaryButton(
                text = if (adsWatched >= 6) "Daily Limit Reached" else "Watch Ad (+1 Credit)",
                enabled = adsWatched < 6,
                onClick = {
                    if (activity != null) {
                        vm.watchRewardedAd(
                            activity = activity,
                            onSuccess = {
                                Toast.makeText(context, "Credit earned successfully!", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { error ->
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                }
            )
        }

        // --- Spend Credits Section ---
        val creditsSpent = settings?.creditsSpentToday ?: 0
        SectionHeader("2. Disable Ads")
        SurfaceCard {
            Text(
                "Spend your earned credits to disable all ads. You can spend up to 6 credits (4 hours) per day.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            // Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Daily Spend Progress", style = MaterialTheme.typography.bodySmall)
                Text("$creditsSpent / 6 credits", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { creditsSpent / 6f },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            Spacer(Modifier.height(16.dp))

            PrimaryButton(
                text = if (creditsSpent >= 6) "Daily Limit Reached" else "Spend 1 Credit (-1 Credit / +40m)",
                enabled = creditsSpent < 6 && credits > 0,
                onClick = {
                    vm.spendCredit(
                        onSuccess = {
                            Toast.makeText(context, "Ad-free time extended by 40 minutes!", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { error ->
                            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                        }
                    )
                }
            )
        }
    }
}

private fun getRemainingTimeStr(adFreeUntil: Long): String {
    val diff = adFreeUntil - System.currentTimeMillis()
    if (diff <= 0) return "Ads are active"
    val hours = diff / (3600 * 1000)
    val minutes = (diff % (3600 * 1000)) / (60 * 1000)
    val seconds = (diff % (60 * 1000)) / 1000
    return if (hours > 0) {
        String.format("%d hours %02d minutes %02d seconds remaining", hours, minutes, seconds)
    } else if (minutes > 0) {
        String.format("%d minutes %02d seconds remaining", minutes, seconds)
    } else {
        String.format("%d seconds remaining", seconds)
    }
}
