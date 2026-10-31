package org.onekash.kashcal.data.travel

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.onekash.kashcal.data.preferences.KashCalDataStore

/** The three online providers against a mock server: request shape, parsing and error mapping. */
class RoutingProvidersTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient()
    private val dataStore = mockk<KashCalDataStore>()

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        coEvery { dataStore.getTravelTomTomApiKey() } returns "tt-key"
        coEvery { dataStore.getTravelTomTomEnabled() } returns true
        coEvery { dataStore.getTravelStadiaApiKey() } returns "st-key"
        coEvery { dataStore.getTravelStadiaEnabled() } returns true
        coEvery { dataStore.getTravelValhallaEnabled() } returns true
    }

    @After
    fun tearDown() = server.shutdown()

    private fun tomtom() = TomTomRoutingProvider(client, dataStore, Dispatchers.Unconfined)
        .also { it.baseUrl = server.url("/routing/1/calculateRoute").toString().trimEnd('/') }

    private fun stadia() = StadiaMapsProvider(client, dataStore, Dispatchers.Unconfined)
        .also { it.endpoint = server.url("/route/v1").toString() }

    private fun valhalla() = ValhallaDemoProvider(client, dataStore, Dispatchers.Unconfined)
        .also { it.endpoint = server.url("/route").toString() }

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)

    // ---------- TomTom ----------

    @Test
    fun `tomtom parses time and traffic delay and sends key, mode and traffic`() = runTest {
        server.enqueue(ok("""{"routes":[{"summary":{"lengthInMeters":3428,"travelTimeInSeconds":2101,"trafficDelayInSeconds":725}}]}"""))

        val r = tomtom().estimate(-23.55, -46.63, -23.56, -46.65, TravelMode.CAR)!!

        assertEquals(36, r.minutes) // 2101 s = 35.02 min, rounded up
        assertEquals(12, r.trafficDelayMinutes)
        assertTrue(r.hasLiveTraffic)
        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals("/routing/1/calculateRoute/-23.55,-46.63:-23.56,-46.65/json", req.requestUrl!!.encodedPath)
        assertEquals("tt-key", req.requestUrl!!.queryParameter("key"))
        assertEquals("car", req.requestUrl!!.queryParameter("travelMode"))
        assertEquals("true", req.requestUrl!!.queryParameter("traffic"))
    }

    @Test
    fun `tomtom walking has no live traffic`() = runTest {
        server.enqueue(ok("""{"routes":[{"summary":{"travelTimeInSeconds":600,"trafficDelayInSeconds":0}}]}"""))
        val r = tomtom().estimate(0.0, 0.0, 0.0, 0.1, TravelMode.FOOT)!!
        assertFalse(r.hasLiveTraffic)
        assertEquals("pedestrian", server.takeRequest().requestUrl!!.queryParameter("travelMode"))
    }

    @Test
    fun `tomtom without a key makes no request`() = runTest {
        coEvery { dataStore.getTravelTomTomApiKey() } returns ""
        assertNull(tomtom().estimate(0.0, 0.0, 0.0, 0.1, TravelMode.CAR))
        assertEquals(0, server.requestCount)
        assertFalse(tomtom().isEnabled())
    }

    @Test
    fun `tomtom is disabled by its switch even with a key`() = runTest {
        coEvery { dataStore.getTravelTomTomEnabled() } returns false
        assertFalse(tomtom().isEnabled())
    }

    @Test
    fun `tomtom maps 403 to an auth failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        try {
            tomtom().estimate(0.0, 0.0, 0.0, 0.1, TravelMode.CAR)
            fail("expected RoutingAuthException")
        } catch (_: RoutingAuthException) {
        }
    }

    @Test
    fun `tomtom maps 429 to rate limited`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        try {
            tomtom().estimate(0.0, 0.0, 0.0, 0.1, TravelMode.CAR)
            fail("expected RoutingRateLimitedException")
        } catch (_: RoutingRateLimitedException) {
        }
    }

    @Test
    fun `tomtom returns null for a server error, bad json and a missing summary`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        assertNull(tomtom().estimate(0.0, 0.0, 0.0, 0.1, TravelMode.CAR))
        server.enqueue(ok("not json"))
        assertNull(tomtom().estimate(0.0, 0.0, 0.0, 0.1, TravelMode.CAR))
        server.enqueue(ok("""{"routes":[]}"""))
        assertNull(tomtom().estimate(0.0, 0.0, 0.0, 0.1, TravelMode.CAR))
    }

    // ---------- Stadia Maps ----------

    @Test
    fun `stadia posts a valhalla request with the key and parses trip time`() = runTest {
        server.enqueue(ok("""{"trip":{"summary":{"time":1019.4,"length":9.1}}}"""))

        val r = stadia().estimate(-23.55, -46.63, -23.56, -46.65, TravelMode.CAR)!!

        assertEquals(17, r.minutes) // 16.99 min rounds up
        assertFalse(r.hasLiveTraffic)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("st-key", req.requestUrl!!.queryParameter("api_key"))
        val body = req.body.readUtf8()
        assertTrue(body, body.contains(""""costing":"auto""""))
        assertTrue(body, body.contains(""""lat":-23.55,"lon":-46.63"""))
    }

    @Test
    fun `stadia without a key makes no request`() = runTest {
        coEvery { dataStore.getTravelStadiaApiKey() } returns " "
        assertNull(stadia().estimate(0.0, 0.0, 0.0, 0.1, TravelMode.CAR))
        assertEquals(0, server.requestCount)
        assertFalse(stadia().isEnabled())
    }

    @Test
    fun `stadia uses the pedestrian costing on foot and bus for transit`() = runTest {
        server.enqueue(ok("""{"trip":{"summary":{"time":60}}}"""))
        stadia().estimate(0.0, 0.0, 0.0, 0.1, TravelMode.FOOT)
        assertTrue(server.takeRequest().body.readUtf8().contains(""""costing":"pedestrian""""))
        server.enqueue(ok("""{"trip":{"summary":{"time":60}}}"""))
        stadia().estimate(0.0, 0.0, 0.0, 0.1, TravelMode.TRANSIT)
        assertTrue(server.takeRequest().body.readUtf8().contains(""""costing":"bus""""))
    }

    @Test
    fun `stadia maps 401 to an auth failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        try {
            stadia().estimate(0.0, 0.0, 0.0, 0.1, TravelMode.CAR)
            fail("expected RoutingAuthException")
        } catch (_: RoutingAuthException) {
        }
    }

    // ---------- Valhalla demo ----------

    @Test
    fun `valhalla demo sends the client id and needs no key`() = runTest {
        server.enqueue(ok("""{"trip":{"summary":{"time":600.0}}}"""))

        val r = valhalla().estimate(-23.55, -46.63, -23.56, -46.65, TravelMode.CAR)!!

        assertEquals(10, r.minutes)
        val req = server.takeRequest()
        assertEquals("kashcal-tasks-fork", req.getHeader("X-Client-Id"))
        assertEquals(null, req.requestUrl!!.queryParameter("api_key"))
    }

    @Test
    fun `valhalla demo follows its switch`() = runTest {
        coEvery { dataStore.getTravelValhallaEnabled() } returns false
        assertFalse(valhalla().isEnabled())
    }

    @Test
    fun `valhalla demo maps 429 to rate limited and a network error to null`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        try {
            valhalla().estimate(0.0, 0.0, 0.0, 0.1, TravelMode.CAR)
            fail("expected RoutingRateLimitedException")
        } catch (_: RoutingRateLimitedException) {
        }
        val provider = valhalla()
        server.shutdown()
        assertNull(provider.estimate(0.0, 0.0, 0.0, 0.1, TravelMode.CAR))
    }
}
