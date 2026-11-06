# Tasks: all-day-stacking-views

## Phase 1: Recursos de Localização e Empilhamento Inline em Listas e Drawers

Antes de implementar, leia:
1. `AGENTS.md` e `docs/agents/architecture.md` — regras de arquitetura e comandos de build/teste
2. `openspec/changes/all-day-stacking-views/proposal.md`, `openspec/changes/all-day-stacking-views/specs/tasks-org-integration/spec.md` e `openspec/changes/all-day-stacking-views/design.md` — requisitos RIGID (`RF-03`, `RF-06`, `RF-07`, `RNF-01`), cenários e decisões técnicas desta fase

- [ ] T01 — Adicionar strings localizadas para empilhamento e resumos de eventos de dia todo
      Arquivos: `app/src/main/res/values/strings.xml`, `app/src/fork/tools/gen_strings_pt.py`, `app/src/main/res/values-pt/strings.xml`, `app/src/main/res/values-pt-rBR/strings.xml`
      Mudança: Adicionar em `app/src/main/res/values/strings.xml` as chaves necessárias para empilhamento/resumo de eventos de dia todo (como `split_agenda_more_all_day_events_today` `+ %1$d events for today` e `label_more_all_day_events_compact` `+ %1$d all-day events`, além de reutilizar `split_agenda_more_all_day_events` `+ %1$d events` e `label_more_tasks_compact` `+ %1$d tasks`), registrar as traduções em `app/src/fork/tools/gen_strings_pt.py` e regenerar `values-pt/strings.xml` e `values-pt-rBR/strings.xml`.
      Cobre: RNF-01, AC-07
      Acceptance criteria: `python3 app/src/fork/tools/gen_strings_pt.py --check` retorna `ok` com código 0 e as chaves novas existem em `values/strings.xml`, `values-pt/strings.xml` e `values-pt-rBR/strings.xml`.
      Testes: `python3 app/src/fork/tools/gen_strings_pt.py --check` — verificação de paridade de placeholders e presença nos três arquivos de recursos

- [ ] T02 — Estender `AgendaListModel` para agrupar eventos de dia todo excedentes em `overflowEvents`
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/ui/components/AgendaListModel.kt`, `app/src/test/kotlin/org/onekash/kashcal/ui/components/AgendaListModelTest.kt`
      Mudança: Adicionar `val overflowEvents: List<AgendaDisplayItem> = emptyList() // FORK:tasks-org` em `AgendaDayGroup`. Em `buildAgendaListModel`, quando um dia possuir `> 1` evento de dia todo (`it.displayEvent !is DisplayEvent.Task && it.displayEvent.isAllDay`), manter apenas o 1º evento de dia todo em `items` e mover os `N - 1` excedentes para `overflowEvents` (mantendo a mesma regra já existente para `overflowTasks`). Atualizar o cálculo de `headerIndexByDayCode` para somar `(if (group.overflowEvents.isNotEmpty()) 1 else 0) + (if (group.overflowTasks.isNotEmpty()) 1 else 0)`. Quando houver apenas `1` evento de dia todo, `overflowEvents` permanece vazia.
      Cobre: RF-03, AC-01, RNF-01
      Acceptance criteria: `AgendaDayGroup` contém `overflowEvents` e `overflowTasks`; `buildAgendaListModel` mantém apenas o 1º evento de dia todo em `items` quando `> 1` e soma `+1` em `headerIndexByDayCode` para cada pilha não vazia (`overflowEvents` e `overflowTasks`).
      Testes: `app/src/test/kotlin/org/onekash/kashcal/ui/components/AgendaListModelTest.kt` — testes unitários cobrindo dia com 3 eventos de dia todo + 4 tarefas de dia todo + 2 reuniões (validando `items`, `overflowEvents`, `overflowTasks` e `headerIndexByDayCode`), dia com 1 único evento de dia todo (`overflowEvents` vazia) e dia com eventos de dia todo sem tarefas (`tasksOrgEnabled = false`)

