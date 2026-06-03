package com.charles.trailsage

import com.charles.trailsage.data.local.UserSettingsEntity
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class AdSystemTest {

    private fun isSameDay(time1: Long, time2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = time1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = time2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    // Pure logic simulation of day-change resets in Settings flow
    private fun checkAndResetDailyLimits(settings: UserSettingsEntity, now: Long): UserSettingsEntity {
        return if (!isSameDay(settings.lastAdResetTimestamp, now)) {
            settings.copy(
                adsWatchedToday = 0,
                creditsSpentToday = 0,
                lastAdResetTimestamp = now
            )
        } else {
            settings
        }
    }

    // Pure logic simulation of watching a rewarded ad
    private fun watchRewardedAd(settings: UserSettingsEntity, now: Long): Pair<UserSettingsEntity, Boolean> {
        val s = checkAndResetDailyLimits(settings, now)
        return if (s.adsWatchedToday < 6) {
            Pair(
                s.copy(
                    adsWatchedToday = s.adsWatchedToday + 1,
                    credits = s.credits + 1
                ),
                true
            )
        } else {
            Pair(s, false)
        }
    }

    // Pure logic simulation of spending a credit
    private fun spendCredit(settings: UserSettingsEntity, now: Long): Pair<UserSettingsEntity, Boolean> {
        val s = checkAndResetDailyLimits(settings, now)
        if (s.credits <= 0 || s.creditsSpentToday >= 6) {
            return Pair(s, false)
        }
        val currentAdFreeUntil = s.adFreeUntil
        val newAdFreeUntil = maxOf(now, currentAdFreeUntil) + (40 * 60 * 1000)
        return Pair(
            s.copy(
                credits = s.credits - 1,
                creditsSpentToday = s.creditsSpentToday + 1,
                adFreeUntil = newAdFreeUntil
            ),
            true
        )
    }

    @Test
    fun testDayChangeResetsDailyCounters() {
        val initialTime = System.currentTimeMillis()
        val nextDayTime = initialTime + (24 * 60 * 60 * 1000) + 1000 // tomorrow

        val settings = UserSettingsEntity(
            adsWatchedToday = 4,
            creditsSpentToday = 3,
            lastAdResetTimestamp = initialTime
        )

        // Same day check: shouldn't reset
        val sameDayResult = checkAndResetDailyLimits(settings, initialTime + 1000)
        assertEquals(4, sameDayResult.adsWatchedToday)
        assertEquals(3, sameDayResult.creditsSpentToday)

        // Next day check: should reset
        val nextDayResult = checkAndResetDailyLimits(settings, nextDayTime)
        assertEquals(0, nextDayResult.adsWatchedToday)
        assertEquals(0, nextDayResult.creditsSpentToday)
        assertEquals(nextDayTime, nextDayResult.lastAdResetTimestamp)
    }

    @Test
    fun testEarnCreditsLimit() {
        val now = System.currentTimeMillis()
        var settings = UserSettingsEntity(lastAdResetTimestamp = now)

        // Earn 6 credits
        for (i in 1..6) {
            val (updatedSettings, success) = watchRewardedAd(settings, now)
            assertTrue("Expected ad watch $i to be successful", success)
            settings = updatedSettings
            assertEquals(i, settings.credits)
            assertEquals(i, settings.adsWatchedToday)
        }

        // Try to earn a 7th credit - should fail
        val (finalSettings, success) = watchRewardedAd(settings, now)
        assertFalse("Expected 7th ad watch to fail due to daily limit", success)
        assertEquals(6, finalSettings.credits)
        assertEquals(6, finalSettings.adsWatchedToday)
    }

    @Test
    fun testSpendCreditsLimitAndExtension() {
        val now = System.currentTimeMillis()
        var settings = UserSettingsEntity(
            credits = 10,
            lastAdResetTimestamp = now
        )

        // Spend 6 credits
        for (i in 1..6) {
            val (updatedSettings, success) = spendCredit(settings, now)
            assertTrue("Expected spend $i to be successful", success)
            settings = updatedSettings
            assertEquals(10 - i, settings.credits)
            assertEquals(i, settings.creditsSpentToday)
            
            // Each spend adds 40 minutes
            val expectedDuration = i * 40 * 60 * 1000L
            assertEquals(now + expectedDuration, settings.adFreeUntil)
        }

        // Try to spend a 7th credit - should fail
        val (finalSettings, success) = spendCredit(settings, now)
        assertFalse("Expected 7th spend to fail due to daily limit", success)
        assertEquals(4, finalSettings.credits)
        assertEquals(6, finalSettings.creditsSpentToday)
        assertEquals(now + (6 * 40 * 60 * 1000L), finalSettings.adFreeUntil)
    }

    @Test
    fun testSpendCreditsFailsWhenNoCredits() {
        val now = System.currentTimeMillis()
        val settings = UserSettingsEntity(
            credits = 0,
            lastAdResetTimestamp = now
        )

        val (finalSettings, success) = spendCredit(settings, now)
        assertFalse("Expected spend to fail due to 0 credits", success)
        assertEquals(0, finalSettings.credits)
        assertEquals(0, finalSettings.creditsSpentToday)
        assertEquals(0L, finalSettings.adFreeUntil)
    }
}
