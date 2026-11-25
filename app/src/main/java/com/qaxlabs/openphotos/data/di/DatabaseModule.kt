package com.qaxlabs.openphotos.data.di

import android.content.Context
import androidx.room.Room
import com.qaxlabs.openphotos.data.db.AlbumDao
import com.qaxlabs.openphotos.data.db.BackupTaskDao
import com.qaxlabs.openphotos.data.db.UnifiedMediaDao
import com.qaxlabs.openphotos.data.db.UploadedItemDao
import com.qaxlabs.openphotos.data.db.VaultDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideVaultDatabase(@ApplicationContext context: Context): VaultDatabase =
        Room.databaseBuilder(
            context,
            VaultDatabase::class.java,
            "vault_db",
        )
            .addMigrations(VaultDatabase.MIGRATION_3_4)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideUploadedItemDao(db: VaultDatabase): UploadedItemDao = db.uploadedItemDao()

    @Provides
    @Singleton
    fun provideUnifiedMediaDao(db: VaultDatabase): UnifiedMediaDao = db.unifiedMediaDao()

    @Provides
    @Singleton
    fun provideBackupTaskDao(db: VaultDatabase): BackupTaskDao = db.backupTaskDao()

    @Provides
    @Singleton
    fun provideAlbumDao(db: VaultDatabase): AlbumDao = db.albumDao()
}
