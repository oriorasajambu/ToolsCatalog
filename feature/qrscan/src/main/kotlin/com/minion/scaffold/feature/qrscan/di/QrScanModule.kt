package com.minion.scaffold.feature.qrscan.di

import com.minion.scaffold.feature.qrscan.data.AndroidSchemaDocumentReader
import com.minion.scaffold.feature.qrscan.data.ImageBarcodeDecoder
import com.minion.scaffold.feature.qrscan.data.SchemaDocumentReader
import com.minion.scaffold.feature.qrscan.data.MlKitImageBarcodeDecoder
import com.minion.scaffold.feature.qrscan.data.local.PaymentSchemaDataStore
import com.minion.scaffold.feature.qrscan.domain.export.PaymentSchemaRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * This feature's bindings, `internal` and living beside the implementations they bind.
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

    /**
     * @param dataStore The DataStore-backed implementation.
     * @return The [PaymentSchemaRepository] binding.
     */
    @Binds
    @Singleton
    abstract fun bindPaymentSchemaRepository(
        dataStore: PaymentSchemaDataStore,
    ): PaymentSchemaRepository

    /**
     * @param reader The Storage Access Framework implementation.
     * @return The [SchemaDocumentReader] binding.
     */
    @Binds
    @Singleton
    abstract fun bindSchemaDocumentReader(
        reader: AndroidSchemaDocumentReader,
    ): SchemaDocumentReader
}
