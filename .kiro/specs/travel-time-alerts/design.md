# Design Técnico — Alertas de Saída por Tempo de Viagem (Travel Time Alerts)

## 1. Visão Geral da Arquitetura

A funcionalidade é desenhada como uma **camada lateral (lateralized fork layer)** ao ecossistema de lembretes e agendamentos do KashCal, preservando a lógica existente e conectando-se exclusivamente através de costuras (*seams*) bem delimitadas e marcadas com o identificador `// FORK:travel-time`.

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
│     │ Fallback Chain:                                                            │      │
│     │ ① StadiaMapsProvider      (online, API key nas configs)                    │      │
│     │ ② ValhallaDemoProvider    (online, gratuito valhalla1.openstreetmap.de)     │      │
│     │ ③ LocalHaversineProvider  (offline, fórmula geodésica + tortuosidade)      │      │
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
│  - Notificação de saída: "Saia agora → [Local] · est. 18 min de carro"                 │
│  - Ações na notificação: [Ver no mapa] (abre navegação) e [Dispensar]                   │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Novos Pacotes e Arquivos

```
app/src/main/kotlin/org/onekash/kashcal/
│
├── data/travel/
│   ├── TravelMode.kt                    # Enum dos modais: CAR, FOOT, TRANSIT
│   ├── RoutingProvider.kt               # Interface abstrata e classe de resultado (RoutingResult)
│   ├── RoutingService.kt                # Orquestrador da cadeia de provedores com fallback
│   ├── StadiaMapsProvider.kt            # Provedor 1: API em nuvem Stadia Maps (Valhalla)
│   ├── ValhallaDemoProvider.kt          # Provedor 2: Servidor público OSM Valhalla
│   └── LocalHaversineProvider.kt        # Provedor 3: Cálculo geodésico offline
│
├── reminder/travel/
│   ├── TravelLocationProvider.kt        # Wrapper de FusedLocationProviderClient com coroutines
│   ├── TravelAlarmWorker.kt             # Hilt Worker responsável pelo recálculo e agendamento
│   └── TravelReminderScheduler.kt       # Gerenciador de enfileiramento e cancelamento de workers
│
└── ui/screens/settings/
    ├── TravelAlertsScreen.kt            # Composable dedicado das configurações da feature
    └── TravelAlertsViewModel.kt         # Gerenciamento de estado, validação de chave e permissões
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

> **Nota de unicidade**: O índice único existente em `scheduled_reminders` é:
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

## 4. Camada de Roteamento (3 Provedores com Fallback)

### 4.1 Modais de Transporte (`TravelMode.kt`)

```kotlin
package org.onekash.kashcal.data.travel

enum class TravelMode(
    val valhallaCosting: String,
    val speedKmh: Double,
    val tortuosityFactor: Double
) {
    CAR(
        valhallaCosting = "auto",
        speedKmh = 30.0,       // velocidade média urbana em trânsito
        tortuosityFactor = 1.35 // curva viária típica vs linha reta
    ),
    FOOT(
        valhallaCosting = "pedestrian",
        speedKmh = 4.5,
        tortuosityFactor = 1.20
    ),
    TRANSIT(
        valhallaCosting = "pedestrian",
        speedKmh = 18.0,       // velocidade média combinada (caminhada + ônibus/metrô)
        tortuosityFactor = 1.50
    );

    companion object {
        fun fromString(value: String?): TravelMode =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: CAR
    }
}
```

### 4.2 Interfaces e Resultados (`RoutingProvider.kt`)

```kotlin
package org.onekash.kashcal.data.travel

interface RoutingProvider {
    val name: String
    suspend fun estimateMinutes(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        mode: TravelMode
    ): Int?
}

