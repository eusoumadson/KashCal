# Proposal: cleanup-root-docs-post-adopt

## Metadata

- **Complexity Tier**: `light`
- **Service / Module**: `kashcal_tasks` (Root Documentation & `docs/` Hygiene)
- **Architecture References**: `AGENTS.md`, `docs/agents/architecture.md` (pós-`/opsh:ai-context --adopt`)
- **Capability Policy**: `ignore_for_this_run`

## Why

Após a execução de `/opsh:ai-context --adopt`, o repositório passa a contar com a árvore canônica `AGENTS.md`, `CLAUDE.md` e `docs/agents/*.md`. No entanto, como o `/opsh:ai-context` não remove arquivos manuais da raiz por conta própria, permanecem na raiz do repositório 4 documentos específicos do fork (`FORK_INTEGRATION.md`, `FORK_FEATURES.md`, `TASKS_ORG_DEVICE_TEST.md` e `TRAVEL_TIME_DEVICE_TEST.md`). Movê-los e consolidá-los sob `docs/` deixa a raiz do projeto limpa e alinhada ao upstream `KashCal`, preservando 100% do guia de rebase e dos roteiros de teste em aparelho físico.

## What Changes

- Consolidar `FORK_INTEGRATION.md` e `FORK_FEATURES.md` em `docs/agents/fork_integration.md` (ou manter em `docs/fork/`) preservando o mapa completo de `// FORK:tasks-org` e `// FORK:travel-time`.
- Mover `TASKS_ORG_DEVICE_TEST.md` e `TRAVEL_TIME_DEVICE_TEST.md` da raiz para `docs/testing/`.
- Atualizar todas as referências e links em `AGENTS.md` e `docs/agents/*.md` para apontarem para os novos caminhos sob `docs/`.
- Remover da raiz os 4 arquivos `.md` migrados (`FORK_INTEGRATION.md`, `FORK_FEATURES.md`, `TASKS_ORG_DEVICE_TEST.md`, `TRAVEL_TIME_DEVICE_TEST.md`).

## Capabilities

### New Capabilities
- `documentation-hygiene`: Organização canônica dos documentos do fork e roteiros de teste em dispositivo fora da raiz do repositório.

### Modified Capabilities
- *(nenhuma especificação de código alterada)*

## Confirmed Acceptance Criteria

- [ ] **AC-01**: Todo o conteúdo de `FORK_INTEGRATION.md` e `FORK_FEATURES.md` está preservado em `docs/fork/FORK_INTEGRATION.md` e `docs/fork/FORK_FEATURES.md` (e referenciado em `AGENTS.md` / `docs/agents/`).
- [ ] **AC-02**: Os roteiros `TASKS_ORG_DEVICE_TEST.md` e `TRAVEL_TIME_DEVICE_TEST.md` estão movidos para `docs/testing/` com links atualizados em `AGENTS.md`.
- [ ] **AC-03**: Os 4 arquivos (`FORK_INTEGRATION.md`, `FORK_FEATURES.md`, `TASKS_ORG_DEVICE_TEST.md`, `TRAVEL_TIME_DEVICE_TEST.md`) não existem mais soltos na raiz do repositório.

## Impact

- **Affected Modules / Layers**: Apenas arquivos de documentação Markdown (`.md`). Nenhum código Kotlin ou recurso Android é alterado.
- **Database / Migrations**: Nenhuma.
- **External APIs / Contracts**: Nenhum.
