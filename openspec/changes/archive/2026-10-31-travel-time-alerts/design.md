# Design Técnico — Alertas de Saída por Tempo de Viagem (Travel Time Alerts)

## 1. Visão Geral da Arquitetura

A funcionalidade é desenhada como uma **camada lateral (lateralized fork layer)** ao ecossistema de lembretes e agendamentos do KashCal, preservando a integridade da base existente e conectando-se através de costuras (*seams*) bem delimitadas e marcadas com o identificador `// FORK:travel-time`.

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                   EventFormSheet                                        │
│  - Toggle "Alerta de saída" (visível se location != null && !isAllDay)                  │
│  - Seletor de modal: [🚗 Carro] [🚶 A pé] [🚌 Transporte]                               │
│  - Origem: "Localização atual" (padrão) | "Endereço fixo" com autocomplete              │
│  - Buffer extra de aviso: 0 / 5 / 10 / 15 / 30 minutos                                  │
└────────────────────────────────────────────┬────────────────────────────────────────────┘
                                             │ saveEvent (HomeViewModel)
                                             ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                          Entidade Event (Room DB v28)                                   │
│  + travel_mode: TEXT? ("CAR" | "FOOT" | "TRANSIT")                                      │
│  + travel_depart_buffer_min: INTEGER?                                                   │
│  + travel_origin_fixed: TEXT?                                                           │
│  + geo_lat / geo_lon: REAL? (persistidos ao selecionar sugestão)                        │
└────────────────────────────────────────────┬────────────────────────────────────────────┘
                                             │
                                ┌────────────▼─────────────┐
                                │     EventCoordinator     │ // FORK:travel-time seam 1
                                │  scheduleForEvent(...)   │
                                └────────────┬─────────────┘
                                             │ enfileira workers
                                             ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                              TravelReminderScheduler                                    │
│  WorkManager.enqueueUniqueWork("travel_alarm_{eventId}_{occurrenceTs}")                 │
│  - Executa ~1h antes do início da ocorrência (ou imediatamente se faltar < 1h)          │
│  - Integrado também ao ReminderRefreshWorker (varredura diária dos próximos 7 dias)     │
└────────────────────────────────────────────┬────────────────────────────────────────────┘
                                             │ dispara WorkManager
                                             ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                   TravelAlarmWorker (CoroutineWorker + Expedited)                       │
│  1. Resolve coordenadas de Destino (geoLat/geoLon ou Geocoder)                          │
│  2. Resolve coordenadas de Origem:                                                      │
│     - Se fixed: Geocoder(travel_origin_fixed)                                           │
│     - Se atual: TravelLocationProvider (FusedLocationClient, timeout 8s)                │
│  3. RoutingService.estimate(...)                                                        │
│     ┌────────────────────────────────────────────────────────────────────────────┐      │
│     │ In-Memory LRU RouteCache (TTL 15 min, lat/lon quantizados em ~100m)        │      │
│     ├────────────────────────────────────────────────────────────────────────────┤      │
│     │ Intelligent Fallback Chain com Circuit Breaker:                            │      │
│     │ ① TomTomRoutingProvider   (online, trânsito real, 2.5k req/dia free)       │      │
│     │ ② StadiaMapsProvider      (online, Valhalla nuvem dedicada com chave)      │      │
│     │ ③ ValhallaDemoProvider    (online, gratuito valhalla1.openstreetmap.de)     │      │
│     │ ④ LocalHaversineProvider  (offline, fórmula geodésica + Rush Hour Buffer)  │      │
│     └────────────────────────────────────────────────────────────────────────────┘      │
│  4. Calcula triggerTime de partida: occurrenceTs - (travelMinutes * 60s)                │
│  5. Calcula triggerTime de buffer (se buffer > 0): occurrenceTs - ((travel + buf) * 60s)│
│  6. Insere ScheduledReminder (reminder_type = TRAVEL_DEPART / TRAVEL_BUFFER)            │
│  7. Agenda AlarmManager exato: ReminderScheduler.scheduleAlarm(reminderId, triggerTime) │
└────────────────────────────────────────────┬────────────────────────────────────────────┘
                                             │ alarme exato toca (AlarmManager)
                                             ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                       ReminderAlarmReceiver & NotificationManager                       │
