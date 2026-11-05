# Tasks.org Integration Specification

## Purpose
Enables reading and synchronizing tasks from the Tasks.org ContentProvider (`content://org.tasks.api/v0`) to display as calendar items, support check/uncheck with undo, and manage task lifecycles.

## Requirements

### Requirement: Non-persistent Task Overlay
The system SHALL query tasks dynamically from the Tasks.org ContentProvider and render them as `DisplayEvent.Task` without persisting tasks in the local Room database, AND SHALL format and align task due dates consistently with `LocalDisplayZone` so tasks never drift or displace across midnight boundaries on timezone transitions.

#### Scenario: Tasks.org integration disabled
- **WHEN** the preference `tasks_org_enabled` is set to false
- **THEN** no task FAB is rendered and no task events are displayed on month/day views.

#### Scenario: Timezone transition with due tasks
- **WHEN** the system time zone changes while tasks are visible
- **THEN** tasks scheduled on a given local date SHALL remain assigned to that date without being shifted to the previous or next day.

### Requirement: Task Completion and Undo
The system SHALL allow completing or reopening tasks with immediate UI reaction and an Undo snackbar.

#### Scenario: User toggles repeating task checkbox
- **WHEN** user completes a recurring task
- **THEN** advance occurrence in Tasks.org and save pre-completion snapshot to allow full Undo restoration.

### Requirement: RF-03 - Empilhamento e Proteção de Timeline na Visão de Agenda
WHEN a Agenda for renderizada via `AgendaContent` (verificado em `HomeScreen.kt:2306`), IF houver mais de 1 tarefa de dia todo (`displayEvent is DisplayEvent.Task && isAllDay`), THE SYSTEM SHALL renderizar a 1ª tarefa com checkbox ativo e agrupar as tarefas de dia todo excedentes em um card compacto expansível `+X tarefas`, impedindo que tarefas sem horário saturem a rolagem vertical.

#### Scenario: Dia com múltiplas tarefas sem horário na tela de Agenda
- **WHEN** o usuário visualizar na Agenda um dia contendo 8 tarefas sem horário e 2 reuniões com hora
- **THEN** a Agenda exibe o card da 1ª tarefa e um card condensado `✓ +7 tarefas para hoje`, exibindo os cards das 2 reuniões logo abaixo.

#### Scenario: Expansão inline ao tocar na pilha de tarefas da Agenda
- **WHEN** o usuário toca no card condensado `+X tarefas` na Agenda
- **THEN** a lista expande inline os cards das tarefas restantes daquele dia.

### Requirement: RF-04 - Consolidação de Tarefas de Dia Todo no Planejador e no Mês
WHEN tarefas de dia todo forem exibidas no Planejador (`PlannerView.kt:432`) ou no Mês (`MonthFullSpanLayout.kt:35`), THE SYSTEM SHALL consolidar as tarefas de dia todo de cada dia em um resumo único (exibindo total e ícone de checklist), preservando as linhas da caixa diária do Planejador e os slots da célula do Mês para os eventos com horário marcado (`rows`).

#### Scenario: Visualização do dia com tarefas e reuniões no Planejador
- **WHEN** um dia no Planejador possuir 8 tarefas sem horário e 2 reuniões com horário marcado
- **THEN** o Planejador não esgota os slots com 8 ribbons de tarefas, exibindo uma linha consolidada de tarefas e exibindo as reuniões com horário nas linhas subsequentes.
