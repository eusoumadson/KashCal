# Tasks

## Phase 1: Refinamento do Layout da Coluna Esquerda e Divisória

Antes de implementar, leia:
1. `AGENTS.md` e `docs/agents/architecture.md` — regras de arquitetura e comandos de build/teste
2. `proposal.md`, `specs/` e `design.md` deste diretório de change — requisitos RIGID, cenários e decisões técnicas

- [x] T01 — Refatorar cabeçalho SplitDateHeader com nomes completos e altura ampliada
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/widget/SplitAgendaWidgetContent.kt`, `app/src/test/kotlin/org/onekash/kashcal/widget/SplitAgendaWidgetContentTest.kt`
      Mudança: Atualizar `SplitDateHeader` para altura entre 46dp e 52dp (~48dp), número do dia destacado (28sp–30sp) à esquerda, e coluna à direita com nome completo do dia da semana (`QUINTA` ou `QUINTA-FEIRA`) e mês/ano completo (`Novembro 2026`), aproveitando a largura da coluna.
      Cobre: RF-02, AC-01
      Acceptance criteria: `SplitDateHeader` renderiza o nome do dia da semana completo e mês com ano completo de 4 dígitos sem abreviações, com altura vertical entre 46dp e 52dp.
      Testes: `app/src/test/kotlin/org/onekash/kashcal/widget/SplitAgendaWidgetContentTest.kt` — teste unitário de composição do `SplitDateHeader` verificando a presença dos textos completos.

- [x] T02 — Renderizar 6 semanas completas com dias adjacentes atenuados e ancoragem na base
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/widget/SplitAgendaWidgetContent.kt`, `app/src/test/kotlin/org/onekash/kashcal/widget/SplitAgendaWidgetContentTest.kt`
      Mudança: Em `WideSplitLayout`, iterar invariavelmente pelas 6 semanas de `data.monthGrid.weeks`, exibindo células com `position != MonthDate` (`InDate` e `OutDate`) com número visível estilizado com `WidgetTheme.adjacentMonthText()` sem indicador de eventos; posicionar a grade ancorada na base da coluna esquerda respeitando o padding inferior de 8dp.
      Cobre: RF-02, AC-02, AC-03
      Acceptance criteria: A grade mensal exibe 6 semanas completas, renderizando os números dos dias adjacentes com a cor de texto atenuado `adjacentMonthText` e sem dots de eventos, preenchendo a base sem buracos vazios.
      Testes: `app/src/test/kotlin/org/onekash/kashcal/widget/SplitAgendaWidgetContentTest.kt` — teste unitário validando que células `OutDate` exibem seus números de dia e não deixam linhas invisíveis.

- [x] T03 — Harmonizar padding lateral da divisória e aplicar wrapper de clique na coluna esquerda
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/widget/SplitAgendaWidgetContent.kt`, `app/src/test/kotlin/org/onekash/kashcal/widget/SplitAgendaWidgetContentTest.kt`
      Mudança: Ajustar o espaçamento lateral da linha divisória vertical para 8dp de cada lado, alinhando com as margens internas do container; envolver a `Column` raiz da coluna esquerda com modificador `.clickable(actionStartActivity<MainActivity>(...ACTION_GO_TO_TODAY))`, removendo o clique isolado de dentro de `SplitDateHeader`.
      Cobre: RF-02, RF-09, AC-04, AC-05
      Acceptance criteria: A divisória central possui padding lateral simétrico de 8dp e o container pai da coluna esquerda inteira possui a ação de clique para abrir o KashCal na data de hoje (`ACTION_GO_TO_TODAY`).
      Testes: `app/src/test/kotlin/org/onekash/kashcal/widget/SplitAgendaWidgetContentTest.kt` — teste unitário de clique verificando que a coluna esquerda possui ação de iniciar atividade para hoje.

- [x] T04 — Execução da suíte de testes unitários e validação OpenSpec
      Arquivos: `app/src/test/kotlin/org/onekash/kashcal/widget/SplitAgendaWidgetContentTest.kt`
      Mudança: Executar a suíte de testes unitários e a validação do OpenSpec, garantindo que a implementação atende a todos os critérios de aceitação e conformidade sem quebras de regressão.
      Cobre: AC-01, AC-02, AC-03, AC-04, AC-05
      Acceptance criteria: `./gradlew testDebugUnitTest` e `openspec validate --specs` executam com sucesso com zero falhas.
      Testes: `./gradlew testDebugUnitTest --tests "*SplitAgenda*"`
