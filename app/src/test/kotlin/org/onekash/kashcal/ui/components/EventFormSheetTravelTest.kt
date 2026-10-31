// FORK:travel-time
package org.onekash.kashcal.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onekash.kashcal.data.db.entity.Event

class EventFormSheetTravelTest {

    @Test
    fun withDuplicateOf_copiesTravelFieldsFromSourceEvent() {
        val source = Event(
            id = 100L,
            uid = "test-travel@example.com",
            calendarId = 1L,
            title = "Meeting at Office",
            startTs = 1700000000000L,
            endTs = 1700003600000L,
            dtstamp = 1700000000000L,
            location = "Main St 123",
            geoLat = -23.5505,
            geoLon = -46.6333,
            travelMode = "CAR",
            travelDepartBufferMin = 15,
            travelOriginFixed = "Home"
        )

        val formState = EventFormState().withDuplicateOf(source)

        assertEquals("Meeting at Office", formState.title)
        assertEquals("Main St 123", formState.location)
        assertEquals(-23.5505, formState.locationLat)
        assertEquals(-46.6333, formState.locationLon)
        assertEquals("CAR", formState.travelMode)
        assertEquals(15, formState.travelDepartBufferMin)
        assertEquals("Home", formState.travelOriginFixed)
    }

    @Test
    fun eventFormHasUnsavedChanges_detectsTravelFieldChanges() {
        val baseline = EventFormState(
            title = "Dentist",
            travelMode = "CAR",
            travelDepartBufferMin = 10,
            travelOriginFixed = null,
            locationLat = 40.7128,
            locationLon = -74.0060
        )

        // No change
        assertFalse(eventFormHasUnsavedChanges(baseline, baseline))

        // Change travel mode
        assertTrue(eventFormHasUnsavedChanges(baseline, baseline.copy(travelMode = "FOOT")))

        // Change buffer
        assertTrue(eventFormHasUnsavedChanges(baseline, baseline.copy(travelDepartBufferMin = 30)))

        // Change fixed origin
        assertTrue(eventFormHasUnsavedChanges(baseline, baseline.copy(travelOriginFixed = "Office")))

        // Change location coordinates
        assertTrue(eventFormHasUnsavedChanges(baseline, baseline.copy(locationLat = 40.7130)))
        assertTrue(eventFormHasUnsavedChanges(baseline, baseline.copy(locationLon = -74.0050)))
    }
}
