# Requisitos — Alertas de Saída por Tempo de Viagem (Travel Time Alerts)

## 1. Visão Geral

Adicionar ao KashCal Tasks um tipo especial de alerta para eventos com local definido que notifica o usuário **quando deve sair** para chegar a tempo ao evento, independentemente de onde ele esteja no momento em que o alarme dispara.

O diferencial central desta funcionalidade: o tempo de viagem é calculado **dinamicamente no momento do disparo do alarme** (~1 hora antes do evento), usando a localização atual do dispositivo obtida via GPS/Fused Location, e não no momento estático em que o evento é criado.

Isso significa que, para eventos recorrentes (por exemplo, "Trabalho às 09:00"), se na segunda-feira o usuário dormiu em sua residência e na terça-feira dormiu na casa dos pais, cada ocorrência calculará o trajeto e o tempo de partida a partir do ponto em que o usuário se encontra naquele dia específico.

---

## 2. Requisitos Funcionais

### RF-01 — Seção e Toggle no Formulário de Evento
- O formulário de criação/edição de eventos (`EventFormSheet`) exibe a seção "Alerta de saída" se e somente se:
  1. O campo `location` (Local) estiver preenchido (não vazio e não composto apenas por espaços).
  2. O evento não for de dia inteiro (`isAllDay == false`).
- A seção permanece recolhida/desativada por padrão; o usuário a ativa por meio de um toggle/switch explícito.
- Ao desativar o toggle, todos os parâmetros de viagem associados ao formulário são redefinidos/limpos.

### RF-02 — Seleção do Modal de Transporte
- O usuário pode selecionar entre três modais de deslocamento:
  - 🚗 **Carro** (padrão)
  - 🚶 **A pé**
  - 🚌 **Transporte Público**
- A interface exibe aviso informativo indicando que o modo "Transporte Público" utiliza uma heurística estimada (velocidade média ajustada + buffer de integração com rota a pé), visto que servidores abertos de roteamento não dispõem de feed GTFS em tempo real em todas as cidades.

### RF-03 — Origem da Viagem (Flexível com Padrão Dinâmico)
- O campo "Sair de" (Origem) possui como valor padrão **"Localização atual"** (`travel_origin_fixed = null` no banco).
- O usuário tem a opção de alternar para **"Endereço fixo"** (`travel_origin_fixed = String`), dispondo do mesmo mecanismo de autocompletar e sugestões de endereço (`LocationSuggestionService`) presente no campo de destino.
- Ao selecionar "Localização atual": o trajeto é calculado a partir das coordenadas geográficas em tempo real do aparelho quando o `TravelAlarmWorker` executa (~1h antes do início do evento).
- Ao definir um endereço fixo: o aplicativo geocodifica essa origem e a utiliza para todas as ocorrências daquele evento.

### RF-04 — Buffer Adicional de Saída
- O usuário pode configurar um alerta antecipado ("Avisar também X min antes de precisar sair") com as seguintes opções:
  - `0` (Sem aviso extra — apenas "Hora de sair")
  - `5 minutos`
  - `10 minutos`
  - `15 minutos` (sugestão recomendada na UI)
  - `30 minutos`
- Se `bufferMin == 0`: é agendada uma única notificação de saída ("Hora de sair").
- Se `bufferMin > 0`: são agendadas duas notificações:
  1. Antecipada: "Saia em X min → [Local]"
  2. No momento de partida: "Saia agora → [Local]"

### RF-05 — Cálculo Independente por Ocorrência
- Para eventos recorrentes (RRULE), o cálculo de rota e tempo de deslocamento é realizado **de forma isolada e independente para cada ocorrência**.
- O alarme de uma ocorrência nunca reaproveita dados de tempo de viagem calculados para outra, garantindo precisão mesmo diante de deslocamentos diários distintos do usuário.

### RF-06 — Notificações de Saída e Ações Rápidas
- **Notificação "Hora de sair"**:
  - Momento de disparo: `triggerTime = occurrenceStartTs - (travelMinutes * 60_000)`
  - Texto principal: *"Saia agora → [Local do evento] · est. 23 min de carro"*
- **Notificação "Buffer antecipado"** (quando configurado):
  - Momento de disparo: `triggerTime = occurrenceStartTs - ((travelMinutes + bufferMin) * 60_000)`
  - Texto principal: *"Saia em 15 min → [Local do evento] · est. 23 min de carro"*
- **Canal de Notificação**: Canal dedicado `CHANNEL_TRAVEL_REMINDERS` (`"travel_departure_reminders"`), configurado com prioridade ALTA (`IMPORTANCE_HIGH`), som, vibração e banner heads-up.
- **Ações Rápidas na Notificação**:
  - **"Ver no mapa"**: Dispara Intent padrão de navegação com rota preenchida (`geo:lat,lon` ou URL de rota externa) para o app de mapas do dispositivo.
  - **"Dispensar"**: Encerra o alarme/notificação.

### RF-07 — Estratégia de Roteamento em 3 Camadas com Fallback Resiliente
O cálculo de rota e tempo de viagem opera através de uma cadeia de 3 provedores (*Provider Chain*), garantindo que o usuário nunca fique sem notificação:
1. **Provedor 1: Stadia Maps (Valhalla Cloud)**:
   - Requer chave de API gratuita cadastrada pelo usuário nas Configurações.
   - Alta estabilidade, SLA em nuvem dedicada, sem risco de bloqueio de IP.
