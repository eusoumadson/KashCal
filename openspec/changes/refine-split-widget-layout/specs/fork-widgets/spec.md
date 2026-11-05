# Spec Delta

## MODIFIED Requirements

### Requirement: RF-02 - Coluna Esquerda com Cabeçalho e Grid Mensal (Modo Wide)
WHILE a largura disponível do widget for maior ou igual a 260dp (`size.width >= 260.dp`, verificado em `SplitAgendaWidgetContent.kt:62`), THE SYSTEM SHALL renderizar na coluna interna esquerda:
1. No topo, um cabeçalho de data em disposição horizontal ampla com altura entre 46dp e 52dp (verificado em `SplitAgendaWidgetContent.kt:267`) — posicionando o número do dia em destaque à esquerda (`fontSize = 28.sp` a `30.sp`) e à direita a coluna com o nome do dia da semana completo em caixa alta (`QUINTA` ou `QUINTA-FEIRA`) e o mês com ano completo (`Novembro 2026`), aproveitando a largura da coluna esquerda sem truncamento ou abreviação.
2. Na base da coluna, uma grade mensal estável de 6 semanas completas calculada via `MonthGrid.compute` (verificado em `SplitAgendaWidgetContent.kt:163`), assentada sobre a margem inferior respeitando o padding interno do container (8dp), destacando o dia atual em `WidgetTheme.accentColor`, exibindo os dias do mês vigente em `WidgetTheme.primaryText` e renderizando as células de dias adjacentes (`InDate` e `OutDate`) em estilo de texto atenuado (`WidgetTheme.adjacentMonthText`).
3. Uma linha divisória vertical entre as colunas esquerda e direita com padding lateral harmonizado de 8dp de cada lado (verificado em `SplitAgendaWidgetContent.kt:235`).

#### Scenario: Renderização da coluna esquerda no modo wide
- **WHEN** o widget `SplitAgendaWidget` for colocado na tela inicial em layout amplo (largura >= 260dp)
- **THEN** a coluna esquerda exibe o cabeçalho no topo com altura entre 46dp e 52dp com nomes não abreviados de dia e mês/ano, e a grade de 6 semanas ancorada na base sem buracos vazios em meses com 5 semanas ativas, mantendo dias fora do mês em tom atenuado.

### Requirement: RF-09 - Ações de Navegação e Cliques por Convenção no Widget
WHEN o usuário interagir com os elementos clicáveis do `SplitAgendaWidget`, THE SYSTEM SHALL:
1. Abrir o KashCal na data de hoje ao tocar em qualquer ponto da coluna esquerda (verificado em `SplitAgendaWidgetContent.kt:120`), através de um wrapper de clique único aplicado no container pai da coluna inteira.
2. Alternar a conclusão inline ao tocar no checkbox da tarefa via `ToggleTaskAction`.
3. Abrir o KashCal no contexto correspondente ao tocar no título de tarefa, pill de tarefas, evento ou pill de eventos na coluna direita.

#### Scenario: Toque na pill de tarefas abre o KashCal
- **WHEN** o usuário toca no botão/pill `+X tarefas para hoje` no widget
- **THEN** o KashCal é lançado exibindo as tarefas e eventos consolidados daquele dia.

#### Scenario: Toque em qualquer área da coluna esquerda abre o dia de hoje
- **WHEN** o usuário toca em qualquer elemento da coluna esquerda do widget (no cabeçalho de data, nas letras dos dias da semana, em qualquer célula da grade mensal ou no espaçamento entre eles)
- **THEN** o KashCal é lançado imediatamente na data de hoje (`ACTION_GO_TO_TODAY`).
