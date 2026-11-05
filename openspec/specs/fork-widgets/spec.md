# Fork Widgets Specification

## Purpose
Prover a especificação dos widgets exclusivos do fork KashCal Tasks, unificando as funcionalidades de agenda dividida e visão de calendário mensal em um widget responsivo avançado (`SplitAgendaWidget`) com exibição inteligente de próximos eventos.

## Requirements

### Requirement: RF-01 - Exclusão de Componentes do Widget Legado MonthAgendaWidget
WHEN a unificação for aplicada, THE SYSTEM SHALL remover completamente o componente `MonthAgendaWidget` (verificado em `MonthAgendaWidget.kt:29`), seus layouts `MonthAgendaWidgetContent.kt`, receiver `MonthAgendaWidgetReceiver.kt` e referências em `AndroidManifest.xml` (verificado na linha 997), `ExperimentalWidgetsManager`, `WidgetRefreshAction` e `WidgetStateKeys`.

#### Scenario: Remoção completa e isolamento do componente legado
- **WHEN** o aplicativo ou testes unitários forem compilados após a unificação
- **THEN** nenhuma referência a `MonthAgendaWidget` ou `MonthAgendaWidgetReceiver` deve permanecer ativa no código de produção ou no manifesto, evitando duplicação e consumo de recursos em background.

### Requirement: RF-02 - Coluna Esquerda com Cabeçalho e Grid Mensal (Modo Wide)
WHILE a largura disponível do widget for maior ou igual a 260dp (`size.width >= 260.dp`), THE SYSTEM SHALL renderizar na coluna interna esquerda um cabeçalho compacto de data em disposição horizontal (verificado em `SplitAgendaWidgetContent.kt:127`) com altura total <= 36dp — posicionando o número do dia à esquerda e dia da semana com mês e ano abreviado (ex: `Nov '26`) à direita — e a grade do mês atual calculada via `MonthGrid.compute`, destacando o dia atual em `WidgetTheme.accentColor`.

#### Scenario: Renderização da coluna esquerda no modo wide
- **WHEN** o widget `SplitAgendaWidget` for colocado na tela inicial em layout amplo (largura >= 260dp)
- **THEN** a coluna esquerda exibe o cabeçalho compacto ocupando <= 36dp de altura com o ano abreviado (ex: `'26`), conferindo maior espaço vertical à grade mensal de 6 semanas para evitar células achatadas.

### Requirement: RF-03 - Coluna Direita com LazyColumn e Ações de Cabeçalho
WHILE a largura disponível do widget for maior ou igual a 260dp, THE SYSTEM SHALL renderizar a lista scrollável `LazyColumn` contendo eventos e tarefas iniciando imediatamente no topo da coluna interna direita (verificado em `SplitAgendaWidgetContent.kt:285`), SEM exibir barra superior de cabeçalho (removendo `WidgetRefreshButton`, `WidgetAddButton` e rótulo "Próximo"), aproveitando 100% da altura vertical da coluna para conteúdo da agenda.

#### Scenario: Visualização e interação com a lista da coluna direita
- **WHEN** o usuário interage com a coluna direita do widget
- **THEN** a lista permite scroll independente dos itens agendados sem overhead de botões no topo, iniciando alinhada ao topo da área útil do widget.

### Requirement: RF-04 - Inteligência de Próximos Eventos e Ocultação de Eventos Passados
WHEN a lista de eventos da coluna direita for computada via `buildSplitUpcomingItems` (verificado em `SplitAgendaGrouping.kt:97`), THE SYSTEM SHALL delimitar a exibição a 2 seções diárias dinâmicas: exibindo Hoje e Amanhã quando restarem eventos futuros/em andamento hoje, ou avançando automaticamente para Amanhã e D+2 quando não houver eventos futuros para hoje, filtrando eventos passados de hoje (`event.isPast == true`).

#### Scenario: Evento do dia corrente já finalizado
- **WHEN** a hora atual for 23:43 e todos os eventos de hoje terminaram antes desse horário
- **THEN** nenhum desses eventos passados é exibido na lista e a janela avança automaticamente para Amanhã e D+2.

#### Scenario: Evento em andamento
- **WHEN** um evento tiver início às 14:00 e término previsto para as 15:30, e a hora atual for 14:45
- **THEN** o evento deve permanecer visível na seção "Hoje" como evento em andamento (`isPast == false`).