- [ ] T03 — Implementar colapse inline de eventos e tarefas de dia todo em `AgendaContent` e `DayEventsPage` (`HomeScreen.kt`)
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/ui/screens/HomeScreen.kt`, `app/src/test/kotlin/org/onekash/kashcal/ui/components/AgendaListModelTest.kt`
      Mudança: Em `AgendaContent` (`HomeScreen.kt`), adicionar estado independente `expandedEventDays` ao lado de `expandedDays` (tarefas) e renderizar o item `key = "overflow_events_$displayDay"` logo após o 1º evento de dia todo quando `group.overflowEvents.isNotEmpty()`, exibindo `AgendaEventStackCard` (ou card de pilha com ícone de calendário) que expande/recolhe `group.overflowEvents` inline via `AnimatedVisibility`. Em `DayEventsPage` (`HomeScreen.kt`), aplicar a mesma regra de colapse inline tanto para eventos de dia todo (`!isTask && isAllDay`, `size > 1` -> 1º evento + card expansível `+X eventos`) quanto para tarefas de dia todo (`isTask && isAllDay`, `size > 1` -> 1ª tarefa + card expansível `+X tarefas`), com estados `isEventsExpanded` e `isTasksExpanded` independentes, renderizando diretamente quando `size == 1`. Marcar enxertos com `// FORK:tasks-org`.
      Cobre: RF-03, RF-06, AC-01, AC-02, RNF-01
      Acceptance criteria: `AgendaContent` emite `item(key = "overflow_events_$displayDay")` para `group.overflowEvents.isNotEmpty()` e `item(key = "overflow_tasks_$displayDay")` para `group.overflowTasks.isNotEmpty()` com estados de expansão independentes; `DayEventsPage` agrupa `> 1` eventos de dia todo e `> 1` tarefas de dia todo em cards expansíveis independentes e exibe diretamente quando `== 1`.
      Testes: `app/src/test/kotlin/org/onekash/kashcal/ui/components/AgendaListModelTest.kt` — testes de modelo/agrupamento e compilação/execução da suíte `:app:testDebugUnitTest`

- [ ] T04 — Implementar colapse inline de eventos de dia todo no `DayEventsSheet`
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/ui/components/DayEventsSheet.kt`, `app/src/test/kotlin/org/onekash/kashcal/ui/components/DayEventsSheetSizeTest.kt`
      Mudança: Em `DayEventsSheet.kt`, estender o particionamento `remember(events)` para identificar `allDayEvents` (`it !is DisplayEvent.Task && it.isAllDay`) além de `allDayTasks` (`it is DisplayEvent.Task && it.isAllDay`). Quando `allDayEvents.size > 1`, renderizar o 1º evento de dia todo seguido de `item(key = "day_sheet_event_stack")` com card colapsável inline (`+X eventos`, estado `isEventsExpanded` independente de `isTasksExpanded`) que expande `allDayEvents.drop(1)`, preservando o card `day_sheet_task_stack` para `allDayTasks.size > 1` e os demais eventos com horário. Quando `allDayEvents.size == 1`, exibir o evento diretamente sem card de pilha.
      Cobre: RF-07, AC-03, RNF-01
      Acceptance criteria: `DayEventsSheet.kt` contém estados independentes `isEventsExpanded` e `isTasksExpanded`, renderiza `day_sheet_event_stack` quando `allDayEvents.size > 1` e `day_sheet_task_stack` quando `allDayTasks.size > 1`, e mantém `DayEventsSheetSizeTest` passando.
      Testes: `app/src/test/kotlin/org/onekash/kashcal/ui/components/DayEventsSheetSizeTest.kt` — testes de renderização do `DayEventsSheet` com múltiplos eventos de dia todo e múltiplas tarefas de dia todo verificando colapse e expansão independente

## Phase 2: Consolidação em Grades, Faixa de Dia Todo e Documentação do Fork

Antes de implementar, leia:
1. `AGENTS.md` e `docs/agents/architecture.md` — regras de arquitetura e comandos de build/teste
2. `openspec/changes/all-day-stacking-views/proposal.md`, `openspec/changes/all-day-stacking-views/specs/tasks-org-integration/spec.md` e `openspec/changes/all-day-stacking-views/design.md` — requisitos RIGID (`RF-04`, `RF-08`, `RNF-01`), cenários e decisões técnicas desta fase

- [ ] T05 — Consolidar múltiplos eventos de dia todo de 1 dia em `PlannerDayBody` (`PlannerView.kt`)
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/ui/screens/planner/PlannerView.kt`, `app/src/test/kotlin/org/onekash/kashcal/ui/screens/planner/PlannerViewTest.kt`
      Mudança: Em `PlannerDayBody` (`PlannerView.kt`), separar os itens de `strip` em eventos multi-dia (`it !is DisplayEvent.Task && it.startDay != it.endDay`), eventos de dia todo de 1 único dia (`it !is DisplayEvent.Task && it.startDay == it.endDay`) e tarefas de dia todo (`it is DisplayEvent.Task`). Quando houver `> 1` evento de 1 único dia (`hasSingleDayEventSummary`), consolidá-los em 1 único `PlannerEventSummaryRibbon` (com `testTag("planner_event_summary")` e contagem total) que abre `onDayClick(dayMs())`, exibindo `PlannerRibbon` normal quando houver apenas `1`, preservando `PlannerRibbon` para cada evento multi-dia e `PlannerTaskSummaryRibbon` quando `allDayTasks.size > 1`.
      Cobre: RF-04, AC-04, RNF-01
      Acceptance criteria: `PlannerDayBody` renderiza `PlannerEventSummaryRibbon` (`testTag("planner_event_summary")`) quando houver `> 1` evento de 1 único dia em `strip`, renderiza `PlannerRibbon` normal quando `== 1` ou para eventos multi-dia, e preserva linhas na célula para as reuniões em `rows`.
      Testes: `app/src/test/kotlin/org/onekash/kashcal/ui/screens/planner/PlannerViewTest.kt` — teste verificando que 3 eventos de dia todo de 1 dia + 1 tarefa + 2 reuniões com horário exibem 1 `planner_event_summary`, o ribbon da tarefa única e as 2 reuniões visíveis, acionando `dayClicks` ao clicar

