# Spec Delta: tasks-org-integration

## MODIFIED Requirements

### Requirement: RF-03 - Empilhamento e Proteção de Timeline na Visão de Agenda
WHEN a Agenda for renderizada via `buildAgendaListModel` (`AgendaListModel.kt:44`) e `AgendaContent` (`HomeScreen.kt:2312`), IF houver `> 1` evento de dia todo (`!isTask && isAllDay`) ou `> 1` tarefa de dia todo (`isTask && isAllDay`), THE SYSTEM SHALL exibir o 1º item da categoria e agrupar os excedentes em um card colapsável inline (`+X eventos` / `+X tarefas`), somando 1 índice por pilha em `headerIndexByDayCode` (`AgendaListModel.kt:95`), e exibindo o item diretamente quando `== 1`.

#### Scenario: Dia com múltiplas tarefas sem horário na tela de Agenda
- **WHEN** o usuário visualizar na Agenda um dia contendo 8 tarefas sem horário e 2 reuniões com hora
- **THEN** a Agenda exibe o card da 1ª tarefa e um card condensado `✓ +7 tarefas para hoje`, exibindo os cards das 2 reuniões logo abaixo.

#### Scenario: Expansão inline ao tocar na pilha de tarefas da Agenda
- **WHEN** o usuário toca no card condensado `+X tarefas` na Agenda
- **THEN** a lista expande inline os cards das tarefas restantes daquele dia.

#### Scenario: Dia com múltiplos eventos de dia todo e tarefas de dia todo na Agenda
- **WHEN** o usuário visualizar na Agenda um dia contendo 3 eventos de dia todo, 4 tarefas de dia todo e 2 reuniões com horário marcado
- **THEN** a Agenda exibe o 1º evento de dia todo seguido de 1 card colapsável `+2 eventos`, a 1ª tarefa de dia todo seguida de 1 card colapsável `+3 tarefas`, e os cards das 2 reuniões, mantendo `headerIndexByDayCode` exato para os dias seguintes.

#### Scenario: Dia com apenas 1 evento de dia todo e 1 tarefa de dia todo na Agenda
- **WHEN** o usuário visualizar na Agenda um dia contendo 1 evento de dia todo e 1 tarefa de dia todo
- **THEN** o sistema renderiza o card do evento de dia todo e o card da tarefa de dia todo diretamente, sem exibir cards de agrupamento.

### Requirement: RF-04 - Consolidação de Tarefas de Dia Todo no Planejador e no Mês
WHEN um dia for renderizado em `PlannerDayBody` (`PlannerView.kt:390`) ou no Mês Completo (`MonthFullSpanLayout.kt:169`, `FullHeightMonthGrid.kt:148`), mantendo eventos multi-dia em `computeWeekSpans` (`MonthFullSpanLayout.kt:31`), IF houver `> 1` evento de dia todo de 1 dia (`!isTask && startDay == endDay`) ou `> 1` tarefa de dia todo, THE SYSTEM SHALL consolidar cada categoria com `> 1` item em 1 único ribbon/slot de resumo com contagem total, e exibir o item individualmente quando `== 1`.

#### Scenario: Visualização do dia com tarefas e reuniões no Planejador
- **WHEN** um dia no Planejador possuir 8 tarefas sem horário e 2 reuniões com horário marcado
- **THEN** o Planejador não esgota os slots com 8 ribbons de tarefas, exibindo uma linha consolidada de tarefas e exibindo as reuniões com horário nas linhas subsequentes.

#### Scenario: Dia no Planejador com múltiplos eventos de dia todo de 1 dia e reuniões com horário
- **WHEN** um dia no Planejador possuir 3 eventos de dia todo de 1 único dia, 1 tarefa sem horário e 2 reuniões com horário marcado
- **THEN** `PlannerDayBody` exibe 1 ribbon consolidado de resumo para os 3 eventos de dia todo, o ribbon individual da única tarefa de dia todo e preserva as linhas subsequentes para as 2 reuniões com horário, abrindo o `DayEventsSheet` ao clicar.

#### Scenario: Célula no Mês Completo com evento único versus múltiplos itens de dia todo
- **WHEN** uma célula do Mês Completo possuir 2 eventos de dia todo de 1 único dia e 3 tarefas de dia todo
- **THEN** a célula exibe 1 slot consolidado de resumo para os 2 eventos de dia todo e 1 slot consolidado de resumo para as 3 tarefas de dia todo, abrindo o `DayEventsSheet` ao tocar na célula.

## ADDED Requirements

### Requirement: RF-06 - Empilhamento Inline de Eventos e Tarefas de Dia Todo na Visão Mês (`DayEventsPage`)
WHEN a lista diária abaixo do calendário mensal for renderizada via `DayEventsPage` (verified at `HomeScreen.kt:2017`), IF o dia selecionado possuir `> 1` evento de dia todo (`!isTask && isAllDay`) ou `> 1` tarefa de dia todo (`isTask && isAllDay`), THE SYSTEM SHALL exibir o 1º item da respectiva categoria e agrupar os excedentes em um card colapsável inline (`+X eventos` e/ou `+X tarefas`), exibindo o item diretamente sem pilha quando a contagem da categoria for `== 1`.

#### Scenario: Dia selecionado na visão Mês com múltiplos eventos e tarefas de dia todo
- **WHEN** o usuário seleciona um dia na visão Mês (`DayEventsPage`) que contém 4 eventos de dia todo, 3 tarefas de dia todo e 1 evento com horário
- **THEN** a lista abaixo do calendário exibe o 1º evento de dia todo + card colapsável `+3 eventos`, a 1ª tarefa de dia todo + card colapsável `+2 tarefas`, e o card do evento com horário.

