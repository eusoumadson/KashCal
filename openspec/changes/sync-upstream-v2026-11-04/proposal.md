# Proposal: sync-upstream-v2026-11-04

## Metadata
- **Tier**: standard
- **Architecture References**: `AGENTS.md`, `docs/agents/architecture.md`, `docs/agents/domain_rules.md`, `docs/fork/FORK_INTEGRATION.md`
- **Capability Policy**: ignore_for_this_run

## Why

O projeto upstream (`KashCal/KashCal`, remoto `origin/main`) lançou a versão `v2026.11.04` (`f1391fca`), trazendo quatro correções e melhorias estruturais: suporte a eventos com anexos ou descrições HTML grandes (~2 MB) e isolamento de falhas por evento/operação no sync CalDAV, reconciliação não-destrutiva de lembretes após edição com janela de lookback de 36 horas (#425), ajuste de contraste `DEEMPHASIZED_ALPHA = 0.65f` para eventos passados/recusados/cancelados (#426) e adição do preset de ícone `Kash` com rótulo `"Calendar"` nos ícones diários. Precisamos incorporar essas evoluções ao nosso fork preservando integralmente os lembretes preditivos de Travel Time (`// FORK:travel-time`), a integração com Tasks.org (`// FORK:tasks-org`) e a identidade Abacate Calendário (`// FORK:fork-identity`).

## Scope
- **In**:
  - Incorporação do commit upstream `v2026.11.04` (`f1391fca`) sobre a base atual do fork (`v2026.11.06`).
  - Hardening de sincronização CalDAV: limite `MAX_EXTRA_PROPERTY_VALUE_CHARS` em `ICalEventMapper.kt`, preservação de `rawIcal` e isolamento de linhas acima do CursorWindow de 2 MB em `PullStrategy.kt` e isolamento de exceções por operação em `PushStrategy.kt`.
  - Reconciliação de lembretes (`reconcileRemindersForEvent`) e lookback de ocorrências (`STARTED_LOOKBACK_MS = 36h`) em `ReminderScheduler.kt`, `ScheduledRemindersDao.kt`, `EventReader.kt`, `EventCoordinator.kt`, `UtcSeriesZoneRepair.kt`, `CalDavSyncWorker.kt`, `IcsSubscriptionRepository.kt` e `BaseContactEventRepository.kt`.
  - Proteção dos lembretes de partida (`ReminderType.TRAVEL_DEPART` / `TRAVEL_BUFFER`) em `ReminderScheduler.reconcileRemindersForEvent` para que sincronizações remotas não descartem alarmes calculados pelo `TravelAlarmWorker`, mantendo o reagendamento explícito em `EventCoordinator.rescheduleRemindersForEvent`.
  - Atualização da opacidade de itens de-enfatizados para `DEEMPHASIZED_ALPHA = 0.65f` em `DeclinedVisuals.kt`, `HomeScreen.kt`, `MonthFullSpanLayout.kt`, `PlannerView.kt` e nas tarefas concluídas em `TaskCard.kt`.
  - Adição do alias `.MainActivityKash` (`AppIconPreset.KASH`), mipmaps correspondentes e `android:label="@string/app_icon_label_calendar"` nos 31 aliases `.MainActivityDay1..31` no `AndroidManifest.xml`, mantendo `R.string.fork_app_name` em `LauncherAliasManifestTest.kt`.
  - Exclusão de `fastlane/metadata/android/en-US/changelogs/820.txt` e preservação do `version.properties` do fork.
  - Incorporação e adaptação da suíte de testes unitários da `v2026.11.04` e atualização de `docs/fork/FORK_INTEGRATION.md`.
- **Out**:
  - Alteração de esquemas do banco Room ou criação de novas migrações SQLite.
  - Reintrodução de metadados de loja upstream em `fastlane/metadata/android/`.

## What Changes

- **Sync Hardening (`ICalEventMapper`, `PullStrategy`, `PushStrategy`, `Event`)**: Limita propriedades extras mapeadas em JSON a `MAX_EXTRA_PROPERTY_VALUE_CHARS` para evitar `SQLiteBlobTooBigException` na janela de 2 MB do Cursor do Android, mantendo anexos inline na cópia `rawIcal` e isolando falhas por evento/operação no pull e no push.
- **Reconciliação de Lembretes + Coexistência com Travel Time (`ReminderScheduler`, `ScheduledRemindersDao`, `EventCoordinator`)**: Substitui o cancelamento destrutivo em edições por `reconcileRemindersForEvent(event)` (preservando lembretes `SNOOZED` e notificações `FIRED`), adiciona `STARTED_LOOKBACK_MS` (36h) para armar lembretes de dia todo do próprio dia mesmo após meia-noite UTC, e filtra `row.reminderType == ReminderType.TIME_BEFORE` na reconciliação de offsets para proteger lembretes do `TravelReminderScheduler`.
- **Acessibilidade e Contraste (`DeclinedVisuals`, `HomeScreen`, `MonthFullSpanLayout`, `PlannerView`, `TaskCard`)**: Unifica o alpha de eventos passados/recusados/cancelados, números de datas passadas e tarefas concluídas em `DEEMPHASIZED_ALPHA = 0.65f` (garantindo contraste WCAG AA >= 4.5:1 nos temas claro e escuro).
- **Ícones do Launcher (`AppIconPreset`, `AndroidManifest.xml`, `LauncherAliasManifestTest`)**: Registra `.MainActivityKash` e `AppIconPreset.KASH`, aplica `@string/app_icon_label_calendar` nos aliases `.MainActivityDay1..31` e adapta `LauncherAliasManifestTest` à identidade `fork_app_name` (`"Calendário"`).

## Capabilities

### New Capabilities
<!-- Nenhuma nova capability isolada; estende upstream-sync-parity. -->

### Modified Capabilities
- `upstream-sync-parity`: Estende a paridade com o KashCal upstream até a versão `v2026.11.04` (sync de anexos grandes e isolamento de falhas, reconciliação de lembretes com lookback de 36h coexistindo com Travel Time, opacidade `DEEMPHASIZED_ALPHA = 0.65f` e preset de ícone `Kash` / rótulos de ícones diários).

## Confirmed Acceptance Criteria (Source of Truth)
- **AC-01**: Eventos com anexos ou descrições HTML grandes (até ~2 MB) sincronizam sem estourar o `CursorWindow` do SQLite (`MAX_EXTRA_PROPERTY_VALUE_CHARS`), preservam o anexo via `rawIcal` após edições, e falhas individuais no `PullStrategy` ou `PushStrategy` não bloqueiam os demais eventos/operações da fila.
- **AC-02**: Edições e sincronizações de eventos reconciliam lembretes via `ReminderScheduler.reconcileRemindersForEvent` (mantendo lembretes `SNOOZED` e notificações `FIRED` válidas e aplicando `STARTED_LOOKBACK_MS = 36h`), sem remover indevidamente lembretes `TRAVEL_DEPART`/`TRAVEL_BUFFER` em syncs remotos e reagendando-os corretamente em edições locais no `EventCoordinator`.
- **AC-03**: Eventos passados, recusados ou cancelados (incluindo resultados de busca em `HomeScreen`), números de dias passados (`MonthFullSpanLayout`, `PlannerView`) e tarefas concluídas (`TaskCard`) utilizam `DEEMPHASIZED_ALPHA = 0.65f`.
- **AC-04**: O preset `AppIconPreset.KASH` (`.MainActivityKash`) e os 31 aliases diários com `@string/app_icon_label_calendar` estão configurados no `AndroidManifest.xml`, `LauncherAliasManifestTest` valida o rótulo do fork (`R.string.fork_app_name`) e nenhum arquivo em `fastlane/metadata/android/en-US/changelogs/` é mantido.
- **AC-05**: `./gradlew testDebugUnitTest --no-daemon` e `./gradlew lintDebug --no-daemon` executam com 100% de aprovação.

## Impact
- **Sync & Data**: `ICalEventMapper.kt`, `PullStrategy.kt`, `PushStrategy.kt`, `CalDavSyncWorker.kt`, `Event.kt`, `ScheduledRemindersDao.kt`, `BaseContactEventRepository.kt`, `IcsSubscriptionRepository.kt`.
- **Domain & Reminders**: `ReminderScheduler.kt`, `EventCoordinator.kt`, `EventReader.kt`, `UtcSeriesZoneRepair.kt`.
- **UI & Manifest**: `DeclinedVisuals.kt`, `HomeScreen.kt`, `MonthFullSpanLayout.kt`, `PlannerView.kt`, `TaskCard.kt`, `AppIconPreset.kt`, `AndroidManifest.xml`, `values/strings.xml`, `values-pt/strings.xml`, `values-pt-rBR/strings.xml`.
- **Testes & Docs**: `SyncedEditReminderTest.kt`, `StartedOccurrenceReminderTest.kt`, `PullStrategyOversizedRowTest.kt`, `PushStrategyOperationIsolationTest.kt`, `MultiServerLargeAttachmentRoundTripTest.kt`, `ContactEventRenameReminderTest.kt`, `IcsRefreshReminderTest.kt`, `LauncherAliasManifestTest.kt`, `docs/fork/FORK_INTEGRATION.md`.
