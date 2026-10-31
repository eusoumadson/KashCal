package org.onekash.kashcal.reminder.travel

import android.content.Context
import android.content.pm.PackageManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.onekash.kashcal.util.location.AddressSuggestion
import org.onekash.kashcal.util.location.LocationSuggestionService

@OptIn(ExperimentalCoroutinesApi::class)
class TravelLocationProviderTest {

    private val context: Context = mockk(relaxed = true)
    private val locationSuggestionService: LocationSuggestionService = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var provider: TravelLocationProvider

    @Before
    fun setUp() {
        provider = TravelLocationProvider(
            context = context,
            locationSuggestionService = locationSuggestionService,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun hasLocationPermission_returnsFalseWhenDenied() {
        every { context.checkPermission(any(), any(), any()) } returns PackageManager.PERMISSION_DENIED
        assertFalse(provider.hasLocationPermission())
    }

    @Test
    fun getCurrentLocation_returnsNullWhenPermissionDenied() = runTest(testDispatcher) {
        every { context.checkPermission(any(), any(), any()) } returns PackageManager.PERMISSION_DENIED
        val loc = provider.getCurrentLocation()
        assertNull(loc)
    }

    @Test
    fun resolveCoordinates_returnsCoordinatesWhenServiceFindsResult() = runTest(testDispatcher) {
        coEvery { locationSuggestionService.getSuggestions("Av. Paulista, SP", 1) } returns listOf(
            AddressSuggestion(
                displayName = "Avenida Paulista, São Paulo",
                latitude = -23.5615,
                longitude = -46.6560
            )
        )

        val result = provider.resolveCoordinates("Av. Paulista, SP")
        assertEquals(-23.5615, result?.first ?: 0.0, 0.0001)
        assertEquals(-46.6560, result?.second ?: 0.0, 0.0001)
    }

    @Test
    fun resolveCoordinates_returnsNullForBlank() = runTest(testDispatcher) {
        assertNull(provider.resolveCoordinates(""))
        assertNull(provider.resolveCoordinates("   "))
    }

    @Test
    fun resolveCoordinates_returnsNullWhenNotFound() = runTest(testDispatcher) {
        coEvery { locationSuggestionService.getSuggestions(any(), any()) } returns emptyList()
        assertNull(provider.resolveCoordinates("Unknown Place 12345"))
    }
}
