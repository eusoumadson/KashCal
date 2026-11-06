# Proposal: all-day-stacking-views

## Metadata
- **Tier**: standard
- **Architecture References**: `AGENTS.md`, `docs/agents/architecture.md`, `docs/agents/domain_rules.md`, `docs/agents/coding_guidelines.md`, `docs/fork/FORK_INTEGRATION.md`
- **Capability Policy**: `ignore_for_this_run`

## Why

Atualmente, o empilhamento de itens de dia todo está inconsistente entre as visualizações do aplicativo: enquanto o widget já empilha tarefas e eventos de dia todo (1º item + "+N") e a Agenda/DayEventsSheet/Planejador empilham apenas tarefas de dia todo, múltiplos eventos de dia todo ainda saturam verticalmente a Agenda, a visão Mês (`DayEventsPage`), o `DayEventsSheet`, o Planejador, a faixa de dia todo das visões Dia/3 Dias/Semana e as células da visão Mês Completo (além de tarefas de dia todo desagrupadas na visão Mês, na faixa de Dia/3 Dias/Semana e no Mês Completo). Unificar as regras de empilhamento por colapse inline nas listas/drawers e por resumo consolidado nos grids preserva o espaço visual para compromissos com horário marcado e garante paridade comportamental em todas as visões.

## Scope
- **In**:
  - Empilhamento inline com colapse (1º item + card expansível `+N`) para eventos de dia todo (`!isTask && isAllDay`) e tarefas de dia todo (`isTask && isAllDay`) nas visões baseadas em lista/cards: Agenda (`AgendaListModel.kt`, `HomeScreen.kt`), Mês (`DayEventsPage` em `HomeScreen.kt`) e Drawer/Bottom Sheet de Dia (`DayEventsSheet.kt`).
  - Consolidação em resumo único quando `> 1` (e exibição individual quando `== 1`) para eventos de dia todo de 1 único dia (`startDay == endDay`) e tarefas de dia todo nas visões baseadas em grade/colunas: Planejador (`PlannerView.kt`), Dia / 3 Dias / Semana (`AllDayStripLayout.kt`, `WeekViewContent.kt`, `OverlapListSheet.kt`) e Mês Completo (`MonthFullSpanLayout.kt`, `FullHeightMonthGrid.kt`), preservando barras contínuas para eventos multi-dia (`startDay != endDay`).
  - Abertura de bottom sheet filtrado (`OverlapListSheet.kt`) ao clicar em um chip de agrupamento na faixa de dia todo das visões Dia, 3 Dias e Semana, exibindo exclusivamente os itens daquele agrupamento (eventos ou tarefas, com suporte a checkbox interativo e abertura de `QuickViewSheet`).
  - Isolamento de fork (`// FORK:tasks-org`, respeito a `tasksOrgEnabled`), atualização de `docs/fork/FORK_INTEGRATION.md` e localização em `values/strings.xml`, `values-pt/strings.xml` e `values-pt-rBR/strings.xml`.
- **Out**:
  - Alterações nos widgets de tela inicial (Glance), onde o empilhamento de eventos e tarefas de dia todo já opera conforme esperado.
  - Mudanças na persistência Room, sincronização CalDAV ou provedor `content://org.tasks.api/v0`.
  - Agrupamento de eventos multi-dia (`startDay != endDay`) nos grids de Semana/Dia/3 Dias, Planejador e Mês Completo (que permanecem como barras horizontais contínuas).

## What Changes

