# Tasks

## Phase 1: Exclusão de Componentes Legados do MonthAgendaWidget

Antes de implementar, leia:
1. `AGENTS.md` e `docs/agents/architecture.md` — regras de arquitetura e comandos de build/teste
2. `proposal.md`, `specs/` e `design.md` deste diretório de change — requisitos RIGID, cenários e decisões técnicas

- [ ] T01 — Remoção dos arquivos de código do MonthAgendaWidget e limpeza do manifesto
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/widget/MonthAgendaWidget.kt`, `app/src/main/kotlin/org/onekash/kashcal/widget/MonthAgendaWidgetContent.kt`, `app/src/main/kotlin/org/onekash/kashcal/widget/MonthAgendaWidgetReceiver.kt`, `app/src/main/AndroidManifest.xml`
      Mudança: Excluir os 3 arquivos Kotlin obsoletos do widget `MonthAgendaWidget` e remover a declaração `<receiver android:name=".widget.MonthAgendaWidgetReceiver" .../>` do manifesto.
      Cobre: RF-01, AC-01
      Acceptance criteria: Arquivos deletados do disco e nenhuma referência a `MonthAgendaWidgetReceiver` restante em `app/src/main/AndroidManifest.xml`.
      Testes: `./gradlew testDebugUnitTest --tests "*WidgetKindTest*"` — verificar integridade dos tipos de widget.

- [ ] T02 — Limpeza de referências residuais do MonthAgendaWidget em managers, ações e testes
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/widget/ExperimentalWidgetsManager.kt`, `app/src/main/kotlin/org/onekash/kashcal/widget/WidgetRefreshAction.kt`, `app/src/main/kotlin/org/onekash/kashcal/widget/WidgetStateKeys.kt`, `app/src/main/kotlin/org/onekash/kashcal/widget/WidgetPreviewContent.kt`, `app/src/test/kotlin/org/onekash/kashcal/widget/ForkWidgetsTest.kt`, `app/src/test/kotlin/org/onekash/kashcal/widget/WidgetKindTest.kt`
      Mudança: Remover o enum/chave `MONTH_AGENDA`, referências de componente no `ExperimentalWidgetsManager` e instâncias do widget em listas estáticas de receivers/preview, atualizando os testes de `ForkWidgetsTest` e `WidgetKindTest` para refletir apenas `SPLIT_AGENDA`.
      Cobre: RF-01, AC-01
      Acceptance criteria: Compilação limpa do projeto sem referências a `MonthAgendaWidget` ou `MONTH_AGENDA`.
      Testes: `app/src/test/kotlin/org/onekash/kashcal/widget/ForkWidgetsTest.kt`, `app/src/test/kotlin/org/onekash/kashcal/widget/WidgetKindTest.kt` — asserções atualizadas passando.

## Phase 2: Motor de Filtragem, Agrupamento e Testes Unitários Puros

Antes de implementar, leia:
1. `AGENTS.md` e `docs/agents/architecture.md` — regras de arquitetura e comandos de build/teste
2. `proposal.md`, `specs/` e `design.md` deste diretório de change — requisitos RIGID, cenários e decisões técnicas

- [ ] T03 — Criação da suíte de testes unitários para a engine de agrupamento de próximos eventos
      Arquivos: `app/src/test/kotlin/org/onekash/kashcal/widget/SplitAgendaGroupingTest.kt`
      Mudança: Criar testes com JUnit 4 puro cobrindo: 1) filtragem de eventos passados em Hoje (`isPast == true`); 2) manutenção de eventos em andamento (`isPast == false`); 3) presença mandatória da seção "Hoje" mesmo com lista vazia (gerando aviso de estado vazio); 4) omissão de dias futuros sem eventos; 5) aplicação do limite `widgetMaxEventsPerDay` apenas nos dias futuros (Amanhã e Amanhã+1); 6) agrupamento de tarefas pendentes e concluídas.
      Cobre: RF-04, RF-05, RF-06, AC-04, AC-05, AC-06
      Acceptance criteria: Arquivo de teste compilando e falhando inicialmente apenas pela ausência das funções de agrupamento (TDD).
      Testes: `app/src/test/kotlin/org/onekash/kashcal/widget/SplitAgendaGroupingTest.kt` — suíte com múltiplos cenários de teste temporal.

- [ ] T04 — Implementação da engine pura de agrupamento e formatação de cabeçalhos
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/widget/SplitAgendaGrouping.kt`
      Mudança: Implementar modelos de itens (`SplitAgendaItem.DayHeader`, `SplitAgendaItem.EventRow`, `SplitAgendaItem.EmptyTodayNotice`) e função pura `buildSplitUpcomingItems(...)` que recebe `eventsByDay`, `todayDayCode`, `nowMs`, `maxEventsPerDay`, filtrando eventos passados de hoje, aplicando limites para dias seguintes e formatando os rótulos de data relativos ("Hoje", "Amanhã", "Dia-da-semana, data").
      Cobre: RF-04, RF-05, RF-06, AC-04, AC-05, AC-06
      Acceptance criteria: Todos os testes de `SplitAgendaGroupingTest` passam sem erros.
      Testes: `./gradlew testDebugUnitTest --tests "*SplitAgendaGroupingTest*"` — 100% de aprovação nos testes de agrupamento.

## Phase 3: Reestruturação do SplitAgendaWidget, Layouts e Responsividade

Antes de implementar, leia:
1. `AGENTS.md` e `docs/agents/architecture.md` — regras de arquitetura e comandos de build/teste
2. `proposal.md`, `specs/` e `design.md` deste diretório de change — requisitos RIGID, cenários e decisões técnicas

- [ ] T05 — Atualização da busca de dados e estado em SplitAgendaWidget
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/widget/SplitAgendaWidget.kt`, `app/src/main/kotlin/org/onekash/kashcal/widget/WidgetStateFetchers.kt`
      Mudança: Atualizar `provideGlance` em `SplitAgendaWidget` para buscar os eventos da janela de 3 dias (hoje até hoje+2), os eventos do mês para os dots (`getEventsInRange`), a grade `MonthGrid` e as preferências `widgetMaxEventsPerDay`, `widgetDetailedRows` e `firstDayOfWeek`.
      Cobre: RF-02, RF-03, RF-06, AC-02, AC-03, AC-06
      Acceptance criteria: `SplitAgendaWidget` coleta e repassa os parâmetros necessários para `SplitAgendaWidgetContent` através de `produceState`.
      Testes: `./gradlew testDebugUnitTest --tests "*WidgetStateFetchersTest*"` — validação de fetch sem falhas.

