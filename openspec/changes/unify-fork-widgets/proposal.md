# Proposal: unify-fork-widgets

## Metadata
- **Tier**: standard
- **Architecture References**: AGENTS.md, docs/agents/architecture.md, docs/agents/domain_rules.md
- **Capability Policy**: ignore_for_this_run (personal)

## Why

Atualmente, o fork KashCal Tasks oferece dois widgets experimentais separados: "Agenda Dividida" (`SplitAgendaWidget`) e "Mês + Agenda" (`MonthAgendaWidget`). Essa divisão fragmenta a experiência do usuário, duplicando componentes e obrigando a escolha entre visualizar o calendário mensal ou a visualização dividida. A unificação consolida o melhor dos dois mundos em um único widget avançado: na coluna esquerda, data em destaque e grid mensal interativo com indicadores de eventos; na coluna direita, próximos eventos e tarefas com agrupamento inteligente por data (Hoje, Amanhã e Amanhã+1), respeitando as configurações de exibição do upstream e oferecendo uma responsividade compacta otimizada.

## Scope
- **In**:
  - Exclusão completa do widget `MonthAgendaWidget` e de seus componentes associados.
  - Reestruturação da coluna esquerda de `SplitAgendaWidget` (modo wide) para exibir cabeçalho de data e o grid do mês atual com indicadores (dots) de eventos e destaque para o dia de hoje.
  - Reestruturação da coluna direita de `SplitAgendaWidget` para concentrar a lista de eventos e tarefas em `LazyColumn`.
  - Implementação da inteligência de próximos eventos: filtro de eventos passados no dia de hoje (apenas eventos em andamento ou futuros permanecem); janela de exibição restrita a Hoje, Amanhã e Amanhã+1.
  - Agrupamento por data com cabeçalhos visuais ("Hoje", "Amanhã", "Dia-da-semana, data") e mensagem de estado vazio específico para o dia de hoje ("Nenhum evento hoje") caso não restem eventos pendentes para a data atual.
  - Integração com configurações upstream: carregar todos os eventos restantes de Hoje e limitar os dias futuros ao valor de `widgetMaxEventsPerDay`; respeitar a preferência `widgetDetailedRows`.
  - Nova responsividade compacta (< 260dp): ocultar completamente a coluna esquerda (cabeçalho + grid mensal) e apresentar a coluna direita em largura total com a mesma lista agrupada e botões de ação.
  - Atualização da tela de configurações de widgets experimentais (`ExperimentalWidgetsScreen`), mantendo apenas o widget unificado.
  - Atualização dos testes unitários correspondentes.
- **Out**:
  - Persistência em banco Room para tarefas (mantém princípio arquitetural de não-persistência direta para Tasks.org).
  - Alterações nos widgets padrão do upstream (Agenda, Upcoming, Week, Month).
  - Alteração no suporte a fusos horários ou lógica de cálculo do RRULE.

## What Changes
- **Exclusão de Componentes Legados** (**BREAKING**): Remoção de `MonthAgendaWidget`, `MonthAgendaWidgetContent`, `MonthAgendaWidgetReceiver` e limpeza de suas referências em `AndroidManifest.xml`, `ExperimentalWidgetsManager`, `WidgetRefreshAction` e `WidgetStateKeys`.
- **Evolução de `SplitAgendaWidgetContent`**:
  - Coluna esquerda: Cabeçalho com dia da semana, número do dia, mês/ano, seguido do grid mensal de 6 semanas com dias do mês, destaque circular no dia corrente e dots indicadores para dias com eventos.
  - Coluna direita: Linha superior com título/controles (`WidgetRefreshButton` e `WidgetAddButton`), seguida de lista scrollável (`LazyColumn`) contendo itens agrupados por data.
