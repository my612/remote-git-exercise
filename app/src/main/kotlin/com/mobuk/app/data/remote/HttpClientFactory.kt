package com.mobuk.app.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import com.mobuk.app.data.local.AppJson

object HttpClientFactory {
    const val USER_AGENT = "MobUK-Android/1.0 (+recipes on device)"

    fun create(): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(AppJson)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
        defaultRequest {
            header("User-Agent", USER_AGENT)
        }
        engine {
            config {
                retryOnConnectionFailure(true)
            }
        }
    }
}
