package org.onekash.kashcal.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests [SettingsDestination]'s depths, its forward/back decision and [SettingsDestination.from].
 *
 * The Settings transition slides the incoming screen in from the trailing edge when the user
 * drills into a detail and reverses when backing out to the root. These tests pin the pure
 * forward/back decision; the Compose slide itself is verified on device, not here.
 */
class SettingsDestinationTest {

    @Test
    fun `root is the only depth-0 destination`() {
        assertEquals(0, SettingsDestination.Root.depth)
        assertTrue(SettingsDestination.Accounts.depth > 0)
        assertTrue(SettingsDestination.BirthdaysAnniversaries.depth > 0)
        assertTrue(SettingsDestination.Subscriptions.depth > 0)
        assertTrue(SettingsDestination.Tags.depth > 0)
        assertTrue(SettingsDestination.DeviceCalendars.depth > 0)
        assertTrue(SettingsDestination.TasksOrg.depth > 0) // FORK:tasks-org
        assertTrue(SettingsDestination.TravelAlerts.depth > 0) // FORK:travel-time
        assertTrue(SettingsDestination.ExperimentalWidgets.depth > 0) // FORK:experimental-widgets
        assertTrue(SettingsDestination.OfflineCalendars.depth > 0)
    }

    @Test
    fun `drilling from root into a detail slides forward`() {
        assertTrue(SettingsDestination.Root.isForwardTo(SettingsDestination.Accounts))
        assertTrue(SettingsDestination.Root.isForwardTo(SettingsDestination.BirthdaysAnniversaries))
        assertTrue(SettingsDestination.Root.isForwardTo(SettingsDestination.Subscriptions))
        assertTrue(SettingsDestination.Root.isForwardTo(SettingsDestination.Tags))
        assertTrue(SettingsDestination.Root.isForwardTo(SettingsDestination.DeviceCalendars))
        assertTrue(SettingsDestination.Root.isForwardTo(SettingsDestination.TasksOrg)) // FORK:tasks-org
        assertTrue(SettingsDestination.Root.isForwardTo(SettingsDestination.TravelAlerts)) // FORK:travel-time
        assertTrue(SettingsDestination.Root.isForwardTo(SettingsDestination.ExperimentalWidgets)) // FORK:experimental-widgets
        assertTrue(SettingsDestination.Root.isForwardTo(SettingsDestination.OfflineCalendars))
    }

    @Test
    fun `backing from a detail out to root slides backward`() {
        assertFalse(SettingsDestination.Accounts.isForwardTo(SettingsDestination.Root))
        assertFalse(SettingsDestination.BirthdaysAnniversaries.isForwardTo(SettingsDestination.Root))
        assertFalse(SettingsDestination.Subscriptions.isForwardTo(SettingsDestination.Root))
        assertFalse(SettingsDestination.Tags.isForwardTo(SettingsDestination.Root))
        assertFalse(SettingsDestination.DeviceCalendars.isForwardTo(SettingsDestination.Root))
        assertFalse(SettingsDestination.TasksOrg.isForwardTo(SettingsDestination.Root)) // FORK:tasks-org
        assertFalse(SettingsDestination.TravelAlerts.isForwardTo(SettingsDestination.Root)) // FORK:travel-time
        assertFalse(SettingsDestination.ExperimentalWidgets.isForwardTo(SettingsDestination.Root)) // FORK:experimental-widgets
        assertFalse(SettingsDestination.OfflineCalendars.isForwardTo(SettingsDestination.Root))
    }

    @Test
    fun `staying on the same destination is not treated as forward`() {
        assertFalse(SettingsDestination.Root.isForwardTo(SettingsDestination.Root))
        assertFalse(SettingsDestination.Accounts.isForwardTo(SettingsDestination.Accounts))
    }

    @Test
    fun `boolean flags resolve to the matching destination, detail winning over root`() {
        assertEquals(
            SettingsDestination.Root,
            SettingsDestination.from(
                accounts = false,
                birthdaysAnniversaries = false,
                subscriptions = false,
                tags = false,
                deviceCalendars = false,
                offlineCalendars = false,
            )
        )
        assertEquals(
            SettingsDestination.Accounts,
            SettingsDestination.from(
                accounts = true,
                birthdaysAnniversaries = false,
                subscriptions = false,
                tags = false,
                deviceCalendars = false,
                offlineCalendars = false,
            )
        )
        assertEquals(
            SettingsDestination.Tags,
            SettingsDestination.from(
                accounts = false,
                birthdaysAnniversaries = false,
                subscriptions = false,
                tags = true,
                deviceCalendars = false,
                offlineCalendars = false,
            )
        )
        assertEquals(
            SettingsDestination.DeviceCalendars,
            SettingsDestination.from(
                accounts = false,
                birthdaysAnniversaries = false,
                subscriptions = false,
                tags = false,
                deviceCalendars = true,
                offlineCalendars = false,
            )
        )
    }

    @Test
    fun `the offline calendars flag opens the Offline calendars screen`() {
        assertEquals(1, SettingsDestination.OfflineCalendars.depth)
        assertEquals(
            SettingsDestination.OfflineCalendars,
            SettingsDestination.from(
                accounts = false,
                birthdaysAnniversaries = false,
                subscriptions = false,
                tags = false,
                deviceCalendars = false,
                offlineCalendars = true,
            )
        )
    }

    // FORK:tasks-org
    @Test
    fun `the tasks org flag opens the Tasks org screen`() {
        assertEquals(1, SettingsDestination.TasksOrg.depth)
        assertEquals(
            SettingsDestination.TasksOrg,
            SettingsDestination.from(
                accounts = false,
                birthdaysAnniversaries = false,
                subscriptions = false,
                tags = false,
                deviceCalendars = false,
                offlineCalendars = false,
                tasksOrg = true
            )
        )
    }

    // FORK:travel-time
    @Test
    fun `the travel alerts flag opens the Travel alerts screen`() {
        assertEquals(1, SettingsDestination.TravelAlerts.depth)
        assertEquals(
            SettingsDestination.TravelAlerts,
            SettingsDestination.from(
                accounts = false,
                birthdaysAnniversaries = false,
                subscriptions = false,
                tags = false,
                deviceCalendars = false,
                offlineCalendars = false,
                travelAlerts = true
            )
        )
    }

    @Test
    fun `backing out of the screen Settings was launched on finishes the activity`() {
        assertTrue(SettingsDestination.Tags.closesActivity(launchedOn = SettingsDestination.Tags))
        assertTrue(
            SettingsDestination.DeviceCalendars.closesActivity(
                launchedOn = SettingsDestination.DeviceCalendars
            )
        )
    }

    @Test
    fun `backing out of any other screen returns within Settings`() {
        assertFalse(SettingsDestination.DeviceCalendars.closesActivity(launchedOn = null))
        assertFalse(SettingsDestination.Tags.closesActivity(launchedOn = null))
        assertFalse(
            SettingsDestination.DeviceCalendars.closesActivity(launchedOn = SettingsDestination.Tags)
        )
    }
}
