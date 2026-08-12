package com.minion.scaffold.core.network.di

import android.content.Context
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.minion.scaffold.core.network.BaseUrl
import com.minion.scaffold.core.network.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * The shared HTTP stack.
 *
 * One [OkHttpClient] and one [Retrofit] for the whole app, because each carries a connection pool
 * and a thread pool — a second instance silently doubles both. Features call
 * `retrofit.create<TheirApi>()` on this instance and keep their `*Api` interface `internal`.
 *
 * Feature-specific concerns do not belong here. An auth interceptor that reads a token store is
 * the usual exception, and it belongs here only once, added to this client.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val TIMEOUT_SECONDS = 30L

    /**
     * The shared Gson instance for JSON (de)serialisation.
     *
     * @return The application-wide [Gson].
     */
    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    /**
     * The HTTP logging interceptor, at BODY level in debug and NONE in release.
     *
     * @return The configured [HttpLoggingInterceptor].
     */
    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            // BODY logs credentials and personal data. It must never reach a release build.
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

    /**
     * Chucker is built here rather than in its own `@Provides`, on purpose.
     *
     * A provided type is referenced by the Hilt component generated in `:app`, which would put
     * Chucker on `:app`'s compile classpath and undo the `debugImplementation` scoping. Kept as a
     * local, it stays an implementation detail of this module.
     *
     * `chucker-noop` replaces the real library in release builds and exposes the same API, so
     * this line compiles and does nothing there — no debug/release source-set split needed.
     *
     * @param context           The application context, for the Chucker interceptor.
     * @param loggingInterceptor The logging interceptor from [provideLoggingInterceptor].
     * @return The application-wide [OkHttpClient].
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        loggingInterceptor: HttpLoggingInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(ChuckerInterceptor.Builder(context).build())
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    /**
     * The shared Retrofit instance features call `create<TheirApi>()` on.
     *
     * @param baseUrl The API base URL, injected via [BaseUrl] from `:app`.
     * @param client  The shared [OkHttpClient].
     * @param gson    The shared [Gson] for the converter factory.
     * @return The application-wide [Retrofit].
     */
    @Provides
    @Singleton
    fun provideRetrofit(
        @BaseUrl baseUrl: String,
        client: OkHttpClient,
        gson: Gson,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
}
