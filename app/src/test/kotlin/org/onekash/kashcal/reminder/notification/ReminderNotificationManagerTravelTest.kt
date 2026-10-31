package org.onekash.kashcal.reminder.notification

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.onekash.kashcal.R
import org.onekash.kashcal.data.db.entity.ReminderType
import org.onekash.kashcal.data.db.entity.ScheduledReminder
import org.onekash.kashcal.data.preferences.KashCalDataStore
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ReminderNotificationManagerTravelTest {

    private lateinit var context: Context
    private val channels: ReminderNotificationChannels = mockk(relaxed = true)
    private val dataStore: KashCalDataStore = mockk(relaxed = true)

    private lateinit var notificationManager: ReminderNotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        channels.createChannels()
        every { channels.getNotificationId(any()) } returns 2001

        notificationManager = ReminderNotificationManager(
            context = context,
            channels = channels,
            dataStore = dataStore
        )
    }

    private fun sampleTravelReminder(
        type: ReminderType,
        travelMin: Int = 35,
        note: String = "provider=TomTom;trafficDelay=12;rushHour=false;live=true;estimate=false;mode=CAR",
        triggerTime: Long = 100_000_000L,
        occurrenceTime: Long = 100_000_000L + (35 * 60_000L)
    ) = ScheduledReminder(
        id = 123L,
        eventId = 1L,
        occurrenceTime = occurrenceTime,
        reminderOffset = "TRAVEL_DEPART",
        triggerTime = triggerTime,
        eventTitle = "Dentist Appointment",
        eventLocation = "Av. Paulista, 1000",
        calendarColor = 0xFF0000,
        isAllDay = false,
        reminderType = type,
        estimatedTravelMin = travelMin,
        travelNote = note
    )

    @Test
    fun buildNotification_travelDepart_usesTravelChannelAndTrafficDelayText() = runTest {
        val reminder = sampleTravelReminder(ReminderType.TRAVEL_DEPART)
        val notification = notificationManager.buildNotification(reminder)

        assertEquals(ReminderNotificationChannels.CHANNEL_TRAVEL_REMINDERS, notification.channelId)
        assertEquals(context.getString(R.string.travel_leave_now_title, "Dentist Appointment"), notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())

        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertTrue(text.contains("35 min"))
        assertTrue(text.contains("12 min"))

        // Actions: View on map + Dismiss
        val actions = notification.actions
        assertNotNull(actions)
        assertEquals(2, actions.size)
        assertEquals(context.getString(R.string.travel_action_view_on_map), actions[0].title.toString())
        assertEquals(context.getString(R.string.action_dismiss), actions[1].title.toString())
    }

    @Test
    fun buildNotification_travelBuffer_formatsLeaveInMinTitle() = runTest {
        val departTs = 100_000_000L
        val triggerTs = departTs - (10 * 60_000L) // 10 min buffer
        val occTs = departTs + (20 * 60_000L) // 20 min travel

        val reminder = sampleTravelReminder(
            type = ReminderType.TRAVEL_BUFFER,
            travelMin = 20,
            triggerTime = triggerTs,
            occurrenceTime = occTs
        )

        val notification = notificationManager.buildNotification(reminder)
        assertEquals(context.getString(R.string.travel_leave_in_min_title, 10, "Dentist Appointment"), notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    }

    @Test
    fun buildNotification_transitMode_includesTransitLinesAction() = runTest {
        val note = "provider=Stadia;trafficDelay=0;rushHour=false;live=false;estimate=false;mode=TRANSIT"
        val reminder = sampleTravelReminder(ReminderType.TRAVEL_DEPART, travelMin = 45, note = note)

        val notification = notificationManager.buildNotification(reminder)
        val actions = notification.actions
        assertNotNull(actions)
        assertEquals(3, actions.size) // View on map, Transit lines, Dismiss
        assertEquals(context.getString(R.string.travel_action_view_on_map), actions[0].title.toString())
        assertEquals(context.getString(R.string.travel_action_transit_lines), actions[1].title.toString())
        assertEquals(context.getString(R.string.action_dismiss), actions[2].title.toString())
    }
}
