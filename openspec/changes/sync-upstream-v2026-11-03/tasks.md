# Tasks

## Phase 1: Upstream Merge & Core Conflicts Resolution

Antes de implementar, leia:
1. `AGENTS.md` e `docs/agents/architecture.md` — regras de arquitetura e comandos de build/teste
2. `proposal.md`, `specs/` e `design.md` deste diretório de change — requisitos RIGID, cenários e decisões técnicas
3. `docs/fork/FORK_INTEGRATION.md` — mapa dos pontos de enxerto do fork no core

- [ ] T01 — Executar merge de origin/main e isolar conflitos
      Arquivos: `git status`, `CHANGELOG.md`
      Mudança: Executar `git merge origin/main --no-commit` para aplicar os commits upstream no fork e deixar os conflitos prontos para resolução cirúrgica
      Cobre: RF-01, RF-02, RF-03, RF-04, RF-05, AC-01
      Acceptance criteria: Árvore git em estado de merge ativo com os 7 arquivos de conflito identificados
      Testes: `git status` — confirma merge em andamento e arquivos em conflito

- [ ] T02 — Resolver conflitos em KashCalApplication e DisplayEventRepository
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/KashCalApplication.kt`, `app/src/main/kotlin/org/onekash/kashcal/domain/reader/DisplayEventRepository.kt`
      Mudança: Resolver conflitos mantendo os imports e inicializadores de TasksOrgManager e Travel Time em KashCalApplication, e mantendo a injeção de tasksOrgRepository em DisplayEventRepository integrada ao novo parâmetro de ZoneId do upstream
      Cobre: RF-01, RF-04, AC-02, AC-03, AC-04
      Acceptance criteria: Arquivos sem marcadores de conflito git e com comentários sentinela `// FORK:tasks-org` intactos
      Testes: `./gradlew compileDebugKotlin` — compilação bem-sucedida desses dois módulos

- [ ] T03 — Resolver conflitos de rotas e ViewModel de configurações
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/ui/screens/SettingsRoute.kt`, `app/src/main/kotlin/org/onekash/kashcal/ui/viewmodels/AccountSettingsViewModel.kt`, `app/src/test/kotlin/org/onekash/kashcal/ui/screens/settings/SettingsDestinationTest.kt`
      Mudança: Preservar as rotas de Tasks.org (`SettingsDestination.TasksOrg`, gestão de listas/tags/lugares) ao lado das novas rotas upstream de Force Full Sync in-place e Offline Calendars
      Cobre: RF-04, AC-02, AC-03
      Acceptance criteria: Rotas do Tasks.org e rotas upstream coexistem em SettingsRoute e SettingsDestinationTest passa sem falhas
      Testes: `app/src/test/kotlin/org/onekash/kashcal/ui/screens/settings/SettingsDestinationTest.kt` — teste de cobertura de rotas passando

- [ ] T04 — Resolver conflitos em HomeViewModel e strings.xml
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/ui/viewmodels/HomeViewModel.kt`, `app/src/main/res/values/strings.xml`
      Mudança: Incorporar o novo fluxo reativo de fuso (`ZoneSource`, `displayZone`) no HomeViewModel mantendo os seletores, toggles e ações do Tasks.org; mesclar novas chaves de strings upstream mantendo chaves exclusivas do fork
      Cobre: RF-01, RF-02, RF-03, AC-02, AC-03, AC-04
      Acceptance criteria: HomeViewModel e strings.xml livres de conflitos de merge e compilando
      Testes: `./gradlew compileDebugKotlin` — compilação do módulo app sem erros

## Phase 2: Feature Parity, Localization & Build Gate

Antes de implementar, leia:
1. `AGENTS.md` e `docs/agents/architecture.md` — regras de convenção e testes
2. `openspec/changes/sync-upstream-v2026-11-03/specs/` — especificações formais de paridade e Tasks.org
3. `app/src/fork/tools/gen_strings_pt.py` — script de sincronização de strings em português

- [ ] T05 — Harmonizar reatividade de fuso horário em tarefas e calendário
      Arquivos: `app/src/main/kotlin/org/onekash/kashcal/domain/reader/DisplayEventRepository.kt`, `app/src/main/kotlin/org/onekash/kashcal/ui/viewmodels/HomeViewModel.kt`
      Mudança: Garantir que a leitura de tarefas do Tasks.org e seu mapeamento em DisplayEvent.Task considerem a zona ativa do ZoneSource sem sofrer off-by-one ou mudança de dia em transições de fuso
      Cobre: RF-01, AC-04
      Acceptance criteria: Tarefas agendadas para uma data continuam exibidas no dia correto após mudança simulada de fuso
      Testes: `app/src/test/kotlin/org/onekash/kashcal/domain/reader/DisplayEventRepositoryTest.kt` — teste de integração do repositório de eventos com tarefas

- [ ] T06 — Sincronizar localização em português para novas strings upstream
      Arquivos: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-pt/strings.xml`, `app/src/main/res/values-pt-rBR/strings.xml`
      Mudança: Adicionar as traduções das novas strings do upstream (seletor de data/hora, mascote Kash, calendários offline, force sync) em values-pt e values-pt-rBR
      Cobre: RF-02, RF-05, AC-05
      Acceptance criteria: Todas as novas chaves presentes em `values/strings.xml` possuem traduções equivalentes em `values-pt` e `values-pt-rBR`
      Testes: `./gradlew lintDebug` — verificação de integridade de recursos e traduções

- [ ] T07 — Executar suíte de testes e fechar commit de sincronização
      Arquivos: `git status`, `build.gradle.kts`
      Mudança: Executar compilação completa e suíte de testes unitários do projeto, finalizando o commit de merge `chore: sync upstream v2026.11.03 into fork`
      Cobre: AC-06
      Acceptance criteria: `./gradlew assembleDebug` e `./gradlew testDebugUnitTest` executam com 100% de sucesso (0 testes falhando)
      Testes: `./gradlew testDebugUnitTest --no-daemon` — suíte completa de testes unitários

## Phase 3: Hardware Smoke-Test & Deploy Validation

Antes de implementar, leia:
1. `docs/fork/FORK_FEATURES.md` e `docs/testing/TASKS_ORG_DEVICE_TEST.md` — roteiro de teste em hardware
2. Dispositivo conectado: `RQ8N502VJ1M`

- [ ] T08 — Deploy e teste de fumaça no dispositivo Android físico
      Arquivos: `app/build/outputs/apk/debug/app-debug.apk`
      Mudança: Instalar o build debug no dispositivo conectado `RQ8N502VJ1M` via `adb install -r` e validar abertura, visualização do calendário com tarefas do Tasks.org, novo seletor de data/hora e tela Sobre com o mascote Kash
      Cobre: AC-07
      Acceptance criteria: App instalado com sucesso no dispositivo `RQ8N502VJ1M`, abrindo sem crashes e com Tasks.org ativo
      Testes: `adb shell am start -n org.onekash.kashcal.tasks/org.onekash.kashcal.MainActivity` — inicialização limpa no dispositivo
