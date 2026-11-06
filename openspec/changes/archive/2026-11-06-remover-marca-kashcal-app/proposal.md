# Proposal: remover-marca-kashcal-app

## Metadata
- **Tier**: light
- **Architecture References**: AGENTS.md, docs/agents/architecture.md, docs/agents/coding_guidelines.md, docs/agents/domain_rules.md
- **Capability Policy**: ignore_for_this_run

## Why

Embora o rótulo do aplicativo no launcher Android já tenha sido configurado como `"Calendário"` e o repositório tenha sido consolidado como **Abacate Calendário**, a interface interna ainda exibe `"KashCal"` no cabeçalho do menu hambúrguer, no rodapé das configurações, nas folhas de informações/sobre (`AboutSheet` e `AppInfoSheet`) e em cerca de 20 strings conversacionais (permissões, bloqueio do app, backups e compartilhamento). Precisamos alinhar toda a experiência interna à marca **Abacate Calendário** (e ao rótulo minimalista **Calendário** no topo do menu lateral), mantendo o nome e os links do **KashCal** exclusivamente nos contextos de atribuição e reconhecimento ao projeto upstream.

## Scope
- **In**:
  - Cabeçalho do menu lateral (`CalendarDrawer.kt` / `R.string.app_name`) exibindo `"Calendário"` de forma minimalista.
  - Substituição de `"KashCal"` por `"Abacate Calendário"` em todas as strings conversacionais com o usuário (permissões, bloqueio de app, backup/restauração, compartilhamento, notas de integração Tasks.org e cor de destaque) em `values/strings.xml`, `values-pt/strings.xml` e `values-pt-rBR/strings.xml`.
  - Reestruturação do `AboutSheet.kt` (acessado via Hub de Perfil) com cabeçalho `"Abacate Calendário 🥑"`, seção principal dedicada ao projeto atual (`eusoumadson/abacate-calendario` — Código & Releases e Reportar problema/Sugestões) seguida da seção de reconhecimento ao upstream KashCal (mantendo links de ajuda/documentação, repositório original e doação) e rodapé `"Abacate Calendário 🥑 v%1$s"`.
  - Atualização do `VersionFooter` (`FlatSettingsComponents.kt`) nas configurações para exibir `"Abacate Calendário 🥑 v%1$s"` e reformulação do `AppInfoSheet.kt` para apresentar a identidade `"Abacate Calendário 🥑"` e link do repositório do fork acima da seção de créditos e apoio ao upstream KashCal.
  - Extensão de `ExternalLinks.kt` com constantes para o repositório e issues do fork (`FORK_SOURCE`, `FORK_ISSUES`).
  - Atualização dos testes unitários/Compose (`AboutSheetTest.kt` e novos testes para `AppInfoSheet` / `VersionFooter`) e do mapa `docs/fork/FORK_INTEGRATION.md`.
- **Out**:
  - Alteração de `namespace`, `applicationId`, `applicationIdSuffix` ou estrutura de pacotes Kotlin (`org.onekash.kashcal`).
  - Edição manual de diretórios de localização diferentes de `values`, `values-pt` e `values-pt-rBR`.
  - Remoção das ilustrações decorativas em `EmptyDayState.kt`.

## What Changes

- Atualização de `app_name` para `"Calendário"` e substituição de referências textuais ao `"KashCal"` por `"Abacate Calendário"` nas strings de diálogo, erro, permissão, backup e compartilhamento nos três arquivos de recursos mantidos pelo fork (`values/strings.xml`, `values-pt/strings.xml`, `values-pt-rBR/strings.xml`).
- Adição de `FORK_SOURCE` (`https://github.com/eusoumadson/abacate-calendario`) e `FORK_ISSUES` (`https://github.com/eusoumadson/abacate-calendario/issues`) em `ExternalLinks.kt`.
- Reformulação de `AboutSheet.kt` para exibir `"Abacate Calendário 🥑"`, subtítulo do projeto, bloco de links do fork e bloco de créditos/links do upstream KashCal (`HELP`, `SOURCE`, `DONATE`), além do rodapé `"Abacate Calendário 🥑 v%1$s"`.
- Atualização de `status_version` para `"Abacate Calendário 🥑 v%1$s"` no `VersionFooter` e reformulação de `AppInfoSheet.kt` com destaque para `"Abacate Calendário 🥑"`, link do GitHub do fork e seção de apoio ao upstream KashCal.
- Atualização de `AboutSheetTest.kt` e documentação de pontos de enxertia em `docs/fork/FORK_INTEGRATION.md`.

## Capabilities

### New Capabilities
<!-- Nenhuma nova capability isolada; estende a capability existente de identidade do fork. -->

### Modified Capabilities
- `fork-identity-distribution`: Adiciona requisitos normativos para a identidade visual interna no menu lateral (`Calendário`), nas strings conversacionais (`Abacate Calendário`) e nas duas superfícies de Sobre/Informações do aplicativo (`AboutSheet` e `VersionFooter`/`AppInfoSheet`) com atribuição preservada ao upstream KashCal.

## Confirmed Acceptance Criteria (Source of Truth)
- **AC-01**: O cabeçalho do menu hambúrguer (`CalendarDrawer.kt`) exibe `"Calendário"` (sem usar `"KashCal"` nem o nome longo no topo do drawer) e nenhuma string conversacional fora do contexto de Sobre/Upstream menciona `"KashCal"` em `values/strings.xml`, `values-pt/strings.xml` e `values-pt-rBR/strings.xml`, utilizando `"Abacate Calendário"` quando o app se refere a si mesmo ao usuário.
- **AC-02**: O `AboutSheet` (acessado pelo Hub de Perfil) exibe o título `"Abacate Calendário 🥑"`, uma seção do projeto atual com links funcionais para o repositório (`https://github.com/eusoumadson/abacate-calendario`) e issues (`https://github.com/eusoumadson/abacate-calendario/issues`), seguida por uma seção de reconhecimento ao upstream KashCal contendo os links originais (`ExternalLinks.HELP`, `ExternalLinks.SOURCE`, `ExternalLinks.DONATE`) e rodapé com `"Abacate Calendário 🥑 v%1$s"`.
- **AC-03**: O rodapé da tela de configurações (`VersionFooter` via `status_version`) exibe `"Abacate Calendário 🥑 v%1$s"` e, ao ser acionado, abre o `AppInfoSheet` apresentando `"Abacate Calendário 🥑"`, link para o repositório do projeto atual e seção de reconhecimento e apoio ao upstream KashCal (`ExternalLinks.DONATE` e `kashcal.onekash.org`).

## Impact
- **UI / Compose**: `app/src/main/kotlin/org/onekash/kashcal/ui/components/AboutSheet.kt`, `app/src/main/kotlin/org/onekash/kashcal/ui/components/AppInfoSheet.kt`, `app/src/main/kotlin/org/onekash/kashcal/ui/components/CalendarDrawer.kt`.
- **Utilitários**: `app/src/main/kotlin/org/onekash/kashcal/util/ExternalLinks.kt`.
- **Recursos de String**: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-pt/strings.xml`, `app/src/main/res/values-pt-rBR/strings.xml`.
- **Testes & Documentação**: `app/src/test/kotlin/org/onekash/kashcal/ui/components/AboutSheetTest.kt`, `docs/fork/FORK_INTEGRATION.md`.
