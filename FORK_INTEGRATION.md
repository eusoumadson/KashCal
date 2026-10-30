# FORK_INTEGRATION.md — enxerto da integração Tasks.org no KashCal

> Mapa de "plug'n'play" do fork. Serve a um único objetivo: **tornar o rebase sobre o KashCal
> upstream o menos doloroso possível.** Toda a feature de Tasks.org (read + completion +
> criação/edição/exclusão + tags + lembretes + deep-link) vive em arquivos próprios; este
> documento lista os poucos pontos onde ela precisa tocar arquivos do *core* e como reaplicar
> cada toque depois de um merge.
>
> Documento de engenharia; o "porquê" da estratégia está em `TASKS_ORG_FORK_REPORT.md` (§5, §6).

---

## 0. Como usar depois de um rebase/merge com o upstream

1. `git rebase upstream/main` (ou merge). Os conflitos vão se concentrar nos 5 arquivos de core
   listados na §2.
2. Para localizar **todos** os pontos de enxerto mecanicamente:
   ```sh
   grep -rn "FORK:tasks-org" app/src app/src/main/AndroidManifest.xml
   ```
   Cada linha marcada com `// FORK:tasks-org` (ou `<!-- FORK:tasks-org -->` no manifesto) é uma
   adição do fork. Nenhuma linha do original leva essa marca.
3. Resolva cada conflito mantendo **ambos** os lados: a versão do upstream **mais** as linhas
   `FORK:tasks-org`. Nenhum toque do fork substitui lógica original — todos são aditivos, exceto
   os três marcados `(original: ...)` em `DisplayEventRepository.kt` (veja §3.2), onde o
   comentário diz exatamente qual era a linha original.
4. Compile e rode os testes (§4). Se algum `when (displayEvent)` acusar branch faltando, é o
   seam da §3.1 — adicione de volta o `is DisplayEvent.Task ->`.

> Regra de ouro: **se você remover todas as linhas `FORK:tasks-org` e os arquivos da §1, o app
> volta a ser o KashCal original, byte a byte na lógica.** É isso que mantém o fork barato.

---

## 1. Arquivos 100% do fork (sem conflito de rebase)

Estes arquivos não existem no upstream; o rebase nunca conflita neles. Eles concentram a feature.

### `data/tasks_org/` (camada de dados + abstração da API v0)
- `TasksOrgContract.kt` — constantes do provider `org.tasks.api/v0` (URIs, colunas, tags,
  task_tags, reminders, `Tasks.viewUri()`).
- `TasksOrgModels.kt` — modelos (`TasksOrgTask`, `TasksOrgList`, `TasksOrgSchedule`,
  `TasksOrgTaskDraft`, `TasksOrgTaskPatch` + `Field`, `TasksOrgWriteResult`, `TasksOrgTag`,
  `TasksOrgReminder`, enums de prioridade/repeat_from).
- `TasksOrgV0Mapper.kt` — **a camada de abstração**. Todo acesso a coluna/URI/quirk da v0 passa
  por aqui. Se a API v0 do Tasks.org evoluir e quebrar contrato, a adaptação é só neste arquivo.
- `TasksOrgRepository.kt` — interface (read + write).
- `AndroidTasksOrgRepository.kt` — implementação sobre o `ContentResolver` (usa o mapper).
- `TasksOrgManager.kt` — `ContentObserver` + `changeSignal` + gating por permissão.

### `domain/tasks/`
- `TasksOrgCoordinator.kt` — liga/desliga a fonte, mostra/esconde listas, completion + undo,
  create/update/delete, tags, lembretes.

### `ui/` (telas e componentes próprios)
- `ui/components/TaskCard.kt`, `TaskQuickViewSheet.kt` — cartão e sheet de detalhe da tarefa.
- `ui/components/TaskEditorState.kt`, `TaskEditorSheet.kt` — **formulário próprio** de
  criar/editar tarefa (não reusa o formulário de `Event`, de propósito — §6 do relatório).
- `ui/screens/TasksOrgScreen.kt` — tela de settings da integração.
- `LocalCanWriteTasksOrg` (CompositionLocal) — exposto por um componente do fork.

> Observação: `TaskQuickViewSheet` ganhou um parâmetro opcional `onEdit` e `TaskCard`/
> `LocalCanWriteTasksOrg` são do fork; como os arquivos são do fork, não há seam de core aqui.

---

## 2. Os 5 arquivos de core tocados (pontos de conflito)

Ordem decrescente de probabilidade de conflito (arquivos mais editados upstream primeiro):

| Arquivo | Nº de toques | Natureza |
| --- | :-: | --- |
| `ui/viewmodels/HomeViewModel.kt` | import(4) + ctor(3) + 2 membros + 5 when-branches + 1 região | aditivo |
| `MainActivity.kt` | import(3) + estado/wiring da UI | aditivo |
| `ui/screens/HomeScreen.kt` | import(4) + params + FAB + 5 when-branches | aditivo |
| `domain/reader/DisplayEventRepository.kt` | import(2) + ctor(2) + sinal + 4 call-sites + helper | 3 linhas substituem original |
| `domain/model/DisplayEvent.kt` | import(1) + variante `Task` + helpers | aditivo |

