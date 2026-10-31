package org.onekash.kashcal.data.travel

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.onekash.kashcal.data.preferences.KashCalDataStore
import org.onekash.kashcal.di.IoDispatcher

/**
 * FORK:travel-time — The public Valhalla demo server of the OpenStreetMap community
 * (valhalla1.openstreetmap.de): free and keyless, with no live traffic and no uptime promise.
 * [CircuitBreaker] keeps a struggling server from slowing every alarm.
 */
@Singleton
class ValhallaDemoProvider @Inject constructor(
    @TravelHttp private val client: OkHttpClient,
    private val dataStore: KashCalDataStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : RoutingProvider {

    override val source = RoutingSource.VALHALLA

    /** Overridable so a test can point at a mock server. */
    internal var endpoint: String = "https://valhalla1.openstreetmap.de/route"

    override suspend fun isEnabled(): Boolean = dataStore.getTravelValhallaEnabled()

    override suspend fun estimate(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        mode: TravelMode,
    ): ProviderResult? = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url(endpoint)
            .header("User-Agent", RoutingHttp.USER_AGENT)
            .header("X-Client-Id", "kashcal-tasks-fork")
            .post(RoutingHttp.jsonBody(
                RoutingHttp.valhallaBody(originLat, originLon, destLat, destLon, mode.valhallaCosting)
            ))
            .build()
        val seconds = RoutingHttp.execute(client, request)?.let(RoutingHttp::valhallaSeconds)
            ?: return@withContext null
        ProviderResult(minutes = ceil(seconds / 60.0).toInt().coerceAtLeast(1))
    }
}
