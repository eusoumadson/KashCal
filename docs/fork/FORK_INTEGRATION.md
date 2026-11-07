# KashCal Fork Integration Map (Mapa Unificado de Pontos de Enxerto)

Este documento mapeia todos os pontos de enxerto (*graft points*) introduzidos no fork do KashCal (`eusoumadson/KashCal`) em relação ao upstream original (`KashCal/KashCal`).

Todos os pontos de enxerto no código-fonte compartilhado com o upstream são marcados com comentários rastreáveis (`// FORK:<feature>`), garantindo facilidade de rebase, auditoria e isolamento de comportamento.

---

## 1. Princípios de Arquitetura do Fork

1. **Não-Regressão e Paridade com Upstream**: O comportamento padrão do upstream é estritamente preservado. O recurso de integração com o Tasks.org é protegido por toggle de configuração (*feature flag*).
2. **Não-Persistência de Tarefas no Room**: Tarefas do Tasks.org residem exclusivamente no seu próprio ContentProvider e são convertidas em memória para `DisplayEvent.Task`. Nenhuma tarefa é persistida nas tabelas do banco de dados Room do KashCal.
3. **Isolamento de Mutação**: Alterações no estado de tarefas disparam atualizações através do `TasksOrgCoordinator` e sincronização assíncrona, mantendo a fila `PendingOperations` do KashCal restrita a eventos CalDAV.
4. **Comentários de Enxerto Rastreados**: Qualquer modificação em arquivos do core ou UI upstream contém `// FORK:tasks-org`, `// FORK:travel-time`, `// FORK:tasks-widget` ou `// FORK:auto-backup`.

---

## 2. Pontos de Enxerto: Empilhamento de Dia Todo nas Visões (`// FORK:tasks-org`)

A especificação `all-day-stacking-views` introduz agrupamento e empilhamento compacto de eventos de dia todo e tarefas nas visões Agenda, Gaveta do Dia (Drawer), Planejador (Planner), Mês Completo e Faixa de Dia Todo (Week/Day/3-Day).

### 2.1 Agenda e Drawer (Lista Linear)

- **`app/src/main/kotlin/org/onekash/kashcal/ui/components/AgendaListModel.kt`**
  - **Tag**: `// FORK:tasks-org`
  - **Função**: `buildAgendaDayGroups` e data class `AgendaDayGroup`
  - **Mudança**: Separa itens de dia todo em `overflowEvents` (quando > 1 evento de dia todo) e `overflowTasks` (quando > 1 tarefa de dia todo). Mantém 1 item visível no topo e agrupa os excedentes para renderização colapsada.

- **`app/src/main/kotlin/org/onekash/kashcal/ui/screens/HomeScreen.kt`**
  - **Tag**: `// FORK:tasks-org`
  - **Função**: `AgendaListContent`, `AgendaDayCard`, `AgendaCompactAllDayGroupCard`
  - **Mudança**: Renderiza cartões de pilha expansíveis independentes para eventos de dia todo (`+N eventos de dia todo`) e tarefas (`+N tarefas`), com estados de expansão inline por dia (`expandedDays`, `expandedEventDays`). Passa `onTaskCheckedChange` para a visualização semanal (`WeekViewContent`).

- **`app/src/main/kotlin/org/onekash/kashcal/ui/components/DayEventsSheet.kt`**
  - **Tag**: `// FORK:tasks-org`
  - **Função**: `DayEventsSheet`
  - **Mudança**: Agrupa eventos de dia todo e tarefas na gaveta de detalhes do dia com cartões colapsáveis inline idênticos à Agenda, evitando rolagem excessiva.

---

### 2.2 Grade do Planejador (`PlannerView`)

- **`app/src/main/kotlin/org/onekash/kashcal/ui/screens/planner/PlannerView.kt`**
  - **Tag**: `// FORK:tasks-org`
  - **Função**: `PlannerDayBody`, `PlannerEventSummaryRibbon`, `PlannerTaskSummaryRibbon`
  - **Mudança**:
    - Separa itens da faixa do dia em: eventos multi-dia (`startDay != endDay`), eventos de dia todo de 1 único dia (`startDay == endDay`), e tarefas de dia todo.
    - Quando houver `> 1` evento de 1 único dia, consolida em um único ribbon `PlannerEventSummaryRibbon` (`testTag("planner_event_summary")`) que abre o dia correspondente ao ser clicado (`onDayClick(dayMs())`).
    - Quando houver `> 1` tarefa de dia todo, consolida em `PlannerTaskSummaryRibbon`.
    - Eventos multi-dia e itens unitários continuam usando `PlannerRibbon` individual, preservando linhas para compromissos com horário.