2. **Provedor 2: Valhalla OpenStreetMap Demo (`valhalla1.openstreetmap.de`)**:
   - Totalmente gratuito e aberto, sem necessidade de chave de API.
   - Ativo por padrão como fallback online quando o Stadia Maps não estiver configurado ou apresentar falha.
3. **Provedor 3: Estimativa Local (Fórmula de Haversine + Fatores Empíricos)**:
   - Execução 100% offline, sem requisição HTTP e sem latência.
   - Calcula distância geodésica em linha reta multiplicada pelo fator de tortuosidade viária do modal (1.35x para carro, 1.20x a pé, 1.50x transporte público) dividida pela velocidade média estimada.
   - Sempre disponível como garantia final de funcionamento mesmo em modo avião ou sem sinal de rede celular.
- A notificação reflete visualmente a fonte da estimativa: *"est. 23 min de carro"* (via API online) vs *"est. ~25 min (estimativa local)"* (via fallback offline).

### RF-08 — Tela Dedicada de Configurações ("Alertas de Saída")
- Inserida na tela de Configurações do app no mesmo nível de "Tasks.org" (`SettingsDestination.TravelAlerts`).
- Apresenta:
  1. **Seção Educativa / O que é a funcionalidade**: Explicação pormenorizada de como funcionam os alertas dinâmicos baseados no local de partida em tempo real.
  2. **Seção de Provedores de Rota**:
     - Explicação da cadeia de fallback (Stadia Maps → Valhalla OSM → Estimativa Local).
     - Cartão do **Stadia Maps**: Guia passo a passo com link direto (`https://stadiamaps.com/signup`) para obtenção da chave gratuita (sem exigência de cartão de crédito), campo para inserção/edição da chave API, botão para salvar/testar e toggle para habilitar/desabilitar.
     - Cartão do **Valhalla OpenStreetMap Demo**: Descrição do servidor público comunitário e toggle para ativar/desativar.
     - Cartão da **Estimativa Local**: Descrição do funcionamento offline e indicador de "Sempre Ativo".
  3. **Seção de Permissões de Localização**: Status em tempo real das permissões `ACCESS_FINE_LOCATION` e `ACCESS_BACKGROUND_LOCATION` com botões de solicitação direta / atalho para configurações do sistema.

### RF-09 — Persistência de Coordenadas de Local no Formulário
- Ao selecionar uma sugestão de endereço no autocomplete de local (`LocationSuggestionService`), as coordenadas `latitude` e `longitude` são armazenadas no estado do formulário (`locationLat` / `locationLon`) e persistidas nos campos `Event.geoLat` e `Event.geoLon`.
- Evita chamadas redundantes de geocodificação no momento da execução do worker.

### RF-10 — Gerenciamento Gradual de Permissões de Localização
- No Android 11+ (API 30+), o acesso à localização em segundo plano exige concessão em duas etapas:
  1. Primeira ativação: solicitação de `ACCESS_FINE_LOCATION` (em primeiro plano) com diálogo explicativo prévio.
  2. Solicitação subsequente para `ACCESS_BACKGROUND_LOCATION` ("Permitir o tempo todo") com diálogo instrutivo direcionando o usuário para a tela de configurações do sistema operacional.
- Caso o usuário utilize apenas a opção "Endereço fixo", a permissão de segundo plano não é obrigatória.

---

## 3. Requisitos Não Funcionais

### RNF-01 — Dependências Mínimas e Compatibilidade
- Utilização da biblioteca oficial `play-services-location:21.3.0` exclusivamente para `FusedLocationProviderClient`.
- Nenhuma dependência externa adicional de rede: reaproveitamento do `OkHttpClient` singleton já configurado no projeto.

### RNF-02 — Isolamento Lateral (Padrão Fork)
- Todo o código novo deve residir em pacotes isolados:
  - `org.onekash.kashcal.data.travel`
  - `org.onekash.kashcal.reminder.travel`
  - Telas e componentes em `org.onekash.kashcal.ui.screens.settings`
- Todos os pontos de contato com arquivos do core do KashCal devem conter o marcador explícito:
  `// FORK:travel-time` (ou `<!-- FORK:travel-time -->` no XML).

### RNF-03 — Idempotência no Agendamento de Workers
- O agendamento no `WorkManager` deve utilizar `enqueueUniqueWork` com tag única no padrão:
  `"travel_alarm_${eventId}_${occurrenceTs}"` com política `ExistingWorkPolicy.REPLACE`.
- Múltiplas invocações do `TravelReminderScheduler` ou do `ReminderRefreshWorker` não duplicam tarefas pendentes.

### RNF-04 — Orçamento Rígido de Timeout
- O `TravelAlarmWorker` possui limite máximo estrito de execução de 25 segundos:
  - Obtenção de localização GPS: máximo de 8 segundos.
  - Requisição HTTP de roteamento: timeout de conexão/leitura de 8 segundos no OkHttp.
  - Estimativa local (Haversine): cálculo síncrono instantâneo (< 1 milissegundo).

### RNF-05 — Retrocompatibilidade de Banco de Dados e Preferências
- Migração Room 27 → 28 implementada em `Migrations.kt` com colunas com valor padrão (`DEFAULT NULL` e `DEFAULT 'TIME_BEFORE'`).
- Todas as chaves novas do DataStore devem ser catalogadas em `PreferencesKeys` e adicionadas à lista de permissões em `ExportablePreferences`, com validação nos testes de backup.
