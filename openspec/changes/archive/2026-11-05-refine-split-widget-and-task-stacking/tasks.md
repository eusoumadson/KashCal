# Tasks

## Phase 1: Motor de Agrupamento, Janela Dinâmica e Empilhamento

Antes de implementar, leia:
1. `AGENTS.md` e `docs/agents/architecture.md` — regras de arquitetura e comandos de build/teste
2. `proposal.md`, `specs/` e `design.md` deste diretório de change — requisitos RIGID, cenários e decisões técnicas

- [x] T01 — Implementar janela dinâmica de 2 dias em SplitAgendaGrouping
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/widget/SplitAgendaGrouping.kt`, `app/src/test/kotlin/org/onekash/kashcal/widget/SplitAgendaLogicTest.kt`
      Mudança: Atualizar `buildSplitUpcomingItems` para emitir seções Hoje + Amanhã quando restam eventos futuros/em andamento hoje, ou avançar automaticamente para Amanhã + D+2 quando hoje estiver sem eventos futuros.
      Cobre: RF-04, AC-03
      Acceptance criteria: `buildSplitUpcomingItems` não emite a seção de hoje se todos os seus eventos já tiverem passado e emite Amanhã e D+2.
      Testes: `app/src/test/kotlin/org/onekash/kashcal/widget/SplitAgendaLogicTest.kt` — Testar avanço automático de janela quando hoje tem apenas eventos passados.

- [x] T02 — Implementar regras de empilhamento 1 Item + Pilha para dia todo no motor de agrupamento
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/widget/SplitAgendaGrouping.kt`, `app/src/test/kotlin/org/onekash/kashcal/widget/SplitAgendaLogicTest.kt`
      Mudança: Separar itens de dia todo em eventos e tarefas; emitir o 1º item e empilhar os excedentes em `SplitAgendaItem.AllDayEventStack` e `SplitAgendaItem.AllDayTaskStack` com contadores de itens restantes.
      Cobre: RF-08, AC-05
      Acceptance criteria: Quando um dia tem 8 tarefas sem horário, o resultado contém exatamente 1 `EventRow` de tarefa e 1 `AllDayTaskStack` com count = 7.
      Testes: `app/src/test/kotlin/org/onekash/kashcal/widget/SplitAgendaLogicTest.kt` — Testar dias com múltiplos eventos de dia todo e múltiplas tasks.

## Phase 2: Refinamento de UI e Ações do SplitAgendaWidget

Antes de implementar, leia:
1. `AGENTS.md` e `docs/agents/architecture.md` — regras de arquitetura e comandos de build/teste
2. `proposal.md`, `specs/` e `design.md` deste diretório de change — requisitos RIGID, cenários e decisões técnicas

- [x] T03 — Suprimir exibição de label 'Dia todo' para tarefas
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/widget/WidgetEventRowContent.kt`, `app/src/main/kotlin/org/onekash/kashcal/widget/SplitAgendaWidgetContent.kt`, `app/src/test/kotlin/org/onekash/kashcal/widget/SplitAgendaWidgetContentTest.kt`
      Mudança: Modificar `EventRowInner` e `SplitEventRow` para não renderizar string de dia todo quando `event.isTask && event.isAllDay`, ocupando linha única no modo detalhado e sem coluna de horário no compacto.
      Cobre: RF-08, AC-04
      Acceptance criteria: O texto "Dia todo" não é emitido em nenhum nó de Text quando o item for uma tarefa.
      Testes: `app/src/test/kotlin/org/onekash/kashcal/widget/SplitAgendaWidgetContentTest.kt` — Validar renderização de task sem horário em modo compacto e detalhado.

- [x] T04 — Eliminar barra de ferramentas da coluna direita do widget
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/widget/SplitAgendaWidgetContent.kt`, `app/src/test/kotlin/org/onekash/kashcal/widget/SplitAgendaWidgetContentTest.kt`
      Mudança: Remover `Row` superior que continha `WidgetRefreshButton`, `WidgetAddButton` e o título `upcoming_widget_name`, fazendo a `LazyColumn` ocupar 100% da altura disponível.
      Cobre: RF-03, AC-01
      Acceptance criteria: A árvore de composables de `SplitAgendaListColumn` não contém instâncias de `WidgetRefreshButton` ou `WidgetAddButton`.
      Testes: `app/src/test/kotlin/org/onekash/kashcal/widget/SplitAgendaWidgetContentTest.kt` — Verificar ausência dos botões na composição do widget.

- [x] T05 — Redesenhar cabeçalho compacto de data na coluna esquerda com ano abreviado
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/widget/SplitAgendaWidgetContent.kt`, `app/src/test/kotlin/org/onekash/kashcal/widget/SplitAgendaWidgetContentTest.kt`
      Mudança: Reestruturar o topo de `WideSplitLayout` para layout horizontal compacto (número do dia em bloco à esquerda, dia da semana abreviado e mês com ano abreviado `Nov '26` à direita), com altura total <= 36dp.
      Cobre: RF-02, AC-02
      Acceptance criteria: O cabeçalho exibe o ano formatado em 2 dígitos (ex: `'26`) e o container de data tem altura reduzida.
      Testes: `app/src/test/kotlin/org/onekash/kashcal/widget/SplitAgendaWidgetContentTest.kt` — Validar formatação e disposição do cabeçalho da data.

