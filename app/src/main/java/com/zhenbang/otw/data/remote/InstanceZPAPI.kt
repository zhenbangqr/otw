package com.zhenbang.otw.data.remote

import com.zhenbang.otw.data.remote.api.PostZPAPI
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object InstanceZPAPI {

    // --- Constants ---
    private const val BASE_URL = "https://open.bigmodel.cn/api/paas/"
    private const val API_KEY = "7cf86bc2e27445b6accf465c5ed10478.y3ZxmbqCCdY0ln2q"
    private const val CONNECT_TIMEOUT_SECONDS = 30L
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val AUTHORIZATION_HEADER = "Authorization"
    private const val BEARER_TOKEN_PREFIX = "Bearer "

    // --- Logging Interceptor ---
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY // Log request and response body
    }

    // --- Authentication Interceptor ---
    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val newRequestBuilder = originalRequest.newBuilder()
            .header(AUTHORIZATION_HEADER, BEARER_TOKEN_PREFIX + API_KEY)
        val newRequest = newRequestBuilder.build()
        chain.proceed(newRequest)
    }

    // --- OkHttpClient ---
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(authInterceptor)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    // --- Retrofit Instance ---
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // --- API Service ---
    val api: PostZPAPI by lazy {
        retrofit.create(PostZPAPI::class.java)
    }
}