│  - Canal de notificação: CHANNEL_TRAVEL_REMINDERS (High Importance)                     │
│  - Notificação de saída com dados de trânsito em tempo real                             │
│  - Ações na notificação: [Ver no mapa] (navegação direta) e [Dispensar]                 │
│  - Modal Transporte Público: Ação [Ver linhas de transporte] (Google Maps Transit)      │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Novos Pacotes e Arquivos

```
app/src/main/kotlin/org/onekash/kashcal/
│
├── data/travel/
│   ├── TravelMode.kt                    # Enum dos modais: CAR, FOOT, TRANSIT
│   ├── RoutingProvider.kt               # Interface abstrata e modelo RoutingResult
│   ├── RoutingService.kt                # Orquestrador com Circuit Breaker e Cache
│   ├── RouteCache.kt                    # Cache LRU em memória com TTL de 15 min
│   ├── CircuitBreaker.kt                # Monitor de saúde e cooldown de provedores
│   ├── RushHourEstimator.kt             # Compensador de pico urbano para estimativas estáticas
│   ├── TomTomRoutingProvider.kt         # Provedor 1: Trânsito em tempo real (2.5k req/dia free)
│   ├── StadiaMapsProvider.kt            # Provedor 2: Valhalla Cloud dedicado (200k créditos)
│   ├── ValhallaDemoProvider.kt          # Provedor 3: Servidor público OSM Valhalla
│   └── LocalHaversineProvider.kt        # Provedor 4: Geodésico offline com rush hour
│
├── reminder/travel/
│   ├── TravelLocationProvider.kt        # Wrapper de FusedLocationProviderClient com coroutines
│   ├── TravelAlarmWorker.kt             # Hilt Worker responsável pelo recálculo e agendamento
│   └── TravelReminderScheduler.kt       # Gerenciador de enfileiramento e cancelamento de workers
│
└── ui/screens/settings/
    ├── TravelAlertsScreen.kt            # Painel completo de configurações da feature
    └── TravelAlertsViewModel.kt         # Estado reativo, validação de chaves e permissões
```

---

## 3. Modelo de Dados e Banco de Dados (Room v28)

### 3.1 Alterações na Tabela `events`
Adição de 3 novas colunas anuláveis em `Event.kt`:

```kotlin
@ColumnInfo(name = "travel_mode")
val travelMode: String? = null, // "CAR", "FOOT", "TRANSIT" ou null

@ColumnInfo(name = "travel_depart_buffer_min")
val travelDepartBufferMin: Int? = null, // ex: 0, 5, 10, 15, 30 ou null

@ColumnInfo(name = "travel_origin_fixed")
val travelOriginFixed: String? = null // endereço textual customizado ou null
```

### 3.2 Alterações na Tabela `scheduled_reminders`
Adição do tipo de lembrete e tempo estimado em `ScheduledReminder.kt`:

```kotlin
enum class ReminderType {
    TIME_BEFORE,   // Lembretes convencionais existentes
    TRAVEL_DEPART, // Alarme de saída imediata ("Hora de sair")
    TRAVEL_BUFFER  // Alarme antecipado de saída ("Saia em X minutos")
}

// Na data class ScheduledReminder:
@ColumnInfo(name = "reminder_type", defaultValue = "'TIME_BEFORE'")
val reminderType: ReminderType = ReminderType.TIME_BEFORE,

@ColumnInfo(name = "estimated_travel_min")
val estimatedTravelMin: Int? = null
```

> **Garantia de Unicidade**: O índice único existente em `scheduled_reminders` é:
> `Index(value = ["event_id", "occurrence_time", "reminder_offset"], unique = true)`.
> Para lembretes de viagem, o campo `reminder_offset` é populado como `"TRAVEL_DEPART"` ou `"TRAVEL_BUFFER"`, garantindo idempotência e prevenindo colisões entre si e com lembretes tradicionais ISO-8601 (ex: `"-PT15M"`).

### 3.3 Migração do Banco de Dados: `MIGRATION_27_28`
Arquivo: `app/src/main/kotlin/org/onekash/kashcal/data/db/migration/Migrations.kt`

