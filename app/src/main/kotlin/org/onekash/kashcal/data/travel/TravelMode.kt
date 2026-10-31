package org.onekash.kashcal.data.travel

/**
 * FORK:travel-time — How the user travels to an event, and the numbers each routing source needs.
 *
 * [TRANSIT] has no real public-transport routing behind it: the free sources route a road vehicle
 * ("bus" costing) and [org.onekash.kashcal.data.travel.RoutingService] adds [TRANSIT_OVERHEAD_MIN]
 * for walking to a stop and waiting. The notification offers the exact lines in a maps app.
 */
enum class TravelMode(
    /** The `travelMode` of TomTom's calculateRoute. */
    val tomtomMode: String,
    /** The `costing` of a Valhalla route request (Stadia Maps and the OSM demo server). */
    val valhallaCosting: String,
    /** Average speed of the offline estimate, in km/h. */
    val speedKmh: Double,
    /** Road distance over straight-line distance, for the offline estimate. */
    val tortuosityFactor: Double,
) {
    CAR(tomtomMode = "car", valhallaCosting = "auto", speedKmh = 30.0, tortuosityFactor = 1.35),
    FOOT(tomtomMode = "pedestrian", valhallaCosting = "pedestrian", speedKmh = 4.5, tortuosityFactor = 1.20),

    // The offline speed already folds in walking and waiting, so it gets no overhead added.
    TRANSIT(tomtomMode = "bus", valhallaCosting = "bus", speedKmh = 18.0, tortuosityFactor = 1.50);

    companion object {
        /** Minutes added to an online road-vehicle route to stand for walking and waiting. */
        const val TRANSIT_OVERHEAD_MIN = 10

        /** Returns the mode named [value], or null for a null or unknown name. */
        fun fromName(value: String?): TravelMode? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}
