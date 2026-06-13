package com.mapsupervision.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mapsupervision.data.db.MapSupervisionDatabase
import com.mapsupervision.data.db.dao.DailyLogDao
import com.mapsupervision.data.db.dao.GisNodeDao
import com.mapsupervision.data.db.dao.GisRouteDao
import com.mapsupervision.data.db.dao.AiDecisionCacheDao
import com.mapsupervision.data.db.dao.ChatHistoryDao
import com.mapsupervision.data.db.dao.MaterialProgressDao
import com.mapsupervision.data.db.dao.NodeProgressDao
import com.mapsupervision.data.db.dao.NoteDao
import com.mapsupervision.data.db.dao.TaskDao
import com.mapsupervision.data.db.dao.ProjectDao
import com.mapsupervision.data.db.dao.SitePhotoDao
import com.mapsupervision.data.db.dao.WorkCategoryDao
import com.mapsupervision.data.repository.DailyLogRepositoryImpl
import com.mapsupervision.data.repository.AiDecisionCacheStoreImpl
import com.mapsupervision.data.repository.ChatHistoryRepositoryImpl
import com.mapsupervision.data.repository.GisRepositoryImpl
import com.mapsupervision.data.repository.MaterialProgressRepositoryImpl
import com.mapsupervision.data.repository.PhotoRepositoryImpl
import com.mapsupervision.data.repository.ProgressRepositoryImpl
import com.mapsupervision.data.repository.ProjectRepositoryImpl
import com.mapsupervision.data.repository.NoteRepositoryImpl
import com.mapsupervision.data.repository.TaskRepositoryImpl
import com.mapsupervision.data.repository.WorkCategoryRepositoryImpl
import com.mapsupervision.domain.repository.DailyLogRepository
import com.mapsupervision.domain.repository.ChatHistoryRepository
import com.mapsupervision.domain.repository.GisRepository
import com.mapsupervision.domain.repository.MaterialProgressRepository
import com.mapsupervision.domain.repository.PhotoRepository
import com.mapsupervision.domain.repository.ProgressRepository
import com.mapsupervision.domain.repository.ProjectRepository
import com.mapsupervision.domain.repository.NoteRepository
import com.mapsupervision.domain.repository.TaskRepository
import com.mapsupervision.domain.repository.WorkCategoryRepository
import com.mapsupervision.data.db.dao.ReportDraftDao
import com.mapsupervision.data.repository.ReportDraftRepositoryImpl
import com.mapsupervision.domain.repository.ReportDraftRepository
import com.mapsupervision.domain.service.WeatherService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MapSupervisionDatabase =
        Room.databaseBuilder(context, MapSupervisionDatabase::class.java, "mapsupervision.db")
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.execSQL("PRAGMA foreign_keys = ON")
                    db.execSQL("PRAGMA synchronous = NORMAL")
                    db.execSQL("PRAGMA temp_store = MEMORY")
                }
            })
            .addMigrations(
                MapSupervisionDatabase.MIGRATION_8_9,
                MapSupervisionDatabase.MIGRATION_9_10,
                MapSupervisionDatabase.MIGRATION_10_11,
                MapSupervisionDatabase.MIGRATION_11_12,
                MapSupervisionDatabase.MIGRATION_12_13,
                MapSupervisionDatabase.MIGRATION_13_14,
                MapSupervisionDatabase.MIGRATION_14_15,
                MapSupervisionDatabase.MIGRATION_15_16,
                MapSupervisionDatabase.MIGRATION_16_17,
                MapSupervisionDatabase.MIGRATION_17_18,
                MapSupervisionDatabase.MIGRATION_18_19,
                MapSupervisionDatabase.MIGRATION_19_20,
                MapSupervisionDatabase.MIGRATION_20_21,
                MapSupervisionDatabase.MIGRATION_21_22,
                MapSupervisionDatabase.MIGRATION_22_23,
                MapSupervisionDatabase.MIGRATION_23_24
            )
            .build()

    @Provides
    fun provideProjectDao(db: MapSupervisionDatabase): ProjectDao = db.projectDao()

    @Provides
    fun provideNodeProgressDao(db: MapSupervisionDatabase): NodeProgressDao = db.nodeProgressDao()

    @Provides
    fun provideSitePhotoDao(db: MapSupervisionDatabase): SitePhotoDao = db.sitePhotoDao()

    @Provides
    fun provideDailyLogDao(db: MapSupervisionDatabase): DailyLogDao = db.dailyLogDao()

    @Provides
    fun provideGisNodeDao(db: MapSupervisionDatabase): GisNodeDao = db.gisNodeDao()

    @Provides
    fun provideGisRouteDao(db: MapSupervisionDatabase): GisRouteDao = db.gisRouteDao()

    @Provides
    fun provideImportedFileDao(db: MapSupervisionDatabase): com.mapsupervision.data.db.dao.ImportedFileDao = db.importedFileDao()

    @Provides
    fun provideMaterialProgressDao(db: MapSupervisionDatabase): MaterialProgressDao = db.materialProgressDao()

    @Provides
    fun provideNoteDao(db: MapSupervisionDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideTaskDao(db: MapSupervisionDatabase): TaskDao = db.taskDao()

    @Provides
    fun provideWorkCategoryDao(db: MapSupervisionDatabase): WorkCategoryDao = db.workCategoryDao()

    @Provides
    fun provideAiDecisionCacheDao(db: MapSupervisionDatabase): AiDecisionCacheDao = db.aiDecisionCacheDao()

    @Provides
    fun provideChatHistoryDao(db: MapSupervisionDatabase): ChatHistoryDao = db.chatHistoryDao()

    @Provides
    fun provideReportDraftDao(db: MapSupervisionDatabase): ReportDraftDao = db.reportDraftDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindModule {
    @Binds
    abstract fun bindProjectRepository(impl: ProjectRepositoryImpl): ProjectRepository

    @Binds
    abstract fun bindProgressRepository(impl: ProgressRepositoryImpl): ProgressRepository

    @Binds
    abstract fun bindPhotoRepository(impl: PhotoRepositoryImpl): PhotoRepository

    @Binds
    abstract fun bindDailyLogRepository(impl: DailyLogRepositoryImpl): DailyLogRepository

    @Binds
    abstract fun bindGisRepository(impl: GisRepositoryImpl): GisRepository

    @Binds
    abstract fun bindImportedFileRepository(impl: com.mapsupervision.data.repository.ImportedFileRepositoryImpl): com.mapsupervision.domain.repository.ImportedFileRepository

    @Binds
    abstract fun bindAiRepository(impl: com.mapsupervision.data.repository.GeminiRepositoryImpl): com.mapsupervision.domain.repository.AiRepository

    @Binds
    abstract fun bindMaterialProgressRepository(impl: MaterialProgressRepositoryImpl): MaterialProgressRepository

    @Binds
    abstract fun bindNoteRepository(impl: NoteRepositoryImpl): NoteRepository

    @Binds
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds
    abstract fun bindWorkCategoryRepository(impl: WorkCategoryRepositoryImpl): WorkCategoryRepository

    @Binds
    abstract fun bindTfLiteRepository(impl: com.mapsupervision.data.tflite.TfLiteRepositoryImpl): com.mapsupervision.domain.repository.TfLiteRepository

    @Binds
    abstract fun bindWeatherService(impl: com.mapsupervision.data.network.WeatherServiceImpl): WeatherService

    @Binds
    abstract fun bindAiDecisionCacheStore(impl: AiDecisionCacheStoreImpl): com.mapsupervision.domain.repository.AiDecisionCacheStore

    @Binds
    abstract fun bindLocalLlmRepository(impl: com.mapsupervision.data.mediapipe.LocalLiteRtRepositoryImpl): com.mapsupervision.domain.repository.LocalLlmRepository

    @Binds
    abstract fun bindChatHistoryRepository(impl: ChatHistoryRepositoryImpl): ChatHistoryRepository

    @Binds
    abstract fun bindReportDraftRepository(impl: ReportDraftRepositoryImpl): ReportDraftRepository
}