```kotlin
val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            // 1. events
            addColumnIfNotExists(db, "events", "travel_mode", "TEXT")
            addColumnIfNotExists(db, "events", "travel_depart_buffer_min", "INTEGER")
            addColumnIfNotExists(db, "events", "travel_origin_fixed", "TEXT")

            // 2. scheduled_reminders
            addColumnIfNotExists(db, "scheduled_reminders", "reminder_type", "TEXT NOT NULL DEFAULT 'TIME_BEFORE'")
            addColumnIfNotExists(db, "scheduled_reminders", "estimated_travel_min", "INTEGER")

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
```
Atualização do `KashCalDatabase.kt` para `version = 28` e inclusão de `MIGRATION_27_28` no array de migrações.

---

## 4. Camada de Roteamento Inteligente (4 Níveis com Trânsito Real e Resiliência)

### 4.1 Modais de Transporte (`TravelMode.kt`)

```kotlin
package org.onekash.kashcal.data.travel

enum class TravelMode(
    val tomtomMode: String,
    val valhallaCosting: String,
    val speedKmh: Double,
    val tortuosityFactor: Double
) {
    CAR(
        tomtomMode = "car",
        valhallaCosting = "auto",
        speedKmh = 30.0,
        tortuosityFactor = 1.35
    ),
    FOOT(
        tomtomMode = "pedestrian",
        valhallaCosting = "pedestrian",
        speedKmh = 4.5,
        tortuosityFactor = 1.20
    ),
    TRANSIT(
        tomtomMode = "bus",
        valhallaCosting = "pedestrian",
        speedKmh = 18.0,
        tortuosityFactor = 1.50
    );

    companion object {
        fun fromString(value: String?): TravelMode =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: CAR
    }
}
```

### 4.2 Interfaces e Resultados Enriquecidos (`RoutingProvider.kt`)

```kotlin
package org.onekash.kashcal.data.travel

interface RoutingProvider {
    val name: String
    suspend fun estimate(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        mode: TravelMode
    ): ProviderResult?
}

data class ProviderResult(
    val minutes: Int,
    val trafficDelayMinutes: Int = 0,
    val hasLiveTraffic: Boolean = false
)

data class RoutingResult(
    val minutes: Int,
    val trafficDelayMinutes: Int = 0,
    val providerName: String,
    val hasLiveTraffic: Boolean,
    val isRushHourAdjusted: Boolean,
    val isEstimate: Boolean // true se calculado pelo fallback local offline
)
```

### 4.3 Provedor 1: TomTom Routing API com Trânsito Real (`TomTomRoutingProvider.kt`)
- **Vantagem Competitiva**: 2.500 requisições diárias gratuitas sem exigência de cartão de crédito.
- **Endpoint**:
  `POST https://api.tomtom.com/routing/1/calculateRoute/{originLat},{originLon}:{destLat},{destLon}/json?traffic=true&travelMode={mode.tomtomMode}&key={apiKey}`
- **Parsing de Resposta**:
  ```kotlin
  val summary = responseJson.getJSONArray("routes").getJSONObject(0).getJSONObject("summary")
  val travelTimeSec = summary.getInt("travelTimeInSeconds")
  val delaySec = summary.optInt("trafficDelayInSeconds", 0)
  ProviderResult(
      minutes = Math.ceil(travelTimeSec / 60.0).toInt(),
      trafficDelayMinutes = Math.round(delaySec / 60.0f),
      hasLiveTraffic = true
  )
  ```
- **Condição de Execução**: Executa somente se a chave TomTom estiver configurada e o provedor habilitado.

### 4.4 Provedor 2: Stadia Maps Cloud (`StadiaMapsProvider.kt`)
- **Endpoint**: `POST https://api.stadiamaps.com/route/v1`
- **Autenticação**: Query Parameter `api_key=<CHAVE>` ou Header `Authorization: api_key <CHAVE>`.
- **Parsing**: `trip.summary.time` em segundos, convertido para minutos arredondados para cima.

### 4.5 Provedor 3: Valhalla OpenStreetMap Demo (`ValhallaDemoProvider.kt`)
- **Endpoint**: `POST https://valhalla1.openstreetmap.de/route`
- **Header Obrigatório**: `X-Client-Id: kashcal-tasks-fork`.
- **Proteção por Circuit Breaker**: Se retornar HTTP 429 ou falhar duas vezes consecutivas, entra em cooldown de 15 minutos, liberando o fluxo imediatamente para o fallback local sem latência.

