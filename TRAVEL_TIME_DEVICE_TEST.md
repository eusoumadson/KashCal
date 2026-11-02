# TRAVEL_TIME_DEVICE_TEST.md: roteiro de teste em dispositivo para Alertas de Saída (Travel Time)

Os testes unitários cobrem a matemática dos provedores de rota, o circuit breaker, o compensador de horário de pico e os agendamentos simulados.
Este roteiro valida, no **hardware físico real**, o comportamento com os serviços do sistema Android que testes unitários não conseguem simular: GPS real, permissão de localização em segundo plano (Android 11+), agendamento de alarmes no `AlarmManager`/`WorkManager`, conexão de rede com provedores externos e notificação no canal dedicado.

---

## 📱 Contexto & Pré-requisitos do Teste

- **Dispositivo Alvo**: Aparelho físico Android (ex: `RQ8N502VJ1M`), com serviços de localização do Google Play ativos.
- **Identificador do App**: `org.onekash.kashcal.tasks` (build `debug` do fork).
- **Instalação**: Executar `./gradlew :app:installDebug` e abrir o aplicativo.
- **Permissões do Sistema**:
  - Localização: ao habilitar a feature, conceder "Durante o uso do app" e posteriormente nas configurações do sistema "Permitir o tempo todo" (*Allow all the time*).
  - Notificações: conceder permissão de notificações (Android 13+).
  - Alarmes e Lembretes exatos: concedido por padrão no debug.

---

## ⚠️ A. O que mais pode estar errado (Pontos Críticos)

| # | Cenário / Passo | Comportamento Esperado | Por que importa (Risco Real) |
|---|---|---|---|
| 1 | **Permissão de background no Android 11+**<br>Ativar alerta com "Localização atual" e ir para a tela inicial. | O app solicita localização aproximada/precisa e depois orienta o usuário a selecionar "Permitir o tempo todo" nas configurações do sistema. | O Android bloqueia requisições em background se a permissão for apenas em primeiro plano. |
| 2 | **Criação de evento com endereço via geocoder**<br>No `EventFormSheet`, digitar "Av. Paulista, 1000" e selecionar a sugestão. | As coordenadas (lat/lon) são preenchidas internamente no estado do evento. O switch de "Alerta de saída" se torna interativo. | Se o geocoder do Play Services falhar ou o usuário digitar sem selecionar sugestão, as coordenadas ficam `null` e a rota não calcula. |
| 3 | **Persistência ao editar evento**<br>Criar evento com alerta de saída (Carro, 10 min de buffer). Salvar, reabrir para edição. | Os campos (modal Carro, buffer 10 min, coordenadas) permanecem selecionados exatamente como salvos. | Valida se a entidade Room v28 salvou as novas colunas e se o `EventFormState` as reconstitui. |
| 4 | **Reagendamento ao mudar horário**<br>Alterar o horário de início do evento de 15:00 para 18:00. | O alarme antigo é cancelado e um novo cálculo de partida é agendado para o novo horário. | Evita notificações fantasmas no horário antigo e inconsistência no `TravelReminderScheduler`. |
| 5 | **Fallback sem internet (Modo Avião)**<br>Ativar Modo Avião antes do cálculo do trajeto. | A notificação é gerada utilizando o `LocalHaversineProvider` (estimativa por linha reta + fator de tráfego de horário de pico). **Zero crash.** | A rede móvel pode oscilar no momento da saída; o app nunca deve falhar silenciosamente ou fechar. |
| 6 | **Disparo da notificação em tempo real**<br>Agendar evento para 20 min no futuro, com tempo de viagem estimado de ~10 min. | Notificação aparece antes do evento no canal "Alertas de saída", indicando o tempo estimado e o botão "Ver no mapa". | Prova a cadeia ponta a ponta: `AlarmManager` → `TravelAlarmWorker` → `ReminderNotificationManager`. |

---

## 🧭 B. Configurações da Feature (Configurações → Recursos Avançados → Alertas de Saída)

Acesse **Configurações > Recursos avançados > Alertas de saída**:

