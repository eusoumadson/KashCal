# Design & Technical Plan

## Context & Architecture Grounding

Este design estabelece a arquitetura do assistente local de lançamentos para o repositório `kashcal_tasks`.
Conforme estabelecido em `AGENTS.md` (§3):
- Nunca executar `git push` sem autorização explícita do desenvolvedor.
- Manter o commit e a tag estritamente alinhados com a branch `main` e o remoto `fork`.
- Preservar o isolamento do código de produção: ferramentas de fork residem em `app/src/fork/tools/`.

## Goals / Non-Goals

**Goals:**
- Prover um script Python interativo e testável (`app/src/fork/tools/release.py`) que automatize: checagem de pré-voo, incremento de versão em `version.properties`, rascunho de notas via OpenSpec ou entrada manual com suporte a `$EDITOR`, criação de commit e tag git, e push seguro mediante confirmação.
- Prover um `Makefile` na raiz com atalhos de conveniência (`make release`, `make test`, `make install`, `make dry-run`).
- Isolar a lógica de parsing e resolução de versão em funções puras testadas com `unittest`.

**Non-Goals:**
- Modificar o fluxo de compilação ou secrets no GitHub Actions (`.github/workflows/release.yml`).
- Fazer push silencioso ou forçado.

## AS IS — Estado e Componentes Atuais

```mermaid
flowchart LR
  Dev[\"Desenvolvedor\"] -->|edita manualmente| VP[\"version.properties (verificado)\"]
  Dev -->|redige manualmente| CL[\"CHANGELOG.md (verificado)\"]
  Dev -->|executa comandos git manuais| GitLocal[\"Git CLI (commit / tag)\"]
  GitLocal -->|push manual para| Remote[\"GitHub (fork/main)\"]
```

_AS IS: Todo o ciclo de preparação, bump de versão, redação de changelog, commit, tag e push é executado manualmente pelo desenvolvedor com comandos de terminal dispersos._

## TO BE — Estado e Componentes Propostos

```mermaid
flowchart LR
  Dev[\"Desenvolvedor\"] -->|make release| Makefile[\"Makefile (novo)\"]
  Makefile -->|invoca| ReleaseScript[\"app/src/fork/tools/release.py (novo)\"]
  ReleaseScript -->|lê/escreve| VP[\"version.properties (verificado)\"]
  ReleaseScript -->|insere notas| CL[\"CHANGELOG.md (verificado)\"]
  ReleaseScript -->|extrai minuta| OpenSpecArchive[\"openspec/changes/archive/ (verificado)\"]
  ReleaseScript -->|commit e tag| GitLocal[\"Git CLI\"]
  ReleaseScript -->|pede confirmação antes de| Remote[\"GitHub (fork/main)\"]
```

_TO BE: O comando `make release` orquestra o assistente interativo em Python, que lê o OpenSpec recente para sugerir o changelog, incrementa `version.properties`, comita, tagueia e publica após aprovação do desenvolvedor (RF-01 a RF-04)._

## RIGID vs FLEXIBLE Boundaries

- **RIGID (Non-Negotiable)**:
  - Nunca efetuar push para o repositório remoto sem confirmação explícita (`[s/N]`) do desenvolvedor no terminal.
  - Se a flag `--dry-run` estiver presente, nenhuma escrita em disco (`version.properties`, `CHANGELOG.md`) ou mutação no git (`commit`, `tag`, `push`) pode ser realizada.
  - O commit deve obrigatoriamente seguir a convenção `chore(release): bump version to <VERSION_NAME> (<VERSION_CODE>)`.
  - A tag deve seguir o formato `v<VERSION_NAME>`.

## Decisions

1. **Python 3 para o script de automação (`release.py`)**:
   - *Decisão*: Implementar em Python 3 padrão (sem dependências externas) na pasta `app/src/fork/tools/`.
   - *Motivo*: Segue o padrão já estabelecido por `gen_strings_pt.py` e `gen_icons.py`. Evita inconsistências de `sed`/`awk` entre macOS e Linux.
2. **Estratégia de resolução de versão**:
   - *Decisão*: Calcular `YYYY.MM.DD` a partir do relógio do sistema. Se a tag `vYYYY.MM.DD` já existir no repositório local, iterar `YYYY.MM.DD.1`, `.2`, etc.
   - *Motivo*: Suporta múltiplos deploys ou hotfixes no mesmo dia de forma determinística sem intervenção manual obrigatória.
3. **Draft de Changelog via OpenSpec com Fallback**:
   - *Decisão*: Buscar o diretório mais recente em `openspec/changes/archive/` (ordenado por nome decrescente ou data de modificação). Extrair as seções de `proposal.md` para formatar um rascunho inicial.
   - *Motivo*: Garante coerência entre as especificações e as notas públicas de release, economizando tempo do desenvolvedor e permitindo ajuste manual.

## Risks / Trade-offs

| Risk | Blast Radius | Mitigation | Rollback |
|---|---|---|---|
| Tag criada incorretamente localmente | Tag órfã no repositório local antes do push | O script verifica pré-condições antes de criar a tag; abortar remove a tag local se o push for cancelado | `git tag -d <tag>` |
| Árvore de trabalho com arquivos modificados não salvos | Commit de release incluiria arquivos indevidos | Checagem estrita de pré-voo (`git status --porcelain`) aborta imediatamente se a árvore não estiver 100% limpa | N/A (aborto preventivo) |

## Open Questions
Nenhuma ambiguidade pendente. Requisitos e estratégias alinhados com o desenvolvedor.
