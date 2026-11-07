# Upstream Sync Parity Specification

## Purpose
Estabelece a paridade com as funcionalidades incorporadas no KashCal upstream (versões v2026.10.30 a v2026.11.03), incluindo gerenciamento dinâmico de fusos horários, seletor unificado de datas, acessibilidade de contraste e hardening de sincronização.

## Requirements

### Requirement: RF-01 - Upstream Timezone Dynamic Reactivity
WHEN the device time zone changes while KashCal is running, THE SYSTEM SHALL dynamically update event positions and lengths in active views (month view, week view, day sheet, and search) via `ZoneSource` and `LocalDisplayZone` without requiring an application restart.

#### Scenario: Device time zone transition during active session
- **WHEN** the device broadcasts `Intent.ACTION_TIMEZONE_CHANGED`
- **THEN** all rendered events in `HomeViewModel` and `DayColumn` MUST re-evaluate their start and end time boundaries against the new local timezone immediately.

### Requirement: RF-02 - Unified Event Date and Time Picker
WHEN creating or editing a calendar event in `EventFormSheet`, THE SYSTEM SHALL provide a unified bottom sheet containing a Starts | Ends toggle selector and quick-duration chips (30m, 1h, 2h, 2d, 5d, 7d).

#### Scenario: User toggles between start and end selection
- **WHEN** the user opens the date/time selector sheet and taps the Ends tab
- **THEN** the picker MUST preserve the selected start instant and display valid end choices with automatic duration computation.

### Requirement: RF-03 - Accessible Color Contrast Enforcement
WHEN rendering event chips, month widgets, color checkmarks, or the event form save button, IF white text contrast over the background color is at least 3.0:1, THEN THE SYSTEM SHALL use white foreground text; otherwise, THE SYSTEM SHALL use dark text.

