# TASKS_ORG_DEVICE_TEST.md: roteiro de teste com o Tasks.org real

Os 133 testes unitários rodam contra um provider **falso**. Este roteiro valida, no aparelho, o
que o falso não consegue provar. Os itens estão na ordem em que mais podem revelar erro.

> **Cuidado com os dados.** Crie uma **lista nova só para teste** no Tasks.org (por exemplo
> "KC teste") e use apenas ela. Os passos 12 e 13 apagam dados; faça-os só nessa lista, nas tags
> e nos lugares criados por você. Fazer backup do Tasks.org antes (Configurações → Backup) é uma
> boa prática.

Requisitos: Tasks.org 15.12 ou mais novo, KashCal do fork instalado, permissões de leitura e
escrita do Tasks.org concedidas em Configurações → Tasks.org.

## A. O que mais pode estar errado

| # | Passo | Esperado | Por que importa |
| --- | --- | --- | --- |
| 1 | Criar tarefa **de dia inteiro** para amanhã, pelo KashCal. Abrir no Tasks.org. | Mesma data, sem hora, sem pular de dia. | Convenção de dia inteiro: leitura em UTC, escrita em meia-noite local. |
| 2 | Repetir o passo 1 perto da meia-noite, ou trocando o fuso do aparelho para UTC+9 e UTC-9. | A data não muda. | Era o bug do PR original. |
| 3 | Abrir o seletor de data de uma tarefa existente com fuso leste e oeste de UTC. | Abre no dia da tarefa. | Correção do `DatePicker`. |
| 4 | Criar tarefa **recorrente** "Semanal, a cada 2, seg e qua" no KashCal. Ver no Tasks.org. | O Tasks.org mostra a mesma regra e a próxima data. | Valida o formato sem prefixo `RRULE:`. |
| 5 | No Tasks.org, criar tarefa "Mensal, 2ª terça". Abrir no KashCal, mudar só o **título**, salvar. Voltar ao Tasks.org. | A regra continua "2ª terça". | "Regra personalizada" preservada. |
| 6 | Na tarefa do passo 5, mudar a **data** para uma quarta, salvar. | Aviso antes de salvar; se o Tasks.org re-ancorar a regra, o snackbar diz que ajustou. | Releitura da regra. |
| 7 | Criar tarefa nova com 2 tags (uma **nova**), 2 lembretes e um lugar com "ao chegar". | Tudo aparece no Tasks.org, de uma vez. | Batch atômico e back-references. |
| 8 | Repetir o 7 numa lista **somente leitura** (se tiver uma). | Mensagem de lista somente leitura e **nada** criado. | Atomicidade / `ReadOnly`. |

## B. Exibição

| # | Passo | Esperado |
| --- | --- | --- |
| 9 | Ter uma tarefa aberta com vencimento na semana passada. Abrir o KashCal em **hoje**. | A tarefa **não** aparece em hoje. |
| 9b | Abrir o dia do vencimento (ou uma visão que o contém). | Aparece no **dia programado**, como qualquer tarefa, sem aviso de atraso. |
| 10 | Completar a tarefa do passo 9b pelo checkbox. | Fica riscada no mesmo dia. Desfazer a reabre. |
| 11 | Cartão de tarefa com prioridade alta, 2 tags e subtarefas (criadas no Tasks.org). | Ponto vermelho, `#tag #tag`, `⤷ x/y`. No quick view, "Gerenciar subtarefas no Tasks.org" abre **a tarefa** no Tasks.org. |

## C. Gestão (Configurações → Tasks.org → seção de gestão)

| # | Passo | Esperado |
| --- | --- | --- |
| 12 | Em Configurações > Tasks.org tasks > gerenciar, tocar em "Create a list in Tasks.org" (o fork não cria listas). Renomear e excluir lista só em lista de teste própria. | O Tasks.org abre. Renomear/excluir reflete no Tasks.org; excluir apaga as tarefas dela. |
| 13 | Criar tag, renomear para o nome de **outra** tag. | Recusado (não há fusão). Renomear para um nome livre funciona. Excluir remove das tarefas. |
| 14 | Criar um lugar pelo editor: digitar um endereço e tocar "Buscar coordenadas". | Preenche latitude/longitude (se o aparelho tiver geocoder). Sem rede/geocoder: mensagem e coordenadas manuais. |
| 15 | Criar de novo um lugar com as mesmas coordenadas. | Reaproveita o existente, sem duplicar. |
| 16 | Tarefa com lugar A. Editar para o lugar B com "ao sair". | Lembretes antigos somem, novo aparece. Sem erro de "segundo lugar". |
| 17 | Remover o lugar da tarefa. | Lugar e lembretes de local saem. |
| 18 | Conta com login vencido (ou desconectar uma conta de teste). | Aviso vermelho com o nome da conta. |
| 19 | Versão: ver a linha "Tasks.org x.y". | Mostra a versão instalada, sem aviso. |

