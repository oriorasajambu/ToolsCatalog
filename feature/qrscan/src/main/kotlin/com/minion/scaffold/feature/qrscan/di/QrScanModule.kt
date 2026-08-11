package com.minion.scaffold.feature.qrscan.di

import com.minion.scaffold.feature.qrscan.data.ImageBarcodeDecoder
import com.minion.scaffold.feature.qrscan.data.MlKitImageBarcodeDecoder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * This feature's only binding, `internal` and living beside the implementation it binds.
 *
 * Not in a central DI module: that would have to depend on every feature, inverting the direction
 * the module graph exists to enforce.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class QrScanModule {

    /**
     * @param decoder The ML Kit-backed implementation.
     * @return The [ImageBarcodeDecoder] binding.
     */
    @Binds
    @Singleton
    abstract fun bindImageBarcodeDecoder(
        decoder: MlKitImageBarcodeDecoder,
    ): ImageBarcodeDecoder
}
