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

### RF-02 — Seleção do Modal de Transporte e Integração com Apps de Trânsito
- O usuário pode selecionar entre três modais de deslocamento:
  - 🚗 **Carro** (padrão)
  - 🚶 **A pé**
  - 🚌 **Transporte Público**
- Para **Transporte Público**:
  - O aplicativo calcula uma estimativa base de tempo (caminhada combinada com velocidade média de transporte urbano).
  - A notificação e a interface exibem ação direta **"Ver linhas de transporte"** que abre a rota de transporte público diretamente no app de navegação instalado (Google Maps / Citymapper / Moovit) via deep-link padrão (`google.navigation:q=dest&mode=transit`), permitindo ao usuário consultar horários exatos de ônibus e trens em tempo real com um único toque.

### RF-03 — Origem da Viagem (Flexível com Padrão Dinâmico)
- O campo "Sair de" (Origem) possui como valor padrão **"Localização atual"** (`travel_origin_fixed = null` no banco).
- O usuário tem a opção de alternar para **"Endereço fixo"** (`travel_origin_fixed = String`), dispondo do mesmo mecanismo de autocompletar e sugestões de endereço (`LocationSuggestionService`) presente no campo de destino.
- Ao selecionar "Localização atual": o trajeto é calculado a partir das coordenadas geográficas em tempo real do aparelho quando o `TravelAlarmWorker` executa (~1h antes do início do evento).
- Ao definir um endereço fixo: o aplicativo geocodifica essa origem e a utiliza para todas as ocorrências daquele evento.

### RF-04 — Buffer Adicional de Saída
- O usuário pode configurar um tempo de buffer ("Avisar também X min antes de precisar sair") com as seguintes opções:
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

### RF-06 — Notificações de Saída e Enriquecimento com Dados de Tráfego
- **Notificação "Hora de sair"**:
  - Momento de disparo: `triggerTime = occurrenceStartTs - (travelMinutes * 60_000)`
  - Texto principal (com trânsito em tempo real): *"Saia agora → [Local] · est. 32 min de carro (inclui 10 min de trânsito)"*
  - Texto principal (sem trânsito em tempo real / horário de pico compensado): *"Saia agora → [Local] · est. 28 min de carro (ajustado para horário de pico)"*
  - Texto principal (estimativa offline): *"Saia agora → [Local] · est. ~25 min (estimativa local)"*
- **Notificação "Buffer antecipado"** (quando configurado):
  - Momento de disparo: `triggerTime = occurrenceStartTs - ((travelMinutes + bufferMin) * 60_000)`
  - Texto principal: *"Saia em 15 min → [Local] · est. 32 min de carro"*
- **Canal de Notificação**: Canal dedicado `CHANNEL_TRAVEL_REMINDERS` (`"travel_departure_reminders"`), configurado com prioridade ALTA (`IMPORTANCE_HIGH`), som, vibração e banner heads-up.
- **Ações Rápidas na Notificação**:
  - **"Ver no mapa"**: Dispara Intent padrão de navegação com rota preenchida (`geo:lat,lon` ou URL de rota externa) para o app de mapas do dispositivo.
  - **"Dispensar"**: Encerra o alarme/notificação.

### RF-07 — Estratégia de Roteamento em Camadas com Fallback Resiliente e Trânsito Real
O cálculo de rota e tempo de viagem opera através de uma cadeia inteligente de 4 níveis de provedores (*Provider Chain*):

1. **Nível 1 — Trânsito em Tempo Real (TomTom Routing API)**:
   - **Precisão**: ★★★★★ (Máxima).
   - **Requisito**: Chave de API gratuita cadastrada pelo usuário nas Configurações.
   - **Vantagem crítica**: Oferece **2.500 requisições/dia gratuitas (~75.000/mês)**, **SEM exigir cartão de crédito** no cadastro.
   - **Capacidade**: Fornece tempo de viagem com congestionamento em tempo real (`travelTimeInSeconds`) e atraso no trânsito (`trafficDelayInSeconds`).
