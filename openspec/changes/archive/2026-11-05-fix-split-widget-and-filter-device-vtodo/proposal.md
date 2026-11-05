# Proposal: fix-split-widget-and-filter-device-vtodo

## Metadata
- **Tier**: light
- **Architecture References**: AGENTS.md, docs/agents/architecture.md, docs/agents/domain_rules.md, docs/fork/FORK_INTEGRATION.md
- **Capability Policy**: ignore_for_this_run

## Why

1. No `SplitAgendaWidget`, o cabeçalho compacto da coluna esquerda foi delimitado com altura fixa de `32.dp`. Em dispositivos Android reais (com métricas de fonte nativas e escala de acessibilidade do sistema), essa restrição causa corte vertical severo no número da data (`24.sp`), na abreviação do dia da semana (`11.sp`) e no mês com ano abreviado (`11.sp`).
2. Provedores de sincronização CalDAV (como DAVx5) registram coleções de tarefas (`VTODO`) no `CalendarContract.Calendars` do Android. No KashCal, essas coleções aparecem na tela de "Calendários do dispositivo", permitindo ativá-las, mas não exibem nenhum evento (pois não contêm `VEVENT`). Como essas mesmas listas já são gerenciadas pelo Tasks.org via `org.tasks.api`, sua presença nos calendários do dispositivo polui a interface e confunde o usuário.

## Scope
- **In**:
  - Ajuste de dimensionamento e espaçamento do `SplitDateHeader` em `SplitAgendaWidgetContent.kt` para garantir exibição sem corte no Glance.
  - Filtro robusto composto por tupla de conta e título `(accountName, displayName)` contra as listas registradas no Tasks.org `(TasksOrgAccount.name, TasksOrgList.title)`, ocultando listas VTODO dos calendários do dispositivo na tela de configurações e na gaveta lateral.
  - Gating estrito de todo código do filtro por `tasksOrgEnabled` e marcação obrigatória com sentinelas `// FORK:tasks-org`.
  - Atualização dos testes unitários correspondentes e documentação de integração em `docs/fork/`.
- **Out**:
  - Criação de mecanismo de persistência de tarefas no Room (invariante do fork mantida).
  - Modificação do comportamento de listagem quando a integração Tasks.org estiver desligada (paridade total com upstream mantida).

## What Changes

- `SplitAgendaWidgetContent.kt`: Ampliação da altura de `SplitDateHeader` de `32.dp` para `40.dp` com espaçamentos proporcionais para evitar truncamento no Glance.
- `DeviceEventReader.kt` / `AccountSettingsViewModel.kt` / `HomeViewModel.kt`: Introdução da filtragem de `DeviceCalendar` baseada nas listas e contas do Tasks.org sob `// FORK:tasks-org`.
- `docs/fork/FORK_FEATURES.md` e `docs/fork/FORK_INTEGRATION.md`: Documentação da nova funcionalidade e do novo seam no core.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `fork-widgets`: Atualiza o requisito RF-02 ampliando a restrição de altura do cabeçalho da coluna esquerda para acomodar tipografia completa sem corte vertical.
- `tasks-org-integration`: Adiciona o requisito RF-05 especificando a filtragem de coleções VTODO do provedor de calendários do dispositivo via tupla de conta e lista.

## Confirmed Acceptance Criteria (Source of Truth)
- **AC-01**: No `SplitAgendaWidgetContent.kt`, expandir a restrição vertical do `SplitDateHeader` de `32.dp` para ~`40.dp`, eliminando cortes no número da data (`24.sp`), dia da semana (`11.sp`) e mês/ano (`11.sp`), mantendo a geometria do grid de 6 semanas inferior intacta.
- **AC-02**: Criar a resolução de tupla de contas e listas do Tasks.org (`TasksOrgAccount.name` + `TasksOrgList.title`). Quando a integração Tasks.org estiver ativa, qualquer `DeviceCalendar` cuja combinação `(accountName, displayName)` corresponder a uma lista de tarefas da mesma conta no Tasks.org será omitido da tela de configurações de calendários do dispositivo e da gaveta lateral.
- **AC-03**: Todo código inserido nos arquivos do core deve ser demarcado com `// FORK:tasks-org` e condicionado a `dataStore.getTasksOrgEnabled()`. Se a integração estiver desativada ou a permissão revogada, a listagem de calendários do dispositivo permanece 100% idêntica ao upstream.
- **AC-04**: Atualizar `docs/fork/FORK_INTEGRATION.md` e `docs/fork/FORK_FEATURES.md` documentando o filtro de listas VTODO.
- **AC-05**: Suíte de testes unitários (`SplitAgendaWidgetContentTest`, testes de filtragem de `DeviceCalendar` com fakes do Tasks.org, e `./gradlew testDebugUnitTest`) passando com 100% de sucesso.

## Impact
- Afeta `SplitAgendaWidgetContent.kt` e seu teste `SplitAgendaWidgetContentTest.kt`.
- Afeta os pontos de leitura e exibição de calendários do dispositivo (`AccountSettingsViewModel.kt`, `HomeViewModel.kt`, `DeviceEventReader.kt`).
- Sem migração de banco de dados, sem alteração de schemas do Room, sem novos pacotes externos.
