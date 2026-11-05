# Tasks

## Phase 1: Implementação da Automação de Release e Makefile

Antes de implementar, leia:
1. `AGENTS.md` e `docs/agents/architecture.md` — regras de arquitetura e comandos de build/teste
2. `proposal.md`, `specs/` e `design.md` deste diretório de change — requisitos RIGID, cenários e decisões técnicas

- [x] T01 — Testes unitários para resolução de versão e extração de changelog
      Arquivos: `app/src/fork/tools/test_release.py`
      Mudança: Criar suíte de testes unitários com módulo padrão `unittest` cobrindo as funções puras de incremento de `VERSION_CODE`, resolução de `VERSION_NAME` com base na data e sufixos de colisão `.N`, parsing de `proposal.md` para formato de changelog e acionamento de fallback manual.
      Cobre: RF-01, RF-02, AC-01, AC-02, AC-07
      Acceptance criteria: Testes cobrem incremento de versão, detecção de colisão de tags na mesma data e extração de tópicos do OpenSpec, executáveis sem erros via `python3 -m unittest app/src/fork/tools/test_release.py`.
      Testes: `python3 -m unittest app/src/fork/tools/test_release.py` — casos de teste de versionamento, extração de changelog e fallback.

- [x] T02 — Implementação do assistente interativo app/src/fork/tools/release.py
      Arquivos: `app/src/fork/tools/release.py`
      Mudança: Implementar o script executável em Python 3 contendo: checagens de pré-voo (`git status --porcelain` e branch `main`), leitura e atualização de `version.properties`, extração do changelog a partir de `openspec/changes/archive/` com confirmação interativa (aceitar minuta, abrir no `$EDITOR` ou redigir manualmente), prepend no `CHANGELOG.md`, criação de commit `chore(release)` e tag `v*`, confirmação interativa antes de executar `git push fork main` e `git push fork v*`, e suporte completo à flag `--dry-run`.
      Cobre: RF-01, RF-02, RF-03, AC-01, AC-02, AC-03, AC-04, AC-05
      Acceptance criteria: O script executa de ponta a ponta em modo `--dry-run` exibindo o plano completo sem alterar arquivos nem executar mutações no git; respeita confirmação explícita antes de qualquer push.
      Testes: `python3 app/src/fork/tools/release.py --dry-run` e suíte `python3 -m unittest app/src/fork/tools/test_release.py`.

- [x] T03 — Criação do Makefile na raiz e atualização de AGENTS.md
      Arquivos: `Makefile`, `AGENTS.md`
      Mudança: Criar `Makefile` na raiz contendo os alvos `release`, `dry-run`, `test` e `install`, e registrar `make release` na tabela de comandos comuns de `AGENTS.md`.
      Cobre: RF-04, AC-06
      Acceptance criteria: O comando `make dry-run` invoca com sucesso o assistente em modo de simulação; `Makefile` e `AGENTS.md` versionados no repositório.
      Testes: Execução de `make dry-run` no terminal verificando código de saída 0.
