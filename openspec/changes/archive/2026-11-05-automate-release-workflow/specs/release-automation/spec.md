# Spec Delta: release-automation

## Purpose
Prover uma ferramenta de automação e orquestração do ciclo de release local para o fork KashCal Tasks, garantindo versionamento semântico baseado em data, geração assistida de changelog e publicação segura de tags git.

## ADDED Requirements

### Requirement: RF-01 - Checagens de Pré-voo e Resolução de Versão
WHEN o comando de release for disparado via `release.py`, THE SYSTEM SHALL verificar se o working tree git está limpo (`git status --porcelain` vazio) e se a branch ativa é `main`. IF existirem pendências não comitadas ou a branch for divergente, THE SYSTEM SHALL abortar a execução.
THE SYSTEM SHALL ler `version.properties`, incrementar `VERSION_CODE` em 1 e calcular a data atual (`YYYY.MM.DD`), adicionando sufixo incremental `.N` caso a tag git correspondente já exista.

#### Scenario: Lançamento de versão regular em dia sem releases anteriores
- **WHEN** o desenvolvedor rodar `make release` em uma árvore limpa e não existirem tags para a data de hoje
- **THEN** o script propõe `VERSION_NAME` como `YYYY.MM.DD` e incrementa `VERSION_CODE` em 1.

#### Scenario: Detecção de colisão de tag na mesma data
- **WHEN** a tag `v2026.11.05` já existir no repositório local e o release for disparado na mesma data
- **THEN** o script detecta a colisão e propõe `2026.11.05.1` como próxima versão.

#### Scenario: Execução em árvore de trabalho suja
- **WHEN** existirem arquivos modificados ou untracked não ignorados no git
- **THEN** o script interrompe a execução e alerta o desenvolvedor para comitar ou arquivar antes de prosseguir.

### Requirement: RF-02 - Redação do Changelog via OpenSpec e Fallback Manual
WHEN as notas do lançamento forem preparadas, THE SYSTEM SHALL inspecionar o histórico de propostas em `openspec/changes/archive/` e gerar uma minuta formatada a partir do `proposal.md` mais recente.
THE SYSTEM SHALL solicitar confirmação do usuário para aceitar a minuta, abrir no `$EDITOR` ou redigir manualmente. IF nenhuma proposta for encontrada, THE SYSTEM SHALL acionar diretamente a entrada manual interativa.

#### Scenario: Extração bem-sucedida a partir de mudança OpenSpec
- **WHEN** existir uma change recentemente arquivada em `openspec/changes/archive/`
- **THEN** o script lê os tópicos de `proposal.md` e gera um rascunho de notas formatado pronto para aprovação.

#### Scenario: Rejeição da minuta ou ausência de change OpenSpec
- **WHEN** o usuário rejeitar a minuta sugerida ou não houver diretório de change arquivado
- **THEN** o script abre a entrada manual interativa permitindo digitação direta de tópicos de release.

### Requirement: RF-03 - Aplicação de Versão, Commit, Tag e Publicação Segura
WHEN a versão e as notas forem confirmadas, THE SYSTEM SHALL persistir as alterações em `version.properties`, inserir a seção `## [VERSION_NAME]` no topo do `CHANGELOG.md`, criar o commit `chore(release)` e a tag git `v<VERSION_NAME>`.
THE SYSTEM SHALL solicitar confirmação explícita antes de executar `git push fork main` e `git push fork v<VERSION_NAME>`, e SHALL suportar simulação via `--dry-run`.

#### Scenario: Publicação interativa aprovada pelo usuário
- **WHEN** o usuário responder afirmativamente à confirmação de push
- **THEN** o script executa o push da branch `main` e da tag criada para o remote `fork`.

#### Scenario: Execução em modo dry-run
- **WHEN** o script for invocado com `--dry-run`
- **THEN** o terminal exibe exatamente as notas calculadas, os arquivos que seriam alterados e a tag que seria gerada, sem tocar no git nem em `version.properties`.

### Requirement: RF-04 - Makefile de Orquestração Local
THE SYSTEM SHALL prover um `Makefile` na raiz do repositório contendo os alvos `release` (`python3 app/src/fork/tools/release.py`), `dry-run` (`--dry-run`), `test` (`./gradlew testDebugUnitTest --no-daemon`) e `install` (`./gradlew installDebug`).

#### Scenario: Execução de comando make release
- **WHEN** o desenvolvedor executar `make release` no terminal a partir da raiz
- **THEN** o script `release.py` é invocado diretamente no ambiente Python local.
