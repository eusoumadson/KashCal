# Proposal: automate-release-workflow

## Metadata
- **Tier**: light
- **Architecture References**: AGENTS.md, docs/agents/architecture.md, docs/agents/domain_rules.md, docs/fork/FORK_INTEGRATION.md
- **Capability Policy**: ignore_for_this_run

## Why

O processo de publicação de uma nova versão do fork KashCal Tasks envolve múltiplos passos manuais repetitivos e suscetíveis a erro humano: incremento de `VERSION_CODE` e cálculo de `VERSION_NAME` em `version.properties`, inserção e formatação de notas no `CHANGELOG.md`, criação de commit convencional `chore(release)`, criação de tag git anotada `v*` e envio para a branch e tag remotas. A falta de um assistente interativo aumenta a fricção de lançamento e o risco de tags duplicadas ou changelogs despadronizados.

## Scope
- **In**:
  - Script interativo em Python (`app/src/fork/tools/release.py`) realizando checagens de pré-voo, bump automático de versão com detecção de tags existentes no mesmo dia, montagem de notas de changelog com extração automática via OpenSpec e fallback para edição manual, atualização de arquivos, commit, tag e push com confirmação explícita.
  - Suporte a flag `--dry-run` para simulação sem efeitos colaterais.
  - `Makefile` na raiz do projeto contendo os alvos `release`, `dry-run`, `test` e `install`.
  - Testes unitários para as funções puras de parsing de versão, resolução de tag e formatação de changelog.
  - Atualização da documentação do desenvolvedor em `AGENTS.md`.
- **Out**:
  - Alterações no workflow de CI/CD remoto do GitHub Actions (`.github/workflows/release.yml`), que já funciona e deve continuar sendo o executor do build de produção assinado.
  - Modificações no código de produção do aplicativo Android (:app).

## What Changes

- `app/src/fork/tools/release.py`: Novo script de automação de lançamento de versão.
- `app/src/fork/tools/test_release.py`: Testes unitários das funções lógicas do script de release.
- `Makefile`: Novo arquivo na raiz provendo comandos rápidos de desenvolvimento e lançamento (`make release`, `make test`, `make install`, `make dry-run`).
- `AGENTS.md`: Registro do comando `make release` na tabela de comandos comuns.

## Capabilities

### New Capabilities
- `release-automation`: Assistente automatizado de ciclo de release local para cálculo de versão, redação assistida de changelog a partir do OpenSpec e publicação segura de tags git.

### Modified Capabilities
None.

## Confirmed Acceptance Criteria (Source of Truth)
- **AC-01**: O script `app/src/fork/tools/release.py` deve realizar checagens de pré-voo (verificar se `git status --porcelain` está limpo e se a branch atual é `main`), alertando e impedindo a execução se houver pendências não comitadas.
- **AC-02**: O script deve ler `version.properties`, incrementar `VERSION_CODE` em 1 e calcular a data atual (`YYYY.MM.DD`). Se a tag `vYYYY.MM.DD` já existir no repositório local, deve calcular o próximo sufixo incremental (`YYYY.MM.DD.1`, `.2`, etc.), permitindo que o desenvolvedor confirme com `[Enter]` ou informe uma versão customizada.
- **AC-03**: O script deve buscar a última mudança no histórico do OpenSpec (`openspec/changes/archive/` ou `openspec/changes/`), extrair os destaques do `proposal.md` e propor uma minuta formatada para o `CHANGELOG.md`. O desenvolvedor deve ter a opção de aceitar a minuta, editar no `$EDITOR` ou redigir manualmente. Se nenhuma mudança OpenSpec for encontrada, deve abrir diretamente o fallback de redação manual.
- **AC-04**: O script deve atualizar `version.properties`, inserir a seção `## [VERSION_NAME]` no topo do `CHANGELOG.md`, criar o commit git `chore(release): bump version to <VERSION_NAME> (<VERSION_CODE>)` e criar a tag git anotada `v<VERSION_NAME>`.
- **AC-05**: O script deve exibir os comandos de push e exigir confirmação explícita do usuário antes de executar `git push fork main` e `git push fork v<VERSION_NAME>`, suportando a flag `--dry-run` para simulação sem alterar arquivos ou executar comandos git mutáveis.
- **AC-06**: Um `Makefile` na raiz deve prover os alvos `release`, `dry-run`, `test` e `install`.
- **AC-07**: A suíte de testes unitários do assistente de release (`python3 -m unittest app/src/fork/tools/test_release.py`) deve passar com 100% de sucesso.

## Impact
- Adiciona ferramentas de desenvolvimento sob `app/src/fork/tools/` e na raiz (`Makefile`).
- Sem impacto em código Kotlin/Java do app ou em dependências do Gradle.
