package org.onekash.kashcal.data.travel

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.onekash.kashcal.data.preferences.KashCalDataStore
import org.onekash.kashcal.di.IoDispatcher

/**
 * FORK:travel-time — Valhalla routing hosted by Stadia Maps: a dedicated, reliable server on
 * OpenStreetMap data, with no live traffic. Needs the user's free API key (no card required).
 */
@Singleton
class StadiaMapsProvider @Inject constructor(
    @TravelHttp private val client: OkHttpClient,
    private val dataStore: KashCalDataStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : RoutingProvider {

    override val source = RoutingSource.STADIA

    /** Overridable so a test can point at a mock server. */
    internal var endpoint: String = "https://api.stadiamaps.com/route/v1"

    override suspend fun isEnabled(): Boolean =
        dataStore.getTravelStadiaEnabled() && dataStore.getTravelStadiaApiKey().isNotBlank()

    override suspend fun estimate(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        mode: TravelMode,
    ): ProviderResult? {
        val key = dataStore.getTravelStadiaApiKey()
        if (key.isBlank()) return null
        return withContext(ioDispatcher) {
            val url = endpoint.toHttpUrl().newBuilder().addQueryParameter("api_key", key).build()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", RoutingHttp.USER_AGENT)
                .post(RoutingHttp.jsonBody(
                    RoutingHttp.valhallaBody(originLat, originLon, destLat, destLon, mode.valhallaCosting)
                ))
                .build()
            val seconds = RoutingHttp.execute(client, request)?.let(RoutingHttp::valhallaSeconds)
                ?: return@withContext null
            ProviderResult(minutes = ceil(seconds / 60.0).toInt().coerceAtLeast(1))
        }
    }
}
