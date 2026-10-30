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
| 12 | Criar lista "KC teste 2", renomear, mudar a cor, excluir (confirmando). | Reflete no Tasks.org; excluir apaga as tarefas dela. Em conta CalDAV a chamada pode demorar (rede). |
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