---

### 2.3 Faixa de Dia Todo nas Visões Dia / 3 Dias / Semana (`AllDayStripLayout`, `WeekViewContent`, `OverlapListSheet`)

- **`app/src/main/kotlin/org/onekash/kashcal/ui/components/weekview/AllDayStripLayout.kt`**
  - **Tag**: `// FORK:tasks-org`
  - **Função**: `AllDaySlot`, `computeAllDayStripRender`
  - **Mudança**:
    - Adiciona variantes de slot `AllDaySlot.EventGroupSummary(events)` e `AllDaySlot.TaskGroupSummary(tasks)`.
    - `computeAllDaySpans` permanece responsável exclusivamente por barras de eventos multi-dia.
    - Por coluna, quando houver `> 1` evento de dia todo de 1 único dia, consolida em `EventGroupSummary`.
    - Por coluna, quando houver `> 1` tarefa de dia todo, consolida em `TaskGroupSummary`.
    - Se houver apenas 1 item de qualquer categoria, renderiza `AllDaySlot.CellEvent` individual.
    - Caso os grupos não caibam nas linhas disponíveis (`maxRows`), expande seus itens constituintes em `ColumnOverflow.events` para abertura no sheet "+N".

- **`app/src/main/kotlin/org/onekash/kashcal/ui/components/weekview/WeekViewContent.kt`**
  - **Tag**: `// FORK:tasks-org`
  - **Função**: `WeekViewContent`, `TimeGridPager`, `AllDayEventsPagerRow`, `AllDayGroupChip`
  - **Mudança**:
    - Repassa callback `onTaskCheckedChange: OnTaskCheckedChange? = null` até `AllDayEventsPagerRow`.
    - Renderiza `AllDayGroupChip` estilizado e compacto para `EventGroupSummary` e `TaskGroupSummary`.
    - Ao clicar em um chip de resumo, abre `OverlapListSheet` filtrado estritamente com os itens daquele grupo específico.

- **`app/src/main/kotlin/org/onekash/kashcal/ui/components/weekview/OverlapListSheet.kt`**
  - **Tag**: `// FORK:tasks-org`
  - **Função**: `OverlapListSheet`
  - **Mudança**:
    - Recebe `onTaskCheckedChange: OnTaskCheckedChange? = null`.
    - Renderiza `TaskCard` com checkbox interativo quando o item for `DisplayEvent.Task`.
    - Ajusta o título para `label_more_tasks_compact` quando a lista contiver apenas tarefas.
    - Permite clicar em qualquer item para exibir detalhes no `QuickViewSheet`.

---

### 2.4 Grade do Mês Completo (`MonthFullSpanLayout`, `FullHeightMonthGrid`)

- **`app/src/main/kotlin/org/onekash/kashcal/ui/screens/monthfull/MonthFullSpanLayout.kt`**
  - **Tag**: `// FORK:tasks-org`
  - **Função**: `computeMonthFullWeekRender`
  - **Mudança**:
    - Mantém `computeWeekSpans` para barras multi-dia.
    - Em cada coluna (dia), consolida múltiplos eventos de 1 dia (`> 1`) em no máximo 1 slot de resumo de eventos.
    - Consolida múltiplas tarefas de dia todo (`> 1`) em no máximo 1 slot de resumo de tarefas.
    - Contabiliza os itens excedentes em `hiddenCounts[col]`.
    - Itens unitários (`== 1`) recebem snippet individual normal, preservando slots para eventos com horário.

- **`app/src/main/kotlin/org/onekash/kashcal/ui/screens/FullHeightMonthGrid.kt`**
  - **Tag**: `// FORK:tasks-org`
  - **Função**: `MonthCellContent`
  - **Mudança**:
    - Exibe indicação de overflow na célula do mês refletindo a contagem consolidada de eventos e tarefas ocultos.

