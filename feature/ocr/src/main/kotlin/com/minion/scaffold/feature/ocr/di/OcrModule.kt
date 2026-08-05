package com.minion.scaffold.feature.ocr.di

import com.minion.scaffold.feature.ocr.data.MlKitTextRecognizer
import com.minion.scaffold.feature.ocr.data.TextRecognizer
import com.minion.scaffold.feature.ocr.data.local.OcrPreferencesDataStore
import com.minion.scaffold.feature.ocr.domain.OcrPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** This feature's bindings, `internal` and living beside the implementations they bind. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class OcrModule {

    @Binds
    @Singleton
    abstract fun bindTextRecognizer(recognizer: MlKitTextRecognizer): TextRecognizer

    /**
     * `@Singleton` matters here beyond the usual reasons: DataStore throws if a second instance is
     * created for the same file within one process, and a non-scoped binding would build a new
     * wrapper — though not a new `DataStore`, since the delegate caches per `Context` — on every
     * injection.
     */
    @Binds
    @Singleton
    abstract fun bindOcrPreferencesRepository(
        impl: OcrPreferencesDataStore,
    ): OcrPreferencesRepository
}
