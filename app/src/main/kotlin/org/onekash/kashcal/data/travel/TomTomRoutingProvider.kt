package org.onekash.kashcal.data.travel

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.onekash.kashcal.data.preferences.KashCalDataStore
import org.onekash.kashcal.di.IoDispatcher
import org.onekash.kashcal.data.travel.RoutingHttp.number

/**
 * FORK:travel-time — TomTom Routing API, the one source with live traffic. The free plan allows
 * 2,500 requests a day with no card, which a personal calendar never reaches. Needs the user's key.
 */
@Singleton
class TomTomRoutingProvider @Inject constructor(
    @TravelHttp private val client: OkHttpClient,
    private val dataStore: KashCalDataStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : RoutingProvider {

    override val source = RoutingSource.TOMTOM

    /** Overridable so a test can point at a mock server; no trailing slash. */
    internal var baseUrl: String = "https://api.tomtom.com/routing/1/calculateRoute"

    override suspend fun isEnabled(): Boolean =
        dataStore.getTravelTomTomEnabled() && dataStore.getTravelTomTomApiKey().isNotBlank()

    override suspend fun estimate(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        mode: TravelMode,
    ): ProviderResult? {
        val key = dataStore.getTravelTomTomApiKey()
        if (key.isBlank()) return null
        return withContext(ioDispatcher) {
            val url = "$baseUrl/$originLat,$originLon:$destLat,$destLon/json".toHttpUrl().newBuilder()
                .addQueryParameter("key", key)
                .addQueryParameter("travelMode", mode.tomtomMode)
                // Only a road vehicle has traffic; the pedestrian profile ignores the flag.
                .addQueryParameter("traffic", "true")
                .addQueryParameter("routeType", "fastest")
                .build()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", RoutingHttp.USER_AGENT)
                .get()
                .build()
            val summary = RoutingHttp.execute(client, request)?.let(RoutingHttp::tomtomSummary)
                ?: return@withContext null
            val seconds = summary.number("travelTimeInSeconds") ?: return@withContext null
            val delaySeconds = summary.number("trafficDelayInSeconds") ?: 0.0
            ProviderResult(
                minutes = ceil(seconds / 60.0).toInt().coerceAtLeast(1),
                trafficDelayMinutes = (delaySeconds / 60.0).roundToInt().coerceAtLeast(0),
                hasLiveTraffic = mode != TravelMode.FOOT,
            )
        }
    }
}
