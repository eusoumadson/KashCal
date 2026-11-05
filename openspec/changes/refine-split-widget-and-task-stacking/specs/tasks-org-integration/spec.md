# Spec Delta: tasks-org-integration

## ADDED Requirements

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
