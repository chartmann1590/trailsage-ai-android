package com.charles.trailsage.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.WorkManager
import com.charles.trailsage.data.local.AppDatabase
import com.charles.trailsage.data.local.TrailSageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "trailsage.db")
            .addMigrations(
                object : Migration(1, 2) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE downloads ADD COLUMN startedAt INTEGER NOT NULL DEFAULT 0")
                        db.execSQL("UPDATE downloads SET startedAt = updatedAt WHERE startedAt = 0")
                    }
                },
                object : Migration(2, 3) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE user_settings ADD COLUMN adFreeUntil INTEGER NOT NULL DEFAULT 0")
                        db.execSQL("ALTER TABLE user_settings ADD COLUMN credits INTEGER NOT NULL DEFAULT 0")
                        db.execSQL("ALTER TABLE user_settings ADD COLUMN adsWatchedToday INTEGER NOT NULL DEFAULT 0")
                        db.execSQL("ALTER TABLE user_settings ADD COLUMN creditsSpentToday INTEGER NOT NULL DEFAULT 0")
                        db.execSQL("ALTER TABLE user_settings ADD COLUMN lastAdResetTimestamp INTEGER NOT NULL DEFAULT 0")
                    }
                }
            ).build()
    @Provides fun dao(database: AppDatabase): TrailSageDao = database.dao()
    @Provides @Singleton fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
}
