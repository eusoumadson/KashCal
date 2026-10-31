// FORK:travel-time
package org.onekash.kashcal.ui.viewmodels

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.onekash.kashcal.data.calendar_provider.FakeCalendarProviderRepository
import org.onekash.kashcal.data.calendar_provider.deviceEventReader
import org.onekash.kashcal.data.calendar_provider.deviceEventWriter
import org.onekash.kashcal.data.db.entity.Event
import org.onekash.kashcal.data.preferences.KashCalDataStore
import org.onekash.kashcal.data.repository.AccountRepository
import org.onekash.kashcal.domain.coordinator.EventCoordinator
import org.onekash.kashcal.domain.reader.DisplayEventRepository
import org.onekash.kashcal.domain.reader.EventReader
import org.onekash.kashcal.network.NetworkMonitor
import org.onekash.kashcal.sync.scheduler.SyncScheduler
import org.onekash.kashcal.sync.scheduler.SyncStatus
import org.onekash.kashcal.ui.components.EventFormState

/**
 * Tests that travel departure alert fields entered in the event form are persisted across
 * all five Event create/update branches in [HomeViewModel.saveEvent].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelSaveTravelAlertsTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var eventCoordinator: EventCoordinator
    private lateinit var eventReader: EventReader
    private lateinit var displayEventRepository: DisplayEventRepository
    private lateinit var dataStore: KashCalDataStore
    private lateinit var accountRepository: AccountRepository
    private lateinit var syncScheduler: SyncScheduler
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var fakeCalendarProviderRepository: FakeCalendarProviderRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        eventCoordinator = mockk(relaxed = true)
        eventReader = mockk(relaxed = true)
        displayEventRepository = mockk(relaxed = true)
        dataStore = mockk(relaxed = true)
        accountRepository = mockk(relaxed = true)
        syncScheduler = mockk(relaxed = true)
        networkMonitor = mockk(relaxed = true)
        fakeCalendarProviderRepository = FakeCalendarProviderRepository()

        every { networkMonitor.isOnline } returns MutableStateFlow(true)
        every { networkMonitor.isMetered } returns MutableStateFlow(false)
        every { syncScheduler.observeImmediateSyncStatus() } returns MutableStateFlow(SyncStatus.Idle)
        every { syncScheduler.lastSyncChanges } returns MutableStateFlow(emptyList())
        every { syncScheduler.showBannerForSync } returns MutableStateFlow(false)
        coEvery { dataStore.defaultCalendarId } returns MutableStateFlow(null)
        coEvery { dataStore.defaultReminderMinutes } returns MutableStateFlow(15)
        coEvery { dataStore.defaultAllDayReminder } returns MutableStateFlow(1440)
        coEvery { dataStore.defaultEventDuration } returns MutableStateFlow(20)
        coEvery { dataStore.timeFormat } returns MutableStateFlow("system")
        coEvery { dataStore.showEventEmojis } returns MutableStateFlow(true)
        coEvery { dataStore.onboardingDismissed } returns MutableStateFlow(true)
        every { eventCoordinator.getAllCalendars() } returns MutableStateFlow(emptyList())
        every { eventReader.getVisibleOccurrencesInRange(any(), any()) } returns MutableStateFlow(emptyList())
        every { eventReader.getVisibleOccurrencesForDay(any()) } returns MutableStateFlow(emptyList())
        every { eventReader.getVisibleOccurrencesWithEventsForDay(any()) } returns MutableStateFlow(emptyList())
        coEvery { accountRepository.getAccountsByProvider(any()) } returns emptyList()
        coEvery { accountRepository.hasCredentials(any()) } returns false
        every { displayEventRepository.deviceCalendarChangeSignal } returns MutableStateFlow(0)
        coEvery { eventCoordinator.getLocalCalendarId() } returns 1L
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = HomeViewModel(
        eventCoordinator = eventCoordinator,
        eventReader = eventReader,
        displayEventRepository = displayEventRepository,
        dataStore = dataStore,
        accountRepository = accountRepository,
        syncScheduler = syncScheduler,
        networkMonitor = networkMonitor,
        deviceEventReader = fakeCalendarProviderRepository.deviceEventReader(),
        deviceEventWriter = fakeCalendarProviderRepository.deviceEventWriter(dataStore),
        attendeeBackfill = mockk(relaxed = true),
        contactEmailReader = mockk(relaxed = true),
        context = mockk(relaxed = true),
        ioDispatcher = testDispatcher,
        applicationScope = kotlinx.coroutines.CoroutineScope(testDispatcher),
    )

    private fun master() = Event(
        id = 42L,
        uid = "u42",
        calendarId = 1L,
        title = "Master",
        startTs = 1_000,
        endTs = 4_600_000,
        timezone = "UTC",
        rrule = "FREQ=DAILY",
        dtstamp = 1_000,
    )

    @Test
    fun `create path persists travel alert fields`() = runTest(testDispatcher) {
        val captured = slot<Event>()
        coEvery { eventCoordinator.createEvent(capture(captured), any(), any()) } answers { captured.captured }

        val vm = createViewModel()
        vm.saveEvent(
            EventFormState(
                title = "Airport Trip",
                selectedCalendarId = 1L,
                location = "JFK Airport",
                locationLat = 40.6413,
                locationLon = -73.7781,
                travelMode = "TRANSIT",
                travelDepartBufferMin = 30,
                travelOriginFixed = "Grand Central Station"
            )
        )

        val saved = captured.captured
        assertEquals("Airport Trip", saved.title)
        assertEquals("JFK Airport", saved.location)
        assertEquals(40.6413, saved.geoLat)
        assertEquals(-73.7781, saved.geoLon)
        assertEquals("TRANSIT", saved.travelMode)
        assertEquals(30, saved.travelDepartBufferMin)
        assertEquals("Grand Central Station", saved.travelOriginFixed)
    }

    @Test
    fun `same-calendar update path persists travel alert fields`() = runTest(testDispatcher) {
        val existing = Event(
            id = 42L,
            uid = "u42",
            calendarId = 1L,
            title = "Old",
            startTs = 1_000,
            endTs = 4_600_000,
            timezone = "UTC",
            dtstamp = 1_000,
        )
        coEvery { eventCoordinator.getEventById(42L) } returns existing
        val captured = slot<Event>()
        coEvery { eventCoordinator.updateEvent(capture(captured), any()) } answers { captured.captured }

        val vm = createViewModel()
        vm.saveEvent(
            EventFormState(
                title = "Client Office",
                selectedCalendarId = 1L,
                editingEventId = 42L,
                isEditMode = true,
                location = "Avenida Paulista 1000",
                locationLat = -23.565,
                locationLon = -46.652,
                travelMode = "CAR",
                travelDepartBufferMin = 15,
                travelOriginFixed = null
            )
        )

        val updated = captured.captured
        assertEquals(-23.565, updated.geoLat)
        assertEquals(-46.652, updated.geoLon)
        assertEquals("CAR", updated.travelMode)
        assertEquals(15, updated.travelDepartBufferMin)
        assertEquals(null, updated.travelOriginFixed)
    }

    @Test
    fun `calendar-move update path persists travel alert fields`() = runTest(testDispatcher) {
        val existing = master().copy(rrule = null, calendarId = 1L)
        coEvery { eventCoordinator.getEventById(42L) } returns existing
        coEvery { eventCoordinator.moveEventToCalendar(42L, 2L) } returns Unit
        coEvery { eventCoordinator.getEventById(42L) } returnsMany listOf(existing, existing.copy(calendarId = 2L))
        val captured = slot<Event>()
        coEvery { eventCoordinator.updateEvent(capture(captured), any()) } answers { captured.captured }

        val vm = createViewModel()
        vm.saveEvent(
            EventFormState(
                title = "Moved Event",
                selectedCalendarId = 2L,
                editingEventId = 42L,
                isEditMode = true,
                travelMode = "FOOT",
                travelDepartBufferMin = 5
            )
        )

        assertEquals("FOOT", captured.captured.travelMode)
        assertEquals(5, captured.captured.travelDepartBufferMin)
    }

    @Test
    fun `single-occurrence exception path persists travel alert fields`() = runTest(testDispatcher) {
        coEvery { eventCoordinator.getEventById(42L) } returns master()
        val changesSlot = slot<(Event) -> Event>()
        coEvery {
            eventCoordinator.editSingleOccurrence(any(), any(), any(), capture(changesSlot))
        } answers { changesSlot.captured(master()) }

        val vm = createViewModel()
        vm.saveEvent(
            EventFormState(
                title = "Single Occurrence with Travel",
                selectedCalendarId = 1L,
                editingEventId = 42L,
                isEditMode = true,
                editingOccurrenceTs = 90_000_000L,
                travelMode = "CAR",
                travelDepartBufferMin = 10
            )
        )

        val result = changesSlot.captured(master())
        assertEquals("CAR", result.travelMode)
        assertEquals(10, result.travelDepartBufferMin)
    }

    @Test
    fun `this-and-future split path persists travel alert fields`() = runTest(testDispatcher) {
        coEvery { eventCoordinator.getEventById(42L) } returns master()
        val changesSlot = slot<(Event) -> Event>()
        coEvery {
            eventCoordinator.editThisAndFuture(any(), any(), any(), capture(changesSlot))
        } answers { changesSlot.captured(master()) }

        val vm = createViewModel()
        vm.saveEvent(
            EventFormState(
                title = "Split with Travel",
                selectedCalendarId = 1L,
                editingEventId = 42L,
                isEditMode = true,
                editingOccurrenceTs = 90_000_000L,
                travelMode = "CAR",
                travelDepartBufferMin = 20,
                travelOriginFixed = "Home"
            ),
            scope = EditScope.THIS_AND_FUTURE
        )

        val result = changesSlot.captured(master())
        assertEquals("CAR", result.travelMode)
        assertEquals(20, result.travelDepartBufferMin)
        assertEquals("Home", result.travelOriginFixed)
    }
}
