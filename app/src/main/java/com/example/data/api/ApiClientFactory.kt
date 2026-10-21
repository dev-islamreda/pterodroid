package com.example.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClientFactory {

    val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    fun cleanUrl(rawUrl: String): String {
        return UrlNormalizer.normalize(rawUrl)
    }

    fun createClient(panelUrl: String, apiToken: String): PterodactylApiService {
        val baseUrl = UrlNormalizer.normalize(panelUrl)
        val sanitizedToken = apiToken.trim()

        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
                .header("Authorization", "Bearer $sanitizedToken")
                .header("Accept", "Application/vnd.pterodactyl.v1+json, application/json")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) Pterodroid/1.0")
            if (original.header("Content-Type") == null) {
                requestBuilder.header("Content-Type", "application/json")
            }
            chain.proceed(requestBuilder.build())
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
            redactHeader("Authorization")
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(PterodactylApiService::class.java)
    }

    fun createRawOkHttpClient(apiToken: String): OkHttpClient {
        val sanitizedToken = apiToken.trim()
        val authInterceptor = Interceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
                .header("Authorization", "Bearer $sanitizedToken")
                .header("Accept", "Application/vnd.pterodactyl.v1+json, application/json")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) Pterodroid/1.0")
            chain.proceed(requestBuilder.build())
        }
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
            redactHeader("Authorization")
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