data class RoutingResult(
    val minutes: Int,
    val providerName: String,
    val isEstimate: Boolean // true se calculado pelo fallback local offline
)
```

### 4.3 Provedor 1: Stadia Maps Cloud (`StadiaMapsProvider.kt`)
- **Endpoint**: `POST https://api.stadiamaps.com/route/v1`
- **Autenticação**: Header `Authorization: api_key <CHAVE>` ou Query Parameter `api_key=<CHAVE>`
- **Corpo da Requisição JSON**:
  ```json
  {
    "locations": [
      {"lat": -23.5505, "lon": -46.6333},
      {"lat": -23.5615, "lon": -46.6559}
    ],
    "costing": "auto",
    "directions_options": {"units": "km"}
  }
  ```
- **Parsing de Resposta**: Campo `trip.summary.time` (em segundos). Minutos = `ceil(time / 60.0)`.
- **Condição de Execução**: Executa somente se a preferência de Stadia Maps estiver habilitada e a chave configurada no DataStore for não nula e não vazia.

### 4.4 Provedor 2: Valhalla OpenStreetMap Demo (`ValhallaDemoProvider.kt`)
- **Endpoint**: `POST https://valhalla1.openstreetmap.de/route`
- **Header Obrigatório**: `X-Client-Id: kashcal-tasks-fork`
- **Corpo da Requisição JSON**: Idêntico ao Stadia Maps (mesma engine Valhalla).
- **Timeout**: 8 segundos no `OkHttpClient`. Retorna `null` imediatamente em falhas de rede, 429 ou respostas inválidas.

### 4.5 Provedor 3: Estimativa Geodésica Offline (`LocalHaversineProvider.kt`)
- Execução puramente matemática em memória:
  $$d_{\text{haversine}} = 2 R \cdot \arcsin\left(\sqrt{\sin^2\left(\frac{\Delta\phi}{2}\right) + \cos(\phi_1)\cos(\phi_2)\sin^2\left(\frac{\Delta\lambda}{2}\right)}\right)$$
  $$d_{\text{estimada}} = d_{\text{haversine}} \times \text{mode.tortuosityFactor}$$
  $$\text{minutos} = \left\lceil \frac{d_{\text{estimada}}}{\text{mode.speedKmh}} \times 60 \right\rceil$$
- Garante no mínimo 1 minuto de deslocamento se a distância for maior que 0.
- Nunca retorna `null`; marca `isEstimate = true` no `RoutingResult`.

### 4.6 Orquestrador de Rota (`RoutingService.kt`)
Avalia os provedores em sequência estrita de fallback:
1. Se Stadia ativado e chave presente: invoca `StadiaMapsProvider.estimateMinutes(...)`.
2. Se falhar ou estiver desativado: se Valhalla Demo ativado, invoca `ValhallaDemoProvider.estimateMinutes(...)`.
3. Se falhar ou estiver desativado: invoca `LocalHaversineProvider.estimateMinutes(...)`.
Retorna sempre um `RoutingResult` válido.

---

## 5. Provedor de Localização (`TravelLocationProvider.kt`)

Encapsula o `FusedLocationProviderClient` do Google Play Services com suporte assíncrono do Kotlin Coroutines:
- Verifica se `ACCESS_FINE_LOCATION` ou `ACCESS_COARSE_LOCATION` estão concedidas no runtime do Android antes de qualquer chamada.
- Método `getCurrentLocation()`:
  - Invoca `getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationToken)` com timeout máximo de 8 segundos.
- Método `getLastKnownLocation()`:
  - Fallback imediato para `lastLocation` em caso de perda temporária de sinal GPS.

---

## 6. Agendamento e Execução em Background

### 6.1 `TravelAlarmWorker.kt`
- `@HiltWorker` derivado de `CoroutineWorker` com `setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)`.
- Parâmetros recebidos em `inputData`:
  - `KEY_EVENT_ID: Long`
  - `KEY_OCCURRENCE_TS: Long`
