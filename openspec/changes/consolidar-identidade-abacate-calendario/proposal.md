# Proposal: consolidar-identidade-abacate-calendario

## Metadata
- **Tier**: standard
- **Architecture References**: AGENTS.md, docs/agents/architecture.md, docs/agents/domain_rules.md, docs/fork/FORK_INTEGRATION.md
- **Capability Policy**: ignore_for_this_run (personal)

## Why
O fork do KashCal evoluiu significativamente, tornando-se uma central completa de produtividade pessoal privada com suporte a tarefas do Tasks.org, alertas inteligentes de deslocamento (Travel Time), widgets de duas colunas e backups automáticos. Contudo, a identidade pública do repositório (`README.md`, metadados de lojas e nome do repositório) ainda reproduz o KashCal upstream. É necessário consolidar a identidade própria do aplicativo como **Abacate Calendário** (do ecossistema *abacatelab*), exibir o rótulo minimalista **"Calendário"** no celular, remover os canais de distribuição de lojas do upstream (Play Store/F-Droid), implementar distribuição automatizada de APK assinado via GitHub Releases + Obtainium e reescrever a documentação primária em Português do Brasil, preservando integralmente os pontos de enxerto para sincronização contínua com o upstream.

## Scope
- **In**:
  - Renomeação do repositório remoto no GitHub de `eusoumadson/KashCal` para `eusoumadson/abacate-calendario` via GitHub CLI e atualização do remote `fork` local.
  - Atualização dos metadados de identidade do app no Android: rótulo público `Calendário` (`fork_app_name`), referências ao ecossistema Abacatelab/Abacate Calendário, mantendo isolamento de pacote (`applicationIdSuffix = ".tasks"` ou equivalente) sem quebrar o namespace Kotlin.
  - Remoção de referências a canais de publicação exclusivos do upstream (Google Play, F-Droid, chaves/certificados de terceiros).
  - Limpeza dos arquivos de metadados de lojas em `fastlane/`, preservando capturas de tela necessárias para a documentação.
  - Atualização do workflow `.github/workflows/release.yml` para compilar APK release assinado do fork e publicar no GitHub Releases em formato compatível com o Obtainium.
  - Redação de novo `README.md` completo em Português do Brasil com apresentação do app, filosofia inspirada no Apple Calendar (eventos + tarefas + deslocamento), guia de instalação via Obtainium e créditos abertos ao upstream KashCal.
- **Out**:
  - Alterações na lógica de domínio de eventos, tarefas ou rotas de viagem.
  - Quebra de compatibilidade com os pontos de enxerto (`// FORK:tasks-org`, `// FORK:travel-time`) documentados em `docs/fork/FORK_INTEGRATION.md`.
  - Publicação em lojas proprietárias (Google Play) ou repositórios centrais públicos (F-Droid oficial).

## What Changes
- **Remoto GitHub**: renomeação do repositório para `abacate-calendario` e ajuste do remote local `fork`.
- **Identidade Android**: padronização de `fork_app_name` para "Calendário", descrição e menção a "Abacate Calendário" nas telas de Sobre e configurações do fork.
- **Limpeza de Distribuição Upstream**: remoção de metadados da Play Store no diretório `fastlane/` e remoção da impressão digital do certificado upstream do README.
- **Workflow de Release**: remoção da restrição `if: github.repository == 'KashCal/KashCal'` no `.github/workflows/release.yml`, nomenclatura padronizada do APK (`AbacateCalendario-<version>-release.apk`) e suporte a release notes automáticos.
- **Documentação Primária**: `README.md` reimaginado e reescrito em Português do Brasil, com selos do Obtainium e GitHub Releases, resumo de funcionalidades e seção de agradecimentos ao KashCal upstream.

## Capabilities

### New Capabilities
- `fork-identity-distribution`: Governa a identidade do aplicativo (Abacate Calendário / Calendário), o canal de distribuição direta via APK/Obtainium no GitHub e o pipeline automatizado de release.

### Modified Capabilities
- `documentation-hygiene`: Atualiza os requisitos de documentação da raiz, estabelecendo o `README.md` em Português do Brasil e a remoção de artefatos obsoletos de lojas externas.

## Confirmed Acceptance Criteria (Source of Truth)
- **AC-01**: O repositório remoto no GitHub está renomeado para `eusoumadson/abacate-calendario` e o remote local `fork` aponta para a nova URL.
- **AC-02**: O aplicativo compila com o rótulo visível `Calendário` no lançador do Android (`fork_app_name`), mantendo a separação de contas e pacotes para coexistir com o KashCal upstream.
- **AC-03**: Todas as referências a Google Play, F-Droid oficial e certificado de assinatura upstream foram removidas da documentação pública e dos arquivos de build.
- **AC-04**: O workflow `.github/workflows/release.yml` é executável no repositório `eusoumadson/abacate-calendario`, gerando APKs de release assinados anexados às tags de versão para consumo pelo Obtainium.
- **AC-05**: O `README.md` na raiz do repositório está escrito integralmente em Português do Brasil, contendo badges de Obtainium/Releases, descrição das funcionalidades integradas (calendário, tarefas Tasks.org, travel time) e créditos explícitos ao KashCal.
- **AC-06**: A suíte de testes unitários `./gradlew testDebugUnitTest` passa sem regressões e as sentinelas de graft (`// FORK:*`) permanecem intactas.

## Impact
- **Repositório Git & GitHub Actions**: Atualização de URLs de remotes e execução do pipeline de CI de release no repositório pessoal.
- **Configuração Android (`app/build.gradle.kts`, `AndroidManifest.xml`)**: Strings de nome de exibição (`fork_app_name`) e nomes de APK gerado.
- **Documentação & Assets**: Substituição do `README.md` upstream e reorganização de imagens de demonstração.
