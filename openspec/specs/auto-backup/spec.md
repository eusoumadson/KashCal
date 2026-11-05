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

### Requirement: RF-03 - Advanced Features Settings Placement
The system SHALL display the automatic settings backup entry row within the Advanced Features section (`settings_section_advanced_features`, verified at `AccountSettingsScreen.kt:675`) and SHALL NOT display it within the Data & Storage section (`settings_section_data`, verified at `AccountSettingsScreen.kt:876`).

#### Scenario: User views settings screen
- **WHEN** the user navigates to the AccountSettingsScreen
- **THEN** the settings row with label `auto_backup_label` (verified at `AccountSettingsScreen.kt:950`) appears grouped under `settings_section_advanced_features` alongside Tasks.org, Travel Alerts and Experimental Widgets, and the `settings_section_data` section contains only native upstream options.

#### Scenario: User opens auto backup configuration from advanced features
- **WHEN** the user taps the auto-backup row in the advanced features section
- **THEN** the system launches `AutoBackupSheet` displaying configuration options (folder, frequency, retention) and an explanatory hint that settings include fork preferences.
