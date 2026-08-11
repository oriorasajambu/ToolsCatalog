package com.minion.scaffold.feature.exifstrip.di

import com.minion.scaffold.feature.exifstrip.data.AndroidCleanCopyExporter
import com.minion.scaffold.feature.exifstrip.data.AndroidPhotoInspector
import com.minion.scaffold.feature.exifstrip.data.local.ExifStripPreferencesDataStore
import com.minion.scaffold.feature.exifstrip.domain.CleanCopyExporter
import com.minion.scaffold.feature.exifstrip.domain.ExifStripPreferencesRepository
import com.minion.scaffold.feature.exifstrip.domain.PhotoInspector
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** This feature's bindings, `internal` and living beside the implementations they bind. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class ExifStripModule {

    /**
     * @param impl The Android-backed implementation.
     * @return The [PhotoInspector] binding.
     */
    @Binds
    abstract fun bindPhotoInspector(impl: AndroidPhotoInspector): PhotoInspector

    /**
     * `@Singleton` so there is one thing in the process writing to the working directory.
     *
     * The exporter clears that directory before each write, so two instances racing would be able to
     * delete a file the other had just handed to a share target.
     *
     * @param impl The Android-backed implementation.
     * @return The [CleanCopyExporter] binding.
     */
    @Binds
    @Singleton
    abstract fun bindCleanCopyExporter(impl: AndroidCleanCopyExporter): CleanCopyExporter

    /**
     * `@Singleton` matters beyond the usual reasons: DataStore throws if a second instance is
     * created for the same file within one process.
     *
     * @param impl The DataStore-backed implementation.
     * @return The [ExifStripPreferencesRepository] binding.
     */
    @Binds
    @Singleton
    abstract fun bindExifStripPreferencesRepository(
        impl: ExifStripPreferencesDataStore,
    ): ExifStripPreferencesRepository
}