---

## 3. Pontos de Enxerto Preexistentes no Fork

### 3.1 Tasks.org Integration (`// FORK:tasks-org`)
- **`app/src/main/kotlin/org/onekash/kashcal/MainActivity.kt`**: Integração de sheets de tarefas (`TaskQuickViewSheet`, `TaskEditorSheet`), toggle do FAB de tarefas e interceptação de cliques em `DisplayEvent.Task`.
- **`app/src/main/kotlin/org/onekash/kashcal/di/RepositoryModule.kt`**: Provisão de `AndroidTasksOrgRepository` via Hilt.
- **`app/src/main/kotlin/org/onekash/kashcal/ui/viewmodels/HomeViewModel.kt`**: Coordenação de tarefas com o ciclo de vida da tela principal e persistência de preferências de integração.
- **`app/src/main/kotlin/org/onekash/kashcal/ui/components/TaskCard.kt`**: Card de tarefa com checkbox interativo, badges de prioridade e suporte a opacidade de-enfatizada (`DEEMPHASIZED_ALPHA = 0.65f`) para tarefas concluídas em conformidade com as regras de contraste WCAG AA.

### 3.2 Departure Alerts / Travel Time (`// FORK:travel-time`)
- **`app/src/main/kotlin/org/onekash/kashcal/data/db/KashCalDatabase.kt`**: Versão do banco incrementada para 28.
- **`app/src/main/kotlin/org/onekash/kashcal/data/db/migration/Migrations.kt`**: `MIGRATION_27_28` para colunas `travel_mode`, `travel_depart_buffer_min`, `travel_origin_fixed` e `reminder_type`.
- **`app/src/main/kotlin/org/onekash/kashcal/data/db/dao/EventsDao.kt`** e **`ScheduledRemindersDao.kt`**: Consultas para lembretes de tempo de viagem.
- **`app/src/main/kotlin/org/onekash/kashcal/reminder/scheduler/ReminderScheduler.kt`**: Em `reconcileRemindersForEvent`, preserva linhas com `reminderType != ReminderType.TIME_BEFORE` (`TRAVEL_DEPART`, `TRAVEL_BUFFER`), evitando que alarmes de viagem calculados sejam descartados durante reconciliações de sincronização CalDAV ou edições locais.

### 3.3 Widgets e Auto-Backup (`// FORK:tasks-widget`, `// FORK:auto-backup`)
- **`app/src/main/kotlin/org/onekash/kashcal/widget/WidgetDataRepository.kt`**: Inclusão opcional de tarefas nos dados fornecidos aos widgets da tela inicial.
- **`app/src/main/kotlin/org/onekash/kashcal/domain/backup/ForkBackupPreferences.kt`**: Serialização e restauração das preferências exclusivas do fork no fluxo de backup automático.

### 3.4 Identidade do Fork (`// FORK:fork-identity`)
- **`app/src/main/kotlin/org/onekash/kashcal/ui/components/AboutSheet.kt`**: Seções do projeto atual (`FORK_SOURCE`, `FORK_ISSUES`) e reconhecimento ao upstream (`HELP`, `SOURCE`, `DONATE`).
- **`app/src/main/kotlin/org/onekash/kashcal/ui/components/AppInfoSheet.kt`**: Cabeçalho `Abacate Calendário 🥑` e link do repositório do fork, mantendo doação e site upstream.
- **`app/src/main/kotlin/org/onekash/kashcal/util/ExternalLinks.kt`**: Constantes `FORK_SOURCE` e `FORK_ISSUES`.
- **`app/src/main/kotlin/org/onekash/kashcal/ui/components/CalendarDrawer.kt`** e **`values*/strings.xml`**: Rótulo minimalista `Calendário` (`R.string.app_name`).

---

## 4. Rastreabilidade e Manutenção

Ao realizar rebases em relação ao upstream:
1. Buscar ocorrências de conflito nos arquivos listados acima.
2. Conferir se novos arquivos ou funções upstream interferem nos cálculos de `AgendaListModel`, `computeAllDayStripRender` ou `computeMonthFullWeekRender`.
3. Executar sempre `make test` (`./gradlew testDebugUnitTest --no-daemon`) para certificar conformidade.
