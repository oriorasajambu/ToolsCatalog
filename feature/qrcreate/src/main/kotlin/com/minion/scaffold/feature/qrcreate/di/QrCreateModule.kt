package com.minion.scaffold.feature.qrcreate.di

import com.minion.scaffold.feature.qrcreate.data.AndroidQrImageExporter
import com.minion.scaffold.feature.qrcreate.data.QrImageExporter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** This feature's only binding, `internal` and living beside the implementation it binds. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class QrCreateModule {

    /**
     * @param exporter The MediaStore-backed implementation.
     * @return The [QrImageExporter] binding.
     */
    @Binds
    @Singleton
    abstract fun bindQrImageExporter(exporter: AndroidQrImageExporter): QrImageExporter
}
