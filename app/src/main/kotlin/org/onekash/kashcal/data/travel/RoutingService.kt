package org.onekash.kashcal.data.travel

import android.util.Log
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import org.onekash.kashcal.data.preferences.KashCalDataStore

/**
 * FORK:travel-time — Estimates a trip with the best source available and never fails.
 *
 * Order: the [RouteCache]; then TomTom (live traffic), Stadia Maps and the OSM Valhalla demo,
 * each only if switched on and keyed, and skipped while its [CircuitBreaker] is open; and last the
 * offline [LocalHaversineProvider]. A weekday rush-hour margin is added to a car trip from any
 * source without live traffic, if the user left that on.
 */
@Singleton
class RoutingService(
    private val onlineProviders: List<RoutingProvider>,
    private val local: LocalHaversineProvider,
    private val dataStore: KashCalDataStore,
    private val cache: RouteCache,
    private val breaker: CircuitBreaker,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    @Inject constructor(
        tomtom: TomTomRoutingProvider,
        stadia: StadiaMapsProvider,
        valhalla: ValhallaDemoProvider,
        local: LocalHaversineProvider,
        dataStore: KashCalDataStore,
        cache: RouteCache,
        breaker: CircuitBreaker,
    ) : this(listOf(tomtom, stadia, valhalla), local, dataStore, cache, breaker)

    /**
     * Returns the trip from the origin to the destination for [mode]. [arrivalEpochMs] is when the
     * user must be there; it decides whether the trip falls in a rush hour.
     */
    suspend fun estimate(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        mode: TravelMode,
        arrivalEpochMs: Long,
    ): RoutingResult {
        cache.get(originLat, originLon, destLat, destLon, mode)?.let { return it }

        for (provider in onlineProviders) {
            val result = tryProvider(provider, originLat, originLon, destLat, destLon, mode, arrivalEpochMs)
                ?: continue
            cache.put(originLat, originLon, destLat, destLon, mode, result)
            return result
        }

        val base = local.estimateMinutes(originLat, originLon, destLat, destLon, mode)
        return withRushHour(
            minutes = base,
            trafficDelay = 0,
            source = RoutingSource.LOCAL,
            hasLiveTraffic = false,
            mode = mode,
            arrivalEpochMs = arrivalEpochMs,
        )
    }

    private suspend fun tryProvider(
        provider: RoutingProvider,
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        mode: TravelMode,
        arrivalEpochMs: Long,
    ): RoutingResult? {
        if (!provider.isEnabled() || breaker.isOpen(provider.source)) return null
        val answer = try {
            provider.estimate(originLat, originLon, destLat, destLon, mode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RoutingRateLimitedException) {
            Log.w(TAG, "${provider.source} rate limited, pausing it: ${e.message}")
            breaker.tripRateLimited(provider.source)
            return null
        } catch (e: RoutingAuthException) {
            Log.w(TAG, "${provider.source} rejected the key, pausing it: ${e.message}")
            breaker.tripBadKey(provider.source)
            return null
        } catch (e: Exception) {
            Log.w(TAG, "${provider.source} failed", e)
            null
        }
        if (answer == null) {
            breaker.recordFailure(provider.source)
            return null
        }
        breaker.recordSuccess(provider.source)
        val minutes = answer.minutes + if (mode == TravelMode.TRANSIT) TravelMode.TRANSIT_OVERHEAD_MIN else 0
        return withRushHour(
            minutes = minutes,
            trafficDelay = answer.trafficDelayMinutes,
            source = provider.source,
            hasLiveTraffic = answer.hasLiveTraffic,
            mode = mode,
            arrivalEpochMs = arrivalEpochMs,
        )
    }

    private suspend fun withRushHour(
        minutes: Int,
        trafficDelay: Int,
        source: RoutingSource,
        hasLiveTraffic: Boolean,
        mode: TravelMode,
        arrivalEpochMs: Long,
    ): RoutingResult {
        val departureEpochMs = arrivalEpochMs - minutes * 60_000L
        val applies = mode == TravelMode.CAR &&
            !hasLiveTraffic &&
            dataStore.getTravelRushHourEnabled() &&
            (RushHourEstimator.isRushHour(departureEpochMs, zone()) ||
                RushHourEstimator.isRushHour(arrivalEpochMs, zone()))
        return RoutingResult(
            minutes = if (applies) RushHourEstimator.withMargin(minutes) else minutes,
            trafficDelayMinutes = trafficDelay,
            source = source,
            hasLiveTraffic = hasLiveTraffic,
            isRushHourAdjusted = applies,
        )
    }

    private companion object {
        const val TAG = "RoutingService"
    }
}
