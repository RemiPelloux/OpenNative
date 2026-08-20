package app.gamenative.di

import android.content.Context
import app.gamenative.provider.AllDebridClient
import app.gamenative.provider.AllDebridResolver
import app.gamenative.provider.PrefsSecretPersistence
import app.gamenative.provider.ProviderFeedClient
import app.gamenative.provider.ProviderKeystoreCipher
import app.gamenative.provider.ProviderSecretStore
import app.gamenative.provider.StreamingDownloader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class ProviderModule {
    @Provides
    @Singleton
    fun provideProviderSecretStore(@ApplicationContext context: Context): ProviderSecretStore {
        val prefs = context.getSharedPreferences("provider_secrets", Context.MODE_PRIVATE)
        return ProviderSecretStore(
            cipher = ProviderKeystoreCipher.create(),
            persistence = PrefsSecretPersistence(prefs),
        )
    }

    @Provides
    @Singleton
    fun provideProviderFeedClient(): ProviderFeedClient = ProviderFeedClient()

    @Provides
    @Singleton
    fun provideAllDebridResolver(): AllDebridResolver = AllDebridClient()

    @Provides
    @Singleton
    fun provideStreamingDownloader(): StreamingDownloader = StreamingDownloader()

    @Provides
    @Singleton
    @Named("providerStagingRoot")
    fun provideProviderStagingRoot(@ApplicationContext context: Context): File {
        val root = File(context.filesDir, "provider-staging")
        root.mkdirs()
        return root
    }
}
