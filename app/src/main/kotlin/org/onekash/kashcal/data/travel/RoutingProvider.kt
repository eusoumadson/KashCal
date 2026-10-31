package org.onekash.kashcal.data.travel

/** FORK:travel-time — Where a [RoutingResult] came from. */
enum class RoutingSource(val displayName: String) {
    TOMTOM("TomTom"),
    STADIA("Stadia Maps"),
    VALHALLA("OpenStreetMap"),
    LOCAL("local estimate"),
}

/** One online provider's answer. */
data class ProviderResult(
    val minutes: Int,
    /** Minutes of the trip due to current congestion; 0 when unknown or none. */
    val trafficDelayMinutes: Int = 0,
    /** True when [minutes] already counts live traffic. */
    val hasLiveTraffic: Boolean = false,
)

/** The outcome [RoutingService.estimate] hands the alarm worker; there is always one. */
data class RoutingResult(
    val minutes: Int,
    val trafficDelayMinutes: Int,
    val source: RoutingSource,
    val hasLiveTraffic: Boolean,
    /** True when a weekday rush-hour margin was added to a trip with no live traffic. */
    val isRushHourAdjusted: Boolean,
) {
    /** True for the offline estimate, the least precise source. */
    val isEstimate: Boolean get() = source == RoutingSource.LOCAL
}

/** A source of travel time. Implementations never throw except the two exceptions below. */
interface RoutingProvider {
    val source: RoutingSource

    /** Whether the provider should be tried: switched on and, when it needs one, keyed. */
    suspend fun isEnabled(): Boolean

    /**
     * Returns the trip, or null when it can't be computed (network, timeout, bad response).
     *
     * @throws RoutingRateLimitedException when the server says to slow down
     * @throws RoutingAuthException when the server rejects the API key
     */
    suspend fun estimate(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        mode: TravelMode,
    ): ProviderResult?
}

/** The server answered 429 Too Many Requests. */
class RoutingRateLimitedException(message: String) : Exception(message)

/** The server rejected the API key (401 or 403). */
class RoutingAuthException(message: String) : Exception(message)