## D. Casos de borda

- Editar tarefa recorrente de conta que **repete no servidor** (CalDAV): completar e desfazer.
- Mudar a recorrência de uma tarefa sem data de vencimento: o editor deve definir hoje e avisar.
- Tarefa com início relativo ao vencimento: mudar o vencimento e conferir se o início acompanha
  (o aviso do editor deve aparecer).
- Rotação da tela com o editor aberto (o editor fecha ao recriar a Activity; conferir que não
  perde nem duplica nada).
- Revogar a permissão de escrita nas Configurações do sistema com o editor aberto, e salvar.

## E. Como registrar o resultado

Para cada falha, anote: número do passo, versão do Tasks.org, tipo de conta (local, CalDAV,
Google), fuso do aparelho e o que apareceu. Falhas de formato (4, 5, 7) apontam para
`TasksOrgV0Mapper` / `TasksOrgBatch.kt`; falhas de data (1 a 3), para `TaskEditorFields.kt` e o
mapper; falhas de exibição (9 a 11), para `AndroidTasksOrgRepository.getTasksDueInRange` e
`TaskCard.kt`.

## F. Rodar com o emulador

O AVD `KashCal_API37` existe, mas não iniciou na máquina de desenvolvimento por falta de
aceleração (`hvf is not enabled on this aarch64 host`). Em outra máquina, ou depois de corrigir
a aceleração, dá para instalar o APK do Tasks.org (`gh release download 15.13 -R tasks/tasks -p
"tasks-fdroid-*.apk"`) e rodar este roteiro sem tocar nos dados do telefone.

---

## Resultados da execução em aparelho (30/10/2026)

Aparelho: SM-G970F, Android 16, Tasks.org 15.12 real, build do fork instalado como
`org.onekash.kashcal.forktest` (`--init-script` fora do repositório; o `applicationId` do repositório
era o do KashCal original na época). Desde a identidade própria do fork (`FORK_INTEGRATION.md` §8) o
build `debug` já instala como `org.onekash.kashcal.tasks`, sem o script. Só foram escritas tarefas e uma tag criadas pelo teste (`KC-TEST_*`, `kctest`),
todas removidas ao final. Tarefas, listas e lugares existentes só foram lidos.

| Verificação | Resultado |
| --- | --- |
| Linha "Tasks.org tasks" nas configurações; detecta a versão 15.12 | ok |
| Integração desligada: sem FAB de tarefa, sem tarefas | ok |
| Ligar com permissões já concedidas: listas lidas, FAB "New task" aparece | ok |
| Criar tarefa com prioridade, tag nova e lembrete "no vencimento" em um único salvamento | ok: título, data (dia inteiro), lembrete, lista, tag e prioridade conferidos no Tasks.org |
| Tarefa atrasada (25/out) não aparece em hoje; aparece no dia 25, sem texto de atraso | ok |
| Quick view mostra "Due dom., 25 de out. · O dia todo" | ok |
| Concluir pelo checkbox: riscada e marcada, permanece no dia 25 | ok |
| Editar: repetição semanal | ok: o Tasks.org mostra "Repetir semanalmente"; a regra sem prefixo `RRULE:` é aceita; título, lembrete, tag e prioridade preservados |
| Excluir tarefa (simples e recorrente), com confirmação | ok |
| Excluir tag | ok |
| Criar lista pelo provider | **falha no Tasks.org, não no fork**: as listas do aparelho estão numa conta CalDAV e o Tasks.org lança `NullPointerException` em `CaldavClient.makeCollection` (`ApiListManager.remote`). Por isso o fork **não oferece mais criar lista**: a seção de gestão tem "Create a list in Tasks.org", que abre o app. |
| Gestão visível com a integração desligada | **bug do fork, corrigido**: a seção só exigia a permissão de leitura; agora exige a integração ligada |

### Segunda rodada (30/10/2026)

