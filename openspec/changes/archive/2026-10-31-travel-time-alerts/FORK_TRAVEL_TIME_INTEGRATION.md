# Departure Alerts (Alertas de Saída por Tempo de Viagem)
## KashCal Fork Architectural & Technical Integration Guide

This document details the architecture, design decisions, database migrations, routing engine, and UI integration for the **Departure Alerts** feature implemented in the `feature/travel-time-alerts` branch.

---

### 1. Architectural Philosophy & Fork Lateralization

In line with the Tasks.org integration pattern, all departure alert features are implemented **laterally** without compromising upstream CalDAV sync, RFC 5545 compliance, or database integrity.

- **Zero Google Mobile Services (GMS) Dependency**: Location resolution uses Android Open Source Project (AOSP) `LocationManager` and the native platform `Geocoder`. Fully compliant with F-Droid and IzzyOnDroid distribution standards.
- **Traceable Upstream Tagging**: Any modifications made to upstream KashCal core files are labeled with:
  ```kotlin
  // FORK:travel-time
  ```
- **Modular Packaging**: All new logic resides in dedicated packages:
  - `org.onekash.kashcal.data.travel`: HTTP routing clients, multi-provider cascading engine, rate limiter, circuit breaker, and offline estimator.
  - `org.onekash.kashcal.reminder.travel`: WorkManager worker (`TravelAlarmWorker`), scheduler (`TravelReminderScheduler`), and AOSP location provider (`TravelLocationProvider`).
  - `org.onekash.kashcal.ui.screens.settings`: Configuration UI (`TravelAlertsScreen`, `TravelAlertsViewModel`).

---

### 2. Multi-Provider Routing Strategy

Departure travel duration is computed dynamically ~1 hour before the event start using an automated 4-tier cascading engine:

```
┌─────────────────────────────────────────────────────────────────┐
│                    RoutingService.estimate()                    │
└────────────────────────────────┬────────────────────────────────┘
                                 │
           ┌─────────────────────┴─────────────────────┐
           ▼                                           │ (not enabled / no key)
 1. TomTom Online Routing                              │
    • Live traffic & congestion delays                 │
    • 2,500 free requests/day (no credit card)         ▼
    • Fallback on error / 401 / 429 ─────────► 2. Stadia Maps (Valhalla Cloud)
                                                  • Multimodal (Car, Walk, Transit)
                                                  • 200k free credits/month
                                                  • Fallback on error ─────► 3. OpenStreetMap Valhalla Demo
                                                                                • Free public demo server
                                                                                • Rate-limited + Circuit Breaker
                                                                                • Fallback on open / trip ──► 4. Local Haversine Offline
                                                                                                                 • 100% offline
                                                                                                                 • Geodesic distance + detours
                                                                                                                 • Rush-hour buffer (+30%)
```

#### Resilience & Safety: Circuit Breaker
The public OpenStreetMap Valhalla demo server (`valhalla1.openstreetmap.de`) is protected by a thread-safe `TravelCircuitBreaker`. If 3 consecutive failures (network timeouts, HTTP 429/500/503) occur:
1. The circuit **opens** immediately for 10 minutes (`OPEN`).
2. Subsequent calls bypass the remote demo server instantly without network overhead, falling back to the local offline estimator.
3. After the cooldown, a single request is allowed through (`HALF_OPEN`) to probe server health.
4. The settings screen displays a live status badge: **"Operacional"** or **"Pausado temporariamente (Circuit Breaker ativo)"**.

---

### 3. Database Schema Migration (27 → 28)

Room Database was cleanly migrated from version 27 to 28 via `MIGRATION_27_28`:

#### Changes to `events` table:
- `travel_mode` (`TEXT`, nullable): Mode of transportation (`CAR`, `FOOT`, `TRANSIT`). When `NULL`, departure alerts are disabled for the event.
- `travel_depart_buffer_min` (`INTEGER`, nullable): Additional preparation buffer before departure (e.g. `0` = at departure time, `5`, `10`, `15`, `30` min before).
- `travel_origin_fixed` (`TEXT`, nullable): When specified, routes originate from this address (e.g., "Home" or "Work") instead of the device's live GPS position.

#### Changes to `scheduled_reminders` table:
- `reminder_type` (`TEXT`, not null, default `'TIME_BEFORE'`): Distinguishes standard calendar reminders (`TIME_BEFORE`) from departure alarms (`TRAVEL_DEPART` and `TRAVEL_BUFFER`).
- `estimated_travel_min` (`INTEGER`, nullable): Stores the estimated travel duration in minutes returned by the routing provider.
- `travel_note` (`TEXT`, nullable): Stores traffic delay context and routing provider attribution for display in notifications.

---