- [ ] T06 — Consolidar múltiplos eventos de 1 dia e tarefas de dia todo no Mês Completo (`MonthFullSpanLayout.kt` e `FullHeightMonthGrid.kt`)
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/ui/screens/monthfull/MonthFullSpanLayout.kt`, `app/src/main/kotlin/org/onekash/kashcal/ui/screens/FullHeightMonthGrid.kt`, `app/src/test/kotlin/org/onekash/kashcal/ui/screens/monthfull/MonthFullSpanLayoutTest.kt`
      Mudança: Em `computeMonthFullWeekRender` (`MonthFullSpanLayout.kt`), mantendo os eventos multi-dia em `computeWeekSpans`, consolidar em cada coluna tanto `> 1` evento de dia todo de 1 único dia (`it !is DisplayEvent.Task && it.isAllDay`) em no máximo 1 slot de resumo de eventos quanto `> 1` tarefa de dia todo (`it is DisplayEvent.Task && it.isAllDay`) em no máximo 1 slot de resumo de tarefas (contabilizando os `N - 1` itens agrupados em `hiddenCounts[col]` e exibindo indicação visual de resumo em `FullHeightMonthGrid.kt`), enquanto `== 1` renderiza o snippet individual do próprio evento ou tarefa normalmente.
      Cobre: RF-04, AC-06, RNF-01
      Acceptance criteria: Em `computeMonthFullWeekRender`, uma célula com `> 1` evento de dia todo de 1 dia ocupa no máximo 1 slot para esses eventos e uma célula com `> 1` tarefa de dia todo ocupa no máximo 1 slot para essas tarefas, preservando slots livres para eventos com horário e mantendo barras `SlotContent.BarSegment` para eventos multi-dia.
      Testes: `app/src/test/kotlin/org/onekash/kashcal/ui/screens/monthfull/MonthFullSpanLayoutTest.kt` — testes unitários cobrindo célula com múltiplos eventos de dia todo de 1 dia + múltiplas tarefas de dia todo + eventos com horário, e célula com apenas 1 evento de dia todo e 1 tarefa de dia todo

- [ ] T07 — Consolidar eventos de 1 dia e tarefas na faixa de dia todo das visões Dia/3 Dias/Semana e filtrar `OverlapListSheet`
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/ui/components/weekview/AllDayStripLayout.kt`, `app/src/main/kotlin/org/onekash/kashcal/ui/components/weekview/WeekViewContent.kt`, `app/src/main/kotlin/org/onekash/kashcal/ui/components/weekview/OverlapListSheet.kt`, `app/src/main/kotlin/org/onekash/kashcal/ui/screens/HomeScreen.kt`, `app/src/test/kotlin/org/onekash/kashcal/ui/components/weekview/AllDayStripLayoutTest.kt`
      Mudança: Em `AllDayStripLayout.kt`, manter `computeAllDaySpans` para eventos multi-dia (`startDay != endDay`) e, em `computeAllDayStripRender`, consolidar por coluna `> 1` evento de dia todo de 1 único dia (`!isTask && isAllDay && startDay == endDay`) em 1 slot de agrupamento de eventos e `> 1` tarefa de dia todo (`isTask && isAllDay`) em 1 slot de agrupamento de tarefas (mantendo `AllDaySlot.CellEvent` individual quando `== 1` e expandindo itens caso o grupo caia em `overflowByColumn`). Em `WeekViewContent.kt` e `HomeScreen.kt`, repassar `onTaskCheckedChange: OnTaskCheckedChange? = null`, renderizar os chips de agrupamento na faixa de dia todo e, ao clicar em um chip de agrupamento (de eventos ou de tarefas), abrir `OverlapListSheet` apenas com os itens daquele agrupamento. Em `OverlapListSheet.kt`, renderizar `TaskCard` com checkbox interativo (`onTaskCheckedChange`) quando o item for `DisplayEvent.Task` e permitir clicar em qualquer item para abrir seu `QuickViewSheet` e fechar o sheet.
      Cobre: RF-08, AC-05, RNF-01
      Acceptance criteria: `computeAllDayStripRender` preserva `AllDaySlot.BarSegment` para multi-dia e gera 1 slot consolidado quando há `> 1` evento de 1 dia e 1 slot consolidado quando há `> 1` tarefa de dia todo (ou `AllDaySlot.CellEvent` quando `== 1`); clicar em cada chip de agrupamento abre `OverlapListSheet` apenas com os itens daquela categoria e renderiza `TaskCard` com `onTaskCheckedChange` para tarefas.
      Testes: `app/src/test/kotlin/org/onekash/kashcal/ui/components/weekview/AllDayStripLayoutTest.kt` — testes unitários para coluna com 1 evento multi-dia + 3 eventos de 1 dia + 2 tarefas de dia todo, coluna com 1 evento de 1 dia e 1 tarefa de dia todo, e comportamento de overflow quando `maxRows = 1`

- [ ] T08 — Atualizar `docs/fork/FORK_INTEGRATION.md` e validar isolamento de fork e suíte de testes
      Arquivos: `docs/fork/FORK_INTEGRATION.md`
      Mudança: Atualizar `docs/fork/FORK_INTEGRATION.md` registrando os novos pontos de enxerto `// FORK:tasks-org` adicionados em `AgendaListModel.kt`, `HomeScreen.kt`, `DayEventsSheet.kt`, `PlannerView.kt`, `AllDayStripLayout.kt`, `WeekViewContent.kt`, `OverlapListSheet.kt`, `MonthFullSpanLayout.kt` e `FullHeightMonthGrid.kt`, e verificar que todas as adições em arquivos de core possuem `// FORK:tasks-org`.
      Cobre: RNF-01, AC-07
      Acceptance criteria: `docs/fork/FORK_INTEGRATION.md` documenta todos os pontos de enxerto de empilhamento/consolidação e `./gradlew testDebugUnitTest --no-daemon` executa com 100% de aprovação.
      Testes: `./gradlew testDebugUnitTest --no-daemon` — execução completa dos testes unitários do projeto
