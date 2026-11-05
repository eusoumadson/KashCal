# Tasks

## Phase 1: Reorganização da UI de Backup e Testes

Antes de implementar, leia:
1. `AGENTS.md` e `docs/agents/architecture.md` — regras de arquitetura e comandos de build/teste
2. `proposal.md`, `specs/` e `design.md` deste diretório de change — requisitos RIGID, cenários e decisões técnicas

- [ ] T01 — Mover a linha de backup automático para Recursos Avançados em AccountSettingsScreen
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/ui/screens/AccountSettingsScreen.kt`
      Mudança: Mover o bloco `// FORK:auto-backup` de `settings_section_data` para `settings_section_advanced_features` preservando os lambdas e o marcador do fork
      Cobre: RF-03, AC-01, AC-02
      Acceptance criteria: O item de menu `auto-backup` é filho direto de `settings_section_advanced_features` e a seção `settings_section_data` contém estritamente os 5 itens nativos do upstream
      Testes: `app/src/test/kotlin/org/onekash/kashcal/ui/screens/AccountSettingsScreenComposeTest.kt` — teste de renderização dos itens em suas respectivas seções

- [ ] T02 — Adicionar nota explicativa em AutoBackupSheet sobre preferências do fork e atualizar strings
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/ui/screens/settings/AutoBackupSheet.kt`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values-pt/strings.xml`, `app/src/main/res/values-pt-rBR/strings.xml`
      Mudança: Incluir texto descritivo informando que o backup de configurações engloba as opções do KashCal e os recursos avançados do fork (Tasks.org, Travel Time e widgets)
      Cobre: RF-03, AC-03, AC-04
      Acceptance criteria: Nova string declarada em `values/strings.xml` e traduzida sincronizadamente em `values-pt` e `values-pt-rBR` exibida na `AutoBackupSheet`
      Testes: `app/src/test/kotlin/org/onekash/kashcal/ui/screens/settings/AutoBackupSheetTest.kt`

- [ ] T03 — Executar validação automatizada e testes de regressão
      Arquivos: `app/src/test/kotlin/org/onekash/kashcal/ui/screens/AccountSettingsScreenComposeTest.kt`
      Mudança: Atualizar asserções de testes para a nova hierarquia de componentes e rodar `./gradlew testDebugUnitTest`
      Cobre: AC-05
      Acceptance criteria: `./gradlew testDebugUnitTest` executa com sucesso sem falhas
      Testes: `./gradlew testDebugUnitTest`