| Verificação | Resultado |
| --- | --- |
| Botão "+ New list" removido; "Create a list in Tasks.org" abre o Tasks.org | ok |
| Novo lugar `KC-TEST_lugar` com endereço e "Find coordinates from address" | ok: o geocoder devolveu -23.5503898 / -46.633081 (Praça da Sé) |
| Tarefa com início ("hidden until"), semanal às sextas terminando após 5, tag nova, lembrete 10 min antes, lembrete aleatório diário, lugar | ok: tudo conferido na tela de edição do Tasks.org (início, "Repetir semanalmente em sex., ocorre 5 vezes", os dois lembretes, tag, lista, lugar) |
| Chip "On arrival" desligado no editor | ok: o Tasks.org mostra o lugar com o sino cortado (sem gatilho de chegada/saída). Na primeira escolha do lugar a chegada vem ligada por padrão |
| Concluir tarefa recorrente | ok: vai para a próxima sexta (6/nov), "ocorre 4 vezes", o início andou junto (28/out para 4/nov) |
| Desfazer a conclusão pelo snackbar (tarefa recorrente) | ok: a tarefa volta ao dia 13/nov |
| Tarefa com vencimento "dia inteiro" e início com hora, ao concluir | **comportamento do Tasks.org**: o vencimento passa a ter hora (00:00). A mesma tarefa sem data de início segue "dia inteiro". Antes de concluir, o vencimento estava como dia inteiro |
| Busca por "KC-TEST" | ok: as duas tarefas aparecem, com data e ícone de repetição |
| Agenda e Semana | ok: tarefas aparecem (Semana mostra blocos com checkbox); Dia e 3 dias não foram conferidos por texto |
| Mês: pontos | ok: pontos nos dias com tarefa |
| "Open in Tasks.org" e "Manage subtasks in Tasks.org" | **bug do fork, corrigido**: o `ACTION_VIEW` na URI também casava com outro app (Itaú) e o Android mostrava "Abrir com". Agora a intent fixa o pacote `org.tasks` e abre direto |
| Renomear tag | ok |
| Excluir tag e excluir lugar (da tarefa) | ok: a tag some da lista; o lugar de teste some e o lugar existente fica intacto |
| Excluir tarefa recorrente com confirmação | ok |

Não exercitado: subtarefa (esta versão do Tasks.org não mostra a linha de subtarefa na tela de
edição, então não consegui criar uma subtarefa de teste; o redirecionamento "Manage subtasks in
Tasks.org" foi conferido), lista somente leitura (o aparelho não tem), rollback do `applyBatch` em
falha (não há como forçar a falha), fuso horário diferente do aparelho, lembrete de local ligado
(a chegada foi desligada por engano e o lugar foi apagado depois; só a gravação sem gatilho foi vista).

Observação de i18n: as strings novas do fork estão só em inglês, então aparecem em inglês num
aparelho em português (o resto do app é traduzido pelo upstream).

### Terceira rodada: identidade do app (30/10/2026)

O fork passou a ser o app `org.onekash.kashcal.tasks` ("KashCal Tasks"), ao lado do KashCal original.

| Verificação | Resultado |
| --- | --- |
| `assembleDebug` instala (`adb install -r`) sem desinstalar nada | ok: o KashCal original (2026.09.19) segue instalado e sem alteração |
| `aapt2 dump badging` do APK de release (sem assinatura) | `package: org.onekash.kashcal.tasks`, `application-label: KashCal Tasks` |
| `dumpsys account`: autenticadores | quatro tipos registrados, cada um com o seu app: `org.onekash.kashcal` e `.contacts` (original), `org.onekash.kashcal.tasks` e `.tasks.contacts` (fork). O `forktest` antigo não aparece: só o primeiro app instalado vale por tipo, o que confirma o problema de usar o tipo do original |
| Atalhos estáticos do app | publicados para o pacote do fork |
| Resolução do alias de lançador | `org.onekash.kashcal.tasks/org.onekash.kashcal.MainActivityDefault` |
| Integração com o Tasks.org no app novo | ok: permissões concedidas por `pm grant`, listas lidas, tarefas aparecem |
| Suíte completa de testes unitários | 14.761 testes, 0 falhas |

Não verificado no aparelho: trocar o ícone do app pelas configurações (coberto por testes unitários) e criar
uma conta no sistema com o novo tipo (exigiria adicionar uma conta CalDAV real).
