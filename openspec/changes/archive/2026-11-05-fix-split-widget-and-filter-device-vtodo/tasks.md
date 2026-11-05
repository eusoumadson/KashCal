# Tasks

## Phase 1: Correção do Widget SplitAgenda e Filtragem de VTODO dos Calendários do Dispositivo

Antes de implementar, leia:
1. `AGENTS.md` e `docs/agents/architecture.md` — regras de arquitetura e comandos de build/teste
2. `proposal.md`, `specs/` e `design.md` deste diretório de change — requisitos RIGID, cenários e decisões técnicas

- [x] T01 — Correção do dimensionamento vertical de SplitDateHeader no SplitAgendaWidget
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/widget/SplitAgendaWidgetContent.kt`, `app/src/test/kotlin/org/onekash/kashcal/widget/SplitAgendaWidgetContentTest.kt`
      Mudança: Ampliar a restrição de altura vertical de `SplitDateHeader` de `32.dp` para `40.dp` e ajustar os espaçadores verticais adjacentes para 2dp, garantindo que o número da data (24sp), dia da semana (11sp) e mês/ano (11sp) não sofram truncamento no Glance e mantendo a grade mensal de 6 semanas contida no widget.
      Cobre: RF-02 (fork-widgets), AC-01
      Acceptance criteria: `SplitDateHeader` em `SplitAgendaWidgetContent.kt` utiliza altura de 40.dp com alinhamento vertical centralizado e os testes de Glance continuam asserindo a existência dos nós sem falhas.
      Testes: `app/src/test/kotlin/org/onekash/kashcal/widget/SplitAgendaWidgetContentTest.kt` — testes de formatação do cabeçalho de data em locale US e pt-BR.

- [x] T02 — Criação do componente puro de domínio TasksOrgVTodoFilter
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/domain/tasks/TasksOrgVTodoFilter.kt`, `app/src/test/kotlin/org/onekash/kashcal/domain/tasks/TasksOrgVTodoFilterTest.kt`
      Mudança: Implementar `TasksOrgVTodoFilter` com funções puras para extrair tuplas normalizadas `(accountName, listTitle)` de contas/listas do Tasks.org e filtrar `DeviceCalendar`s que correspondam a listas de tarefas da mesma conta.
      Cobre: RF-05 (tasks-org-integration), AC-02
      Acceptance criteria: O método `TasksOrgVTodoFilter.filterDeviceCalendars` omite qualquer calendário cuja tupla `(accountName, displayName)` coincida (case-insensitive) com uma lista do Tasks.org da mesma conta, preservando calendários de contas distintas ou quando não houver correspondência.
      Testes: `app/src/test/kotlin/org/onekash/kashcal/domain/tasks/TasksOrgVTodoFilterTest.kt` — testes unitários cobrindo correspondência de tupla, diferenciação de contas, insensibilidade a maiúsculas/minúsculas e retorno intacto em caso de listas vazias.

- [x] T03 — Aplicação do filtro em AccountSettingsViewModel e HomeViewModel sob sentinelas // FORK:tasks-org
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/ui/viewmodels/AccountSettingsViewModel.kt`, `app/src/main/kotlin/org/onekash/kashcal/ui/viewmodels/HomeViewModel.kt`, `app/src/test/kotlin/org/onekash/kashcal/ui/viewmodels/AccountSettingsViewModelTest.kt`, `app/src/test/kotlin/org/onekash/kashcal/ui/viewmodels/HomeViewModelTest.kt`
      Mudança: Integrar `TasksOrgVTodoFilter` em `AccountSettingsViewModel` (`loadDeviceCalendars`, `loadWritableDeviceCalendars`) e em `HomeViewModel` (`loadCalendars`, `observeDeviceCalendarDrawerState`) quando `tasksOrgEnabled` estiver ativo e houver listas disponíveis, demarcando todas as alterações com `// FORK:tasks-org` e preservando o fluxo upstream quando a integração estiver desativada.
      Cobre: RF-05 (tasks-org-integration), AC-02, AC-03
      Acceptance criteria: As listas do `CalendarContract` que correspondam a listas VTODO do Tasks.org deixam de aparecer em `_deviceCalendars` e na gaveta lateral quando `tasksOrgEnabled == true`, e voltam a aparecer normalmente quando `tasksOrgEnabled == false`.
      Testes: `app/src/test/kotlin/org/onekash/kashcal/ui/viewmodels/AccountSettingsViewModelTest.kt` e `HomeViewModelTest` — testes unitários verificando a lista exposta com o toggle ligado e desligado.

- [x] T04 — Atualização da documentação do fork e Verificação da Suíte de Testes
      Arquivos: `docs/fork/FORK_FEATURES.md`, `docs/fork/FORK_INTEGRATION.md`
      Mudança: Documentar o filtro de listas VTODO nos calendários do dispositivo em `FORK_FEATURES.md` e catalogar os novos graft points em `FORK_INTEGRATION.md`. Executar verificação da suíte completa de testes unitários.
      Cobre: AC-04, AC-05
      Acceptance criteria: `FORK_FEATURES.md` e `FORK_INTEGRATION.md` atualizados refletindo as sentinelas `// FORK:tasks-org`; `./gradlew testDebugUnitTest` executado com 100% de aprovação.
      Testes: `./gradlew testDebugUnitTest` — verificação de toda a suíte de testes unitários do projeto.
