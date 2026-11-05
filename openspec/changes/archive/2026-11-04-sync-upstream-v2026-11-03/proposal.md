# Proposal: sync-upstream-v2026-11-03

## Metadata
- **Tier**: standard
- **Architecture References**: `AGENTS.md`, `docs/agents/architecture.md`, `docs/agents/domain_rules.md`, `docs/fork/FORK_INTEGRATION.md`
- **Capability Policy**: ignore_for_this_run
- **Target Device**: RQ8N502VJ1M (conectado e autorizado para validação em hardware físico)

## Why

O projeto original upstream (`KashCal/KashCal`, remoto `origin/main`) lançou três versões consecutivas (`v2026.10.30`, `v2026.11.01` e `v2026.11.03`), trazendo correções cruciais de fuso horário (#422), novo seletor unificado de data/hora (#330), melhorias na sincronização CalDAV (#417, #421), contraste acessível de cores (#419) e o mascote Kash.
Precisamos integrar essas inovações no nosso fork (`eusoumadson/KashCal`) de maneira cirúrgica e segura, mantendo 100% funcionais as características exclusivas do fork: integração com Tasks.org, alertas preditivos de Travel Time, auto-backup e localização completa em pt-BR.

## Scope
- **In**:
  - Incorporação dos commits upstream entre `v2026.10.26` (0164dad) e `v2026.11.03` (9ec14f3).
  - Resolução cirúrgica dos conflitos nos 7 arquivos de núcleo (`KashCalApplication.kt`, `DisplayEventRepository.kt`, `SettingsRoute.kt`, `AccountSettingsViewModel.kt`, `HomeViewModel.kt`, `strings.xml`, `SettingsDestinationTest.kt`).
  - Adaptação das tarefas do Tasks.org e do Travel Time à nova reatividade de fuso horário (`ZoneSource`, `LocalDisplayZone`, `TimezoneChangeHandler`).
  - Reconciliação do recurso de backup automático do upstream com o auto-backup já existente no fork.
  - Sincronização e complementação das strings em `values-pt` e `values-pt-rBR`.
  - Compilação limpa, testes unitários sem regressão e validação no dispositivo físico conectado.
- **Out**:
  - Remoção de qualquer funcionalidade ou sentinela existente (`// FORK:tasks-org`, `// FORK:travel-time`).
  - Alteração no esquema do banco Room do Tasks.org (o fork continua não persistindo dados do Tasks.org no Room).

## What Changes

- **Núcleo de Fusos e Exibição**: Atualizar `ZoneSource`, `LocalDisplayZone` e DAOs para o modelo upstream #422, garantindo que `DisplayEvent.Task` e os alertas de viagem respeitem a mesma zona de exibição sem deslocamento incorreto de datas.
- **Seletor de Data e Hora**: Adoção do novo bottom sheet com chave *Starts | Ends* e chips de duração rápida para eventos regulares, mantendo o editor dedicado `TaskEditorSheet` para tarefas.
- **Acessibilidade e Contraste**: Inclusão da regra de 3:1 de contraste para textos e chips.
- **Sincronização CalDAV**: Inclusão de suporte a calendários em `/Tasks/` sem colidir com as rotas do provider Tasks.org.
- **Conflitos de Navegação e Settings**: Integração de novos destinos de configurações upstream sem perder as entradas de listas/tags/lugares do Tasks.org.
- **Strings e Localização**: Atualização dos catálogos de strings em português para todas as novas chaves upstream.
- **Validação em Hardware**: Deploy e teste direto no dispositivo Android físico conectado (`RQ8N502VJ1M`).

## Capabilities

### New Capabilities
- `upstream-sync-parity`: Paridade funcional com a versão v2026.11.03 do KashCal upstream (fusos reativos, seletor moderno, contraste dinâmico e sync hardening) coexistindo com o fork.

### Modified Capabilities
- `tasks-org-integration`: Compatibilização da exibição de tarefas com o novo `LocalDisplayZone` e manutenção dos pontos de enxerto do fork.
- `auto-backup`: Reconciliação da rotina de backup do upstream (para calendários offline) com a exportação de preferências e rotinas do fork.

## Confirmed Acceptance Criteria (Source of Truth)
- **AC-01**: Atualizações upstream integradas (`Time zones #422`, seletor de data/hora `#330`, contraste `#419`, mascote Kash e melhorias de sync).
- **AC-02**: Todas as sentinelas `// FORK:tasks-org` e `// FORK:travel-time` preservadas sem regressão funcional nas tarefas e alertas.
- **AC-03**: Os 7 arquivos com conflitos mecânicos resolvidos mantendo o padrão aditivo do fork documentado em `docs/fork/FORK_INTEGRATION.md`.
- **AC-04**: O novo seletor e infraestrutura de fuso horário (`ZoneSource` / `LocalDisplayZone`) funcionam em harmonia com tarefas do Tasks.org (tarefas continuam no dia exato de vencimento e alertas de partida no instante exato).
- **AC-05**: Recursos de strings novos do upstream traduzidos em `values-pt` e `values-pt-rBR` sem quebrar chaves existentes.
- **AC-06**: Validação automatizada completa: `./gradlew assembleDebug` e `./gradlew testDebugUnitTest` executam com 100% de sucesso.
- **AC-07**: Deploy e smoke-test no dispositivo físico conectado (`RQ8N502VJ1M`) aprovados com Tasks.org ativo.

## Impact
- **Affected Code Paths**: `KashCalApplication.kt`, `DisplayEventRepository.kt`, `HomeViewModel.kt`, `HomeScreen.kt`, `SettingsRoute.kt`, `AccountSettingsViewModel.kt`, `strings.xml`.
- **Database / Migrations**: Preservação das migrações upstream e integridade dos esquemas locais.
- **External Contracts**: Nenhuma quebra na API v0 do Tasks.org ou nos provedores de roteamento do Travel Time.
- **Target Device**: `RQ8N502VJ1M` conectado para teste on-device após implementação.