Detalhe de cada um na §3. Os números de linha são do estado atual do fork e vão mudar após o
rebase — **use o `grep` da §0, não os números**.

---

## 3. Catálogo dos seams (com diff mínimo de reaplicação)

### 3.0 Seam inevitável: `DisplayEvent.Task` como membro de sealed interface

`DisplayEvent` é uma *sealed interface*. Adicionar `DisplayEvent.Task` obriga **todo**
`when (displayEvent)` exaustivo no core a ganhar um branch `is DisplayEvent.Task ->`. Isso **não
dá para extrair** — é uma exigência do compilador Kotlin. São os seams de menor risco (o
compilador aponta qualquer um que faltar), e todos estão marcados. Lista completa dos branches:

- `HomeScreen.kt`: tap handlers (day/agenda/week/search/month) — ~5 branches.
- `HomeViewModel.kt`: filtro de busca, cópia de drag (read-only), zona de RRULE, escopos de
  drag, resultado de move — 5 branches.

Diff mínimo de cada: adicionar uma linha
```kotlin
is DisplayEvent.Task -> <mesmo valor dos outros ramos read-only> // FORK:tasks-org
```

### 3.1 `domain/model/DisplayEvent.kt` — a variante `Task` (aditivo)

- **import** `TasksOrgTask` — 1 linha.
- **`data class Task(val task: TasksOrgTask) : DisplayEvent`** — o bloco inteiro é do fork; é um
  adaptador **somente leitura** (`isReadOnly = true`, `isFree = true`). A escrita nunca passa por
  aqui, passa pelo `TasksOrgCoordinator`.
- **helpers** `DEFAULT_TASK_LIST_COLOR`, `TASK_OPEN_PREFIX`, `TASK_DONE_PREFIX`,
  `taskDisplayTitle(...)` — todos do fork.

Reaplicar: colar o bloco `Task` dentro da sealed interface + os 4 helpers no fim do arquivo.

### 3.2 `domain/reader/DisplayEventRepository.kt` — agregação (3 linhas substituem original)

Único arquivo onde o fork **altera** linhas, não só adiciona. As alterações estão marcadas
`(original: ...)`:

1. **import** `TasksOrgManager`, `TasksOrgRepository` — aditivo (2 linhas).
2. **construtor**: 2 params `tasksOrgRepository`, `tasksOrgManager` — aditivo.
3. **`externalChangeSignal`** — *substituída*. Original:
   ```kotlin
   get() = calendarProviderManager.changeSignal
   ```
   Fork (combina o sinal de Tasks.org):
   ```kotlin
   get() = combine(calendarProviderManager.changeSignal, tasksOrgManager.changeSignal) { d, t -> d to t } // FORK:tasks-org
   ```
4. **4 call-sites** `queryDeviceEvents(...) + queryTaskEvents(...)` — o `+ queryTaskEvents(...)`
   é aditivo; se o upstream mudou a linha do `queryDeviceEvents`, mantenha a mudança dele e só
   reanexe o `+ queryTaskEvents(startDayCode, endDayCode)`.
5. **busca** — `taskResults` e o `+ taskResults` no retorno; o comentário marca o retorno
   original.
6. **`private suspend fun queryTaskEvents(...)`** — helper inteiro do fork. **Primeira linha é o
   gate:** `if (!dataStore.getTasksOrgEnabled()) return emptyList()` — é isto que garante que, com
   a integração desligada, a agregação se comporta como o original.

> Se um dia quiser reduzir ainda mais este arquivo, dá para mover o corpo de `queryTaskEvents`
> para um *extension function* num arquivo do fork, deixando aqui só a chamada. Hoje não vale o
> churn (o helper é pequeno e já está gated).

### 3.3 `ui/viewmodels/HomeViewModel.kt` — estado + operações de escrita (aditivo)

- **imports** (4): `TasksOrgTask`, `TaskCompletionResult`, `TaskCompletionUndo`,
  `TasksOrgCoordinator`.
- **construtor** (3): o param `tasksOrgCoordinator: TasksOrgCoordinator? = null` no ctor
  primário, no `@Inject` secundário, e no encadeamento `this(...)`. **É `null` por padrão**, então
  testes e qualquer caller que não o forneça compilam e rodam como antes.
- **`val tasksOrgEnabled: StateFlow<Boolean>`** — derivado de `coordinator?.enabled ?: flowOf(false)`.
  É o flag que **gateia toda a UI** da feature. Sem coordinator → sempre `false`.
- **`private val tasksOrgWriteMutex`** — serializa as escritas.
- **5 when-branches** `is DisplayEvent.Task ->` (ver §3.0).
- **região `// region FORK:tasks-org ... // endregion`** — bloco contíguo com
  `setTasksOrgTaskCompleted`, `undoTasksOrgCompletion`, `prepareNewTask`, `prepareEditTask`,
  `saveTask`, `deleteTask`. Todas abrem com `tasksOrgCoordinator ?: return`, então são no-op sem
  a integração.

