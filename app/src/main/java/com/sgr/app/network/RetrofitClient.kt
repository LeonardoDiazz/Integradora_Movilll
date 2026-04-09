package com.sgr.app.network

import android.content.Context
import com.sgr.app.utils.SessionManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // URL cacheada en memoria durante la sesión
    @Volatile private var cachedBaseUrl: String? = null

    /**
     * Crea el cliente de API.
     * La primera vez descubre el backend en la red local y cachea la URL.
     * Las siguientes llamadas usan la URL cacheada (rápido).
     */
    suspend fun create(context: Context): ApiService {
        val baseUrl = cachedBaseUrl ?: NetworkDiscovery.resolveBaseUrl(context).also {
            cachedBaseUrl = it
        }
        return buildClient(context, baseUrl)
    }

    /**
     * Fuerza redescubrimiento completo (útil si el backend cambió de IP).
     */
    suspend fun createWithRediscovery(context: Context): ApiService {
        cachedBaseUrl = null
        NetworkDiscovery.clearCache(context)
        val baseUrl = NetworkDiscovery.resolveBaseUrl(context).also {
            cachedBaseUrl = it
        }
        return buildClient(context, baseUrl)
    }

    private fun buildClient(context: Context, baseUrl: String): ApiService {
        val session = SessionManager(context)

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = session.token
                val request = if (token != null) {
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
