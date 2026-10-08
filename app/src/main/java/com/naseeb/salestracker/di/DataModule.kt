package com.naseeb.salestracker.di

import android.content.Context
import com.naseeb.salestracker.data.repo.FileSalesRepository
import com.naseeb.salestracker.data.repo.FileSettingsRepository
import com.naseeb.salestracker.data.repo.SalesRepository
import com.naseeb.salestracker.data.repo.SettingsRepository
import com.naseeb.salestracker.data.store.AccessibleFileStore
import com.naseeb.salestracker.data.store.FileStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Named("cpu")
    fun provideCpuDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    fun provideSalesFileStore(@ApplicationContext context: Context): FileStore {
        return AccessibleFileStore(context, Dispatchers.IO, fileName = "sales_data.json", legacyFileName = "sales_data.jsonl")
    }

    @Provides
    @Singleton
    @Named("settings")
    fun provideSettingsFileStore(@ApplicationContext context: Context): FileStore {
        return AccessibleFileStore(context, Dispatchers.IO, fileName = "app_settings.json", legacyFileName = "app_settings.json")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindingModule {

    @Binds
    @Singleton
    abstract fun bindSalesRepository(impl: FileSalesRepository): SalesRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: FileSettingsRepository): SettingsRepository
}