#### Scenario: Expansão e recolhimento inline na visão Mês (`DayEventsPage`)
- **WHEN** o usuário toca no card `+3 eventos` ou `+2 tarefas` em `DayEventsPage`
- **THEN** os itens agrupados daquela categoria expandem inline na lista do dia e podem ser recolhidos novamente sem afetar o estado de expansão da outra categoria.

### Requirement: RF-07 - Empilhamento Inline de Eventos e Tarefas de Dia Todo no `DayEventsSheet`
WHEN o sheet de eventos do dia for aberto via `DayEventsSheet` (verified at `DayEventsSheet.kt:65`) a partir do Planejador ou do Mês Completo, IF houver `> 1` evento de dia todo (`!isTask && isAllDay`), THE SYSTEM SHALL renderizar o 1º evento de dia todo e agrupar os `N - 1` eventos excedentes em um card colapsável inline (`+X eventos`), além de manter o colapse inline para `> 1` tarefa de dia todo (`+X tarefas`, `DayEventsSheet.kt:97`), exibindo o item diretamente quando `== 1`.

#### Scenario: Abertura do `DayEventsSheet` em dia com múltiplos eventos e tarefas de dia todo
- **WHEN** o usuário clica em um dia no Planejador ou no Mês Completo que possui 3 eventos de dia todo e 2 tarefas de dia todo
- **THEN** o `DayEventsSheet` abre exibindo o 1º evento de dia todo seguido do card colapsável `+2 eventos` e a 1ª tarefa de dia todo seguida do card colapsável `+1 tarefa`.

#### Scenario: Interação independente com as pilhas de eventos e tarefas no `DayEventsSheet`
- **WHEN** o usuário toca no card `+2 eventos` dentro do `DayEventsSheet`
- **THEN** apenas os 2 eventos de dia todo excedentes são expandidos inline, mantendo a pilha de tarefas recolhida até que seja acionada.

### Requirement: RF-08 - Consolidação na Faixa de Dia Todo e Sheet Filtrado nas Visões Dia, 3 Dias e Semana
WHEN a faixa de dia todo das visões Dia, 3 Dias e Semana for renderizada (`AllDayStripLayout.kt:89`, `WeekViewContent.kt:154`), THE SYSTEM SHALL manter eventos multi-dia em `AllDaySpan`, consolidar `> 1` evento de 1 dia (`!isTask && startDay == endDay`) em 1 chip de eventos e `> 1` tarefa de dia todo em 1 chip de tarefas (ou exibir o chip individual se `== 1`), abrindo `OverlapListSheet` (`OverlapListSheet.kt:35`) filtrado ao clicar.

#### Scenario: Coluna na visão Semana com 1 evento multi-dia, 3 eventos de 1 dia e 2 tarefas de dia todo
- **WHEN** uma coluna da visão Semana (ou Dia / 3 Dias) tiver 1 evento multi-dia posicionado em `AllDaySpan`, 3 eventos de dia todo de 1 único dia e 2 tarefas de dia todo
- **THEN** a coluna exibe a barra contínua do evento multi-dia, 1 chip consolidado representando os 3 eventos de dia todo e 1 chip consolidado representando as 2 tarefas de dia todo.

#### Scenario: Clique no chip de agrupamento de tarefas de dia todo na visão Semana
- **WHEN** o usuário toca no chip de agrupamento de tarefas de dia todo de uma coluna na visão Dia, 3 Dias ou Semana
- **THEN** o bottom sheet abre listando apenas as tarefas de dia todo daquele agrupamento (sem misturar os eventos de dia todo da mesma coluna), renderizadas via `TaskCard` (`TaskCard.kt:99`) com checkbox interativo (`OnTaskCheckedChange`) e abertura do `TaskQuickViewSheet` ao tocar.

#### Scenario: Clique no chip de agrupamento de eventos de dia todo na visão Semana
- **WHEN** o usuário toca no chip de agrupamento de eventos de dia todo de uma coluna na visão Dia, 3 Dias ou Semana
- **THEN** o bottom sheet abre listando apenas os eventos de dia todo daquele agrupamento (sem misturar as tarefas de dia todo da mesma coluna), permitindo tocar em qualquer evento para abrir o `EventQuickViewSheet`.

### Requirement: RNF-01 - Isolamento de Enxerto do Fork, Gating por `tasksOrgEnabled` e Localização
WHILE `tasksOrgEnabled == false`, THE SYSTEM SHALL aplicar normalmente o empilhamento e a consolidação de eventos de dia todo (`RF-03`, `RF-04`, `RF-06`, `RF-07`, `RF-08`) sem exibir elementos de `DisplayEvent.Task`, mantendo marcação `// FORK:tasks-org` nos enxertos de core, registro em `docs/fork/FORK_INTEGRATION.md` e localização simultânea em `values/strings.xml`, `values-pt/strings.xml` e `values-pt-rBR/strings.xml`.

#### Scenario: Empilhamento de eventos de dia todo com integração Tasks.org desativada
- **WHEN** a preferência `tasks_org_enabled` estiver definida como `false` e um dia possuir 3 eventos de dia todo
- **THEN** o sistema empilha/consolida os 3 eventos de dia todo normalmente na Agenda, Mês (`DayEventsPage`), `DayEventsSheet`, Planejador, Dia/3 Dias/Semana e Mês Completo, sem exibir qualquer elemento de tarefas.
