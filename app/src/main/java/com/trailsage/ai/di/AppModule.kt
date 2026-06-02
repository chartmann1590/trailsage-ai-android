package com.charles.trailsage.di

import android.content.Context
import androidx.room.Room
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
        Room.databaseBuilder(context, AppDatabase::class.java, "trailsage.db").build()
    @Provides fun dao(database: AppDatabase): TrailSageDao = database.dao()
    @Provides @Singleton fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
}
