# Tasks

## Phase 1: Identidade Android e Atualização de Recursos

Antes de implementar, leia:
1. `AGENTS.md` e `docs/agents/architecture.md` — regras de arquitetura e comandos de build/teste
2. `proposal.md`, `specs/` e `design.md` deste diretório de change — requisitos RIGID, cenários e decisões técnicas

- [ ] T01 — Atualizar rótulo visível do aplicativo e descrições de conta no build
      Arquivos: `app/build.gradle.kts`
      Mudança: Modificar `fork_app_name` para `"Calendário"`, `fork_account_label` para `"Calendário"` e `fork_contacts_account_label` para `"Calendário Contatos"` preservando o `applicationIdSuffix = ".tasks"`.
      Cobre: RF-01, AC-02
      Acceptance criteria: `app/build.gradle.kts` define `resValue("string", "fork_app_name", "Calendário")`.
      Testes: `./gradlew :app:assembleDebug --no-daemon` — compilação bem-sucedida com as novas strings de recurso.

- [ ] T02 — Atualizar testes unitários de rótulo e manifest do launcher
      Arquivos: `app/src/test/kotlin/org/onekash/kashcal/LauncherAliasManifestTest.kt`
      Mudança: Atualizar as asserções de teste que esperavam `"KashCal Tasks"` para verificar o novo rótulo esperado `"Calendário"`.
      Cobre: RF-01, AC-02, AC-06
      Acceptance criteria: `LauncherAliasManifestTest.kt` valida o rótulo `"Calendário"` sem falhas de asserção.
      Testes: `./gradlew :app:testDebugUnitTest --tests "*LauncherAliasManifestTest*"` — todos os testes da classe passam com sucesso.

## Phase 2: Renomeação do Repositório Remoto e Sincronização Local

Antes de implementar, leia:
1. `AGENTS.md` — convenções de remotes Git (origin = upstream, fork = pessoal)
2. `specs/fork-identity-distribution/spec.md` — RF-02 e AC-01

- [ ] T03 — Renomear o repositório remoto no GitHub para abacate-calendario
      Arquivos: `.git/config`
      Mudança: Executar a renomeação do repositório remoto de `eusoumadson/KashCal` para `eusoumadson/abacate-calendario` utilizando o GitHub CLI (`gh repo rename`).
      Cobre: RF-02, AC-01
      Acceptance criteria: O comando `gh repo view eusoumadson/abacate-calendario --json name` retorna `"abacate-calendario"`.
      Testes: `gh repo view eusoumadson/abacate-calendario` — confirmação de acesso e metadados no GitHub.

- [ ] T04 — Atualizar URL do remote local fork
      Arquivos: `.git/config`
      Mudança: Atualizar a URL do remote `fork` para `https://github.com/eusoumadson/abacate-calendario.git` mantendo `origin` apontando para o upstream `KashCal/KashCal`.
      Cobre: RF-02, AC-01
      Acceptance criteria: `git remote get-url fork` retorna `https://github.com/eusoumadson/abacate-calendario.git`.
      Testes: `git remote -v` — exibe a URL atualizada para `fork` e preserva `origin`.

## Phase 3: Higienização de Metadados de Lojas Upstream

Antes de implementar, leia:
1. `specs/fork-identity-distribution/spec.md` — RF-03 e AC-03
2. `design.md` — Decisão D-03 (Limpeza de artefatos de loja)

- [ ] T05 — Reorganizar capturas de tela essenciais para pasta dedicada
      Arquivos: `images/screenshots/`
      Mudança: Mover/copiar as capturas de tela essenciais da pasta `fastlane/metadata/android/en-US/images/phoneScreenshots/` para `images/screenshots/` para servirem de base visual à documentação.
      Cobre: RF-03, AC-03
      Acceptance criteria: Diretório `images/screenshots/` contém as imagens necessárias para o README.
      Testes: `ls images/screenshots/` — confirma presença dos arquivos de imagem PNG.

- [ ] T06 — Expurgar metadados da Google Play Store da pasta fastlane
      Arquivos: `fastlane/metadata/`
      Mudança: Remover os metadados de loja (textos promocionais, changelogs e descrições específicas de submissão do Google Play) que não são utilizados pelo fork.
      Cobre: RF-03, AC-03
      Acceptance criteria: Diretório `fastlane/metadata/android/` não contém mais arquivos obsoletos de submissão da Play Store.
      Testes: `git status -s fastlane/` — confirma remoção dos metadados de loja.

## Phase 4: Automação de CI de Release para o Obtainium

Antes de implementar, leia:
1. `specs/fork-identity-distribution/spec.md` — RF-04 e AC-04
2. `.github/workflows/release.yml` — estrutura atual de build e assinatura

- [ ] T07 — Adaptar workflow de release do GitHub Actions
      Arquivos: `.github/workflows/release.yml`
      Mudança: Remover a condição restritiva `if: github.repository == 'KashCal/KashCal'`, renomear o artefato compilado para `AbacateCalendario-${{ steps.version.outputs.version }}-release.apk` e garantir publicação contínua em tags.
      Cobre: RF-04, AC-04
      Acceptance criteria: `.github/workflows/release.yml` não contém a trava de repositório upstream e gera o asset com prefixo `AbacateCalendario`.
      Testes: Inspeção sintática do arquivo YAML e verificação dos passos de upload do release asset.

## Phase 5: Documentação Primária (PT-BR) e Verificação Geral

Antes de implementar, leia:
1. `specs/fork-identity-distribution/spec.md` — RF-05 e AC-05
2. `AGENTS.md` — seção 3 (Behavioral Guidelines e Mandatory Completion Gate)

- [ ] T08 — Redigir novo README.md primário em Português do Brasil
      Arquivos: `README.md`
      Mudança: Substituir o README upstream por documentação completa em Português (PT-BR): identidade Abacate Calendário, badges de Obtainium e GitHub Releases, resumo dos 3 pilares (eventos, tarefas do Tasks.org e alertas de deslocamento Travel Time), guia de instalação e créditos abertos ao upstream KashCal.
      Cobre: RF-05, AC-05
      Acceptance criteria: `README.md` está redigido em Português, inclui link direto do Obtainium e não contém certificados de assinatura alheios nem links da Google Play/F-Droid oficial.
      Testes: Leitura e validação de links Markdown no arquivo `README.md`.

- [ ] T09 — Executar suíte de testes unitários e validação OpenSpec
      Arquivos: `app/build.gradle.kts`, `app/src/test/`
      Mudança: Executar a suíte de testes unitários do Android para garantir 100% de passagem sem quebras nas sentinelas de graft (`// FORK:*`).
      Cobre: AC-06
      Acceptance criteria: `./gradlew testDebugUnitTest --no-daemon` completa com sucesso (código de saída 0).
      Testes: `./gradlew testDebugUnitTest --no-daemon` — suíte completa executada com sucesso.
