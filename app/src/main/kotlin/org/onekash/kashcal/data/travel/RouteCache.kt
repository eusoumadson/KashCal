package org.onekash.kashcal.data.travel

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.round

/**
 * FORK:travel-time — Remembers a routed trip for [TTL_MS], so two events at one place, or a worker
 * retry, don't spend a second request and a second round trip on the same answer.
 *
 * The key rounds each point to 3 decimals (about 100 m). The cache is in memory and holds at most
 * [MAX_ENTRIES], dropping the least recently used.
 */
@Singleton
class RouteCache(private val clock: () -> Long) {

    @Inject constructor() : this(System::currentTimeMillis)

    private data class Key(
        val originLat: Long,
        val originLon: Long,
        val destLat: Long,
        val destLon: Long,
        val mode: TravelMode,
    )

    private class Entry(val result: RoutingResult, val storedAt: Long)

    private val entries = object : LinkedHashMap<Key, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Entry>?) = size > MAX_ENTRIES
    }

    private fun keyOf(oLat: Double, oLon: Double, dLat: Double, dLon: Double, mode: TravelMode) =
        Key(q(oLat), q(oLon), q(dLat), q(dLon), mode)

    private fun q(v: Double): Long = round(v * 1000.0).toLong()

    /** Returns the fresh cached result for the trip, or null. */
    @Synchronized
    fun get(oLat: Double, oLon: Double, dLat: Double, dLon: Double, mode: TravelMode): RoutingResult? {
        val key = keyOf(oLat, oLon, dLat, dLon, mode)
        val entry = entries[key] ?: return null
        if (clock() - entry.storedAt > TTL_MS) {
            entries.remove(key)
            return null
        }
        return entry.result
    }

    @Synchronized
    fun put(oLat: Double, oLon: Double, dLat: Double, dLon: Double, mode: TravelMode, result: RoutingResult) {
        entries[keyOf(oLat, oLon, dLat, dLon, mode)] = Entry(result, clock())
    }

    @Synchronized
    fun clear() = entries.clear()

    companion object {
        const val TTL_MS = 15 * 60_000L
        const val MAX_ENTRIES = 64
    }
}
