package com.naseeb.salestracker.di

import android.content.Context
import com.naseeb.salestracker.data.repo.FileSalesRepository
import com.naseeb.salestracker.data.repo.SalesRepository
import com.naseeb.salestracker.data.store.AtomicFileStore
import com.naseeb.salestracker.data.store.FileStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideFileStore(@ApplicationContext context: Context): FileStore {
        val file = File(context.filesDir, "sales_data.jsonl")
        return AtomicFileStore(file, Dispatchers.IO)
    }

    @Provides
    @Singleton
    fun provideSalesRepository(fileStore: FileStore): SalesRepository {
        return FileSalesRepository(fileStore)
    }
}
