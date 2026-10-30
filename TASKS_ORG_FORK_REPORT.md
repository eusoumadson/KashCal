# Relatório — Fork próprio do KashCal com integração Tasks.org (read/write)

> Documento de planejamento. Objetivo: manter um **fork pessoal** do KashCal que integra
> o app **Tasks.org** não só para exibir e completar tarefas (o que o PR #412 já faz), mas
> também para **criar e editar tarefas direto no KashCal, refletindo no Tasks.org**.
>
> Fonte da verdade da API: `CONTENT_PROVIDER.md` do repositório `tasks/tasks`
> (API pública `org.tasks.api`, versão `v0`, introduzida na versão 15.12).
> Conteúdo reescrito/parafraseado para conformidade de licenciamento.

---

## 1. Decisão estratégica: por que um fork

O mantenedor deixou tarefas de fora do KashCal **de propósito**. As duas opções que ele deu:

1. **Fork pessoal** — a feature vive só no seu build.
2. **Dono do recurso no upstream** — você assume manutenção aberta + refatoração para um
   "caminho separado".

Este relatório cobre a **Opção 1 (fork)**, que é a de menor atrito político e de maior
liberdade técnica: você não precisa negociar arquitetura com ninguém, e pode ir além do
escopo read-only que o upstream recusou (criar/editar tarefas).

### Custo real do fork (ser honesto)

- **Rebase perpétuo.** O KashCal upstream continua evoluindo. Seu fork precisa fazer
  merge/rebase periódico. Quanto mais seu código tocar o *core* (`DisplayEventRepository`,
  `DisplayEvent`), mais conflitos de merge você terá. **Minimizar o acoplamento ao core não é
  só elegância — é o que reduz seu custo de rebase.**
- **Build e release próprios.** `applicationId` diferente, assinatura própria, canal de
  distribuição próprio (F-Droid pessoal, APK direto, ou Play interno).
- **Sem suporte upstream.** Bugs da sua feature são 100% seus.

---

## 2. Onde o PR #412 está hoje (linha de base)

O que **já existe** e funciona (read + completion):

| Camada | Arquivo | Papel |
| --- | --- | --- |
| Contrato | `data/tasks_org/TasksOrgContract.kt` | Constantes do provider `org.tasks.api/v0` |
| Modelos | `data/tasks_org/TasksOrgModels.kt` | `TasksOrgTask`, `TasksOrgList`, `TasksOrgSchedule` |
| Repositório | `data/tasks_org/TasksOrgRepository.kt` (+ `AndroidTasksOrgRepository`) | Read de tarefas/listas, `setCompleted`, `getSchedule`, `restoreSchedule` |
| Observer | `data/tasks_org/TasksOrgManager.kt` | ContentObserver + `changeSignal`, gating por permissão |
| Coordenador | `domain/tasks/TasksOrgCoordinator.kt` | Liga/desliga fonte, mostra/esconde listas, completion + undo |
| Modelo unificado | `domain/model/DisplayEvent.kt` → `DisplayEvent.Task` | Apresenta a tarefa como "evento" para a UI |
| Agregação | `domain/reader/DisplayEventRepository.kt` | `queryTaskEvents(...)` concatenado nos fluxos |
| UI | `TaskCard.kt`, `TaskQuickViewSheet.kt`, `TasksOrgScreen.kt` | Cartão, sheet de detalhe, tela de settings |
| DI | `di/RepositoryModule.kt` | `@Binds` do repositório |

**Capacidades atuais:** exibir tarefas (mês/agenda/3-dias/busca), completar/reabrir com undo,
tratar recorrência no completar (avança a ocorrência), all-day, esconder listas, desligar a
fonte. Tudo **read-only exceto completion**. `DisplayEvent.Task.isReadOnly = true`.

**O que falta para o objetivo do fork:** criar tarefa, editar tarefa (título, notas, datas,
lista, prioridade, recorrência), mover entre listas, e opcionalmente excluir, tags, lembretes.

---

## 3. A API pública do Tasks.org permite tudo isso?

**Sim.** A API `v0` é read **e** write. Tabela de endpoints (do `CONTENT_PROVIDER.md`):

| Coleção | Query | Insert | Update | Delete |
| --- | :-: | :-: | :-: | :-: |
| `/v0/tasks` | ✅ | ✅ | — | — |
| `/v0/tasks/{id}` | ✅ | — | ✅ | ✅ |
| `/v0/reminders` | ✅ | ✅ | — | — |
| `/v0/reminders/{id}` | ✅ | — | ✅ | ✅ |
| `/v0/task_tags` | ✅ | ✅ | — | ✅ |
| `/v0/lists`, `/v0/tags`, `/v0/places` | ✅ | ✅ | (via `/{id}`) | (via `/{id}`) |
| `/v0/accounts` | ✅ | — | — | — |

- **Criar tarefa:** `insert content://org.tasks.api/v0/tasks` — só `title` é obrigatório.
  Retorna o URI da nova tarefa. Requer `WRITE_TASKS`.
- **Editar tarefa:** `update content://org.tasks.api/v0/tasks/{id}` — é um **patch**: só as
  chaves enviadas mudam; chave com valor vazio/`null` limpa o campo. Retorna nº de linhas (0 =
  id não existe mais).
- **Excluir tarefa:** `delete .../tasks/{id}` — apaga a tarefa **e todas as subtarefas**, sem
  undo.
- **Batch atômico:** `applyBatch("org.tasks.api", ...)` com `withValueBackReference` para criar
  tarefa + tags/lembretes numa transação.

> ⚠️ **`v0` é instável.** Colunas e parâmetros podem mudar entre releases do Tasks.org.
> Cheque o changelog ao atualizar. Para um fork, **fixe uma versão mínima do Tasks.org** e
> trate `IllegalArgumentException` como "coluna/endpoint não suportado nesta versão".

### Colunas graváveis em `/v0/tasks` (`W` = gravável)

| Coluna | Tipo | W | Observação |
| --- | --- | :-: | --- |
| `title` | string | ✅ | Não pode ficar vazio (`""`/`null` → `IllegalArgumentException`) |
| `notes` | string | ✅ | Markdown, como o usuário digitou |
| `priority` | string | ✅ | `high`, `medium`, `low`, `none` |
| `due_date` | long | ✅ | epoch ms, ou string de data local `2026-09-12` |
| `due_all_day` | 0/1 | ✅ | 1 = sem hora do dia |
| `start_date` | long | ✅ | tarefa escondida até; ms ou data local |
| `start_all_day` | 0/1 | ✅ | |
| `completed_at` | long | ✅ | timestamp completa; `0` reabre (ver recorrência) |
| `recurrence` | string | ✅ | RFC 5545 `RRULE`; `""` = não repete |
| `repeat_from` | string | ✅ | `due_date` ou `completion_date` |
| `parent_id` | long | ✅ | `0` = tarefa de topo |
| `list_id` | long | ✅ | move a tarefa; `0` no update lança exceção |
| `place_id` | long | ✅ | `0` remove o lugar e seus lembretes |
| `_id`, `created_at`, `modified_at`, `tag_ids`, `child_count`, `uncompleted_child_count`, `is_read_only` | | — | read-only |

---

## 4. Edge cases (o coração do relatório)

### 4.1 Datas e fuso horário

- **Todos os timestamps são epoch ms; `0` = unset.** Nunca `null` na leitura: campo não setado
  volta como o "vazio" do tipo (`""`, `0`).
- **All-day é sinalizado por coluna companheira:** `due_date` + `due_all_day`,
  `start_date` + `start_all_day`.
- **Datas all-day são no fuso local do dispositivo; só o dia do calendário importa.** A regra
  oficial: **compute a partir da meia-noite local, não de UTC.**
  - ⚠️ **Divergência com o PR atual:** `TasksOrgModels.kt` / `taskAt(...)` hoje coloca a tarefa
    all-day em **UTC midnight** (para casar com a convenção do `Event` do Room). Isso funciona
    para *exibir* junto de eventos, mas na hora de **escrever** de volta você precisa converter
    para a convenção do Tasks.org (meia-noite **local**), senão uma tarefa all-day pode pular
    de dia perto da meia-noite dependendo do fuso. **Trate leitura e escrita com funções de
    conversão separadas e explícitas.**
- **Update parcial de all-day é traiçoeiro:** no `update`, se você mandar `due_date` **sem**
  `due_all_day`, o Tasks.org **mantém a flag que a linha já tinha**. Ou seja, escrever só a data
  numa tarefa all-day a move para outra data all-day (bom); mas se você quer *mudar* de all-day
  para com-hora (ou vice-versa), **tem que mandar a flag junto** — inclusive para voltar.
- **Strings de data local são aceitas na escrita:** `"2026-09-12"` (data) e
  `"2026-09-12T19:00:00"` (data+hora), ambas no fuso do dispositivo. Uma string só-data escrita
  em `due_date`/`start_date` **liga a flag all-day sozinha**, a menos que você mande a flag.
  Isso pode simplificar sua camada de escrita (evita aritmética de epoch manual).

### 4.2 Recorrência (`recurrence` = RRULE RFC 5545)

- **Uma série = uma linha.** Não há "ocorrências" expandidas no provider; há uma tarefa com um
  `RRULE` e uma data atual. O PR já exibe só a ocorrência corrente.
- **Completar uma recorrente NÃO a deixa completa:** avança a linha para a próxima ocorrência,
  mantendo o `_id`. Após completar:
  - `completed_at` lê `0` (não está completa);
  - `due_date`/`start_date` avançam;
  - `recurrence` é **reescrita** se a regra tem `COUNT` (decrementa a contagem);
  - alarmes são reagendados.
- **`repeat_from`** decide de onde conta a próxima: `due_date` (padrão) ou `completion_date`.
- **Fim da série:** última ocorrência de `COUNT`, ou a primeira depois de `UNTIL` — aí a
  conclusão "pega" de verdade.
- **Contas que repetem no servidor** (`accounts.repeats_on_server == 1`): completar **deixa**
  `completed_at` setado e as datas inalteradas; a próxima ocorrência chega no próximo sync.
  ⚠️ Seu undo de completion precisa considerar esses dois comportamentos (o PR já tem
  `RestoreSchedule` vs `SetCompletedAt` justamente por isso — mantenha).
- **Parar a série cedo:** limpe `recurrence` **antes** de completar.
- **Escrever `recurrence` numa tarefa sem due date** faz o Tasks.org dar a ela a data de hoje.
- **Re-âncora de regra mensal:** mudar `due_date` re-ancora uma regra `BYDAY` mensal ao novo
  dia/semana (`2TU` → `3WE`). Edição de data em tarefa recorrente tem efeitos colaterais — a UI
  deve avisar o usuário ou refletir o resultado relido.

### 4.3 Efeitos colaterais de updates "não triviais"

Alguns campos não são escrita pura de coluna:

- **`completed_at`:** completa/reabre em cascata para subtarefas, cancela notificações pendentes,
  avança recorrentes.
- **`due_date`:** um `start_date` relativo (no due, um dia antes, uma semana antes) **se move
  junto**; qualquer outro start fica parado. Mande `start_date` junto para controlar.
- **`list_id`:** move a tarefa **e as subtarefas**. Subtarefa movida sozinha vira topo no
  destino — mande `parent_id` junto para manter a hierarquia. `0` lança exceção.
- **`place_id = 0`:** remove o lugar e os lembretes dele.
- **`parent_id`:** re-parenteia; `0` move para o topo.

### 4.4 Listas read-only

- `is_read_only == 1` na tarefa **ou** na lista → escrever lança `UnsupportedOperationException`.
- A UI do fork deve **desabilitar** edição/criação em listas read-only e tratar a exceção como
  "recusado" (o padrão `guarded {}` do PR já engole exceções e retorna fallback — estenda-o
  para distinguir "recusado por read-only" e mostrar mensagem).

### 4.5 Campos que nunca são null / limpeza de campos

- Nenhuma coluna é `null`. Unset = vazio do tipo (`""`, `0`).
- Na escrita, `null` **e** o valor vazio limpam o campo.
- **`title` não pode ser limpo** (`""`/`null` lança exceção) — valide no formulário.

### 4.6 Erros a tratar explicitamente

| Exceção | Significado | Ação no fork |
| --- | --- | --- |
| `SecurityException` | Permissão faltando/revogada | Desligar fonte, pedir permissão |
| `IllegalArgumentException` | URI/coluna/parâmetro/valor inválido, ou versão sem suporte | Degradar gracioso; tratar como "não suportado" |
| `UnsupportedOperationException` | Alvo read-only | Mensagem "lista somente leitura" |
| `IllegalStateException` | Operação remota falhou (mensagem traz resposta do servidor) | Mostrar/retry |
| `OperationApplicationException` | Batch falhou; nada aplicado | Retry do batch inteiro |
| `TransactionTooLargeException` | Batch grande demais (pode vir embrulhado em `RuntimeException`) | Dividir o batch |
| retorno `null` da query | Provider não alcançado (Tasks não instalado / falta `<queries>`) | Tratar como "não instalado" |

### 4.7 Threading e observabilidade

- **Toda chamada é um round-trip de binder — nunca na main thread.** O PR já usa
  `withContext(Dispatchers.IO)`; mantenha em toda escrita nova.
- **`selfChange` é sempre `false`:** quem escreve acorda a si mesmo pelo observer. O PR já
  chama `notifyChanged()` após escrever para não esperar o observer debounced — mantenha esse
  padrão nas escritas novas (criar/editar), senão a tela demora ~1s para refletir.
- **Notificações só vão para URIs de coleção.** Registrar em `/v0/tasks/{id}` nunca dispara.
- Qualquer coisa que toca uma tarefa notifica `/v0/tasks`, `/v0/reminders` e `/v0/task_tags`.

### 4.8 Paginação e busca (já no PR, confirmar ainda válido)

- Coleções paginam; `limit` default 100, **sem máximo**. O PR usa `MAX_LIMIT = 1000`.
- `search` casa **só título e notas**, case-insensitive, literal (`%`/`_` não são curinga).
- Páginas por offset não são snapshot: linha que se move durante a leitura pode vir duas vezes
  → o PR já faz `distinctBy { it.id }`. Mantenha.

---

## 5. Plano de implementação do fork

### Fase 0 — Preparar o fork (infra)

1. ✅ `applicationId` distinto (`org.onekash.kashcal.tasks`) para coexistir com o KashCal oficial no
   mesmo device. Os tipos de conta e os aliases de ícone também tiveram de acompanhar o id; ver
   `FORK_INTEGRATION.md` §8.
2. Assinatura própria + canal de distribuição.
3. Estratégia de branch: manter `upstream` como remote, sua `main` do fork rebaseando sobre ele.
4. Manifesto: já tem `READ_TASKS`; **adicionar `WRITE_TASKS`** e o bloco `<queries>` com o
   provider `org.tasks.api` (o PR provavelmente já tem parte disso para read).

### Fase 1 — Camada de escrita no repositório (sem tocar UI)

Estender `TasksOrgRepository` / `AndroidTasksOrgRepository` com, no mínimo:

```
suspend fun createTask(draft: TasksOrgTaskDraft): Long?          // insert -> novo id
suspend fun updateTask(taskId: Long, patch: TasksOrgTaskPatch): Boolean  // update parcial
suspend fun deleteTask(taskId: Long): Boolean                    // delete (+ confirmação na UI)
```

- `TasksOrgTaskDraft` = campos de criação (title obrigatório, list_id opcional, datas, etc.).
- `TasksOrgTaskPatch` = mapa esparso de campos a mudar (patch semantics do provider).
- **Converter all-day para meia-noite LOCAL na escrita** (ver 4.1). Encapsular em funções
  `toProviderDueValues(...)` separadas da leitura.
- Enviar `due_all_day`/`start_all_day` **sempre que** mudar a natureza da data (ver 4.1).
- Reusar e estender `guarded {}` para classificar `UnsupportedOperationException` (read-only).
- Após cada escrita, `manager.notifyChanged()`.

### Fase 2 — Domínio

- Estender `TasksOrgCoordinator` com `createTask`/`updateTask`/`deleteTask`, incluindo undo
  onde fizer sentido (edição pode guardar o estado anterior via `getSchedule`/leitura da linha
  antes de escrever, espelhando o padrão de completion-undo que já existe).
- Regras de negócio de recorrência (avisar efeitos colaterais de mudar data em recorrente).

### Fase 3 — UI (a parte mais visível)

- **Decisão de arquitetura do fork:** manter `DisplayEvent.Task.isReadOnly = true` e abrir um
  **formulário de tarefa próprio** (não reusar o formulário de `Event`, cujos campos e persistência
  são do Room/CalDAV). Um formulário separado evita contaminar o core de eventos e **reduz
  conflito de rebase**.
- Telas:
  - Botão "nova tarefa" (talvez no mesmo FAB, com escolha evento/tarefa, ou long-press no dia).
  - Formulário de criação/edição: título, notas, lista (dropdown de `getLists()`, ocultando
    read-only), due date/hora + toggle all-day, start date, prioridade, recorrência (reusar o
    editor de RRULE existente se houver), repeat_from.
  - Ação de completar já existe; adicionar editar/excluir no `TaskQuickViewSheet`.
- Tratar listas read-only (desabilitar campos).
- Confirmar exclusão (apaga subtarefas, sem undo).

### Fase 4 — Opcional / avançado

**Status de implementação (fork):** tudo da Fase 4 foi implementado, exceto o que foi retirado
do escopo por decisão. Detalhes e limites em `FORK_FEATURES.md`.
- ✅ **Tags** (`/v0/tags` + `/v0/task_tags`): criar, associar, remover, e agora renomear, recolorir
  e excluir nas configurações.
- ✅ **Deep-link**: `ACTION_VIEW` na URI da tarefa; "Abrir no Tasks.org" abre a própria tarefa.
- ✅ **Lembretes** (`/v0/reminders`): horário, relativo ao vencimento/início, aleatório, soneca e
  de local (chegada/saída).
- ✅ **Places** (`/v0/places`): escolher, criar (com geocoder do aparelho ou coordenadas
  manuais), listar e excluir; uma tarefa tem no máximo um lugar.
- ✅ **Batch** (`applyBatch`): tarefa + tags + lembretes + lugar numa transação (`TasksOrgBatch.kt`).
- ✅ **Recorrência e início no editor**, aviso de efeito colateral e releitura da regra (§4.2, §4.3).
- ✅ Prioridade, tags e subtarefas (contagem) no cartão. Tarefas atrasadas ficam no dia programado.
- ✅ **Gestão** de listas, tags e lugares; saúde das contas; checagem de versão (mínima 15.12).
- ➖ **Subtarefas**: por decisão, o KashCal só mostra a contagem e **redireciona ao Tasks.org**.
- ➖ **Tarefas sem data**: por decisão, não são exibidas.
- ⬜ **Validação em dispositivo** com o Tasks.org real: roteiro em `TASKS_ORG_DEVICE_TEST.md`.

### Pendência transversal — fork "plug'n'play" para rebase com o upstream ✅

> **Requisito do dono do fork:** o fork precisa ser *plug'n'play* — fácil de atualizar quando
> o projeto original (upstream KashCal) evoluir. Rebase/merge deve ser o menos doloroso
> possível.

**Status: concluído.** Ver `FORK_INTEGRATION.md` para o mapa completo do enxerto.

- ✅ Feature concentrada em `data/tasks_org/`, `domain/tasks/` e telas próprias em `ui/`.
- ✅ Toda a API `v0` atrás do `TasksOrgV0Mapper` e da interface `TasksOrgRepository`.
- ✅ **Patch no core minimizado e marcado.** Todo toque do fork nos 5 arquivos de core
  (`DisplayEventRepository.kt`, `DisplayEvent.kt`, `HomeViewModel.kt`, `HomeScreen.kt`,
  `MainActivity.kt`) — mais `RepositoryModule.kt` e o manifesto — carrega o comentário sentinela
  `// FORK:tasks-org` (ou `<!-- FORK:tasks-org -->`). Localizável mecanicamente com
  `grep -rn "FORK:tasks-org" app/src app/src/main/AndroidManifest.xml`.
- ✅ **Toques são aditivos.** Só 3 linhas em `DisplayEventRepository.kt` substituem lógica
  original, e cada uma traz `(original: ...)` no comentário. As demais são adições puras, então
  o conflito de rebase se resolve mantendo os dois lados.
- ✅ **Seam inevitável documentado.** `DisplayEvent.Task` é membro de *sealed interface*, o que
  força branches `is DisplayEvent.Task ->` em todo `when` exaustivo. Não é extraível (exigência
  do compilador); é o seam de menor risco (o compilador aponta qualquer um faltando) e está
  catalogado no `FORK_INTEGRATION.md` §3.0.
- ✅ **"Enxerto" documentado** em `FORK_INTEGRATION.md`: cada ponto de conexão com o core, o
  diff mínimo de reaplicação, o passo-a-passo de rebase e a verificação (64 testes).

> Decisão de projeto: **não** foi criado um `TaskFabHost`/`TaskEditorHost` nem um delegate de
> ViewModel. A extração traria churn e risco (quebrar os 64 testes) por ganho marginal: os
> toques já são aditivos, gateados e marcados. O caminho de reaplicação (grep + manter os dois
> lados) é mais barato que manter uma camada de indireção extra. Caso o atrito de rebase cresça,
> o `FORK_INTEGRATION.md` §3.2 aponta onde uma extração futura renderia mais.

---

## 6. Como manter o acoplamento baixo (reduzir dor de rebase)

Mesmo num fork, vale isolar. Princípios:

1. **Nada de novo campo no core de eventos.** Formulário e modelo de tarefa próprios; não
   estender o `Event` do Room.
2. **Manter `DisplayEvent.Task` como adaptador de leitura apenas.** A escrita passa pelo
   `TasksOrgCoordinator`, não por caminhos de escrita de evento.
3. **Concentrar toda a feature em `data/tasks_org/`, `domain/tasks/` e um punhado de telas em
   `ui/`.** Quanto menos linhas você muda em `DisplayEventRepository.kt` e `DisplayEvent.kt`
   (arquivos muito editados upstream), menos conflito de merge.
4. **Toda interação com o provider atrás da interface `TasksOrgRepository`**, para que uma
   mudança da API `v0` fique contida num único arquivo.

---

## 7. Riscos e mitigações

| Risco | Impacto | Mitigação |
| --- | --- | --- |
| API `v0` instável entre versões do Tasks.org | Escritas quebram | Fixar versão mínima; checar `versionCode` via `resolveContentProvider`; tratar `IllegalArgumentException` como degradação |
| Convenção all-day UTC (leitura) vs local (escrita) | Tarefa pula de dia | Funções de conversão separadas leitura/escrita; testes de fuso |
| Efeitos colaterais de recorrência em edição | Usuário confuso | Reler a linha após escrever e refletir; avisar na UI |
| Rebase sobre upstream | Conflitos recorrentes | Baixo acoplamento ao core (seção 6) |
| Exclusão apaga subtarefas sem undo | Perda de dados | Diálogo de confirmação explícito |
| Escrita na main thread | ANR | `Dispatchers.IO` sempre |

---

## 8. Resumo executivo

- O objetivo (criar/editar tarefas no KashCal refletindo no Tasks.org) é **tecnicamente
  viável**: a API pública `org.tasks.api/v0` suporta insert/update/delete de tarefas, além de
  tags, lembretes, listas e places, e batch atômico.
- O PR #412 já entrega **read + completion + undo + recorrência (no completar) + all-day
  (leitura)**. O trabalho do fork é adicionar a **camada de escrita** e a **UI de formulário**.
- Os **edge cases que exigem mais cuidado**: convenção de all-day (UTC leitura × local
  escrita), semântica de **patch** no update (enviar flags all-day junto), **efeitos colaterais**
  de `due_date`/`list_id`/`completed_at`, comportamento de **recorrência** (avança ocorrência;
  contas `repeats_on_server`), e **listas read-only**.
- Mantenha o acoplamento ao core do KashCal mínimo — é o que torna o fork sustentável no
  rebase.

> Referências: documentação pública `CONTENT_PROVIDER.md` do projeto `tasks/tasks` no GitHub
> (API `org.tasks.api` v0). Conteúdo parafraseado para conformidade de licenciamento.