- [x] T06 — Implementar renderização das pills de empilhamento e navegação por convenção
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/widget/SplitAgendaWidgetContent.kt`, `app/src/test/kotlin/org/onekash/kashcal/widget/SplitAgendaWidgetContentTest.kt`
      Mudança: Renderizar composables para `AllDayEventStack` e `AllDayTaskStack`; configurar intents de clique direcionando toques na coluna esquerda, pilhas e eventos para o KashCal (MainActivity), e checkbox para conclusão inline.
      Cobre: RF-08, RF-09, AC-05, AC-06
      Acceptance criteria: Toque nas pills de tarefas e eventos dispara navegação para o KashCal com os parâmetros de dia correspondentes.
      Testes: `app/src/test/kotlin/org/onekash/kashcal/widget/SplitAgendaWidgetContentTest.kt` — Validar presença das pills e parâmetros das ações de clique.

## Phase 3: Empilhamento de Tarefas de Dia Todo nas Telas do App

Antes de implementar, leia:
1. `AGENTS.md` e `docs/agents/architecture.md` — regras de arquitetura e comandos de build/teste
2. `proposal.md`, `specs/` e `design.md` deste diretório de change — requisitos RIGID, cenários e decisões técnicas

- [x] T07 — Implementar empilhamento de tarefas de dia todo na tela de Agenda
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/ui/screens/HomeScreen.kt`, `app/src/main/kotlin/org/onekash/kashcal/ui/components/AgendaListModel.kt`, `app/src/test/kotlin/org/onekash/kashcal/ui/components/AgendaListModelTest.kt`
      Mudança: No `AgendaContent` / `buildAgendaListModel`, agrupar tarefas de dia todo que excedam 1 item em um card compacto expansível inline `+X tarefas`, mantendo as reuniões com horário visíveis na primeira dobra.
      Cobre: RF-03 (tasks-org-integration), AC-07
      Acceptance criteria: Quando um dia na Agenda tem 8 tarefas sem horário, apenas 1 card de tarefa e 1 card compacto de contagem são exibidos inicialmente.
      Testes: `app/src/test/kotlin/org/onekash/kashcal/ui/components/AgendaListModelTest.kt` — Testar agrupamento de tarefas de dia todo no modelo da Agenda.

- [x] T08 — Implementar consolidação de tarefas de dia todo no Planejador
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/ui/screens/planner/PlannerView.kt`, `app/src/test/kotlin/org/onekash/kashcal/ui/screens/planner/PlannerViewTest.kt`
      Mudança: Em `PlannerDayBody` / `PlannerFitColumn`, sintetizar tarefas de dia todo em uma única linha de resumo de tarefas, desobstruindo a caixinha do dia para as linhas de eventos com horário.
      Cobre: RF-04 (tasks-org-integration), AC-08
      Acceptance criteria: Um dia no Planejador com múltiplas tarefas sem horário não aloca múltiplos ribbons individuais, reservando espaço para eventos cronológicos.
      Testes: `app/src/test/kotlin/org/onekash/kashcal/ui/screens/planner/PlannerViewTest.kt` — Validar renderização compacta de tarefas no dia do Planejador.

- [x] T09 — Implementar consolidação de tarefas de dia todo na grade e BottomSheet do Mês
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/ui/screens/monthfull/MonthFullSpanLayout.kt`, `app/src/main/kotlin/org/onekash/kashcal/ui/components/DayEventsSheet.kt`, `app/src/test/kotlin/org/onekash/kashcal/ui/screens/monthfull/MonthFullSpanLayoutTest.kt`
      Mudança: Consolidar tarefas de dia todo em slot único por dia em `computeMonthFullSpans`; aplicar o empilhamento condensado na exibição de tarefas em `DayEventsSheet`.
      Cobre: RF-04 (tasks-org-integration), AC-08
      Acceptance criteria: Células do mês com múltiplas tarefas reservam slots para eventos com horário e `DayEventsSheet` agrupa tarefas excedentes.
      Testes: `app/src/test/kotlin/org/onekash/kashcal/ui/screens/monthfull/MonthFullSpanLayoutTest.kt` — Validar cálculo de spans com tarefas consolidadas.

## Phase 4: Recursos, Localização e Verificação Final

Antes de implementar, leia:
1. `AGENTS.md` e `docs/agents/architecture.md` — regras de arquitetura e comandos de build/teste
2. `proposal.md`, `specs/` e `design.md` deste diretório de change — requisitos RIGID, cenários e decisões técnicas

- [x] T10 — Atualizar recursos de strings e executar gerador de localização
      Arquivos: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-pt/strings.xml`, `app/src/main/res/values-pt-rBR/strings.xml`, `app/src/fork/tools/gen_strings_pt.py`
      Mudança: Adicionar strings necessárias (`label_more_tasks_compact`, `split_agenda_more_all_day_events`, etc.) em `values/strings.xml` e executar `gen_strings_pt.py` para sincronizar os locales em português.
      Cobre: AC-05, AC-07
      Acceptance criteria: Strings adicionadas e sincronizadas sem chaves pendentes em `values-pt` e `values-pt-rBR`.
      Testes: Executar `python3 app/src/fork/tools/gen_strings_pt.py` e verificar integridade dos arquivos XML.

- [x] T11 — Execução completa dos testes unitários e verificação do build
      Arquivos: Todos os arquivos do projeto
      Mudança: Executar `./gradlew testDebugUnitTest --no-daemon` e `./gradlew assembleDebug --no-daemon` para certificar ausência de regressões em todo o projeto.
      Cobre: Todos os ACs (AC-01 a AC-08)
      Acceptance criteria: Suíte completa de testes unitários passa com 0 falhas e APK de debug compila com sucesso.
      Testes: Executar `./gradlew testDebugUnitTest --no-daemon`.