- **Inteligência de Filtragem e Agrupamento**:
  - Janela de busca cobre `today`, `today + 1` e `today + 2` (obtida via `WidgetDataRepository.getEventsInRange`).
  - Ocultação de eventos passados (`event.isPast == true`). Eventos em andamento e futuros continuam visíveis.
  - Seção "Hoje" exibe todos os eventos restantes de hoje; se vazia, exibe cabeçalho "Hoje" e mensagem inline "Nenhum evento hoje".
  - Seções "Amanhã" e "Dia-da-semana, data" exibem até `widgetMaxEventsPerDay` eventos cada; dias futuros sem eventos são omitidos da lista.
  - Linhas de evento respeitam `widgetDetailedRows` (1 linha compacta vs 2 linhas detalhadas com horário de término) e suporte a interatividade de tarefas (`ToggleTaskAction`).
- **Responsividade Compacta**:
  - Quando a largura do widget for menor que 260dp, a coluna esquerda é totalmente suprimida, exibindo diretamente o layout da lista de eventos com controles de cabeçalho.
- **Configurações Experimentais**:
  - `ExperimentalWidgetsScreen` passa a apresentar apenas o card descritivo do widget unificado `SplitAgendaWidget`.

## Capabilities

### New Capabilities
- `fork-widgets`: Define a especificação e comportamento dos widgets exclusivos do fork KashCal Tasks, incluindo layout responsivo de agenda dividida com calendário mensal e inteligência de agrupamento de próximos eventos.

### Modified Capabilities
<!-- Nenhuma especificação existente em openspec/specs/ é modificada -->

## Confirmed Acceptance Criteria (Source of Truth)
- **AC-01**: `MonthAgendaWidget`, `MonthAgendaWidgetContent`, `MonthAgendaWidgetReceiver` são completamente removidos do projeto, bem como seus registros no manifesto e gerenciadores de componentes.
- **AC-02**: No modo wide (>= 260dp), a coluna esquerda do `SplitAgendaWidget` exibe o cabeçalho completo de data (dia da semana, número do dia, mês e ano) e o grid do mês atual com o dia de hoje destacado e dots para dias com eventos.
- **AC-03**: No modo wide (>= 260dp), a coluna direita concentra a lista de eventos e tarefas em `LazyColumn`, mantendo os botões de refresh e adicionar evento no topo.
- **AC-04**: A lista de eventos filtra eventos passados no dia atual (exibindo apenas eventos em andamento ou futuros) e restringe os dias futuros a Amanhã e Amanhã+1.
- **AC-05**: Os eventos são agrupados por data com seções claras: "Hoje" (com eventos restantes ou o texto "Nenhum evento hoje") e dias futuros ("Amanhã" e "Dia-da-semana, data") quando possuírem eventos.
- **AC-06**: O carregamento de eventos de Hoje não sofre corte pelo limite diário, enquanto os dias seguintes (Amanhã e Amanhã+1) respeitam o limite configurado em `widgetMaxEventsPerDay`; o estilo de exibição respeita `widgetDetailedRows`.
- **AC-07**: No modo compacto (< 260dp), a coluna esquerda é omitida e a coluna direita é renderizada em largura total, preservando os botões de ação e a lista agrupada com a mesma inteligência de filtragem.

## Impact
- **Código de Widgets**: `SplitAgendaWidget.kt`, `SplitAgendaWidgetContent.kt`, `SplitAgendaWidgetReceiver.kt`, `WidgetDataRepository.kt`, `WidgetStateFetchers.kt`.
- **Configurações & UI**: `ExperimentalWidgetsScreen.kt`, `ExperimentalWidgetsManager.kt`, `WidgetRefreshAction.kt`, `WidgetStateKeys.kt`.
- **Manifesto & Recursos**: `AndroidManifest.xml`, `strings.xml`, `values-pt/strings.xml`, `values-pt-rBR/strings.xml`.
- **Testes Unitários**: `ForkWidgetsTest.kt`, `SplitAgendaWidgetTest.kt` (ou novos testes de layout/agrupamento).
