# Spec Delta: upstream-sync-parity

## Purpose
Estabelece a paridade com as funcionalidades incorporadas no KashCal upstream (versões v2026.10.30 a v2026.11.03), incluindo gerenciamento dinâmico de fusos horários, seletor unificado de datas, acessibilidade de contraste e hardening de sincronização.

## ADDED Requirements

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