- **Fluxo de Execução**:
  1. Carrega o `Event` no banco via `eventReader.getEventById(eventId)`.
  2. Obtém coordenadas do destino:
     - `destLat = event.geoLat ?: geocode(event.location).latitude`
     - `destLon = event.geoLon ?: geocode(event.location).longitude`
  3. Obtém coordenadas de origem:
     - Se `event.travelOriginFixed != null`: geocodifica o endereço fixo.
     - Caso contrário: consulta `travelLocationProvider.getCurrentLocation()` com fallback para `getLastKnownLocation()`.
  4. Caso as coordenadas não sejam resolvidas: aciona agendamento estático de contingência com 30 minutos de viagem estimada.
  5. Consulta `routingService.estimate(originLat, originLon, destLat, destLon, mode)`.
  6. Calcula timestamps de disparo:
     - `triggerDepart = occurrenceTs - (routingResult.minutes * 60_000L)`
     - `triggerBuffer = occurrenceTs - ((routingResult.minutes + bufferMin) * 60_000L)` (se `bufferMin > 0`)
  7. Persiste `ScheduledReminder` em `scheduledRemindersDao` com `reminderType = TRAVEL_DEPART` e `TRAVEL_BUFFER`.
  8. Registra o alarme exato no sistema chamando `reminderScheduler.scheduleAlarm(reminder.id, reminder.triggerTime)`.

### 6.2 `TravelReminderScheduler.kt`
- Responsável por enfileirar o `TravelAlarmWorker` para ocorrências futuras:
  - Calcula o atraso inicial: `initialDelay = occurrenceTs - 1_HOUR_MS - System.currentTimeMillis()`.
  - Se faltar menos de 1 hora para o evento: `initialDelay = 0` (executa imediatamente).
  - Enfileira via `WorkManager.enqueueUniqueWork("travel_alarm_${eventId}_${occurrenceTs}", ExistingWorkPolicy.REPLACE, workRequest)`.
- Método `scheduleUpcomingTravelAlarms()`:
  - Consulta no banco todos os eventos ativos com `travel_mode != null` e ocorrências nos próximos 7 dias e assegura que seus workers estejam devidamente agendados.
- Método `cancelForEvent(eventId: Long)`:
  - Cancela os workers agendados pelo padrão de tag ou prefixo.

---

## 7. Canal e Formatação das Notificações

### 7.1 Canal Dedicado
Em `ReminderNotificationChannels.kt`:
- Constante: `CHANNEL_TRAVEL_REMINDERS = "travel_departure_reminders"`
- Configurações do canal:
  - Nome: "Alertas de saída" (`R.string.channel_travel_reminders`)
  - Descrição: "Avisos sobre a hora de sair para eventos com local definido" (`R.string.channel_travel_reminders_desc`)
  - Importância: `NotificationManager.IMPORTANCE_HIGH`
  - Som, vibração ativa e cor de luz azul.

### 7.2 Customização em `ReminderNotificationManager.kt`
Intercepta `reminder.reminderType`:
- Para `TRAVEL_DEPART`:
  - Se `isEstimate == false`: *"Saia agora → [Local] · est. 18 min de carro"*
  - Se `isEstimate == true`: *"Saia agora → [Local] · est. ~20 min (estimativa local)"*
- Para `TRAVEL_BUFFER`:
  - *"Saia em 15 min → [Local] · est. 18 min de carro"*
- **Ações na Notificação**:
  - `action_view_map`: Dispara `Intent(Intent.ACTION_VIEW, Uri.parse("geo:destLat,destLon?q=destLat,destLon(Local)"))` ou intent para o Google Maps / app de navegação.
  - `action_dismiss`: Descarta o alarme.

---

## 8. Interface com o Usuário (UI)

### 8.1 Seção no Formulário de Evento (`EventFormSheet.kt`)
Localizada logo abaixo do campo de Localização:
- Condição de visibilidade: `state.location.isNotBlank() && !state.isAllDay`.
- Componentes:
  - Switch: **Alerta de saída**.
  - Dropdown/Chips de Modal: [🚗 Carro] [🚶 A pé] [🚌 Transporte público].
  - Campo "Sair de": Seletor entre **"Localização atual"** e **"Endereço fixo"** (com autocomplete idêntico ao campo de local).
  - Dropdown "Buffer": "Sem aviso extra", "5 min antes", "10 min antes", "15 min antes", "30 min antes".
