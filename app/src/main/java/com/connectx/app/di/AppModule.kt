package com.connectx.app.di

import android.content.Context
import androidx.room.Room
import com.connectx.app.data.local.ConnectXDatabase
import com.connectx.app.data.local.dao.ChatDao
import com.connectx.app.data.local.dao.ContactDao
import com.connectx.app.data.local.dao.MessageDao
import com.connectx.app.data.local.preferences.AppPreferencesManager
import com.connectx.app.data.remote.api.ConnectXApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ConnectXDatabase {
        return Room.databaseBuilder(
            context,
            ConnectXDatabase::class.java,
            "connectx_db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideMessageDao(db: ConnectXDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideChatDao(db: ConnectXDatabase): ChatDao = db.chatDao()

    @Provides
    fun provideContactDao(db: ConnectXDatabase): ContactDao = db.contactDao()

    @Provides
    fun provideCallLogDao(db: ConnectXDatabase): com.connectx.app.data.local.dao.CallLogDao = db.callLogDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(prefsManager: AppPreferencesManager): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val tokens = runBlocking { prefsManager.authTokensFlow.first() }
                val requestBuilder = chain.request().newBuilder()
                tokens.accessToken?.let {
                    requestBuilder.addHeader("Authorization", "Bearer $it")
                }
                chain.proceed(requestBuilder.build())
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideConnectXApiService(
        okHttpClient: OkHttpClient,
        prefsManager: AppPreferencesManager
    ): ConnectXApiService {
        val config = runBlocking { prefsManager.appConfigFlow.first() }
        val baseUrl = if (config.apiBaseUrl.endsWith("/")) config.apiBaseUrl else "${config.apiBaseUrl}/"
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ConnectXApiService::class.java)
    }
}
