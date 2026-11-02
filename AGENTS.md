# AGENTS.md

Fork pessoal do KashCal (app Android de calendário/CalDAV) com integração ao Tasks.org.
Remotes: `origin` = upstream KashCal/KashCal, `fork` = eusoumadson/KashCal. Branch de trabalho: `main`.

## Stack
Kotlin, Jetpack Compose, Room, Hilt, WorkManager, DataStore. JDK 21, compileSdk 37, minSdk 31.
Módulos Gradle: `:app`, `:icaldav-core`, `:vcard-core`.

## Comandos
```bash
./gradlew assembleDebug                                   # build
./gradlew testDebugUnitTest                               # testes unitários (~1 min, sem integração)
./gradlew testDebugUnitTest --tests "*NomeDoTest*"        # uma classe de teste
./gradlew lint                                            # lint
./gradlew testDebugUnitTest -Pintegration                 # integração com servidores CalDAV reais (~13 min). Só rode se pedido
```
Gate mínimo antes de dar uma tarefa por concluída: `./gradlew testDebugUnitTest` verde (e `./gradlew lint` se tocou em UI/recursos).

## Arquitetura (regras que não se quebram)
- UI (Compose + ViewModels) → Domain (`EventCoordinator`, `EventReader`) → Data (Room, CalDAV, DataStore).
- ViewModels nunca acessam DAOs diretamente.
- Toda mutação de sync passa pela fila `PendingOperation`. Nunca dispare e esqueça.
- Eventos de exceção de recorrência compartilham o UID do mestre e se distinguem por RECURRENCE-ID.
- Tarefas do Tasks.org nunca são persistidas. São lidas do ContentProvider e exibidas como `DisplayEvent.Task`.
- Nunca registre em log credenciais, tokens de sync ou senhas.

## Fork
- Toda a integração com o Tasks.org fica atrás do toggle "Integração com Tasks.org ativada". Desligado, o app se comporta como o upstream.
- Mantenha os pontos de enxerto no core mínimos para facilitar rebase. O mapa está em `FORK_INTEGRATION.md`. Atualize-o ao mexer no core.
- Contexto das features do fork: `FORK_FEATURES.md`, `TASKS_ORG_FORK_REPORT.md`.
- Strings: o upstream pede só inglês em `values/strings.xml`. Neste fork as traduções `values-pt` e `values-pt-rBR` são editadas junto (ver `app/src/fork/tools/gen_strings_pt.py`). Não edite os outros locales à mão.

## Estilo
- Commits em Conventional Commits: `feat(escopo): resumo imperativo` (≤72 caracteres). Tipos: feat, fix, docs, refactor, test, build, perf, chore.
- Testes primeiro para comportamento novo. Se um teste falhar, corrija o código, não o teste, a menos que o teste esteja errado.
- Comentários e KDoc só para o que o código não diz: invariantes, consequências, regras de RFC. Descrevem a regra atual, não o histórico. Se mudar o código, atualize ou apague o comentário.
- Sem texto de usuário hardcoded. Use recursos de string.
- Prefira banners inline a diálogos bloqueantes. Confirmação só para ações destrutivas.

## Specs e planejamento
- Specs antigas: `.kiro/specs/travel-time-alerts/` e `.docs/features/` (com `PHASES.md`).
- Mudanças novas: `openspec/changes/<nome>/` (ver OpenSpec abaixo).

## OpenSpec
A CLI `openspec` exige Node ≥ 20. O default da máquina é Node 14, então ative antes de usar:
`export PATH="$HOME/.nvm/versions/node/v20.20.0/bin:$PATH"` (ou `nvm use 20`).

## Não toque
- `local.properties`, chaves de assinatura e keystores. Não leia, não imprima, não commite.
- `build/`, `.gradle/`, `.kotlin/`, `.idea/`.
- Nunca faça `git push`, force-push, nem rebase/reset sem pedido explícito.
- Não commite alterações que não sejam da tarefa. A árvore costuma ter trabalho pendente do usuário.
