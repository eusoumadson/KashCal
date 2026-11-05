# Fork Identity Distribution Specification

## Purpose
Define os requisitos normativos para a consolidação da identidade do aplicativo como Abacate Calendário (rótulo visível "Calendário"), a distribuição direta via APK assinado no GitHub Releases integrado ao Obtainium, a higienização dos canais de loja do upstream e a documentação primária do repositório em Português do Brasil.

## Requirements

### Requirement: RF-01 - Identidade do Aplicativo e Rótulo Visível no Android
WHEN o aplicativo for compilado e instalado no sistema operacional Android, THE SYSTEM SHALL exibir o rótulo "Calendário" na gaveta de aplicativos e na tela inicial através do recurso `fork_app_name` (verified at `app/build.gradle.kts:54`), mantendo o isolamento de conta `fork_account_label` (verified at `app/build.gradle.kts:55`) e o `applicationIdSuffix = ".tasks"` para garantir que o aplicativo possa coexistir sem conflitos de autenticador ao lado do KashCal upstream.

#### Scenario: Instalação e exibição no lançador do Android
- **WHEN** o usuário instalar o APK compilado no dispositivo
- **THEN** o nome visível do aplicativo sob o ícone no launcher MUST ser "Calendário"
- **AND** o tipo de conta registrado no Android AccountManager MUST ser `org.onekash.kashcal.tasks` sem colidir com `org.onekash.kashcal`.

### Requirement: RF-02 - Consolidação e Renomeação do Repositório Remoto
WHEN a transição de identidade for executada, THE SYSTEM SHALL registrar o repositório remoto sob o slug `abacate-calendario` na conta `eusoumadson` no GitHub via GitHub CLI, E ATUALIZAR a URL do remote git local `fork` para `https://github.com/eusoumadson/abacate-calendario.git`.

#### Scenario: Sincronização de remotes após renomeação
- **WHEN** o comando `git remote -v` for executado na raiz do repositório
- **THEN** o remote `fork` MUST apontar para `https://github.com/eusoumadson/abacate-calendario.git`
- **AND** o remote `origin` MUST continuar apontando para o upstream `https://github.com/KashCal/KashCal.git`.

### Requirement: RF-03 - Higienização e Remoção de Metadados de Lojas Upstream
WHEN os artefatos de distribuição forem higienizados, THE SYSTEM SHALL remover todas as referências ativas à Google Play Store e F-Droid upstream, a chave de verificação de assinatura alheia do README, e DEVERÁ expurgar os arquivos de metadados da Play Store no diretório `fastlane/metadata/android/` mantendo as capturas de tela essenciais reutilizáveis em `images/`.

#### Scenario: Remoção de artefatos de lojas proprietárias
- **WHEN** a verificação de arquivos for executada
- **THEN** nenhum link ou badge apontando para `play.google.com/store/apps/details?id=org.onekash.kashcal` ou `f-droid.org/packages/org.onekash.kashcal` SHALL permanecer no `README.md`
- **AND** a impressão digital do certificado de terceiro da OneKash SHALL NÃO estar presente no repositório.

### Requirement: RF-04 - Pipeline Automatizado de Release para Obtainium
WHEN uma nova tag de versão for enviada ao repositório remoto no GitHub, o workflow `.github/workflows/release.yml` SHALL compilar o APK de release assinado com nome padronizado `AbacateCalendario-<version>-release.apk`, criar a GitHub Release e anexar o binário como asset de primeiro nível, sem restrição condicional de repositório upstream (removendo `if: github.repository == 'KashCal/KashCal'` em `.github/workflows/release.yml:23`).

#### Scenario: Geração de release no repositório pessoal
- **WHEN** uma tag iniciando com `v*` for enviada para `eusoumadson/abacate-calendario`
- **THEN** o GitHub Actions MUST executar o job `build-release`
- **AND** a release criada no GitHub MUST conter o asset APK pronto para consumo automático pelo Obtainium.

### Requirement: RF-05 - Documentação Primária do Repositório em Português (PT-BR)
WHEN um visitante ou desenvolvedor acessar o arquivo `README.md` na raiz do repositório, THE SYSTEM SHALL apresentar a documentação em Português do Brasil com: identificação do projeto "Abacate Calendário", filosofia de integração transparente inspirada no Apple Calendar (eventos + tarefas Tasks.org + travel time), instruções claras de instalação via Obtainium (com link direto de 1-toque) e download de APKs, e seção de reconhecimento e créditos ao projeto upstream KashCal.

#### Scenario: Apresentação do README.md
- **WHEN** o arquivo `README.md` for renderizado
- **THEN** o texto principal MUST estar redigido em Português do Brasil
- **AND** MUST incluir link funcional para adição direta no Obtainium (`https://apps.obtainium.imranr.dev/redirect?r=obtainium://...`)
- **AND** MUST incluir link de atribuição ao repositório original `https://github.com/KashCal/KashCal`.