#### Scenario: Calendar with mid-tone color
- **WHEN** an event belongs to a calendar with a mid-tone color (e.g. blue or red)
- **THEN** the chip text color MUST evaluate to white (#FFFFFF) if its WCAG contrast ratio equals or exceeds 3.0:1.

### Requirement: RF-04 - CalDAV Collection Path Hardening and Sync Reliability
WHEN performing CalDAV synchronization, THE SYSTEM SHALL discover and parse event calendar collections whose server path ends in `/Tasks/`, AND SHALL NOT record failures in `SyncHistory` when only local offline calendars or device calendars are configured.

#### Scenario: Sync history on device-only setup
- **WHEN** the user opens `SyncHistorySheet` on an installation with no active CalDAV/iCloud account
- **THEN** no error items SHALL be listed in sync history and the UI SHALL state why sync is not applicable.

### Requirement: RF-05 - Mascot Kash UI Presence
WHEN rendering empty month days, the first-launch setup flow, or the About screen, THE SYSTEM SHALL display the Kash mascot artwork and context-sensitive descriptions.

#### Scenario: Empty day selected in Month view
- **WHEN** the user selects a date with zero scheduled events and zero tasks
- **THEN** the view SHALL display the Kash illustration with conversational empty-day copy.

### Requirement: RF-06 - Sync Hardening for Large Attachments and Per-Operation Isolation
WHEN synchronizing events via CalDAV in `ICalEventMapper` (verified at `app/src/main/kotlin/org/onekash/kashcal/sync/parser/icaldav/ICalEventMapper.kt:37`), `PullStrategy` (verified at `app/src/main/kotlin/org/onekash/kashcal/sync/strategy/PullStrategy.kt:111`), and `PushStrategy` (verified at `app/src/main/kotlin/org/onekash/kashcal/sync/strategy/PushStrategy.kt:50`), THE SYSTEM SHALL omit extra properties exceeding `MAX_EXTRA_PROPERTY_VALUE_CHARS` (25,000 chars), preserve inline attachments in `rawIcal`, and isolate per-event and per-operation failures.

#### Scenario: Pulling an event with a large inline attachment or HTML description up to 2 MB
- **WHEN** `ICalEventMapper.toEntity` maps an iCalendar event containing an `ATTACH` or `X-ALT-DESC` property longer than 25,000 characters
- **THEN** `Event.extraProperties` MUST exclude that oversized property so the Room row stays readable within the 2 MB `CursorWindow`
- **AND** `Event.rawIcal` MUST retain the original property so subsequent edits and pushes preserve the attachment.

#### Scenario: Isolating a single failing operation during PushStrategy batch processing
- **WHEN** `PushStrategy` processes a queue of `PendingOperation` items and one operation throws a runtime exception (excluding `CancellationException` and `IOException`)
- **THEN** `PushStrategy` MUST record `SinglePushResult.Error` for that specific operation
- **AND** MUST continue dispatching the remaining `PendingOperation` items in the batch.

### Requirement: RF-07 - Non-Destructive Reminder Reconciliation, 36h Lookback, and Travel Time Coexistence
WHEN an event is edited or synced in `EventCoordinator` (verified at `app/src/main/kotlin/org/onekash/kashcal/domain/coordinator/EventCoordinator.kt:313`) or `CalDavSyncWorker` (verified at `app/src/main/kotlin/org/onekash/kashcal/sync/worker/CalDavSyncWorker.kt:869`), THE SYSTEM SHALL reconcile reminders in `ReminderScheduler` (verified at `app/src/main/kotlin/org/onekash/kashcal/reminder/scheduler/ReminderScheduler.kt:169`) with `STARTED_LOOKBACK_MS` (36h) while skipping `reminderType != ReminderType.TIME_BEFORE` rows so `TravelReminderScheduler` alerts remain intact.

#### Scenario: Editing an event with an active snoozed reminder and a travel alert
- **WHEN** a user edits an event's title or description locally via `EventCoordinator` while a `TIME_BEFORE` reminder is `SNOOZED`
- **THEN** `ReminderScheduler.reconcileRemindersForEvent` MUST retain the `SNOOZED` row with its existing ID and update its display fields (`event_title`, `event_location`, `calendar_color`) via `ScheduledRemindersDao` (verified at `app/src/main/kotlin/org/onekash/kashcal/data/db/dao/ScheduledRemindersDao.kt:19`)
- **AND** `EventCoordinator` MUST refresh the event's travel alert via `TravelReminderScheduler`.

#### Scenario: Background CalDAV sync updates an event that already has a calculated travel reminder
- **WHEN** `CalDavSyncWorker` processes a `ChangeType.MODIFIED` event and invokes `ReminderScheduler.reconcileRemindersForEvent`
- **THEN** any `ScheduledReminder` row with `reminderType` equal to `TRAVEL_DEPART` or `TRAVEL_BUFFER` MUST NOT be deleted by offset mismatch
- **AND** its display metadata (`event_title`, `event_location`, `calendar_color`) MUST be updated in-place.

#### Scenario: All-day event for today synced after midnight UTC
- **WHEN** an all-day event for the current local day is synced after 00:00 UTC (within `STARTED_LOOKBACK_MS` = 36 hours of occurrence start) and its local reminder trigger time is still in the future
- **THEN** `EventReader.getOccurrencesForEventInScheduleWindow` MUST include the occurrence
- **AND** `ReminderScheduler.scheduleRemindersForEvent` MUST arm the reminder alarm.

### Requirement: RF-08 - Unified De-Emphasized Alpha (0.65f) for Past, Declined, Cancelled, and Completed Items
WHEN rendering past, declined, or cancelled events in `DeclinedVisuals` (verified at `app/src/main/kotlin/org/onekash/kashcal/ui/components/DeclinedVisuals.kt:16`), past date headers in `MonthFullSpanLayout` (verified at `app/src/main/kotlin/org/onekash/kashcal/ui/screens/monthfull/MonthFullSpanLayout.kt:266`) and `PlannerView` (verified at `app/src/main/kotlin/org/onekash/kashcal/ui/screens/planner/PlannerView.kt:348`), or completed tasks in `TaskCard` (verified at `app/src/main/kotlin/org/onekash/kashcal/ui/components/TaskCard.kt:147`), THE SYSTEM SHALL apply `DEEMPHASIZED_ALPHA = 0.65f` for >= 4.5:1 WCAG AA contrast.

#### Scenario: Rendering past or declined events and completed tasks
- **WHEN** an event is in the past, declined, or cancelled, or a task in `TaskCard` is marked `checked = true` (and is not a future occurrence)
- **THEN** the card alpha MUST evaluate to `0.65f` (`DEEMPHASIZED_ALPHA`).

### Requirement: RF-09 - Kash App Icon Preset and Day Icon Calendar Labels with Fork Identity Preservation
WHEN the user selects an app icon in `AppIconPreset` (verified at `app/src/main/kotlin/org/onekash/kashcal/ui/appicon/AppIconPreset.kt:30`) or resolves launcher aliases in `AndroidManifest.xml` (verified at `app/src/main/AndroidManifest.xml:259`), THE SYSTEM SHALL expose `AppIconPreset.KASH` (`.MainActivityKash`), set `android:label="@string/app_icon_label_calendar"` on `.MainActivityDay1`..`.MainActivityDay31`, and preserve `@string/fork_app_name` (`"Calendário"`) on the remaining aliases without keeping `fastlane/metadata/android/` files.

#### Scenario: Validating launcher aliases in manifest tests
- **WHEN** `LauncherAliasManifestTest` (verified at `app/src/test/kotlin/org/onekash/kashcal/LauncherAliasManifestTest.kt:23`) inspects all 35 aliases in `LauncherAliases.ALL`
- **THEN** `.MainActivityDay1`..`.MainActivityDay31` and `.MainActivitySupporterCalendar` MUST resolve their label to `@string/app_icon_label_calendar`
- **AND** `.MainActivityDefault`, `.MainActivityKash`, and `.MainActivitySupporter` MUST resolve their label to `@string/fork_app_name` (`"Calendário"`).
