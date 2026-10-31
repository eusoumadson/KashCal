package org.onekash.kashcal.data.travel

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

/**
 * FORK:travel-time — The offline last resort: straight-line distance, stretched by the mode's
 * road factor, over the mode's average speed. It needs no network and never fails, so a departure
 * alert always has a time. It knows nothing of roads or traffic; precision is typically ±20-40%.
 */
@Singleton
class LocalHaversineProvider @Inject constructor() {

    /** Returns the estimated trip in minutes: at least 1 for two different points, 0 for the same. */
    fun estimateMinutes(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        mode: TravelMode,
    ): Int {
        val straightKm = GeoMath.haversineKm(originLat, originLon, destLat, destLon)
        if (straightKm <= 0.0) return 0
        val routeKm = straightKm * mode.tortuosityFactor
        return ceil(routeKm / mode.speedKmh * 60.0).toInt().coerceAtLeast(1)
    }
}