### 4.6 Provedor 4: Estimativa Local com Compensador de Pico (`LocalHaversineProvider.kt` & `RushHourEstimator.kt`)
- **Cálculo Base**: Fórmula de Haversine $\times$ `mode.tortuosityFactor` / `mode.speedKmh`.
- **Rush Hour Compensator**:
  - Verifica se a data/hora do deslocamento cai em horário de pico comercial (segunda a sexta, das 07:30 às 09:30 ou das 17:30 às 19:30).
  - Se `mode == CAR` e o compensador estiver ativado: aplica acréscimo de +30% ao tempo estimado de deslocamento.
  - Nunca falha; garante que mesmo em modo avião ou sem sinal o alarme tocará pontualmente.

### 4.7 Cache LRU em Memória (`RouteCache.kt`)
- Chave de cache baseada em coordenadas quantizadas (arredondamento em 3 casas decimais, ~100m de raio):
  `CacheKey(round(origLat, 3), round(origLon, 3), round(destLat, 3), round(destLon, 3), mode)`
- TTL de 15 minutos. Evita chamadas de rede redundantes para múltiplos eventos no mesmo local ou retentativas de workers.

---

## 5. Provedor de Localização (`TravelLocationProvider.kt`)

Encapsula o `FusedLocationProviderClient` do Google Play Services com suporte assíncrono do Kotlin Coroutines:
- Verifica `ACCESS_FINE_LOCATION` ou `ACCESS_COARSE_LOCATION` antes de invocar APIs nativas.
- `getCurrentLocation()`: Utiliza `Priority.PRIORITY_HIGH_ACCURACY` com timeout estrito de 8 segundos.
- `getLastKnownLocation()`: Fallback instantâneo para a última coordenada em cache do sistema Android.

---

## 6. Agendamento e Execução em Background

### 6.1 `TravelAlarmWorker.kt`
- `@HiltWorker` derivado de `CoroutineWorker` com `setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)`.
- **Fluxo de Execução**:
  1. Carrega o `Event` no banco via `eventReader.getEventById(eventId)`.
  2. Obtém coordenadas do destino (persistidas em `geoLat`/`geoLon` ou via geocoder).
  3. Obtém coordenadas de origem (geocodifica `travelOriginFixed` ou obtém via `TravelLocationProvider`).
  4. Executa `routingService.estimate(originLat, originLon, destLat, destLon, mode)`.
  5. Calcula timestamps de disparo:
     - `triggerDepart = occurrenceTs - (routingResult.minutes * 60_000L)`
     - `triggerBuffer = occurrenceTs - ((routingResult.minutes + bufferMin) * 60_000L)` (se `bufferMin > 0`)
  6. Insere `ScheduledReminder` em `scheduledRemindersDao` com `reminderType = TRAVEL_DEPART` e `TRAVEL_BUFFER`.
  7. Registra os alarmes exatos chamando `reminderScheduler.scheduleAlarm(reminder.id, reminder.triggerTime)`.

### 6.2 `TravelReminderScheduler.kt`
- Enfileira workers com delay inicial: `delay = occurrenceTs - 1_HOUR_MS - now`.
- Se a ocorrência for em menos de 1 hora: `delay = 0` (execução imediata).
- Chamado pelo `EventCoordinator` ao criar/editar eventos e pelo `ReminderRefreshWorker` na varredura diária dos próximos 7 dias.

---

## 7. Canal e Formatação das Notificações

### 7.1 Canal Dedicado
Em `ReminderNotificationChannels.kt`:
- Constante: `CHANNEL_TRAVEL_REMINDERS = "travel_departure_reminders"`
- Configuração: `IMPORTANCE_HIGH`, som, vibração e banner heads-up.

### 7.2 Customização em `ReminderNotificationManager.kt`
- **Texto Enriquecido**:
  - Com trânsito real TomTom: *"Saia agora → [Local] · est. 35 min de carro (inclui 12 min de trânsito)"*
  - Com ajuste de pico: *"Saia agora → [Local] · est. 28 min de carro (ajustado para horário de pico)"*
  - Com estimativa offline: *"Saia agora → [Local] · est. ~25 min (estimativa local)"*
- **Ações na Notificação**:
  - `action_view_map`: Dispara navegação turn-by-turn.
  - Para transporte público: `action_transit_lines` dispara `google.navigation:q=dest&mode=transit`.
  - `action_dismiss`: Descarta o alarme.

