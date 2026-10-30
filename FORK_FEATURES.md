# FORK_FEATURES.md: features do fork (integração Tasks.org)

Relatório das funcionalidades implementadas neste fork do KashCal. Toda a integração é
condicionada a **"Integração com Tasks.org ativada"**: com ela desligada, o app se comporta
como o KashCal original.

Documentos relacionados:
- `TASKS_ORG_FORK_REPORT.md`: análise, edge cases da API v0 e plano.
- `FORK_INTEGRATION.md`: mapa dos pontos de enxerto no core, para rebase com o upstream.
- `TASKS_ORG_DEVICE_TEST.md`: roteiro de teste em dispositivo com o Tasks.org real.

---

## 1. Resumo

| # | Feature | Estado |
| --- | --- | --- |
| 1 | Exibir tarefas do Tasks.org no calendário (mês, agenda, 3 dias/semana, busca) | ✅ |
| 2 | Completar/reabrir tarefa, com undo (inclusive recorrente) | ✅ |
| 3 | Criar, editar e excluir tarefa | ✅ |
| 4 | Prioridade, exibida no cartão e no quick view | ✅ |
| 5 | Tags: criar, associar, remover; exibidas no cartão | ✅ |
| 6 | Lembretes: horário, relativo ao vencimento/início, aleatório, soneca, de local | ✅ |
| 7 | Recorrência no editor (diária, semanal com dias, mensal, anual, intervalo, contagem) e "repetir a partir de" | ✅ |
| 8 | Data de início ("oculta até") no editor | ✅ |
| 9 | Aviso de efeito colateral ao mudar a data + releitura da regra após salvar | ✅ |
| 10 | Tarefas atrasadas aparecem em "hoje" | ✅ |
| 11 | Lugares e lembretes de local (chegada/saída) | ✅ |
| 12 | Salvamento atômico: tarefa + tags + lembretes + lugar em um `applyBatch` | ✅ |
| 13 | Gestão de listas, tags e lugares nas configurações | ✅ |
| 14 | Saúde das contas e checagem de versão do Tasks.org | ✅ |
| 15 | Subtarefas: contagem exibida e redirecionamento ao Tasks.org para gerenciá-las | ✅ (por desenho) |
| 16 | Deep-link: "Abrir no Tasks.org" abre a própria tarefa | ✅ |
| 17 | Camada de abstração da API v0 (`TasksOrgV0Mapper`) | ✅ |
| 18 | Fork "plug'n'play" para rebase (sentinelas + documentação) | ✅ |
| 19 | Validação contra o Tasks.org real em dispositivo | ⬜ não feita |

**Fora do escopo por decisão:** editar subtarefas no KashCal (o app redireciona ao Tasks.org) e
exibir tarefas sem data de vencimento.

---

## 2. Leitura e completion (base do PR #412, endurecida no fork)

- **Exibição** das tarefas no calendário pela data de vencimento, em todas as views, inclusive
  na busca (a busca do Tasks.org casa título e notas).
- **Esconder listas** individualmente nas configurações; **desligar a fonte** inteira.
- **Completar/reabrir** pelo checkbox do cartão e pelo quick view, com snackbar de **Undo**.
- **Recorrência no completar:** o Tasks.org avança a mesma linha para a próxima ocorrência. O
  undo distingue dois casos: restaurar o agendamento anterior, ou limpar `completed_at` em
  contas que repetem no servidor.
- **Escritas serializadas** por um mutex, para que reabrir logo após completar não chegue ao
  Tasks.org antes da conclusão.
- **Permissões** `READ_TASKS`/`WRITE_TASKS` relidas ao voltar para o app.

### 2.1 Tarefas atrasadas
Uma tarefa **aberta e atrasada** (segundo o filtro `overdue=1` do próprio Tasks.org) que venceu
antes do período exibido aparece no **dia de hoje**, como item de dia inteiro, quando hoje está
dentro do período. O cartão mostra "Atrasada desde <data>" na cor de erro; o editor e o
quick view usam a data de vencimento real (`dueTs`/`dueAllDay`), nunca a data fixada.
Não se aplica a tarefas concluídas, à busca, nem a listas ocultas.

### 2.2 Cartão e quick view
- **Prioridade:** ponto colorido (alta, média, baixa) antes do título, com descrição para
  leitores de tela.
- **Tags:** `#tag` numa linha secundária.
- **Subtarefas:** `⤷ feitas/total` no cartão; no quick view, o resumo e um botão que abre a
  tarefa no Tasks.org.

## 3. Escrita de tarefas

### 3.1 Criar
- **FAB secundário** "nova tarefa": menor que o FAB de evento e empilhado acima dele. Só
  aparece com a integração ligada.