- **Visão Agenda (`AgendaListModel.kt`, `HomeScreen.kt`)**: Estende `AgendaDayGroup` e `buildAgendaListModel` para agrupar eventos de dia todo excedentes (`> 1`) em `overflowEvents` (mantendo o 1º evento visível em `items` e criando um card expansível `AgendaEventStackCard`), preservando o cálculo exato de `headerIndexByDayCode` para rolagem da barra semanal, em paridade com `overflowTasks` (`AgendaTaskStackCard`).
- **Visão Mês (`DayEventsPage` em `HomeScreen.kt`)**: Aplica a regra de colapse inline na lista diária abaixo do calendário mensal tanto para eventos de dia todo (`> 1` -> 1º evento + card expansível `+X eventos`) quanto para tarefas de dia todo (`> 1` -> 1ª tarefa + card expansível `+X tarefas`), exibindo o item único diretamente quando `== 1`.
- **Drawer / Bottom Sheet de Dia (`DayEventsSheet.kt`)**: Adiciona o colapse inline de eventos de dia todo (`> 1` -> 1º evento + card expansível `+X eventos`) ao lado do colapse já existente de tarefas de dia todo (`+X tarefas`).
- **Visão Planejador (`PlannerView.kt`)**: Em `PlannerDayBody`, consolida eventos de dia todo de 1 único dia (`startDay == endDay`) quando `> 1` em um único `PlannerEventSummaryRibbon` (exibindo o evento normalmente quando `== 1`), liberando linhas da célula para eventos com horário e abrindo o `DayEventsSheet` ao tocar.
- **Visões Dia, 3 Dias e Semana (`AllDayStripLayout.kt`, `WeekViewContent.kt`, `OverlapListSheet.kt`)**: Na faixa de dia todo de cada coluna, mantém `AllDaySpan` para eventos multi-dia e consolida eventos de dia todo de 1 único dia (`> 1`) em 1 chip de agrupamento de eventos e tarefas de dia todo (`> 1`) em 1 chip de agrupamento de tarefas (exibindo o item individual quando `== 1`). Tocar em um chip de agrupamento abre o sheet exibindo apenas os itens daquele agrupamento, com suporte a `TaskCard` / `OnTaskCheckedChange` para tarefas e abertura do `QuickViewSheet` correspondente.
- **Visão Mês Completo (`MonthFullSpanLayout.kt`, `FullHeightMonthGrid.kt`)**: Em `computeMonthFullWeekRender` e `FullHeightMonthGrid`, mantendo barras contínuas para eventos multi-dia, consolida `> 1` eventos de dia todo de 1 único dia em 1 slot de resumo de eventos e `> 1` tarefas de dia todo em 1 slot de resumo de tarefas por célula (exibindo o snippet individual quando `== 1`). Tocar na célula abre o `DayEventsSheet` com todos os registros do dia aplicando o colapse inline.
- **Recursos e Documentação de Fork**: Adiciona strings localizadas em `values/strings.xml`, `values-pt/strings.xml` e `values-pt-rBR/strings.xml` e atualiza `docs/fork/FORK_INTEGRATION.md`.

## Capabilities

### New Capabilities
<!-- Nenhuma nova capability isolada; estende a especificação existente em tasks-org-integration -->

### Modified Capabilities
- `tasks-org-integration`: Atualiza `RF-03` e `RF-04` e adiciona `RF-06`, `RF-07`, `RF-08`, `RF-09` e `RNF-01` para cobrir o empilhamento unificado de eventos de dia todo e tarefas de dia todo nas visões de Agenda, Mês (`DayEventsPage`), `DayEventsSheet`, Planejador, Dia/3 Dias/Semana e Mês Completo.

