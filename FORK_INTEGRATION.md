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
  task_tags, reminders, places, accounts, `Tasks.viewUri()`).
- `TasksOrgModels.kt` — modelos (`TasksOrgTask`, `TasksOrgList`, `TasksOrgSchedule`,
  `TasksOrgTaskDraft`, `TasksOrgTaskPatch` + `Field`, `TasksOrgTaskExtras`, `TasksOrgWriteResult`,
  `TasksOrgTag`, `TasksOrgReminder`, `TasksOrgPlace`, `TasksOrgAccount`, `TasksOrgVersion`,
  `TasksOrgLocation`, enums de prioridade/repeat_from).
- `TasksOrgBatch.kt` — **plano do `applyBatch` atômico** (tarefa + tags + lembretes + lugar), em
  funções puras sobre o mapper. Depende das regras de back-reference do provider.
- `TasksOrgV0Mapper.kt` — **a camada de abstração**. Todo acesso a coluna/URI/quirk da v0 passa
  por aqui. Se a API v0 do Tasks.org evoluir e quebrar contrato, a adaptação é só neste arquivo.
- `TasksOrgRepository.kt` — interface (read + write).
- `AndroidTasksOrgRepository.kt` — implementação sobre o `ContentResolver` (usa o mapper).
- `TasksOrgManager.kt` — `ContentObserver` + `changeSignal` + gating por permissão.

### `domain/tasks/`
- `TasksOrgCoordinator.kt` — liga/desliga a fonte, mostra/esconde listas, completion + undo,
  salvar (atômico) e excluir tarefa, releitura da regra após editar, gestão de listas/tags/lugares.
- `RecurrenceRule.kt` — `SimpleRecurrence`/`RecurrenceChoice`: o que o editor monta e como mantém
  intacta uma regra criada no Tasks.org.

### `ui/` (telas e componentes próprios)
- `ui/components/TaskCard.kt`, `TaskQuickViewSheet.kt` — cartão e sheet de detalhe da tarefa.
- `ui/components/TaskEditorState.kt`, `TaskEditorSheet.kt`, `TaskEditorFields.kt` — **formulário
  próprio** de criar/editar tarefa (não reusa o formulário de `Event`, de propósito — §6 do
  relatório). `TaskEditorFields.kt` tem datas, repetição e lugar.
- `ui/screens/settings/TasksOrgScreen.kt` e `TasksOrgManagementSection.kt` — tela de settings da
  integração e a seção de gestão de listas, tags e lugares.
- `ui/viewmodels/TasksOrgManageViewModel.kt` — ViewModel da gestão. **É um ViewModel próprio de
  propósito**: estender `AccountSettingsViewModel`/`SettingsRoute` (core) criaria conflito de rebase.
- `rememberCanWriteTasksOrg()` (em `TaskCard.kt`) — lê a permissão de escrita e a relê ao voltar
  para o app; usado pelo cartão e pelo quick view, então o core não precisa fornecê-la.
- `ui/components/TasksOrgFab.kt` — `TasksOrgFabSlot`, que põe o FAB "nova tarefa" acima do FAB
  de evento (integração off: só chama o conteúdo, tela idêntica à original).

> Observação: `TaskQuickViewSheet` ganhou um parâmetro opcional `onEdit` e `TaskCard`/
> `rememberCanWriteTasksOrg` são do fork; como os arquivos são do fork, não há seam de core aqui.

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
  `saveTask` (salvamento atômico), `deleteTask`, `createPlace`. Todas abrem com
  `tasksOrgCoordinator ?: return`, então são no-op sem a integração.

Reaplicar: colar a região inteira (delimitada) + os 2 membros + o param do ctor + os branches.

### 3.4 `ui/screens/HomeScreen.kt` — FAB secundário + params (aditivo)

- **imports** (3): `OnTaskCheckedChange`, `TaskCard`, `TasksOrgFabSlot`.
- **params** do composable (em 2 composables): `onTaskClick`, `onTaskCheckedChange` (já existiam
  no PR de read), e os novos `showTaskFab: Boolean = false`, `onCreateTask: () -> Unit = {}`.
  **Todos com default**, então callers antigos compilam.
- **bloco do FAB**: o FAB de evento original fica **intacto**; duas linhas o envolvem em
  `TasksOrgFabSlot(showTaskFab, onCreateTask) { ... }`. Com `showTaskFab = false` o slot só chama
  o conteúdo → tela idêntica ao upstream.
- **agenda**: o `AgendaCard(...)` original fica intacto; um `if (task) TaskCard(...) else`
  é inserido antes dele.
- **5 when-branches** `is DisplayEvent.Task -> onTaskClick(displayEvent)` (ver §3.0).

### 3.5 `MainActivity.kt` — fiação da UI (aditivo)

- **imports** (3): `TaskQuickViewSheet`, `TaskEditorSheet`, `TaskEditorState`.
- **`val tasksOrgEnabled by homeViewModel.tasksOrgEnabled...`** — coleta o flag.
- **estado**: `taskQuickViewEvent`, `taskEditorState` (dois `remember { mutableStateOf(...) }`).
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

