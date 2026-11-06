# Proposal: refine-split-widget-layout

## Metadata
- **Tier**: light
- **Architecture References**: AGENTS.md, docs/agents/architecture.md, docs/agents/domain_rules.md
- **Capability Policy**: ignore_for_this_run

## Why

Ao analisar as capturas de tela do `SplitAgendaWidget` em hardware real (Samsung S10e, Android 16), identificou-se que a coluna esquerda (calendário) e a divisória central apresentam desequilíbrios estéticos e espaciais severos: o cabeçalho fica comprimido no canto esquerdo com textos abreviados (`QUI.`, `Nov. '26'`) e altura restrita (40dp); a divisória central possui padding lateral desarmônico (6dp) em relação às bordas internas do container (8dp–12dp); meses com 5 semanas ativas (como Novembro de 2026) ocultam a 6ª linha gerando a impressão de um buraco vazio na base; e a coluna inteira da esquerda carece de resposta ao toque, pois apenas o bloco superior de 40dp respondia ao clique.

## Scope
- **In**:
  - Header da coluna esquerda com nomes completos sem abreviações (`QUINTA`, `Novembro 2026`), altura ampliada para ~48dp–50dp e posicionamento no topo com padding de 8dp.
  - Grid mensal com 6 semanas completas sempre visíveis, ancorado na base da coluna esquerda (respeitando o padding inferior de 8dp) e exibindo dias fora do mês vigente (`InDate` e `OutDate`) em estilo atenuado (`WidgetTheme.adjacentMonthText`).
  - Linha divisória central com padding lateral harmonizado em 8dp de cada lado.
  - Wrapper na coluna esquerda inteira com clique único abrindo o KashCal no dia de hoje (`ACTION_GO_TO_TODAY`).
  - Atualização dos testes unitários correspondentes em `SplitAgendaWidgetContentTest.kt`.
- **Out**:
  - Modificações na coluna direita (agenda, empilhamento de tarefas de dia todo já consolidados).
  - Modificações nas visões internas do aplicativo (AgendaContent, PlannerView, MonthView).
  - Alterações no esquema de banco de dados ou preferências.

## What Changes

- **Header da Coluna Esquerda (`SplitDateHeader`)**: Atualizado para exibir dia da semana completo (`QUINTA`) e mês/ano completo (`Novembro 2026`) em disposição horizontal ampla com altura de ~48dp–50dp.
- **Divisória Central**: Espaçamento lateral da linha vertical ajustado para 8dp de cada lado (harmônico com o container).
- **Grid de 6 Semanas (`WideSplitLayout`)**:
  - Ancorado na base da coluna esquerda, respeitando o padding inferior de 8dp.
  - Células de dias fora do mês (`InDate` e `OutDate`) passam a ser renderizadas com texto atenuado (`WidgetTheme.adjacentMonthText()`) em vez de caixas vazias, mantendo a grade estável com 6 semanas em qualquer mês do ano.
- **Interação na Coluna Esquerda**: O container `Column` pai da coluna esquerda inteira recebe o modificador de clique (`.clickable(...)`) direcionando para o KashCal na data de hoje (`ACTION_GO_TO_TODAY`), removendo o clique restrito apenas ao header.

## Capabilities

### New Capabilities
*(Nenhuma nova capability durável criada)*

### Modified Capabilities
- `fork-widgets`: Atualização do requisito RF-02 (Coluna Esquerda com Cabeçalho e Grid Mensal) e RF-09 (Ações de Navegação e Cliques por Convenção no Widget).

## Confirmed Acceptance Criteria (Source of Truth)
- **AC-01**: A coluna esquerda exibe o cabeçalho de data no topo com altura ampliada (~48dp–50dp), número do dia proeminente à esquerda e textos por extenso à direita sem abreviações (`QUINTA` / `Novembro 2026`), aproveitando a largura da coluna.
- **AC-02**: A grade mensal do widget exibe invariavelmente 6 linhas de semana completas, renderizando os dias adjacentes de meses vizinhos (`InDate` e `OutDate`) com estilo de texto atenuado (`WidgetTheme.adjacentMonthText`), eliminando buracos vazios na base em meses de 5 semanas ativas.
- **AC-03**: A grade de 6 semanas repousa ancorada na base da coluna esquerda, respeitando o padding inferior interno do container (8dp).
- **AC-04**: A linha divisória vertical entre as duas colunas possui padding lateral de 8dp de cada lado, alinhando-se harmonicamente com as margens internas do container.
- **AC-05**: Qualquer toque em qualquer área da coluna esquerda (seja no cabeçalho, na grade ou no espaçamento) abre o KashCal diretamente na data de hoje (`ACTION_GO_TO_TODAY`).

## Impact
- **Arquivos Afetados**:
  - `app/src/main/kotlin/org/onekash/kashcal/widget/SplitAgendaWidgetContent.kt`
  - `app/src/test/kotlin/org/onekash/kashcal/widget/SplitAgendaWidgetContentTest.kt`
- **Sem impacto em persistência Room, DataStore ou APIs externas**.