## Confirmed Acceptance Criteria (Source of Truth)
- **AC-01 (Visão Agenda — Eventos e Tarefas de Dia Todo)**: Em `AgendaListModel.kt` e `HomeScreen.kt` (`AgendaContent`), quando um dia possuir > 1 evento de dia todo (`!isTask && isAllDay`), o sistema deve exibir o 1º evento de dia todo e agrupar os excedentes (`N - 1`) em um card colapsável inline (`📅 +X eventos` / `AgendaEventStackCard`), preservando o cálculo exato de `headerIndexByDayCode` para rolagem da barra semanal, além de manter o agrupamento existente para tarefas de dia todo (`✓ +X tarefas`). Caso haja apenas 1, exibe-o diretamente sem card de pilha.
- **AC-02 (Visão Mês — `DayEventsPage`)**: Em `HomeScreen.kt` (`DayEventsPage`), a lista diária abaixo do calendário mensal deve aplicar a mesma regra de colapse inline da Agenda tanto para eventos de dia todo (`> 1` -> 1º evento + card expansível `+X eventos`) quanto para tarefas de dia todo (`> 1` -> 1ª tarefa + card expansível `+X tarefas`), exibindo o item único diretamente quando `== 1`.
- **AC-03 (Drawer / Bottom Sheet de Dia — `DayEventsSheet`)**: Em `DayEventsSheet.kt` (aberto ao clicar em um dia no Planejador ou no Mês Completo), além do colapse já existente para tarefas de dia todo, quando houver `> 1` evento de dia todo, deve exibir o 1º evento e agrupar os demais em um card colapsável inline (`+X eventos`), mantendo paridade visual e comportamental com Agenda e Mês.
- **AC-04 (Visão Planejador — `PlannerView`)**: Em `PlannerView.kt` (`PlannerDayBody`), quando um dia tiver `> 1` evento de dia todo de 1 único dia (`startDay == endDay`), eles devem ser consolidados em um único ribbon de resumo (`PlannerEventSummaryRibbon` com contagem total), exibindo o evento normalmente caso haja apenas `1` (assim como já ocorre para `allDayTasks`), liberando espaço vertical para os eventos com horário e abrindo o `DayEventsSheet` ao clicar. Eventos multi-dia continuam sendo exibidos normalmente.
- **AC-05 (Visões Dia, 3 Dias e Semana — Faixa de Dia Todo e Sheet Filtrado)**: Em `AllDayStripLayout.kt`, `WeekViewContent.kt` e `OverlapListSheet.kt`, na área reservada a itens de dia todo de cada coluna:
  - Eventos multi-dia permanecem como barras horizontais contínuas (`AllDaySpan`).
  - Se houver `> 1` evento de dia todo de 1 único dia (`!isTask && startDay == endDay`), consolidá-los em um único chip de agrupamento na coluna; se `== 1`, exibir o chip do evento.
  - Se houver `> 1` tarefa de dia todo (`isTask && isAllDay`), consolidá-las em um único chip de agrupamento na coluna; se `== 1`, exibir o chip da tarefa.
  - Ao clicar em um chip de agrupamento (de eventos ou de tarefas), abrir o bottom sheet exibindo apenas os itens daquele agrupamento clicado, com suporte a checkbox interativo (`TaskCard` / `OnTaskCheckedChange`) para tarefas e abertura do `QuickViewSheet` correspondente ao tocar em um item.
- **AC-06 (Visão Mês Completo — `MonthFullSpanLayout` e `FullHeightMonthGrid`)**: Em `MonthFullSpanLayout.kt` e `FullHeightMonthGrid.kt`, mantendo barras contínuas para eventos multi-dia:
  - Quando uma célula tiver `> 1` evento de dia todo de 1 único dia, consolidá-los em um único slot de resumo de eventos de dia todo; quando `== 1`, exibir o snippet do próprio evento.
  - Quando uma célula tiver `> 1` tarefa de dia todo, consolidá-las em um único slot de resumo de tarefas de dia todo; quando `== 1`, exibir o snippet da própria tarefa.
  - Ao tocar na célula do dia, abre o `DayEventsSheet` exibindo todos os registros do dia com a regra de colapse inline (AC-03).
- **AC-07 (Isolamento de Fork e Localização)**: O empilhamento de eventos de dia todo funciona independentemente do toggle do Tasks.org, enquanto toda lógica/UI exclusiva de `DisplayEvent.Task` respeita `tasksOrgEnabled` e mantém marcação `// FORK:tasks-org` (com atualização de `FORK_INTEGRATION.md` e strings em `values/strings.xml`, `values-pt/strings.xml` e `values-pt-rBR/strings.xml`).

## Impact
- **Camada de UI (`app/src/main/kotlin/org/onekash/kashcal/ui/`)**:
  - `components/AgendaListModel.kt`
  - `screens/HomeScreen.kt` (`AgendaContent`, `DayEventsPage`, `AgendaTaskStackCard`, `AgendaEventStackCard`)
  - `components/DayEventsSheet.kt`
  - `screens/planner/PlannerView.kt` (`PlannerDayBody`, `PlannerEventSummaryRibbon`)
  - `components/weekview/AllDayStripLayout.kt`, `components/weekview/WeekViewContent.kt`, `components/weekview/OverlapListSheet.kt`
  - `screens/monthfull/MonthFullSpanLayout.kt`, `screens/FullHeightMonthGrid.kt`
- **Recursos e Documentação**:
  - `app/src/main/res/values/strings.xml`, `app/src/fork/res/values-pt/strings.xml`, `app/src/fork/res/values-pt-rBR/strings.xml`
  - `docs/fork/FORK_INTEGRATION.md`
- **Testes Unitários (`app/src/test/kotlin/org/onekash/kashcal/ui/`)**:
  - Testes de modelo/renderização para `AgendaListModel`, `AllDayStripLayout`, `MonthFullSpanLayout` e componentes de empilhamento.
