package org.onekash.kashcal.data.travel

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Qualifies the [OkHttpClient] the departure-alert routing providers share. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TravelHttp

/** FORK:travel-time — Provides the short-timeout HTTP client of the routing providers. */
@Module
@InstallIn(SingletonComponent::class)
object TravelHttpModule {
    /** Every call is capped at [RoutingHttp.CALL_TIMEOUT_SECONDS]; the worker has a tight budget. */
    @Provides
    @Singleton
    @TravelHttp
    fun provideTravelHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(RoutingHttp.CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .connectTimeout(RoutingHttp.CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(RoutingHttp.CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
}

/** FORK:travel-time — Shared request, status and JSON handling of the online providers. */
internal object RoutingHttp {
    const val CALL_TIMEOUT_SECONDS = 8L
    const val USER_AGENT = "KashCalTasks/1.0 (departure alerts; +https://github.com/eusoumadson/KashCal)"

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun jsonBody(body: String) = body.toRequestBody(JSON_MEDIA)

    /**
     * Runs [request] and returns the parsed JSON object of a 200 answer, or null on a network error,
     * another status or a body that isn't a JSON object.
     *
     * @throws RoutingRateLimitedException on 429
     * @throws RoutingAuthException on 401 or 403
     */
    fun execute(client: OkHttpClient, request: Request): JsonObject? {
        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 429 -> throw RoutingRateLimitedException("HTTP 429 from ${request.url.host}")
                    response.code == 401 || response.code == 403 ->
                        throw RoutingAuthException("HTTP ${response.code} from ${request.url.host}")
                    !response.isSuccessful -> return null
                }
                val text = response.body.string()
                return try {
                    json.parseToJsonElement(text).jsonObject
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: IOException) {
            return null
        }
    }

    /** Returns the Valhalla route request body for two points and a costing. */
    fun valhallaBody(oLat: Double, oLon: Double, dLat: Double, dLon: Double, costing: String): String =
        """{"locations":[{"lat":$oLat,"lon":$oLon},{"lat":$dLat,"lon":$dLon}],""" +
            """"costing":"$costing","directions_options":{"units":"kilometers"}}"""

    /** Returns `trip.summary.time` of a Valhalla answer in seconds, or null. */
    fun valhallaSeconds(root: JsonObject): Double? = try {
        (root["trip"]?.jsonObject?.get("summary")?.jsonObject?.get("time") as? JsonPrimitive)?.doubleOrNull
    } catch (_: Exception) {
        null
    }

    /** Returns the first route's `summary` of a TomTom answer, or null. */
    fun tomtomSummary(root: JsonObject): JsonObject? = try {
        root["routes"]?.jsonArray?.firstOrNull()?.jsonObject?.get("summary")?.jsonObject
    } catch (_: Exception) {
        null
    }

    fun JsonObject.number(name: String): Double? = (this[name] as? JsonPrimitive)?.doubleOrNull

    @Suppress("unused")
    fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull
}
