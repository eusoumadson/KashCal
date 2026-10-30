# FORK_FEATURES.md: features do fork (integração Tasks.org)

Relatório das funcionalidades implementadas neste fork do KashCal. Toda a integração é
condicionada a **"Integração com Tasks.org ativada"**: com ela desligada, o app se comporta
como o KashCal original.

Documentos relacionados:
- `TASKS_ORG_FORK_REPORT.md`: análise, edge cases da API v0 e plano.
- `FORK_INTEGRATION.md`: mapa dos pontos de enxerto no core, para rebase com o upstream.

---

## 1. Resumo

| # | Feature | Estado |
| --- | --- | --- |
| 1 | Exibir tarefas do Tasks.org no calendário (mês, agenda, 3 dias/semana, busca) | ✅ |
| 2 | Completar/reabrir tarefa, com undo | ✅ |
| 3 | Tarefas recorrentes: completar avança a ocorrência; undo restaura | ✅ |
| 4 | Criar tarefa | ✅ |
| 5 | Editar tarefa | ✅ |
| 6 | Excluir tarefa (com confirmação) | ✅ |
| 7 | Prioridade | ✅ |
| 8 | Tags (criar, associar, remover) | ✅ |
| 9 | Lembretes baseados em tempo | ✅ |
| 10 | Deep-link para abrir a tarefa no Tasks.org | ✅ |
| 11 | Camada de abstração da API v0 (`TasksOrgV0Mapper`) | ✅ |
| 12 | Fork "plug'n'play" para rebase (sentinelas + documentação) | ✅ |
| 13 | Places / lembretes de local | ⬜ não implementado |
| 14 | Batch atômico (`applyBatch`) tarefa + tags + lembretes | ⬜ não implementado |
| 15 | Subtarefas (`parent_id`) na UI | ⬜ não implementado |

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
- **Permissões** `READ_TASKS`/`WRITE_TASKS` relidas ao voltar para o app (podem mudar nas
  Configurações do sistema).

## 3. Escrita de tarefas

### 3.1 Criar
- **FAB secundário** "nova tarefa": menor que o FAB de evento e empilhado acima dele, com cor
  `secondaryContainer`. Só aparece com a integração ligada.
- A data de vencimento é pré-preenchida com o dia selecionado (como dia inteiro).
- Só listas **graváveis** aparecem no seletor; se nenhuma existe, o editor não abre.

### 3.2 Editar
- Botão **Editar** no quick view da tarefa, visível só com a integração ligada e a tarefa
  gravável.
- Atualização **parcial** (patch): apenas os campos alterados são enviados, como a API espera.
- Mudar de dia inteiro para com hora (ou o inverso) envia a flag junto com a data.

### 3.3 Excluir
- Ação de excluir no editor, com **diálogo de confirmação**. A exclusão remove também as
  subtarefas e não tem undo no Tasks.org.

### 3.4 Campos do editor
Título (obrigatório), notas, lista, data/hora de vencimento, alternância dia inteiro,
prioridade (alta/média/baixa/nenhuma), tags e lembretes. Usa os pickers Material3 padrão.
O formulário é **próprio** e não reaproveita o formulário de evento, para não acoplar ao Room.

### 3.5 Resultado das escritas
`TasksOrgWriteResult` separa `Ok`, `ReadOnly` e `Refused`, e a UI mostra mensagem distinta
para lista somente leitura e para recusa genérica.

## 4. Tags
- Leitura das tags existentes e das tags de cada tarefa.
- Criação **idempotente** por nome.
- Reconciliação por diferença (adiciona/remove só o que mudou).
- Exposto no editor como chips.

## 5. Lembretes
- Tipos baseados em tempo: **horário absoluto** (`date_time`), **relativo ao vencimento**
  (`relative_due`) e **relativo ao início** (`relative_start`).
- Presets no editor: no vencimento, 10 min, 1 h e 1 dia antes.
- Edição por diferença (apaga e recria por id, pois a API não tem identidade editável).
- Fora do escopo: lembretes de local (dependem de Places), `snooze` e `random`.

## 6. Deep-link
`TasksOrgContract.Tasks.viewUri(id)` abre a tela da tarefa no próprio Tasks.org, para o que o
editor do KashCal não cobre.

---

## 7. Correções e decisões técnicas

- **Bug de dia inteiro no PR original:** a tarefa de dia inteiro era lida em UTC midnight
  (para alinhar com a grade de eventos) e, ao escrever, precisava de meia-noite **local**
  (convenção do Tasks.org). Leitura e escrita agora usam conversões separadas e explícitas,
  evitando que a tarefa pule de dia perto da meia-noite.
- **Abstração da API v0:** `TasksOrgV0Mapper` concentra todas as colunas, URIs e particularidades
  da `org.tasks.api/v0`. Se a API quebrar contrato, a adaptação fica nesse arquivo.
- **`UnsupportedOperationException`** (lista/tarefa somente leitura) é tratada como `ReadOnly`.
- **Escritas fora da main thread** e `notifyChanged()` após cada escrita, para a tela refletir
  sem esperar o observer.

## 8. Plug'n'play para rebase

- Toda a feature vive em `data/tasks_org/`, `domain/tasks/` e componentes próprios em `ui/`.
- Os toques no core (`DisplayEventRepository`, `DisplayEvent`, `HomeViewModel`, `HomeScreen`,
  `MainActivity`, `RepositoryModule`, manifesto) são aditivos e marcados com `FORK:tasks-org`.
- `grep -rn "FORK:tasks-org" app/src app/src/main/AndroidManifest.xml` lista todos.
- Passo a passo de rebase e diff de reaplicação em `FORK_INTEGRATION.md`.

## 9. Testes

64 testes unitários passando:

| Classe | Testes |
| --- | :-: |
| `AndroidTasksOrgRepositoryTest` | 26 |
| `TasksOrgV0MapperTest` | 13 |
| `TasksOrgCoordinatorTest` | 14 |
| `TaskEditorStateTest` | 11 |

Comando:
```sh
./gradlew :app:testDebugUnitTest \
  --tests "org.onekash.kashcal.data.tasks_org.*" \
  --tests "org.onekash.kashcal.domain.tasks.*" \
  --tests "org.onekash.kashcal.ui.components.TaskEditorStateTest"
```

Não há testes de UI (Compose) nem testes instrumentados da integração com o Tasks.org real;
a verificação em dispositivo é manual.

## 10. Pendências

- Places / lembretes de local.
- Batch atômico para criar tarefa + tags + lembretes numa transação (hoje são chamadas
  separadas após o insert; uma falha parcial é possível e fica só no log).
- Subtarefas na UI.
- Fixar uma versão mínima do Tasks.org e checá-la em runtime (a API v0 é instável).
- Se o fork for distribuído junto com o KashCal oficial: `applicationId` e assinatura próprios.
