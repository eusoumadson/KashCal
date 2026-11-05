# Proposal: refine-split-widget-and-task-stacking

## Metadata
- **Tier**: standard (8 RFs, 1 repo)
- **Architecture References**: AGENTS.md, docs/agents/architecture.md, docs/agents/domain_rules.md
- **Capability Policy**: ignore_for_this_run (personal)

## Why
Ao testar em hardware real (Samsung S10e, Android 16), identificou-se que o `SplitAgendaWidget` sofre com achatamento vertical da grade mensal devido a um cabeçalho de data alto (~64dp), poluição do topo da coluna direita (botões `↻`, `+` e texto "Próximo" roubando 48dp), e empurrão/afogamento de reuniões com horário por listas extensas de tarefas de dia todo (contas mensais sem hora fixa) que ocupam a tela inteira tanto no widget quanto nas visões de Agenda, Planejador e Mês do aplicativo.

## Scope
- **In**:
  - Eliminação da barra de cabeçalho da coluna direita no `SplitAgendaWidget` (iniciar diretamente na lista de itens).
  - Compactação horizontal do cabeçalho de data da coluna esquerda com ano abreviado (`Nov '26`) e altura reduzida para ~32dp.
  - Janela dinâmica de 2 dias na coluna direita (Hoje + Amanhã se há eventos futuros hoje; Amanhã + Depois de amanhã se hoje está vazio ou finalizado).
  - Remoção de label "Dia todo" para itens de tarefas (Tasks).
  - Empilhamento "1 Item + Pilha" para eventos e tarefas de dia todo no widget.
  - Navegação e cliques por convenção (coluna esquerda, eventos e pilhas abrem KashCal; checkbox marca inline).
  - Empilhamento de tarefas de dia todo na visão de Agenda (`AgendaContent`).
  - Consolidação de tarefas de dia todo em resumo único na visão de Planejador (`PlannerView`) e na célula do Mês (`MonthView`).
- **Out**:
  - Telas de configuração adicionais de clique (adotado Convention over Configuration).
  - Modificações na camada de persistência Room (regras de não-persistência do Tasks.org preservadas).
  - Alterações nas views de Dia, 3 Dias e Semana (já contam com `AllDayStripLayout`).

## What Changes
- **Widget (Coluna Direita)**: Remove `Row` de cabeçalho com `WidgetRefreshButton`, `WidgetAddButton` e "Próximo"; a `LazyColumn` passa a ocupar 100% da altura da coluna.
- **Widget (Coluna Esquerda)**: Redesenha o cabeçalho de data com número em destaque ao lado de dia da semana e mês com ano abreviado (`Nov '26`), liberando ~32dp adicionais para a grade de semanas.
- **Widget (Agrupamento e Inteligência)**: Atualiza `SplitAgendaGrouping.kt` para suportar a janela dinâmica de 2 dias e o empilhamento de itens de dia todo (máximo 1 item expandido por tipo + pill `+X`).
- **Widget (Formatação de Tarefas)**: Suprime exibição de "Dia todo" para tarefas em `EventRowInner` / `WidgetTimeFormatter.kt`.
- **App (Agenda)**: No `AgendaContent`, agrupa tarefas de dia todo excedentes em card compacto expansível `+X tarefas`.
- **App (Planejador)**: No `PlannerFitColumn` / `PlannerDayBody`, consolida tarefas de dia todo em linha única de resumo de tarefas, desobstruindo a visualização de compromissos com horário.
- **App (Mês)**: No `MonthFullSpanLayout`, condensa tarefas de dia todo em slot único por dia, preservando espaço para eventos cronológicos.

## Capabilities

### New Capabilities
*(Nenhuma nova capability durável criada)*

### Modified Capabilities
- `fork-widgets`: Atualização dos requisitos RF-02, RF-03, RF-04 e acréscimo de novos requisitos de empilhamento de dia todo e cabeçalho compacto no SplitAgendaWidget.
- `tasks-org-integration`: Adição de requisitos de empilhamento e não-saturação visual de tarefas sem horário nas telas de visualização do app (Agenda, Planejador e Mês).

## Confirmed Acceptance Criteria (Source of Truth)
- **AC-01**: A coluna direita do `SplitAgendaWidget` inicia diretamente na lista de itens, sem a barra superior de cabeçalho (sem botões `↻`, `+` e sem texto "Próximo"), liberando 48dp de altura vertical útil.
- **AC-02**: A coluna esquerda exibe o cabeçalho de data em disposição horizontal compacta (número do dia em bloco à esquerda e dia da semana com mês e ano abreviado `Nov '26` à direita), com altura total <= 36dp.
- **AC-03**: A lista do widget exibe exatamente 2 seções diárias: "Hoje" e "Amanhã" quando restam eventos futuros/em andamento hoje; ou avança automaticamente para "Amanhã" e "Depois de amanhã" quando não há compromissos futuros para hoje.
- **AC-04**: Tarefas sem horário nunca exibem a string "Dia todo" no widget; no modo detalhado renderizam em linha única e no modo compacto sem reservar coluna vazia de horário.
- **AC-05**: Quando houver mais de 1 evento de dia todo ou mais de 1 tarefa de dia todo, o widget exibe apenas o 1º item e condensa os excedentes em uma pill de contagem (`[ 📅 +X eventos ]` ou `[ ✓ +X tarefas para hoje ]`).
- **AC-06**: O toque no checkbox conclui a tarefa inline; o toque no texto da tarefa, na pilha de tarefas, em eventos ou na coluna esquerda abre o KashCal no contexto correspondente.
- **AC-07**: Na visualização de Agenda do app (`AgendaContent`), tarefas de dia todo de um mesmo dia são empilhadas após o 1º item em um card compacto expansível `+X tarefas`, mantendo os eventos com horário visíveis na primeira dobra.
- **AC-08**: No Planejador (`PlannerView`) e no Mês (`MonthView`), tarefas de dia todo são consolidadas em uma linha/slot único de resumo, impedindo que tarefas sem horário ocupem todos os slots e ocultem os compromissos com horário.

## Impact
- **Arquivos Afetados**:
  - `app/src/main/kotlin/org/onekash/kashcal/widget/SplitAgendaWidgetContent.kt`
  - `app/src/main/kotlin/org/onekash/kashcal/widget/SplitAgendaGrouping.kt`
  - `app/src/main/kotlin/org/onekash/kashcal/widget/WidgetEventRowContent.kt`
  - `app/src/main/kotlin/org/onekash/kashcal/ui/screens/HomeScreen.kt` (AgendaContent)
  - `app/src/main/kotlin/org/onekash/kashcal/ui/screens/planner/PlannerView.kt`
  - `app/src/main/kotlin/org/onekash/kashcal/ui/screens/monthfull/MonthFullSpanLayout.kt`
  - Recursos de strings em `values`, `values-pt`, `values-pt-rBR`
  - Testes unitários de widget e tela correspondentes
- **Sem quebras de API ou migrações de banco de dados**.