### 4. Dynamic Scheduling & Execution Flow

```
[Event Created / Updated / Synced]
               │
               ▼
   TravelReminderScheduler.scheduleUpcomingTravelAlarms(windowDays = 7)
               │ (Enqueues WorkManager OneTimeWorkRequest with ExistingWorkPolicy.REPLACE)
               ▼
[~1 Hour Prior to Event Start Timestamp]
               │
               ▼
       TravelAlarmWorker.doWork()
               │
               ├─► Resolves Destination (geoLat/geoLon from Event or Geocoder from Event.location)
               ├─► Resolves Origin (Current GPS location via AOSP LocationManager or fixed address)
               ├─► Calls RoutingService.estimate(...)
               │       └─ (TomTom -> Stadia -> Valhalla Demo -> Local Offline)
               │
               ├─► Persists ScheduledReminder entries (TRAVEL_DEPART & TRAVEL_BUFFER)
               │
               └─► Arms Exact AlarmManager.setExactAndAllowWhileIdle(...)
                               │
                               ▼
               [Departure Time Triggered]
                               │
                               ▼
                    ReminderAlarmReceiver
                               │
                               ▼
                 ReminderNotificationManager
                               │
       ┌───────────────────────┴───────────────────────┐
       ▼                                               ▼
"Leave now → Team Lunch"               "Leave in 10 min → Team Lunch"
Est. 24 min by Car (incl. 6 min traffic)  Est. 24 min by Car
[📍 View on map]  [🚌 Transit lines]
```

---

### 5. Settings Screen & Key Onboarding

A dedicated settings screen is accessible via **Settings → Departure Alerts** (`TravelAlertsScreen.kt`):
- **Educational Card**: Explains how traffic-aware departure alerts work ~1 hour prior to events.
- **Location Permissions Status Card**: Shows whether fine/coarse and background location permissions are active, with direct triggers to grant them or open system app settings.
- **TomTom Card**: Guidance to create a free API key at `developer.tomtom.com` (2,500 calls/day, no credit card required), input field with visibility toggle, and enable/disable switch.
- **Stadia Maps Card**: Guidance to create a free account at `stadiamaps.com` (200,000 monthly credits), input field, and toggle.
- **OpenStreetMap Valhalla Demo Card**: Community server toggle with live Circuit Breaker status badge.
- **Local Estimator Card**: Rush-hour compensation switch (+30% buffer during morning 07:30-09:30 and evening 17:30-19:30 peak hours).

---

### 6. Event Form UI Integration

In `EventFormSheet.kt`:
- **Location Autocomplete Integration**: When picking a place suggestion from `LocationSuggestionService`, `locationLat` and `locationLon` are saved to the form state. Manual text alterations gracefully reset coordinates to avoid location mismatches.
- **Departure Alert Row**: Appears below reminders for all timed events (`!isAllDay`):
  - Toggle switch to enable/disable departure calculation.
  - Mode selector chips: **Car**, **On foot**, **Public transit**.
  - Buffer selector chips: **At time to leave**, **5 min**, **10 min**, **15 min**, **30 min**.
  - Origin selector: **Current location** (with automatic runtime permission request) or **Fixed address** (with dedicated text field).
- **HomeViewModel Persistence**:
  - All 5 create and update branches (`createEvent`, `same-calendar update`, `calendar-move update`, `single-occurrence exception`, and `this-and-future split`) faithfully persist `geoLat`, `geoLon`, `travelMode`, `travelDepartBufferMin`, and `travelOriginFixed`.

---

### 7. Verification & Test Suite

The feature includes comprehensive unit tests verifying 100% of the new components:
- `LocalTravelEstimatorTest`: Geodesic distance calculation, detours, speed profiles, and rush-hour adjustment.
- `TravelCircuitBreakerTest`: State transitions (`CLOSED` -> `OPEN` -> `HALF_OPEN` -> `CLOSED`), failure thresholds, and cooldown timings.
- `RoutingServiceTest`: Cascade logic, provider fallbacks, and error resilience.
- `TravelLocationProviderTest`: GPS and network provider fallback chain.
- `TravelAlarmWorkerTest`: WorkManager payload extraction, route resolution, and alarm arming.
- `TravelReminderSchedulerTest`: 7-day lookahead window, occurrence timing calculations, and cancellation.
- `ReminderNotificationManagerTravelTest`: Departure alert notification layout, action intents (`geo:`, navigation), and traffic metrics formatting.
- `EventFormSheetTravelTest`: Form dirty checks and duplication fidelity.
- `HomeViewModelSaveTravelAlertsTest`: Data layer persistence across all save operations.
- `MigrationTest` & `MigrationHashValidationTest`: SQLite migration 27 → 28 validation.
