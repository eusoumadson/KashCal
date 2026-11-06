# Spec Delta: fork-identity-distribution

## ADDED Requirements

### Requirement: RF-06 - Rótulo Minimalista no Menu Hambúrguer e Marca Conversacional nas Strings do App
WHEN o usuário abrir o menu lateral ou visualizar mensagens do sistema, THE SYSTEM SHALL exibir `"Calendário"` em `CalendarDrawer` (verified at `app/src/main/kotlin/org/onekash/kashcal/ui/components/CalendarDrawer.kt:99`) via `R.string.app_name` (verified at `app/src/main/res/values/strings.xml:4`) E SHALL utilizar `"Abacate Calendário"` nas strings conversacionais fora dos contextos de atribuição ao upstream (`values`, `values-pt`, `values-pt-rBR`), sem exibir `"KashCal"`.

#### Scenario: Exibição do cabeçalho no menu hambúrguer
- **WHEN** o usuário abrir a gaveta lateral de navegação (`CalendarDrawer`) na tela principal
- **THEN** o cabeçalho no topo do drawer MUST exibir exatamente `"Calendário"`
- **AND** MUST NOT exibir `"KashCal"` nem o nome extenso `"Abacate Calendário"` no topo da gaveta.

#### Scenario: Mensagens conversacionais do sistema (permissões, bloqueio, backup e compartilhamento)
- **WHEN** o aplicativo apresentar avisos de permissão, mensagens de bloqueio por biometria, confirmações/erros de backup, rodapés de compartilhamento ou notas de integração com Tasks.org
- **THEN** o texto exibido MUST referir-se ao próprio aplicativo como `"Abacate Calendário"`
- **AND** nenhuma dessas strings em `values/strings.xml`, `values-pt/strings.xml` ou `values-pt-rBR/strings.xml` SHALL conter a palavra `"KashCal"`.

### Requirement: RF-07 - Folha Sobre Principal (AboutSheet) com Destaque ao Projeto Atual e Créditos Upstream
WHEN o usuário abrir `AboutSheet` (verified at `app/src/main/kotlin/org/onekash/kashcal/ui/components/AboutSheet.kt:52`), THE SYSTEM SHALL exibir o cabeçalho `"Abacate Calendário 🥑"`, uma seção do projeto atual com links para `ExternalLinks.FORK_SOURCE` e `ExternalLinks.FORK_ISSUES` em `ExternalLinks` (verified at `app/src/main/kotlin/org/onekash/kashcal/util/ExternalLinks.kt:15`), seguida pela seção upstream (`HELP`, `SOURCE`, `DONATE`) e rodapé `"Abacate Calendário 🥑 v%1$s"`.

#### Scenario: Renderização e navegação de links no AboutSheet
- **WHEN** o `AboutSheet` for exibido na tela com a versão `"2026.10.30-2"`
- **THEN** o título semântico de cabeçalho MUST conter `"Abacate Calendário 🥑"`
- **AND** a seção do projeto atual MUST exibir botões que abrem `https://github.com/eusoumadson/abacate-calendario` e `https://github.com/eusoumadson/abacate-calendario/issues`
- **AND** a seção de reconhecimento ao upstream MUST preservar os botões para `ExternalLinks.HELP`, `ExternalLinks.SOURCE` e `ExternalLinks.DONATE`
- **AND** o rodapé MUST exibir a identificação da versão no formato `"Abacate Calendário 🥑 v2026.10.30-2"`.

### Requirement: RF-08 - Rodapé de Versão nas Configurações (VersionFooter) e Folha AppInfoSheet
WHEN o usuário visualizar o final das configurações ou tocar no rodapé, THE SYSTEM SHALL exibir `"Abacate Calendário 🥑 v%1$s"` em `VersionFooter` (verified at `app/src/main/kotlin/org/onekash/kashcal/ui/screens/settings/FlatSettingsComponents.kt:380`) via `R.string.status_version` E abrir `AppInfoSheet` (verified at `app/src/main/kotlin/org/onekash/kashcal/ui/components/AppInfoSheet.kt:35`) com `"Abacate Calendário 🥑"`, link `FORK_SOURCE` e créditos upstream (`DONATE` e `HOME`).

#### Scenario: Visualização do rodapé nas Configurações e abertura do AppInfoSheet
- **WHEN** o usuário rolar até o final da tela de configurações e tocar em `VersionFooter`
- **THEN** o texto do rodapé MUST ser `"Abacate Calendário 🥑 v<versionName>"`
- **AND** o `AppInfoSheet` aberto MUST exibir `"Abacate Calendário 🥑"` e um link acionável para `https://github.com/eusoumadson/abacate-calendario`
- **AND** MUST manter na parte inferior os créditos ao upstream KashCal com o botão para `ExternalLinks.DONATE` e o link para `ExternalLinks.HOME`.
