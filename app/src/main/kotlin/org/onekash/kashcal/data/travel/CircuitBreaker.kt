package org.onekash.kashcal.data.travel

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FORK:travel-time — Pauses a routing provider that keeps failing, so the alarm worker falls
 * through to the next source at once instead of waiting out an 8 second timeout each time and
 * hammering a server that asked it to slow down.
 *
 * State is in memory only: a process restart gives every provider a fresh try.
 */
@Singleton
class CircuitBreaker(private val clock: () -> Long) {

    @Inject constructor() : this(System::currentTimeMillis)

    private class State(var consecutiveFailures: Int = 0, var openUntil: Long = 0L, var reason: Reason? = null)

    /** Why a provider is paused; the settings screen words each differently. */
    enum class Reason { FAILURES, RATE_LIMITED, BAD_KEY }

    private val states = ConcurrentHashMap<RoutingSource, State>()

    private fun state(source: RoutingSource) = states.getOrPut(source) { State() }

    /** Returns whether [source] is paused now. */
    fun isOpen(source: RoutingSource): Boolean = synchronized(state(source)) {
        val s = state(source)
        if (s.openUntil > clock()) true else {
            if (s.openUntil != 0L) { s.openUntil = 0L; s.reason = null }
            false
        }
    }

    /** Returns why [source] is paused, or null when it isn't. */
    fun reasonOf(source: RoutingSource): Reason? =
        if (isOpen(source)) synchronized(state(source)) { state(source).reason } else null

    /** Counts a failure; [FAILURES_BEFORE_OPEN] in a row pause the provider for [COOLDOWN_MS]. */
    fun recordFailure(source: RoutingSource) = synchronized(state(source)) {
        val s = state(source)
        s.consecutiveFailures++
        if (s.consecutiveFailures >= FAILURES_BEFORE_OPEN) {
            s.openUntil = clock() + COOLDOWN_MS
            s.reason = Reason.FAILURES
        }
    }

    /** Pauses [source] at once for [COOLDOWN_MS], after a 429. */
    fun tripRateLimited(source: RoutingSource) = synchronized(state(source)) {
        val s = state(source)
        s.openUntil = clock() + COOLDOWN_MS
        s.reason = Reason.RATE_LIMITED
    }

    /** Pauses [source] for [BAD_KEY_COOLDOWN_MS], after the server rejected its key. */
    fun tripBadKey(source: RoutingSource) = synchronized(state(source)) {
        val s = state(source)
        s.openUntil = clock() + BAD_KEY_COOLDOWN_MS
        s.reason = Reason.BAD_KEY
    }

    /** Clears the failure count and any pause of [source], after a good answer. */
    fun recordSuccess(source: RoutingSource) = synchronized(state(source)) {
        val s = state(source)
        s.consecutiveFailures = 0
        s.openUntil = 0L
        s.reason = null
    }

    /** Clears every pause, such as after the user edits a key. */
    fun reset(source: RoutingSource) = recordSuccess(source)

    companion object {
        const val FAILURES_BEFORE_OPEN = 2
        const val COOLDOWN_MS = 15 * 60_000L
        const val BAD_KEY_COOLDOWN_MS = 60 * 60_000L
    }
}