- No autocomplete de destino: quando o usuário clica em uma `AddressSuggestion`, armazena `suggestion.latitude` em `locationLat` e `suggestion.longitude` em `locationLon`.
- Ao salvar o evento (`HomeViewModel.saveEvent`): repassa as novas propriedades para a entidade `Event`.

### 8.2 Tela de Configurações da Feature (`TravelAlertsScreen.kt`)
Acessível a partir de **Configurações → Alertas de saída**:
1. **Cabeçalho e Apresentação Detalhada**:
   - Texto educativo explicando o valor e a dinâmica do cálculo em tempo de disparo.
2. **Cadeia de Provedores de Rota**:
   - **Cartão Stadia Maps**:
     - Status: Ativo / Inativo / Chave não configurada.
     - Guia detalhado passo a passo de como obter a chave gratuita em `https://stadiamaps.com/signup`.
     - Campo de entrada para a chave de API (com visualização/ocultação e salvamento no DataStore).
     - Switch para habilitar/desabilitar.
   - **Cartão Valhalla OpenStreetMap**:
     - Status: Ativo por padrão.
     - Descrição do servidor público aberto mantido pela comunidade.
     - Switch para habilitar/desabilitar.
   - **Cartão Estimativa Local (Haversine)**:
     - Status: Sempre Ativo (Garantia de fallback offline).
     - Descrição do cálculo geodésico sem internet.
3. **Gerenciamento de Permissões de Localização**:
   - Cartão com indicadores de status para `ACCESS_FINE_LOCATION` e `ACCESS_BACKGROUND_LOCATION`.
   - Botão interativo para concessão direta de permissões ou encaminhamento às configurações do sistema Android.

---

## 9. Preferências do Usuário e Sistema de Backup

### 9.1 DataStore (`PreferencesKeys.kt`)
Novas chaves adicionadas:
```kotlin
val TRAVEL_ALERTS_STADIA_API_KEY = stringPreferencesKey("travel_alerts_stadia_api_key")
val TRAVEL_ALERTS_STADIA_ENABLED = booleanPreferencesKey("travel_alerts_stadia_enabled")
val TRAVEL_ALERTS_VALHALLA_ENABLED = booleanPreferencesKey("travel_alerts_valhalla_enabled")
```

### 9.2 Allow-list em `ExportablePreferences.kt`
As chaves acima devem ser incluídas no conjunto `KEYS` de `ExportablePreferences.kt` e o tamanho total atualizado no teste unitário `ExportablePreferencesTest`, garantindo a integridade dos backups do usuário.

---

## 10. Costuras e Pontos de Integração no Código Core (`// FORK:travel-time`)

1. **`AndroidManifest.xml`**:
   `<!-- FORK:travel-time -->` adicionando permissões `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION` e `FOREGROUND_SERVICE_LOCATION`.
2. **`EventCoordinator.kt`**:
   `// FORK:travel-time` em `createEvent()` e `updateEvent()` chamando `travelReminderScheduler.scheduleForEvent(result, occurrences)`.
3. **`ReminderRefreshWorker.kt`**:
   `// FORK:travel-time` em `doWork()` chamando `travelReminderScheduler.scheduleUpcomingTravelAlarms()`.
4. **`ReminderNotificationChannels.kt`**:
   `// FORK:travel-time` criando `CHANNEL_TRAVEL_REMINDERS`.
5. **`ReminderNotificationManager.kt`**:
   `// FORK:travel-time` adicionando branch para `TRAVEL_DEPART` e `TRAVEL_BUFFER`.
6. **`SettingsDestination.kt` & `SettingsRoute.kt`**:
   `// FORK:travel-time` adicionando `TravelAlerts(depth = 1)` e renderizando `TravelAlertsScreen`.
7. **`AccountSettingsScreen.kt`**:
   `// FORK:travel-time` adicionando item de menu para abrir `TravelAlertsScreen`.
8. **`HomeViewModel.kt`**:
   `// FORK:travel-time` mapeando campos de viagem em `saveEvent()`.
