package com.minion.scaffold.feature.ocr.di

import com.minion.scaffold.feature.ocr.data.MlKitTextRecognizer
import com.minion.scaffold.feature.ocr.data.TextRecognizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** This feature's only binding, `internal` and living beside the implementation it binds. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class OcrModule {

    @Binds
    @Singleton
    abstract fun bindTextRecognizer(recognizer: MlKitTextRecognizer): TextRecognizer
}
