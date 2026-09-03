package app.quotatrail.foundation.network

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class ProviderHttpResponse(
    val statusCode: Int,
    val body: String,
)

class ProviderHttpClient(
    okHttpClient: OkHttpClient = OkHttpClient.Builder().build(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    allowedHosts: Set<String>? = null,
) {
    private val client = okHttpClient
        .newBuilder()
        .apply {
            callTimeout(okHttpClient.callTimeoutMillis.clampedTimeoutMillis(), TimeUnit.MILLISECONDS)
            connectTimeout(okHttpClient.connectTimeoutMillis.clampedTimeoutMillis(), TimeUnit.MILLISECONDS)
            readTimeout(okHttpClient.readTimeoutMillis.clampedTimeoutMillis(), TimeUnit.MILLISECONDS)
            writeTimeout(okHttpClient.writeTimeoutMillis.clampedTimeoutMillis(), TimeUnit.MILLISECONDS)
            allowedHosts?.map { it.lowercase() }?.toSet()?.let { normalizedAllowlist ->
                val hostGuard = okhttp3.Interceptor { chain ->
                    if (chain.request().url.host.lowercase() !in normalizedAllowlist) {
                        throw IOException("Blocked network destination")
                    }
                    chain.proceed(chain.request())
                }
                // Application interceptor blocks before DNS/connect; network interceptor also checks
                // each redirect follow-up, which OkHttp handles inside the application chain.
                addInterceptor(hostGuard)
                addNetworkInterceptor(hostGuard)
            }
        }
        .build()

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): ProviderHttpResponse =
        withContext(ioDispatcher) {
            val requestBuilder = Request.Builder()
                .url(url.toHttpUrl())
                .get()

            headers.forEach { (name, value) ->
                requestBuilder.header(name, value)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                ProviderHttpResponse(
                    statusCode = response.code,
                    body = response.body.string(),
                )
            }
        }

    suspend fun postForm(
        url: String,
        formFields: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
    ): ProviderHttpResponse =
        withContext(ioDispatcher) {
            val formBody = FormBody.Builder().apply {
                formFields.forEach { (name, value) ->
                    add(name, value)
                }
            }.build()
            val requestBuilder = Request.Builder()
                .url(url.toHttpUrl())
                .post(formBody)

            headers.forEach { (name, value) ->
                requestBuilder.header(name, value)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                ProviderHttpResponse(
                    statusCode = response.code,
                    body = response.body.string(),
                )
            }
        }

    suspend fun postJson(
        url: String,
        jsonBody: String,
        headers: Map<String, String> = emptyMap(),
    ): ProviderHttpResponse =
        withContext(ioDispatcher) {
            val requestBuilder = Request.Builder()
                .url(url.toHttpUrl())
                .post(jsonBody.toRequestBody(APPLICATION_JSON_MEDIA_TYPE))

            headers.forEach { (name, value) ->
                requestBuilder.header(name, value)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                ProviderHttpResponse(
                    statusCode = response.code,
                    body = response.body.string(),
                )
            }
        }

    private fun Int.clampedTimeoutMillis(): Long =
        when {
            this == NO_TIMEOUT -> MAX_TIMEOUT_MILLIS
            this > MAX_TIMEOUT_MILLIS -> MAX_TIMEOUT_MILLIS
            else -> toLong()
        }

    private companion object {
        const val NO_TIMEOUT = 0
        const val MAX_TIMEOUT_MILLIS = 30_000L
        val APPLICATION_JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