- A data de vencimento é pré-preenchida com o dia selecionado (dia inteiro).
- Só listas **graváveis** aparecem no seletor.

### 3.2 Editar
- Botão **Editar** no quick view, visível só com a integração ligada e a tarefa gravável.
- Atualização **parcial** (patch): apenas os campos alterados são enviados.
- Mudar entre dia inteiro e com hora envia a flag junto com a data.

### 3.3 Excluir
- Ação no editor, com **diálogo de confirmação**. Remove também as subtarefas, sem undo.

### 3.4 Campos do editor
Título (obrigatório), notas, lista, **vencimento**, **início**, **repetição**, prioridade, tags,
**lugar**, lembretes e, ao editar, o redirecionamento de subtarefas. Pickers Material3. O
formulário é **próprio** e não reaproveita o de evento.

### 3.5 Recorrência
- Frequência diária, semanal (com dias da semana), mensal ou anual; "a cada N"; fim por
  contagem; "repetir a partir de" vencimento ou conclusão.
- A regra é gravada **sem prefixo `RRULE:`**, que é como o provider a guarda (confirmado nos
  testes do próprio Tasks.org); o provider aceita os dois formatos na escrita.
- **Regras que o editor não monta** (por exemplo "2ª terça do mês", dia do mês, data final)
  aparecem como "Regra personalizada" e são **regravadas idênticas**: editar a tarefa no KashCal
  nunca achata uma regra criada no Tasks.org. Escolher outra opção a substitui.
- Uma regra exige data de vencimento (senão o Tasks.org usaria hoje); o editor define hoje e
  avisa, e limpar o vencimento remove a regra.

### 3.6 Efeitos colaterais de mudar a data
Mudar o vencimento pode fazer o Tasks.org mover o início relativo ou re-ancorar uma regra mensal
por dia da semana (`2TU` vira `3WE`). O editor avisa antes de salvar. Depois de salvar uma tarefa
recorrente com a data alterada, o app **relê a tarefa** e, se a regra mudou, mostra "Tasks.org
ajustou a regra de repetição". Nada é previsto: compara-se o que o Tasks.org gravou.

### 3.7 Salvamento atômico
Criar e editar usam **um `applyBatch`** com a tarefa, as tags (incluindo as novas, por
back-reference), os lembretes e o lugar. Se algo falhar, nada é gravado: antes, uma falha na
segunda chamada deixava a tarefa criada sem tags e o erro ia só para o log. O plano do batch
está em `TasksOrgBatch.kt`, como funções puras testáveis.

### 3.8 Resultado das escritas
`TasksOrgWriteResult` separa `Ok`, `ReadOnly` e `Refused`; a UI mostra mensagem distinta.

## 4. Tags
- Leitura das tags existentes e das tags de cada tarefa; criação idempotente por nome; digitar um
  nome que já existe (sem diferenciar maiúsculas) seleciona a tag existente.
- Diff de adicionar/remover dentro do batch atômico.
- **Gestão** nas configurações: renomear, recolorir e excluir. Renomear para o nome de outra tag
  é recusado pelo Tasks.org (não há fusão).

## 5. Lembretes
- **Horário absoluto**, **relativo ao vencimento**, **relativo ao início**, **aleatório** (a cada
  ~N) e **soneca** (exibida e removível; só o Tasks.org cria).
- **De local:** ao chegar e/ou ao sair de um lugar (ver §6).
- Presets: no vencimento, 10 min, 1 h e 1 dia antes, e aleatório diário.
- Tipos desconhecidos de versões futuras do Tasks.org são ignorados sem quebrar o editor.

## 6. Lugares e lembretes de local
- Uma tarefa tem **no máximo um lugar**. O editor escolhe entre os lugares salvos e liga
  "ao chegar" e/ou "ao sair".
- **Novo lugar:** nome, endereço, coordenadas e raio. O Tasks.org **não geocodifica**; o botão
  "Buscar coordenadas pelo endereço" usa o geocoder do aparelho e, se ele não responder, as
  coordenadas são digitadas. Lugares são únicos por coordenada (repetir devolve o existente).
- Mudar de lugar grava `place_id = 0` antes (o que também apaga os lembretes antigos) e então
  arquiva sob o novo lugar, porque um segundo lugar na mesma tarefa é recusado pelo provider.
- Gestão (listar, excluir) nas configurações. Excluir um lugar desvincula as tarefas e apaga os
  lembretes de local delas.

## 7. Gestão de listas, tags e lugares (configurações)
Seção própria no `TasksOrgScreen`, com ViewModel próprio (`TasksOrgManageViewModel`), sem tocar
no ViewModel de configurações do core:
- **Listas:** criar (escolhendo a conta), renomear, recolorir e excluir (com confirmação; apaga as
  tarefas da lista). Lista somente leitura não oferece editar nem excluir.