---

## 8. Interface com o Usuário (UI)

### 8.1 Formulário de Evento (`EventFormSheet.kt`)
- Visibilidade: `state.location.isNotBlank() && !state.isAllDay`.
- Componentes:
  - Switch: **Alerta de saída**.
  - Modais: [🚗 Carro] [🚶 A pé] [🚌 Transporte público].
  - Origem: [Localização atual ▾] vs [Endereço fixo] (com autocomplete).
  - Buffer antecipado: Sem aviso extra, 5 min, 10 min, 15 min, 30 min.
- Persistência: Gravação de `locationLat` e `locationLon` ao clicar em sugestão de endereço.

### 8.2 Painel de Configurações (`TravelAlertsScreen.kt`)
Acessível em **Configurações → Alertas de saída**:
1. **Apresentação e Onboarding**: Explicação de como o app calcula o tempo de saída no momento em que o alarme dispara.
2. **TomTom Routing (Trânsito em Tempo Real - Recomendado)**:
   - Destaque: 2.500 rotas/dia gratuitas, sem cartão de crédito, com dados de tráfego ao vivo.
   - Link direto para registro (`https://developer.tomtom.com`).
   - Campo para colar a chave API + botão de salvar/testar + switch liga/desliga.
3. **Stadia Maps / Servidor Dedicado (OpenStreetMap)**:
   - Opção para chave Stadia Maps (`https://stadiamaps.com/signup`).
   - Campo opcional para URL de instância própria (*self-hosted*).
4. **Valhalla Demo (Servidor Comunitário Aberto)**:
   - Switch de ativação e status do Circuit Breaker.
5. **Estimativa Local Inteligente**:
   - Switch para o "Compensador de Horário de Pico".
   - Badge "Sempre Ativo (Garantia Offline)".
6. **Permissões de Localização**: Status de `ACCESS_FINE_LOCATION` e `ACCESS_BACKGROUND_LOCATION` com botões de concessão.

---

## 9. Preferências do Usuário e Backup

### 9.1 DataStore (`PreferencesKeys.kt`)
```kotlin
// FORK:travel-time
val TRAVEL_ALERTS_TOMTOM_API_KEY = stringPreferencesKey("travel_alerts_tomtom_api_key")
val TRAVEL_ALERTS_TOMTOM_ENABLED = booleanPreferencesKey("travel_alerts_tomtom_enabled")
val TRAVEL_ALERTS_STADIA_API_KEY = stringPreferencesKey("travel_alerts_stadia_api_key")
val TRAVEL_ALERTS_STADIA_ENABLED = booleanPreferencesKey("travel_alerts_stadia_enabled")
val TRAVEL_ALERTS_VALHALLA_ENABLED = booleanPreferencesKey("travel_alerts_valhalla_enabled")
val TRAVEL_ALERTS_RUSH_HOUR_ENABLED = booleanPreferencesKey("travel_alerts_rush_hour_enabled")
```

### 9.2 Allow-list em `ExportablePreferences.kt`
As 6 novas chaves são adicionadas ao conjunto `KEYS` de `ExportablePreferences.kt` e o teste unitário `ExportablePreferencesTest` é atualizado para garantir conformidade em backups e restaurações.

---

## 10. Costuras e Pontos de Integração no Código Core (`// FORK:travel-time`)

1. **`AndroidManifest.xml`**: `<!-- FORK:travel-time -->` com permissões de localização.
2. **`EventCoordinator.kt`**: `// FORK:travel-time` em `createEvent`, `updateEvent`, etc.
3. **`ReminderRefreshWorker.kt`**: `// FORK:travel-time` em `doWork()`.
4. **`ReminderNotificationChannels.kt`**: `// FORK:travel-time` registrando canal.
5. **`ReminderNotificationManager.kt`**: `// FORK:travel-time` formatando notificações de viagem.
6. **`SettingsDestination.kt` & `SettingsRoute.kt`**: `// FORK:travel-time` adicionando `TravelAlerts(depth = 1)`.
7. **`AccountSettingsScreen.kt`**: `// FORK:travel-time` adicionando entrada no menu de configurações.
8. **`HomeViewModel.kt`**: `// FORK:travel-time` mapeando propriedades de viagem em `saveEvent()`.
