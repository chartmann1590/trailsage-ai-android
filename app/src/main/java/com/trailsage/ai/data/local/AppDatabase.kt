package com.charles.trailsage.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [
    SetupStatusEntity::class, RequiredAssetEntity::class, DownloadedAssetEntity::class, AssetChecksumEntity::class,
    ModelPackEntity::class, TtsEngineEntity::class, VoicePackEntity::class, DestinationEntity::class,
    TourPackEntity::class, RouteEntity::class, RoutePointEntity::class, PoiEntity::class, StoryTriggerEntity::class,
    StoryEntity::class, StorySourceEntity::class, DownloadEntity::class, UserSettingsEntity::class,
    AiChatMessageEntity::class, AppEventEntity::class, NarrationCacheEntity::class
], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() { abstract fun dao(): TrailSageDao }