| # | Passo | Esperado |
|---|---|---|
| 7 | **Status de Permissões**<br>Observar o badge no card inicial. | Se permissões estiverem completas: badge verde com "Permissões ativas". Se faltar segundo plano: badge em destaque orientando a concessão. |
| 8 | **Seção de Provedores de Rota**<br>Verificar título da seção. | O cabeçalho exibe "Provedores de roteamento e trânsito" (nunca texto residual do Tasks.org). |
| 9 | **Provedor Local (Offline)** | Exibe status "Sempre ativo" com descrição de cálculo via Haversine e Horário de Pico. |
| 10 | **Provedor Stadia Maps / Valhalla** | Permite colar uma chave de API da Stadia Maps e salvar. Exibe status do circuito ("Operacional" ou "Circuit Breaker fechado"). |
| 11 | **Provedor TomTom (Trânsito em Tempo Real)** | Permite colar a chave da TomTom API. Se vazia, indica que usará os provedores seguintes em cascata. |
| 12 | **Compensador de Horário de Pico**<br>Alternar switch "Ajuste de horário de pico". | Salva a preferência no DataStore; quando ativo, aplica fator de 1.35x a 1.6x nos horários 07:00-09:30 e 17:00-19:30 locais. |

---

## 📝 C. Formulário de Evento (`EventFormSheet`)

| # | Passo | Esperado |
|---|---|---|
| 13 | **Evento de Dia Inteiro**<br>Marcar toggle "Dia inteiro". | A seção de "Alerta de saída" fica oculta ou desabilitada (viagem não se aplica a eventos sem horário pontual). |
| 14 | **Seleção de Modais**<br>Testar os chips: "Carro", "A pé" e "Transporte público". | Apenas um modal fica ativo por vez. O modal selecionado é refletido no ícone e texto correspondente. |
| 15 | **Seleção de Buffer de Antecedência**<br>Testar chips: Sem buffer, 5 min, 10 min, 15 min, 30 min antes. | O valor selecionado é destacado e salvo corretamente na entidade do evento. |
| 16 | **Origem Fixa vs Localização Atual**<br>Alternar entre "Localização atual" e "Endereço fixo". | Se "Endereço fixo" for escolhido, abre campo de texto para definir ponto de partida fixo (ex: Casa ou Trabalho). |
| 17 | **Exclusão de Evento**<br>Excluir um evento que possuía alerta de saída ativo. | O alarme no `AlarmManager` / `WorkManager` é cancelado imediatamente. |

---

## 🔔 D. Notificação e Ações do Sistema

| # | Passo | Esperado |
|---|---|---|
| 18 | **Texto Enriquecido da Notificação**<br>Ao receber o alerta de saída. | Título: `Saia agora → [Título do Evento]` ou `Saia em X min → [Título]`.<br>Subtítulo detalhado: `est. 18 min de carro (inclui 5 min de trânsito)` ou `(ajustado para horário de pico)`. |
| 19 | **Ação "Ver no mapa"**<br>Tocar no botão da notificação. | Abre o aplicativo de mapas padrão (Google Maps, Waze ou OsmAnd) já configurado para traçar rota até as coordenadas de destino. |
| 20 | **Canal de Notificação Dedicado**<br>Verificar as propriedades da notificação nas configurações do Android. | A notificação pertence à categoria/canal próprio "Alertas de saída", permitindo ao usuário customizar som e vibração sem afetar lembretes normais. |

---

## 📋 Tabela de Execução no Aparelho

Preencha durante o teste de homologação:

| # | Item do Roteiro | Provedor Testado | Resultado | Observações / Logs |
|---|---|---|---|---|
| 1 | Permissão de Background | Sistema Android | [ ] OK &nbsp; [ ] Falha | |
| 2 | Geocodificação no Form | Google Play Services | [ ] OK &nbsp; [ ] Falha | |
| 3 | Persistência Room v28 | SQLite Local | [ ] OK &nbsp; [ ] Falha | |
| 4 | Reagendamento de Horário | `TravelReminderScheduler` | [ ] OK &nbsp; [ ] Falha | |
| 5 | Fallback Offline (Modo Avião) | `LocalHaversineProvider` | [ ] OK &nbsp; [ ] Falha | |
| 6 | Notificação Ponta a Ponta | `TravelAlarmWorker` | [ ] OK &nbsp; [ ] Falha | |
| 10 | Rota via Valhalla / Stadia | Nuvem / Demo | [ ] OK &nbsp; [ ] Falha | |
| 12 | Fator Horário de Pico | `RushHourEstimator` | [ ] OK &nbsp; [ ] Falha | |
| 19 | Intent "Ver no mapa" | Intent `geo:lat,lon` | [ ] OK &nbsp; [ ] Falha | |
