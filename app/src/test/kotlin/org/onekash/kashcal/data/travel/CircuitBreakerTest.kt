package org.onekash.kashcal.data.travel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CircuitBreakerTest {
    private var now = 1_000_000L
    private val breaker = CircuitBreaker { now }
    private val source = RoutingSource.VALHALLA

    @Test
    fun `starts closed`() {
        assertFalse(breaker.isOpen(source))
        assertNull(breaker.reasonOf(source))
    }

    @Test
    fun `one failure keeps it closed, two open it`() {
        breaker.recordFailure(source)
        assertFalse(breaker.isOpen(source))
        breaker.recordFailure(source)
        assertTrue(breaker.isOpen(source))
        assertEquals(CircuitBreaker.Reason.FAILURES, breaker.reasonOf(source))
    }

    @Test
    fun `a success between failures resets the count`() {
        breaker.recordFailure(source)
        breaker.recordSuccess(source)
        breaker.recordFailure(source)
        assertFalse(breaker.isOpen(source))
    }

    @Test
    fun `closes again after the cooldown`() {
        breaker.recordFailure(source)
        breaker.recordFailure(source)
        now += CircuitBreaker.COOLDOWN_MS - 1
        assertTrue(breaker.isOpen(source))
        now += 2
        assertFalse(breaker.isOpen(source))
        assertNull(breaker.reasonOf(source))
    }

    @Test
    fun `rate limit opens at once`() {
        breaker.tripRateLimited(source)
        assertTrue(breaker.isOpen(source))
        assertEquals(CircuitBreaker.Reason.RATE_LIMITED, breaker.reasonOf(source))
    }

    @Test
    fun `bad key pauses for longer than a failure`() {
        breaker.tripBadKey(source)
        now += CircuitBreaker.COOLDOWN_MS + 1
        assertTrue(breaker.isOpen(source))
        assertEquals(CircuitBreaker.Reason.BAD_KEY, breaker.reasonOf(source))
        now += CircuitBreaker.BAD_KEY_COOLDOWN_MS
        assertFalse(breaker.isOpen(source))
    }

    @Test
    fun `reset clears a pause`() {
        breaker.tripBadKey(source)
        breaker.reset(source)
        assertFalse(breaker.isOpen(source))
    }

    @Test
    fun `providers are independent`() {
        breaker.tripRateLimited(RoutingSource.TOMTOM)
        assertFalse(breaker.isOpen(RoutingSource.STADIA))
    }
}
