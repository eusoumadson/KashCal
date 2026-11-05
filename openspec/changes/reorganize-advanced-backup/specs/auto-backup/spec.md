# Spec Delta: auto-backup

## ADDED Requirements

### Requirement: RF-03 - Advanced Features Settings Placement
The system SHALL display the automatic settings backup entry row within the Advanced Features section (`settings_section_advanced_features`, verified at `AccountSettingsScreen.kt:675`) and SHALL NOT display it within the Data & Storage section (`settings_section_data`, verified at `AccountSettingsScreen.kt:876`).

#### Scenario: User views settings screen
- **WHEN** the user navigates to the AccountSettingsScreen
- **THEN** the settings row with label `auto_backup_label` (verified at `AccountSettingsScreen.kt:950`) appears grouped under `settings_section_advanced_features` alongside Tasks.org, Travel Alerts and Experimental Widgets, and the `settings_section_data` section contains only native upstream options.

#### Scenario: User opens auto backup configuration from advanced features
- **WHEN** the user taps the auto-backup row in the advanced features section
- **THEN** the system launches `AutoBackupSheet` displaying configuration options (folder, frequency, retention) and an explanatory hint that settings include fork preferences.
