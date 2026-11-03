# Design: cleanup-root-docs-post-adopt

## Metadata

- **Complexity Tier**: `light`
- **Architecture References**: `AGENTS.md`, `docs/agents/architecture.md`

## Resumo Técnico (`light` tier)

1. **Onde mexer:** Diretórios de documentação (`docs/fork/`, `docs/testing/`, `AGENTS.md`, `docs/agents/*.md`) e remoção dos 4 arquivos `.md` correspondentes na raiz do repositório (`FORK_INTEGRATION.md`, `FORK_FEATURES.md`, `TASKS_ORG_DEVICE_TEST.md`, `TRAVEL_TIME_DEVICE_TEST.md`).
2. **Padrão a seguir:** Manter na raiz apenas `README.md`, `AGENTS.md` e `CLAUDE.md` (padrão canônico do `/opsh:ai-context`), organizando documentos específicos do fork em `docs/fork/` e roteiros de QA manual em `docs/testing/`.
3. **Como validar:** Verificar via `test ! -f` que os 4 arquivos não existem mais na raiz, `test -f` que existem em `docs/fork/` e `docs/testing/`, e `grep` que todas as referências em `AGENTS.md` e `docs/agents/*.md` apontam para os novos caminhos.

## AS IS — Estado e Componentes Atuais

```mermaid
flowchart LR
  Root["Raiz do Repositorio"] --> FI["FORK_INTEGRATION.md"]
  Root --> FF["FORK_FEATURES.md"]
  Root --> DT1["TASKS_ORG_DEVICE_TEST.md"]
  Root --> DT2["TRAVEL_TIME_DEVICE_TEST.md"]
```

Os 4 documentos específicos do fork residem na raiz para alimentar a execução inicial do `/opsh:ai-context --adopt`.

## TO BE — Estado e Componentes Propostos

```mermaid
flowchart LR
  Docs["docs/"] --> ForkDir["docs/fork/ (novo)"]
  Docs --> TestDir["docs/testing/ (novo)"]
  ForkDir --> N_FI["FORK_INTEGRATION.md (movido)"]
  ForkDir --> N_FF["FORK_FEATURES.md (movido)"]
  TestDir --> N_DT1["TASKS_ORG_DEVICE_TEST.md (movido)"]
  TestDir --> N_DT2["TRAVEL_TIME_DEVICE_TEST.md (movido)"]
  Agents["AGENTS.md + docs/agents/*.md (alterado)"] --> ForkDir
  Agents --> TestDir
```

Após o `/opsh:ai-context --adopt`, os documentos são movidos para `docs/fork/` (RF-01) e `docs/testing/` (RF-02), mantendo a raiz limpa.
