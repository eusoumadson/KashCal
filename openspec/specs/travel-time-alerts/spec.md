# Travel Time Alerts Specification

## Purpose
Provides traffic-aware departure alerts for calendar events based on real-time location, travel duration calculation, and transit/driving time providers in cascading order.

## Requirements

### Requirement: Cascading Routing Providers
The system SHALL attempt routing calculations in cascading order: TomTom (real-time traffic) -> Stadia Maps (Valhalla Cloud) -> Valhalla Public Demo -> Local Haversine with Rush Hour Compensation.

#### Scenario: Network offline or circuit open
- **WHEN** all network routing providers fail, timeout, or circuit breaker trips
- **THEN** fallback gracefully to LocalHaversineProvider without crashing and apply rush hour traffic compensation if enabled.

### Requirement: Background Departure Notification
The system SHALL calculate dynamic departure time and post a high-priority notification on the dedicated `travel_time` notification channel.

#### Scenario: User arrives at departure buffer time
- **WHEN** current time reaches (Event Start Time - Travel Duration - Buffer Minutes)
- **THEN** trigger notification with title "Leave now -> [Event]" and action button "View on map" linking to destination coordinates.

### Requirement: Event Form Integration
The system SHALL allow enabling departure alerts on timed events that possess destination coordinates.

#### Scenario: User toggles departure alert on all-day event
- **WHEN** an event is marked as all-day
- **THEN** the departure alert section SHALL remain disabled or hidden.
