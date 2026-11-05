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
WHILE a largura disponível do widget for maior ou igual a 260dp (`size.width >= 260.dp`), THE SYSTEM SHALL renderizar na coluna interna esquerda o cabeçalho de data clicável e a grade do mês atual calculada via `MonthGrid.compute`, destacando o dia atual em `WidgetTheme.accentColor` e exibindo indicadores de eventos.

#### Scenario: Renderização da coluna esquerda no modo wide
- **WHEN** o widget `SplitAgendaWidget` for colocado na tela inicial em layout amplo (ex.: 4x3 ou largura >= 260dp)
- **THEN** a coluna esquerda exibe o cabeçalho completo da data atual e o grid do mês corrente com indicadores visuais nos dias com compromissos, permitindo ao usuário navegar para hoje com um toque.

### Requirement: RF-03 - Coluna Direita com LazyColumn e Ações de Cabeçalho
WHILE a largura disponível do widget for maior ou igual a 260dp, THE SYSTEM SHALL renderizar na coluna interna direita a barra de controles (`WidgetRefreshButton` e `WidgetAddButton`) e uma lista scrollável `LazyColumn` contendo eventos e tarefas pendentes/futuros organizados por seções de data.

#### Scenario: Visualização e interação com a lista da coluna direita
- **WHEN** o usuário interage com a coluna direita do widget
- **THEN** a lista permite scroll independente dos itens agendados e os botões de ação do topo disparam o refresh com animação e a tela de criação de eventos.

### Requirement: RF-04 - Inteligência de Próximos Eventos e Ocultação de Eventos Passados
WHEN a lista de eventos da coluna direita for computada, THE SYSTEM SHALL delimitar a janela a no máximo 3 dias (Hoje, Amanhã, Amanhã+1) e filtrar eventos passados do dia de hoje (`event.isPast == true`), mantendo visíveis apenas eventos em andamento ou futuros.

#### Scenario: Evento do dia corrente já finalizado
- **WHEN** a hora atual for 23:43 e todos os eventos de hoje terminaram antes desse horário
- **THEN** nenhum desses eventos passados deve ser exibido na lista, mantendo o widget limpo e focado no que resta fazer ou nos próximos dias.

#### Scenario: Evento em andamento
- **WHEN** um evento tiver início às 14:00 e término previsto para as 15:30, e a hora atual for 14:45
- **THEN** o evento deve permanecer visível na seção "Hoje" como evento em andamento (`isPast == false`).

### Requirement: RF-05 - Agrupamento por Data com Cabeçalho e Estado Vazio Específico de Hoje
WHEN os eventos forem organizados para exibição, THE SYSTEM SHALL agrupá-los por data: gerando sempre a seção "Hoje" (com itens ou aviso inline "Nenhum evento hoje") e seções para "Amanhã" e "Dia-da-semana, data" apenas quando houver eventos futuros nesses dias.

#### Scenario: Restante do dia sem compromissos mas com compromissos amanhã
- **WHEN** não existirem eventos pendentes para hoje, mas existirem 2 eventos agendados para amanhã
- **THEN** a lista exibe a seção "Hoje" com a mensagem "Nenhum evento hoje", seguida pela seção "Amanhã" listando os 2 eventos.

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
