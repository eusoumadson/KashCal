# Spec Delta: fork-widgets

## MODIFIED Requirements

### Requirement: RF-02 - Coluna Esquerda com Cabeçalho e Grid Mensal (Modo Wide)
WHILE a largura disponível do widget for maior ou igual a 260dp (`size.width >= 260.dp`, verificado em `SplitAgendaWidgetContent.kt:62`), THE SYSTEM SHALL renderizar na coluna interna esquerda um cabeçalho compacto de data em disposição horizontal (verificado em `SplitAgendaWidgetContent.kt:126`) com altura vertical entre 38dp e 42dp — posicionando o número do dia à esquerda (`fontSize = 24.sp`) e a coluna com dia da semana e mês com ano abreviado em duas linhas à direita (`fontSize = 11.sp`) — garantindo ausência de corte vertical no texto nativo do Glance, e a grade do mês atual calculada via `MonthGrid.compute`, destacando o dia atual em `WidgetTheme.accentColor`.

#### Scenario: Renderização da coluna esquerda no modo wide
- **WHEN** o widget `SplitAgendaWidget` for colocado na tela inicial em layout amplo (largura >= 260dp)
- **THEN** a coluna esquerda exibe o cabeçalho compacto com altura adequada (40dp) sem truncamento visual do número, do dia da semana e do mês/ano, mantendo a grade mensal de 6 semanas logo abaixo totalmente visível e contida no widget.
