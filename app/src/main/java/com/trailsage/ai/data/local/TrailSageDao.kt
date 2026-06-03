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
    @Query("SELECT * FROM downloads WHERE assetId = :assetId LIMIT 1") suspend fun download(assetId: String): DownloadEntity?
    @Upsert suspend fun upsertDownload(value: DownloadEntity)
    @Query("SELECT * FROM user_settings WHERE id = 1") fun observeSettings(): Flow<UserSettingsEntity?>
    @Query("SELECT * FROM user_settings WHERE id = 1") suspend fun settings(): UserSettingsEntity?
    @Upsert suspend fun upsertSettings(value: UserSettingsEntity)
    @Query("SELECT * FROM destinations ORDER BY name") fun observeDestinations(): Flow<List<DestinationEntity>>
    @Query("SELECT * FROM pois WHERE tourPackId=:tourId ORDER BY name") fun observePois(tourId: String): Flow<List<PoiEntity>>
    @Query("SELECT * FROM pois WHERE tourPackId=:tourId") suspend fun pois(tourId: String): List<PoiEntity>
    @Query("SELECT * FROM stories WHERE tourPackId=:tourId ORDER BY title") fun observeStories(tourId: String): Flow<List<StoryEntity>>
    @Query("SELECT * FROM stories WHERE id=:id LIMIT 1") suspend fun story(id: String): StoryEntity?
    @Query("SELECT * FROM stories WHERE tourPackId=:tourId ORDER BY createdAt") suspend fun storiesOf(tourId: String): List<StoryEntity>
    @Query("UPDATE stories SET imageLocalPath=:url WHERE id=:id") suspend fun setStoryImage(id: String, url: String)
    @Query("UPDATE pois SET imageLocalPath=:url WHERE tourPackId=:tourId AND name=:name") suspend fun setPoiImageByName(tourId: String, name: String, url: String)
    @Query("SELECT * FROM story_triggers WHERE tourPackId=:tourId") suspend fun triggers(tourId: String): List<StoryTriggerEntity>
    @Query("SELECT * FROM story_triggers WHERE storyId=:storyId LIMIT 1") suspend fun triggerForStory(storyId: String): StoryTriggerEntity?
    @Query("SELECT * FROM routes WHERE tourPackId=:tourId LIMIT 1") suspend fun route(tourId: String): RouteEntity?
    @Query("SELECT * FROM destinations WHERE id=:id LIMIT 1") suspend fun destination(id: String): DestinationEntity?
    @Query("SELECT * FROM story_sources ORDER BY title") fun observeSources(): Flow<List<StorySourceEntity>>
    @Query("SELECT * FROM story_sources") suspend fun allSources(): List<StorySourceEntity>
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
    @Query("SELECT * FROM voice_packs WHERE id=:id LIMIT 1") suspend fun voice(id: String): VoicePackEntity?
    @Query("SELECT * FROM ai_chat_messages ORDER BY createdAt") fun observeChatMessages(): Flow<List<AiChatMessageEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertChatMessage(value: AiChatMessageEntity)
    @Query("DELETE FROM ai_chat_messages") suspend fun clearChat()
    @Query("DELETE FROM setup_status") suspend fun clearSetup()
}