Reaplicar: colar a região inteira (delimitada) + os 2 membros + o param do ctor + os branches.

### 3.4 `ui/screens/HomeScreen.kt` — FAB secundário + params (aditivo)

- **imports** (4): `CheckCircle` (ícone), `SmallFloatingActionButton`, `OnTaskCheckedChange`,
  `TaskCard`.
- **params** do composable (em 2 composables): `onTaskClick`, `onTaskCheckedChange` (já existiam
  no PR de read), e os novos `showTaskFab: Boolean = false`, `onCreateTask: () -> Unit = {}`.
  **Todos com default**, então callers antigos compilam.
- **bloco do FAB**: dentro do `floatingActionButton`, um `if (showTaskFab) { SmallFloatingActionButton(...) }`
  **acima** do FAB de evento, num `Column`. Com `showTaskFab = false` o `Column` renderiza só o
  FAB original → tela idêntica ao upstream.
- **5 when-branches** `is DisplayEvent.Task -> onTaskClick(displayEvent)` (ver §3.0).

### 3.5 `MainActivity.kt` — fiação da UI (aditivo)

- **imports** (3): `TaskQuickViewSheet`, `TaskEditorSheet`, `TaskEditorState`.
- **`val tasksOrgEnabled by homeViewModel.tasksOrgEnabled...`** — coleta o flag.
- **estado**: `taskQuickViewEvent`, `taskEditorState` (dois `remember { mutableStateOf(...) }`).
- **`readTasksOrgWrite` / `canWriteTasksOrg` / `LifecycleResumeEffect`** — relê a permissão de
  escrita no resume.
- **`LocalCanWriteTasksOrg provides canWriteTasksOrg`** — no `CompositionLocalProvider`.
- **args do `HomeScreen`**: `onTaskClick`, `onTaskCheckedChange`, `showTaskFab = tasksOrgEnabled`,
  `onCreateTask = { ... prepareNewTask ... }`.
- **bloco `taskQuickViewEvent?.let { ... }`** — hospeda o `TaskQuickViewSheet`; o `onEdit` é
  passado **somente quando `tasksOrgEnabled`** (senão `null` → sheet view-only).
- **bloco `taskEditorState?.let { ... }`** — hospeda o `TaskEditorSheet` (save/delete/dismiss).

Ambos os blocos de sheet estão marcados `FORK:tasks-org — entire block`: é só apagar o `?.let`
inteiro para remover.

---

## 4. Verificação pós-rebase

```sh
# compila
./gradlew :app:compileDebugKotlin

# testes da feature (devem passar: 26 + 13 + 14 + 11 = 64)
./gradlew :app:testDebugUnitTest \
  --tests "org.onekash.kashcal.data.tasks_org.*" \
  --tests "org.onekash.kashcal.domain.tasks.*" \
  --tests "org.onekash.kashcal.ui.components.TaskEditorStateTest"
```

Contagem esperada por classe:

| Classe de teste | testes |
| --- | :-: |
| `AndroidTasksOrgRepositoryTest` | 26 |
| `TasksOrgV0MapperTest` | 13 |
| `TasksOrgCoordinatorTest` | 14 |
| `TaskEditorStateTest` | 11 |

---

## 5. Outros toques fora dos 5 arquivos (baixo risco)

Estes tocam arquivos de core mas em seções próprias e delimitadas — baixíssimo risco de
conflito, mas listados para completude:

- `di/RepositoryModule.kt` — `@Binds` de `TasksOrgRepository`. Marcado `FORK:tasks-org`.
- `data/preferences/PreferencesKeys.kt` e `KashCalDataStore.kt` — chaves `TASKS_ORG_ENABLED` e
  `HIDDEN_TASKS_ORG_LIST_IDS` + acessores, sob um cabeçalho `// ===== Tasks.org =====`. Aditivo.
- `AndroidManifest.xml` — `<package android:name="org.tasks"/>` no `<queries>` e as permissões
  `READ_TASKS`/`WRITE_TASKS`. Marcado com `<!-- FORK:tasks-org -->`.
- `res/values/strings.xml` — strings `task_editor_*`, `task_priority_*`, `task_reminder_*`,
  `snackbar_task_*`, etc. Aditivo (merge de XML raramente conflita em adições).

---

## 6. Checklist de "o fork continua plug'n'play?"

Ao adicionar qualquer coisa nova à feature, mantenha estas invariantes:

- [ ] Nenhuma linha nova fora dos arquivos da §1 **sem** o marcador `FORK:tasks-org`.
- [ ] Todo ponto de entrada de UII gateado em `tasksOrgEnabled` (com a integração off, o app =
      original).
- [ ] Nenhum campo novo no `Event` do Room nem no formulário de evento.
- [ ] Todo acesso à API v0 passa pelo `TasksOrgV0Mapper` (nenhuma coluna/URI crua fora dele).
- [ ] Escrita sempre em `Dispatchers.IO`, sempre serializada pelo `tasksOrgWriteMutex`.
- [ ] `./gradlew :app:compileDebugKotlin` + os 64 testes passando.
