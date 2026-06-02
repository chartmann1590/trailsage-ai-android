package com.charles.trailsage.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TrailSageDao {
    @Query("SELECT * FROM setup_status WHERE id = 1") fun observeSetup(): Flow<SetupStatusEntity?>
    @Query("SELECT * FROM setup_status WHERE id = 1") suspend fun setup(): SetupStatusEntity?
    @Upsert suspend fun upsertSetup(value: SetupStatusEntity)
    @Query("SELECT * FROM required_assets ORDER BY type, name") fun observeAssets(): Flow<List<RequiredAssetEntity>>
    @Query("SELECT * FROM required_assets ORDER BY type, name") suspend fun assets(): List<RequiredAssetEntity>
    @Upsert suspend fun upsertAssets(values: List<RequiredAssetEntity>)
    @Query("UPDATE required_assets SET installed=:installed, verified=:verified, demoPlaceholder=:demo, updatedAt=:now WHERE id=:id")
    suspend fun markAsset(id: String, installed: Boolean, verified: Boolean, demo: Boolean = false, now: Long = System.currentTimeMillis())
    @Query("SELECT * FROM downloads ORDER BY updatedAt DESC") fun observeDownloads(): Flow<List<DownloadEntity>>
    @Upsert suspend fun upsertDownload(value: DownloadEntity)
    @Query("SELECT * FROM user_settings WHERE id = 1") fun observeSettings(): Flow<UserSettingsEntity?>
    @Query("SELECT * FROM user_settings WHERE id = 1") suspend fun settings(): UserSettingsEntity?
    @Upsert suspend fun upsertSettings(value: UserSettingsEntity)
    @Query("SELECT * FROM destinations ORDER BY name") fun observeDestinations(): Flow<List<DestinationEntity>>
    @Query("SELECT * FROM pois WHERE tourPackId=:tourId ORDER BY name") fun observePois(tourId: String): Flow<List<PoiEntity>>
    @Query("SELECT * FROM stories WHERE tourPackId=:tourId ORDER BY title") fun observeStories(tourId: String): Flow<List<StoryEntity>>
    @Query("SELECT * FROM story_sources ORDER BY title") fun observeSources(): Flow<List<StorySourceEntity>>
    @Upsert suspend fun upsertDestination(value: DestinationEntity)
    @Upsert suspend fun upsertTour(value: TourPackEntity)
    @Upsert suspend fun upsertRoute(value: RouteEntity)
    @Upsert suspend fun upsertPois(values: List<PoiEntity>)
    @Upsert suspend fun upsertTriggers(values: List<StoryTriggerEntity>)
    @Upsert suspend fun upsertStories(values: List<StoryEntity>)
    @Upsert suspend fun upsertSources(values: List<StorySourceEntity>)
    @Query("SELECT * FROM voice_packs ORDER BY selected DESC, name") fun observeVoices(): Flow<List<VoicePackEntity>>
    @Upsert suspend fun upsertVoice(value: VoicePackEntity)
    @Query("UPDATE voice_packs SET selected = CASE WHEN id=:id THEN 1 ELSE 0 END") suspend fun selectVoice(id: String)
    @Query("DELETE FROM setup_status") suspend fun clearSetup()
}