- [ ] T06 — Reestruturação da coluna esquerda de SplitAgendaWidgetContent (cabeçalho + grade mensal)
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/widget/SplitAgendaWidgetContent.kt`
      Mudança: No `WideSplitLayout`, implementar na coluna esquerda o cabeçalho completo de data (dia da semana em caixa alta, número do dia e mês/ano clicável para `ACTION_GO_TO_TODAY`), seguido pelo grid mensal de 6 semanas baseado em `MonthGrid`, com destaque circular no dia atual e dots nos dias com eventos.
      Cobre: RF-02, AC-02
      Acceptance criteria: Coluna esquerda não renderiza mais lista de eventos; renderiza cabeçalho e grade mensal de 6 semanas interativa.
      Testes: `./gradlew assembleDebug` — compilação bem-sucedida do composable Glance.

- [ ] T07 — Reestruturação da coluna direita de SplitAgendaWidgetContent (LazyColumn agrupada)
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/widget/SplitAgendaWidgetContent.kt`
      Mudança: Implementar na coluna direita a barra superior de controles (`WidgetRefreshButton` e `WidgetAddButton`) e o `LazyColumn` que consome os itens processados por `SplitAgendaGrouping`, renderizando cabeçalhos de seção, linhas de eventos/tarefas (`SplitEventRow` com suporte a `widgetDetailedRows`) e a mensagem "Nenhum evento hoje" quando aplicável.
      Cobre: RF-03, RF-05, RF-06, AC-03, AC-05, AC-06
      Acceptance criteria: Coluna direita renderiza lista única e scrollável contendo os eventos agrupados por data e aviso inline para hoje vazio.
      Testes: `./gradlew testDebugUnitTest` — verificação de regressão em widgets.

- [ ] T08 — Implementação da nova responsividade compacta
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/widget/SplitAgendaWidgetContent.kt`
      Mudança: Substituir o layout antigo `CompactSplitLayout` por um layout que, quando `size.width < 260.dp`, omite inteiramente a coluna esquerda e renderiza a coluna de eventos/tarefas agrupados em largura total com os botões de ação no topo.
      Cobre: RF-07, AC-07
      Acceptance criteria: Em larguras inferiores a 260dp, apenas a coluna de lista e controles é exibida, mantendo os agrupamentos de Hoje, Amanhã e Amanhã+1.
      Testes: `./gradlew testDebugUnitTest` — verificação de compilação e estabilidade.

## Phase 4: Configurações, Recursos e Verificação Final

Antes de implementar, leia:
1. `AGENTS.md` e `docs/agents/architecture.md` — regras de arquitetura e comandos de build/teste
2. `proposal.md`, `specs/` e `design.md` deste diretório de change — requisitos RIGID, cenários e decisões técnicas

- [ ] T09 — Atualização da tela de configurações de widgets experimentais
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/ui/screens/settings/ExperimentalWidgetsScreen.kt`
      Mudança: Remover o Card descritivo do `MonthAgendaWidget` e atualizar o texto do Card de `SplitAgendaWidget` para descrever o widget unificado (Agenda Dividida com Calendário Mensal e Próximos Eventos).
      Cobre: RF-01, AC-01
      Acceptance criteria: `ExperimentalWidgetsScreen` exibe apenas um card para o widget unificado.
      Testes: `./gradlew testDebugUnitTest` — garantir que testes de UI e screens passam.

- [ ] T10 — Sincronização de recursos de strings e traduções
      Arquivos: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-pt/strings.xml`, `app/src/main/res/values-pt-rBR/strings.xml`
      Mudança: Adicionar string para o aviso de estado vazio do agrupamento "Hoje" (`split_agenda_no_events_today`: "Nenhum evento hoje") e atualizar títulos/descrições do widget nos arquivos de strings em inglês e português paritariamente.
      Cobre: RF-05, AC-05
      Acceptance criteria: Strings adicionadas e idênticas em chaves em `values`, `values-pt` e `values-pt-rBR`.
      Testes: `python3 app/src/fork/tools/gen_strings_pt.py --check` (ou verificação de integridade de strings).

- [ ] T11 — Verificação final de qualidade e regressão completa
      Arquivos: `app/src/test/kotlin/`
      Mudança: Executar a suíte de testes unitários completa do projeto (`./gradlew testDebugUnitTest --no-daemon`) e o Android Lint (`./gradlew lintDebug --no-daemon`), garantindo zero erros ou regressões.
      Cobre: AC-01, AC-02, AC-03, AC-04, AC-05, AC-06, AC-07
      Acceptance criteria: Build e testes passam com 0 falhas em todos os módulos (`:app`, `:icaldav-core`, `:vcard-core`).
      Testes: `./gradlew testDebugUnitTest --no-daemon` — aprovação de todos os testes unitários.