2. **Nível 2 — Nuvem Dedicada OpenStreetMap (Stadia Maps / Servidor Próprio)**:
   - **Precisão**: ★★★★☆ (Alta).
   - **Requisito**: Chave gratuita Stadia Maps (200k créditos/mês, sem cartão) ou URL de servidor Valhalla/OSRM auto-hospedado (*self-hosted*).
   - **Capacidade**: Infraestrutura dedicada sem trânsito dinâmico, mas com alto SLA e estabilidade.
3. **Nível 3 — Servidor Comunitário Aberto (Valhalla OpenStreetMap Demo)**:
   - **Precisão**: ★★★☆☆ (Média).
   - **Requisito**: Nenhum (zero cadastro, zero chave). Servidor público `valhalla1.openstreetmap.de`.
   - **Capacidade**: Ativo por padrão como fallback online quando não há chaves configuradas.
4. **Nível 4 — Estimativa Local Inteligente (Fórmula de Haversine + Compensador de Pico)**:
   - **Precisão**: ★★☆☆☆ (Estimativa).
   - **Requisito**: Nenhum (100% offline, zero requisição HTTP).
   - **Capacidade**: Distância geodésica $\times$ fator de tortuosidade viária / velocidade média.
   - **Compensador de Horário de Pico**: Durante dias úteis em janelas de rush (07:30–09:30 e 17:30–19:30), adiciona automaticamente +30% de buffer de deslocamento para o modal Carro, prevenindo atrasos por tráfego quando offline.

### RF-08 — Tela Dedicada de Configurações ("Alertas de Saída")
- Inserida na tela de Configurações do app no mesmo nível de "Tasks.org" (`SettingsDestination.TravelAlerts`).
- Apresenta:
  1. **Seção Educativa / O que é a funcionalidade**: Explicação pormenorizada de como funcionam os alertas dinâmicos baseados no local de partida em tempo real.
  2. **Seção de Provedores de Rota**:
     - Explicação clara da cadeia de fallback (TomTom → Stadia → Valhalla OSM → Estimativa Local).
     - Cartão do **TomTom Routing (Recomendado para Trânsito Real)**: Guia passo a passo com link direto (`https://developer.tomtom.com`) para criação de conta gratuita sem cartão, campo para inserção da chave API com validação, e toggle de ativação.
     - Cartão do **Stadia Maps / Servidor Dedicado**: Guia para obter chave Stadia Maps (`https://stadiamaps.com/signup`) ou campo opcional para URL de instância própria.
     - Cartão do **Valhalla OpenStreetMap Demo**: Toggle para habilitar/desabilitar o servidor público comunitário.
     - Cartão da **Estimativa Local Inteligente**: Toggle do "Compensador de Horário de Pico" e indicador de "Sempre Ativo (Garantia Offline)".
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

### RNF-04 — Cache de Curto Prazo e Circuit Breaker
- **Cache LRU em Memória**: Rotas calculadas para coordenadas similares (arredondadas para ~100m) e mesmo modal são cacheadas com TTL de 15 minutos, poupando bateria e cota de requisições.
- **Circuit Breaker com Cooldown**: Provedores online que retornarem HTTP 429 (Rate Limit) ou falhas consecutivas entram em cooldown de 15 minutos, evitando re-tentativas desnecessárias e permitindo que o fallback assuma instantaneamente.

### RNF-05 — Orçamento Rígido de Timeout
- O `TravelAlarmWorker` opera com limite máximo estrito de execução de 25 segundos:
  - Obtenção de localização GPS: máximo de 8 segundos.
  - Requisição HTTP de roteamento: timeout de conexão/leitura de 8 segundos no OkHttp.
  - Estimativa local (Haversine): cálculo síncrono instantâneo (< 1 milissegundo).

### RNF-06 — Retrocompatibilidade de Banco de Dados e Preferências
- Migração Room 27 → 28 implementada em `Migrations.kt` com colunas com valor padrão (`DEFAULT NULL` e `DEFAULT 'TIME_BEFORE'`).
- Todas as chaves novas do DataStore devem ser catalogadas em `PreferencesKeys` e adicionadas à lista de permissões em `ExportablePreferences`, com validação nos testes de backup.
