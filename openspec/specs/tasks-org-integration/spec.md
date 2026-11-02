# Tasks.org Integration Specification

## Purpose
Enables reading and synchronizing tasks from the Tasks.org ContentProvider (`content://org.tasks.api/v0`) to display as calendar items, support check/uncheck with undo, and manage task lifecycles.

## Requirements

### Requirement: Non-persistent Task Overlay
The system SHALL query tasks dynamically from the Tasks.org ContentProvider and render them as `DisplayEvent.Task` without persisting tasks in the local Room database.

#### Scenario: Tasks.org integration disabled
- **WHEN** the preference `tasks_org_enabled` is set to false
- **THEN** no task FAB is rendered and no task events are displayed on month/day views.

### Requirement: Task Completion and Undo
The system SHALL allow completing or reopening tasks with immediate UI reaction and an Undo snackbar.

#### Scenario: User toggles repeating task checkbox
- **WHEN** user completes a recurring task
- **THEN** advance occurrence in Tasks.org and save pre-completion snapshot to allow full Undo restoration.
