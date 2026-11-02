# Automatic Backup Specification

## Purpose
Provides automated, scheduled export of application preferences, calendar selections, and fork-specific settings to a user-selected SAF folder using WorkManager.

## Requirements

### Requirement: Storage Access Framework Target
The system SHALL request and persist a folder Uri via `takePersistableUriPermission` for automated writes.

#### Scenario: User selects valid backup directory
- **WHEN** user chooses a folder via the OpenDocumentTree contract
- **THEN** persist `auto_backup_tree_uri` and enable schedule reconciliation.

### Requirement: Periodic WorkManager Execution
The system SHALL run `AutoBackupWorker` at configured intervals (Daily, Weekly) under unmetered battery constraints.

#### Scenario: Worker executes backup cycle
- **WHEN** periodic work triggers
- **THEN** generate JSON backup via `SettingsBackupExporter`, write timestamped file to SAF tree, and prune files exceeding `auto_backup_retention_count`.
