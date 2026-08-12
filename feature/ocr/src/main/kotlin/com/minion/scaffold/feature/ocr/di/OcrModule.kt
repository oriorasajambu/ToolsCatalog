package com.minion.scaffold.feature.ocr.di

import com.minion.scaffold.feature.ocr.data.SelectingTextRecognizer
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

    /**
     * The dispatcher, not a concrete engine: which one runs is a runtime preference now, so the
     * choice cannot be made here at graph-construction time.
     *
     * @param recognizer The engine-selecting implementation.
     * @return The [TextRecognizer] binding.
     */
    @Binds
    @Singleton
    abstract fun bindTextRecognizer(recognizer: SelectingTextRecognizer): TextRecognizer

    /**
     * `@Singleton` matters here beyond the usual reasons: DataStore throws if a second instance is
     * created for the same file within one process, and a non-scoped binding would build a new
     * wrapper — though not a new `DataStore`, since the delegate caches per `Context` — on every
     * injection.
     *
     * @param impl The DataStore-backed implementation.
     * @return The [OcrPreferencesRepository] binding.
     */
    @Binds
    @Singleton
    abstract fun bindOcrPreferencesRepository(
        impl: OcrPreferencesDataStore,
    ): OcrPreferencesRepository
}