### Requirement: RF-05 - Agrupamento por Data com Cabeçalho e Estado Vazio Específico de Hoje
WHEN os eventos forem organizados para exibição, THE SYSTEM SHALL agrupá-los por data dinamicamente: omitindo a seção "Hoje" quando não restarem compromissos pendentes para hoje e exibindo as seções para "Amanhã" e "Dia-da-semana, data" (D+2).

#### Scenario: Restante do dia sem compromissos mas com compromissos amanhã
- **WHEN** não existirem eventos pendentes para hoje, mas existirem compromissos agendados para amanhã
- **THEN** a lista avança a exibição diretamente para a seção "Amanhã" seguida por D+2.

### Requirement: RF-06 - Consumo das Preferências Upstream de Limite de Itens e Linhas Detalhadas
WHEN os eventos forem preparados para renderização, THE SYSTEM SHALL carregar todos os eventos restantes de Hoje, limitar os dias futuros a `widgetMaxEventsPerDay` e renderizar cada item respeitando o estilo configurado em `widgetDetailedRows`.

#### Scenario: Aplicação de limite diário em dias futuros
- **WHEN** `widgetMaxEventsPerDay` estiver configurado com o valor 3 e o dia de amanhã possuir 5 eventos
- **THEN** apenas os 3 primeiros eventos de amanhã devem ser renderizados na seção "Amanhã".

### Requirement: RF-07 - Responsividade em Largura Reduzida (Modo Compacto)
WHILE a largura disponível do widget for menor que 260dp (`size.width < 260.dp`), THE SYSTEM SHALL ocultar a coluna esquerda e renderizar a coluna direita em largura total (`fillMaxSize()`), preservando ações de cabeçalho e a lista agrupada com filtro de eventos passados.

#### Scenario: Redimensionamento do widget para coluna única estreita
- **WHEN** o usuário redimensiona o widget na tela inicial para uma grade estreita (ex.: 2x3 ou largura < 260dp)
- **THEN** o widget não exibe o grid do mês nem o cabeçalho expandido, preenchendo toda a extensão disponível com a lista de próximos eventos agrupados e os botões de ação no topo.

### Requirement: RF-08 - Tratamento de Dia Todo e Empilhamento no Widget
WHEN eventos e tarefas forem renderizados no `SplitAgendaWidget`, THE SYSTEM SHALL:
1. Suprimir o texto "Dia todo" para tarefas sem horário (`event.isTask && event.isAllDay`, verificado em `WidgetEventRowContent.kt:39`).
2. Condensar múltiplos eventos de dia todo (> 1) no 1º evento mais pill `+X eventos`.
3. Condensar múltiplas tarefas de dia todo (> 1) na 1ª tarefa mais pill `+X tarefas`.
4. Renderizar eventos com horário abaixo das pilhas.

#### Scenario: Dia com múltiplas tarefas sem horário
- **WHEN** o usuário possuir 8 tarefas sem horário no dia de hoje
- **THEN** o widget exibe a 1ª tarefa com checkbox marcável inline e uma pill indicando `+7 tarefas para hoje`, permitindo que reuniões com horário fiquem visíveis imediatamente abaixo.

#### Scenario: Supressão da label "Dia todo" em tarefa
- **WHEN** uma tarefa sem horário for exibida tanto no modo detalhado quanto no compacto
- **THEN** a string "Dia todo" não é renderizada na tarefa.

### Requirement: RF-09 - Ações de Navegação e Cliques por Convenção no Widget
WHEN o usuário interagir com os elementos clicáveis do `SplitAgendaWidget`, THE SYSTEM SHALL:
1. Abrir o KashCal na data de hoje ao tocar na coluna esquerda (verificado em `SplitAgendaWidgetContent.kt:136`).
2. Alternar a conclusão inline ao tocar no checkbox da tarefa via `ToggleTaskAction` (verificado em `SplitAgendaWidgetContent.kt:475`).
3. Abrir o KashCal no contexto correspondente ao tocar no título de tarefa, pill de tarefas, evento ou pill de eventos.

#### Scenario: Toque na pill de tarefas abre o KashCal
- **WHEN** o usuário toca no botão/pill `+X tarefas para hoje` no widget
- **THEN** o KashCal é lançado exibindo as tarefas e eventos consolidados daquele dia.
