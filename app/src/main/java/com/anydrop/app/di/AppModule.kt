package com.anydrop.app.di

import android.content.Context
import com.anydrop.app.data.repository.CookieRepository
import com.anydrop.app.data.repository.DownloadRepository
import com.anydrop.app.data.repository.DownloadTracker
import com.anydrop.app.data.repository.LibraryRepository
import com.anydrop.app.data.repository.PlaybackProgressRepository
import com.anydrop.app.data.repository.SettingsRepository
import com.anydrop.app.engine.YtDlpEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideYtDlpEngine(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository
    ): YtDlpEngine {
        val engine = YtDlpEngine(context, settingsRepository)
        engine.init()
        return engine
    }

    @Provides
    @Singleton
    fun provideDownloadRepository(
        @ApplicationContext context: Context,
        ytDlpEngine: YtDlpEngine,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): DownloadRepository = DownloadRepository(context, ytDlpEngine, ioDispatcher)

    @Provides
    @Singleton
    fun provideLibraryRepository(
        @ApplicationContext context: Context
    ): LibraryRepository = LibraryRepository(context)

    @Provides
    @Singleton
    fun provideCookieRepository(
        @ApplicationContext context: Context
    ): CookieRepository = CookieRepository(context)

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context
    ): SettingsRepository = SettingsRepository(context)

    @Provides
    @Singleton
    fun providePlaybackProgressRepository(
        @ApplicationContext context: Context
    ): PlaybackProgressRepository = PlaybackProgressRepository(context)

    @Provides
    @Singleton
    fun provideDownloadTracker(
        @ApplicationContext context: Context
    ): DownloadTracker = DownloadTracker(context)

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