# testes da feature (devem passar: 133)
./gradlew :app:testDebugUnitTest \
  --tests "org.onekash.kashcal.data.tasks_org.*" \
  --tests "org.onekash.kashcal.domain.tasks.*" \
  --tests "org.onekash.kashcal.ui.components.TaskEditor*"
```

Contagem esperada por classe:

| Classe de teste | testes |
| --- | :-: |
| `AndroidTasksOrgRepositoryTest` | 26 |
| `AndroidTasksOrgRepositoryFeaturesTest` | 27 |
| `TasksOrgV0MapperTest` | 13 |
| `TasksOrgV0MapperFeaturesTest` | 8 |
| `TasksOrgCoordinatorTest` | 9 |
| `TasksOrgCoordinatorSaveTest` | 7 |
| `RecurrenceRuleTest` | 10 |
| `TaskEditorStateTest` | 11 |
| `TaskEditorStateFeaturesTest` | 12 |
| `TaskEditorFieldsTest` | 10 |

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
- [ ] `./gradlew :app:compileDebugKotlin` + os 133 testes passando.
- [ ] Depois de um rebase, o upstream não mudou os XMLs copiados em `app/src/fork/res/xml/` (comando na §8).

---

## 7. Auditoria: núcleo do fork vs original (`git diff main`)

`main` é o upstream (release v2026.10.26). Conferido com `git diff main --name-status` e
`git diff main -U0 | grep '^-[^-]'`.

**Linhas do original removidas ou alteradas — a lista inteira (15 linhas em 3 arquivos do app, mais 2 testes):**

| Arquivo | O que muda | Por que é inevitável |
| --- | --- | --- |
| `DisplayEventRepository.kt` | vírgula no último param do construtor; 3× `calendarProviderManager.changeSignal` → `externalChangeSignal`; 4× `queryDeviceEvents(...)` ganha `+ queryTaskEvents(...)`; 1 `return` da busca | A agregação precisa incluir as tarefas e reagir às mudanças delas. Com a integração off, `queryTaskEvents` devolve `emptyList()` na primeira linha e o sinal do Tasks.org nunca muda. |
| `HomeViewModel.kt` | vírgula no último param do construtor e no `this(...)` | Receber o `TasksOrgCoordinator` (nulo por padrão). |
| `WidgetDataRepository.kt` | 3× `.map { toWidgetEvent }` → `.mapNotNull`; retorno de `toWidgetEvent` vira nulo-able | `DisplayEvent.Task` não tem id de evento para o widget abrir; é descartada. Sem tarefas, o resultado é idêntico. |
| testes upstream (2) | `ExportablePreferencesTest`, `DisplayEventRepositoryDeviceRealProviderTest` | Novas chaves no export; novos params do `DisplayEventRepository`. |

**Todo o resto do core é inserção pura**, cada uma com o marcador `FORK:tasks-org` ou em bloco
próprio: o manifesto (`<queries>` + 2 permissões), `KashCalApplication` (1 `@Inject` + 1 chamada
`tasksOrgManager.initialize()`), `PreferencesKeys`/`KashCalDataStore` (2 chaves), `RepositoryModule`
(1 `@Binds`), `ExportablePreferences` (2 chaves fora do backup), `DisplayEvent` (variante `Task`) e os
branches `is DisplayEvent.Task ->` exigidos pelo compilador (§3.0), `DayEventsSheet`/`EventCard`
(callbacks opcionais), `HomeScreen`, `MainActivity`, `SettingsRoute`/`SettingsDestination`/
`AccountSettingsScreen`/`AccountSettingsViewModel` (a entrada "Tasks.org" nas configurações) e
`strings.xml`.

**O que NÃO é tocado** (portanto igual ao original): adicionar conta CalDAV/iCloud, sincronização,
calendários do dispositivo (`DeviceCalendars*`, `CalendarProviderManager`,
`AndroidCalendarProviderRepository`), o Room e seu schema, o formulário de evento, notificações,
`libs.versions.toml`, CI e licenças. O `build.gradle.kts` só recebe inserções, todas da identidade do
app (§8).

**Identidade do app:** além das linhas acima, 5 linhas do original mudam e 4 testes ganham 8
linhas trocadas por causa do `applicationId` próprio. Estão na §8 e não afetam a lógica da feature.

**O que o usuário vê com a integração desligada:**

| Superfície | Tasks.org não instalado | Instalado, integração off | Integração on |
| --- | --- | --- | --- |
| Linha "Tasks.org" nas configurações | oculta | visível (é o interruptor) | visível |
| FAB "nova tarefa" | oculto | oculto | visível |
| Tarefas nas visões/busca/pontos do mês | não | não (`queryTaskEvents` → vazio) | sim |
| Observer do ContentProvider | não registrado | não registrado | registrado |
| Permissões `org.tasks.*` | declaradas, nunca pedidas | nunca pedidas | pedidas ao ligar |
| Fluxo de dots do mês | idêntico ao original | idêntico | ver `observeTasksOrgChanges` (só no bloco do fork) |

A única superfície visível fora da integração ligada é a linha de configurações, e só quando o
Tasks.org está instalado: sem ela não haveria como ligar a integração.

**Mudança de comportamento do core revertida nesta auditoria:** uma rodada anterior alterou
`HomeViewModel.buildEventDots` (cancelamento do job anterior) e `observeDeviceCalendarChanges`
(não limpava os pontos antes de reconstruir). Isso mudava o app mesmo com a integração off e foi
revertido; os dots por mudança de tarefas agora são reconstruídos por `observeTasksOrgChanges()`,
dentro da região do fork. O `LocalCanWriteTasksOrg` (que exigia editar o `CompositionLocalProvider`
da `MainActivity`) foi trocado por `rememberCanWriteTasksOrg()` dentro de `TaskCard.kt`.

---

## 8. Identidade do app: instala ao lado do KashCal original

O fork tem `applicationId` próprio (`org.onekash.kashcal.tasks`) e nome próprio ("KashCal Tasks"), para
instalar **junto** do KashCal original sem tocar nele. O `namespace` continua `org.onekash.kashcal`,
então os pacotes Kotlin e o `R` não mudam.

Instalar um segundo app com o mesmo `applicationId` não funciona (assinatura diferente), e um
`applicationIdSuffix` sozinho deixaria o fork quebrado ao lado do original: o Android só liga um
autenticador por tipo de conta (vale o do app instalado primeiro), e os ícones do lançador são
montados como `packageName + ".MainActivityDayN"`. Por isso a identidade tem estes pontos:

| Onde | O que | Por quê |
| --- | --- | --- |
| `app/build.gradle.kts` | `applicationIdSuffix = ".tasks"`; 5 `resValue` `fork_*` (nome, rótulos das contas, tipos de conta); `resValues = true`; `sourceSets` dos tipos de build apontam para `src/fork/res` | Fonte única da identidade. Os tipos de conta saem de `applicationId + sufixo`. Só inserções. |
| `AndroidManifest.xml` | `android:label="@string/fork_app_name"` (era `app_name`) | `app_name` é traduzido em ~70 idiomas, então um override em `values/` não vale no aparelho em pt-BR. |
| `KashCalAuthenticator`, `KashCalContactsAuthenticator` | `ACCOUNT_TYPE = BuildConfig.APPLICATION_ID` (e `+ ".contacts"`) | Têm de ser iguais ao tipo declarado nos XMLs. No build sem sufixo dá o mesmo valor de antes. |
| `AppIconPreset.kt` (`LauncherAliases.className`), `DateIconRefresher.kt` | Nome de classe do alias vem do namespace, não do `packageName` | Os aliases são declarados relativos ao namespace. |
| `app/src/fork/res/xml/` (5 arquivos novos) | Cópias de `kashcal_authenticator`, `kashcal_contacts_authenticator`, `kashcal_syncadapter`, `kashcal_contacts_syncadapter` (tipo e rótulo via `@string/fork_*`) e `shortcuts` (`targetPackage` literal) | Sobrescrevem os de `main` nos builds `debug` e `release`. O `targetPackage` do atalho não aceita referência a string. |
| Testes (4 arquivos) | `packageName + suffix` → `LauncherAliases.className(suffix)`; rótulo esperado vira `fork_app_name` | O Robolectric usa o `applicationId` como `packageName`. |

**Depois de um rebase**, confira se o upstream mexeu nos arquivos copiados desde o commit em que as
cópias foram feitas (`0164dad0`, o `main` da época). Troque `upstream/main` pelo nome do seu remote:

```sh
git diff 0164dad0 upstream/main -- app/src/main/res/xml/{kashcal_authenticator,kashcal_contacts_authenticator,kashcal_syncadapter,kashcal_contacts_syncadapter,shortcuts}.xml
```

Sem saída, nada a fazer. Se houver diferença, aplique-a também nas cópias de `app/src/fork/res/xml/`
(mantendo as trocas de tipo/rótulo e o `targetPackage`) e atualize o commit de referência acima.

**Para renomear ou trocar o sufixo:** nome em `fork_app_name`/`fork_account_label`/
`fork_contacts_account_label` (gradle) e `targetPackage` nos 3 atalhos de `shortcuts.xml`. O resto
deriva do sufixo.

**O que o usuário percebe:**

- É outro app: dados, contas, configurações, notificações e widgets próprios. Nada é copiado do
  KashCal original; contas CalDAV precisam ser adicionadas de novo.
- As contas aparecem no sistema como "KashCal Tasks" (tipos `org.onekash.kashcal.tasks` e
  `org.onekash.kashcal.tasks.contacts`), separadas das do original.
- O ícone ainda é o do KashCal (só o nome muda).
- Se "Calendários de outros apps" estiver ligado nos dois, cada um pode listar os calendários que o
  outro expõe ao sistema. Ligue só em um.
- Os dois apps podem sincronizar a mesma conta CalDAV e gerar notificações em dobro; use as contas
  em um deles.
- Assinatura: o build `debug` usa a chave de debug da máquina; um `release` próprio precisa de
  keystore sua (`KEYSTORE_*` em `local.properties` ou nos secrets do fork).
