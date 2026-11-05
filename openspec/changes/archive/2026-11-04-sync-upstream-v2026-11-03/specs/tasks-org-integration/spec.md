# Spec Delta: tasks-org-integration

## MODIFIED Requirements

### Requirement: Non-persistent Task Overlay
The system SHALL query tasks dynamically from the Tasks.org ContentProvider and render them as `DisplayEvent.Task` without persisting tasks in the local Room database, AND SHALL format and align task due dates consistently with `LocalDisplayZone` so tasks never drift or displace across midnight boundaries on timezone transitions.

#### Scenario: Tasks.org integration disabled
- **WHEN** the preference `tasks_org_enabled` is set to false
- **THEN** no task FAB is rendered and no task events are displayed on month/day views.

#### Scenario: Timezone transition with due tasks
- **WHEN** the system time zone changes while tasks are visible
- **THEN** tasks scheduled on a given local date SHALL remain assigned to that date without being shifted to the previous or next day.