- **Tags** e **lugares:** ver acima.
- **Contas:** aviso quando uma conta precisa de atenção (login expirado, assinatura, falha de
  sincronização).
- **Versão:** mostra a versão instalada e avisa em vermelho se for anterior à 15.12, a primeira
  com a API `v0`.

## 8. Subtarefas (por desenho)
O KashCal **não edita subtarefas**. Mostra a contagem e oferece "Gerenciar subtarefas no
Tasks.org" no cartão/quick view e no editor, que abre a própria tarefa por `ACTION_VIEW`.

## 9. Deep-link
`TasksOrgContract.Tasks.viewUri(id)` abre a tela da tarefa no Tasks.org. O botão "Abrir no
Tasks.org" do quick view agora abre a **tarefa**, não só o app (com o app como alternativa).

---

## 10. Correções e decisões técnicas

- **Bug de dia inteiro no PR original:** leitura em UTC midnight (para alinhar com a grade) e
  escrita em meia-noite **local** (convenção do Tasks.org), com conversões separadas.
- **Seletor de data no fuso:** o `DatePicker` do Material3 trabalha em dias UTC. O editor antigo
  entregava o instante local, então o seletor podia abrir no **dia errado**: o anterior, em fusos
  a leste de UTC, e o seguinte, para horários noturnos a oeste de UTC. Agora entrega o dia local
  como meia-noite UTC (`localDayAsUtcMidnight`, com teste em Tóquio).
- **Abstração da API v0:** `TasksOrgV0Mapper` concentra colunas, URIs e particularidades; o plano
  de batch fica em `TasksOrgBatch.kt`, sobre o mapper.
- **Código removido:** com o salvamento atômico, os caminhos não atômicos do coordenador
  (`createTask(draft)`, `updateTask(id, patch)`, `setTaskTags`, `setReminders`) foram apagados
  para que ninguém volte a salvar sem atomicidade.
- **`UnsupportedOperationException`** vira `ReadOnly`; escritas fora da main thread; `notifyChanged()`
  após cada escrita.

## 11. Plug'n'play para rebase

- A feature vive em `data/tasks_org/`, `domain/tasks/` e componentes próprios em `ui/`.
- Esta rodada **não acrescentou** toques no core além de uma linha em `MainActivity` (sentinela
  `FORK:tasks-org`) e mudanças dentro da região já delimitada do `HomeViewModel`. A gestão de
  listas/tags/lugares ficou em arquivo e ViewModel próprios, em vez de estender
  `AccountSettingsViewModel`/`SettingsRoute`.
- `grep -rn "FORK:tasks-org" app/src app/src/main/AndroidManifest.xml` lista todos os pontos.

## 12. Testes

136 testes unitários passando:

| Classe | Testes |
| --- | :-: |
| `AndroidTasksOrgRepositoryTest` | 26 |
| `AndroidTasksOrgRepositoryFeaturesTest` | 29 |
| `TasksOrgV0MapperTest` | 13 |
| `TasksOrgV0MapperFeaturesTest` | 8 |
| `TasksOrgCoordinatorTest` | 9 |
| `TasksOrgCoordinatorSaveTest` | 7 |
| `RecurrenceRuleTest` | 10 |
| `TaskEditorStateTest` | 11 |
| `TaskEditorStateFeaturesTest` | 12 |
| `TaskEditorFieldsTest` | 11 |

```sh
./gradlew :app:testDebugUnitTest \
  --tests "org.onekash.kashcal.data.tasks_org.*" \
  --tests "org.onekash.kashcal.domain.tasks.*" \
  --tests "org.onekash.kashcal.ui.components.TaskEditor*"
```

**Limites dos testes:**
- Rodam contra um **provider falso** (`FakeTasksOrgProvider`) que reproduz a documentação e os
  testes do próprio Tasks.org, não o app real.
- O provider falso **não é transacional**: confere-se a sequência e as back-references do
  batch, mas não o rollback em caso de falha. Essa garantia vem do `applyBatch` do Tasks.org.
- Não há testes de UI (Compose) nem instrumentados.

## 13. Pendências

- **Validar em dispositivo com o Tasks.org real** (roteiro em `TASKS_ORG_DEVICE_TEST.md`). O
  emulador `KashCal_API37` não inicia nesta máquina (sem aceleração de hardware), e o único
  dispositivo conectado é o telefone pessoal com dados reais, que não foi tocado.
- Decisões de distribuição: `applicationId` e assinatura próprios, e levar a branch ao `main` do fork.
