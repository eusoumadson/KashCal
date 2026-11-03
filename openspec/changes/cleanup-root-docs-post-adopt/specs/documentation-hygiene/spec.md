# Delta Specification: documentation-hygiene

## ADDED Requirements

### Requirement: RF-01 - Consolidação da Documentação do Fork sob `docs/fork/`
WHEN a etapa de limpeza pós-`/opsh:ai-context --adopt` for executada, THE SYSTEM SHALL mover `FORK_INTEGRATION.md` e `FORK_FEATURES.md` da raiz do repositório para `docs/fork/FORK_INTEGRATION.md` e `docs/fork/FORK_FEATURES.md` preservando integralmente o mapa de rebase (`// FORK:tasks-org` e `// FORK:travel-time`).

#### Scenario: Preservação integral dos guias do fork e remoção da raiz
- **WHEN** `docs/fork/FORK_INTEGRATION.md` e `docs/fork/FORK_FEATURES.md` forem criados a partir dos arquivos da raiz
- **THEN** os arquivos originais `FORK_INTEGRATION.md` e `FORK_FEATURES.md` na raiz não deverão mais existir e todas as referências em `AGENTS.md` e `docs/agents/*.md` deverão apontar para `docs/fork/`.

### Requirement: RF-02 - Organização dos Roteiros de Teste em Dispositivo sob `docs/testing/`
WHEN a etapa de limpeza pós-`/opsh:ai-context --adopt` for executada, THE SYSTEM SHALL mover `TASKS_ORG_DEVICE_TEST.md` e `TRAVEL_TIME_DEVICE_TEST.md` da raiz do repositório para `docs/testing/TASKS_ORG_DEVICE_TEST.md` e `docs/testing/TRAVEL_TIME_DEVICE_TEST.md`.

#### Scenario: Migração dos roteiros de teste em aparelho físico
- **WHEN** os roteiros de teste forem movidos para `docs/testing/`
- **THEN** `TASKS_ORG_DEVICE_TEST.md` e `TRAVEL_TIME_DEVICE_TEST.md` não deverão mais existir na raiz do repositório e os links em `AGENTS.md` deverão apontar para `docs/testing/TASKS_ORG_DEVICE_TEST.md` e `docs/testing/TRAVEL_TIME_DEVICE_TEST.md`.
