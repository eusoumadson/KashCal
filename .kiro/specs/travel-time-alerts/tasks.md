# Tarefas de Implementação — Alertas de Saída por Tempo de Viagem (Travel Time Alerts)

> **Orientações para o Agente Executor:**
> - Esta branch (`feature/travel-time-alerts`) já está sincronizada e baseada em `fork/main` (commit `9b6416ca`), contendo a infraestrutura do fork "KashCal Tasks".
> - Siga a convenção de isolamento lateral: todo código novo deve residir nos pacotes dedicados (`travel/`), e qualquer alteração em arquivos existentes do core deve ser marcada com o comentário `// FORK:travel-time` (ou `<!-- FORK:travel-time -->` no XML).
> - Execute e valide os testes com `./gradlew testDebugUnitTest` a cada fase concluída.

---

## Fase 1 — Dependências, Permissões e Strings

- [ ] **Tarefa 1.1 — Adicionar dependência do Google Play Services Location**
  - **Arquivos**: `gradle/libs.versions.toml`, `app/build.gradle.kts`
  - **Ação**:
    - Em `gradle/libs.versions.toml`:
      - Na seção `[versions]`: adicionar `play-services-location = "21.3.0"`.
      - Na seção `[libraries]`: adicionar `play-services-location = { group = "com.google.android.gms", name = "play-services-location", version.ref = "play-services-location" }`.
    - Em `app/build.gradle.kts`:
      - Adicionar `implementation(libs.play.services.location) // FORK:travel-time`.
  - **Verificação**: Executar `./gradlew compileDebugKotlin` e confirmar resolução sem erros.

- [ ] **Tarefa 1.2 — Adicionar permissões no AndroidManifest.xml**
  - **Arquivo**: `app/src/main/AndroidManifest.xml`
  - **Ação**:
    - Inserir sob `<!-- FORK:travel-time -->`:
      ```xml
      <!-- FORK:travel-time: permissões para cálculo dinâmico de trajeto de saída -->
      <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
      <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
      <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
      <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
      ```
  - **Verificação**: Executar `./gradlew processDebugMainManifest`.

- [ ] **Tarefa 1.3 — Adicionar recursos de Strings (Inglês e Português do Brasil)**
  - **Arquivos**:
    - `app/src/main/res/values/strings.xml`
    - `app/src/main/res/values-pt-rBR/strings.xml`
    - `app/src/main/res/values-pt/strings.xml`
  - **Ação**:
    - Adicionar chaves para:
      - Título da feature: "Departure Alerts" / "Alertas de saída"
      - Subtítulo nas configurações: "Time to leave alerts based on real-time location" / "Notificações na hora de sair baseadas na sua localização"
      - Toggle de ativação no formulário: "Departure alert" / "Alerta de saída"
      - Modais: Carro ("Car"), A pé ("On foot"), Transporte Público ("Public transit")
      - Origem: "Current location" / "Localização atual", "Fixed address" / "Endereço fixo"
      - Opções de Buffer: "No extra buffer" / "Sem aviso extra", "5 min before" / "5 min antes", "10 min before" / "10 min antes", "15 min before" / "15 min antes", "30 min before" / "30 min antes"
      - Textos da notificação: "Leave now → %1$s · est. %2$d min by %3$s" / "Saia agora → %1$s · est. %2$d min de %3$s", "Leave in %1$d min → %2$s · est. %3$d min by %4$s" / "Saia em %1$d min → %2$s · est. %3$d min de %4$s"
      - Ações da notificação: "View on map" / "Ver no mapa", "Dismiss" / "Dispensar"
      - Configurações do Stadia Maps, links de cadastro, explicação passo a passo, status dos provedores, e textos explicativos completos da funcionalidade.
  - **Verificação**: Compilar recursos e validar formatação dos placeholders (`%1$s`, `%2$d`).

---

## Fase 2 — Banco de Dados Room (Migração 27 → 28) e Entidades

- [ ] **Tarefa 2.1 — Atualizar a entidade `Event`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/data/db/entity/Event.kt`
  - **Ação**:
    - Adicionar ao final da data class (com valores padrão para retrocompatibilidade):
      ```kotlin
      // FORK:travel-time
      @ColumnInfo(name = "travel_mode")
      val travelMode: String? = null,

      @ColumnInfo(name = "travel_depart_buffer_min")
      val travelDepartBufferMin: Int? = null,

      @ColumnInfo(name = "travel_origin_fixed")
      val travelOriginFixed: String? = null,
      ```

- [ ] **Tarefa 2.2 — Adicionar `ReminderType` e atualizar `ScheduledReminder`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/data/db/entity/ScheduledReminder.kt`
  - **Ação**:
    - Definir `enum class ReminderType { TIME_BEFORE, TRAVEL_DEPART, TRAVEL_BUFFER }`.
    - Adicionar campos à data class:
      ```kotlin
      // FORK:travel-time
      @ColumnInfo(name = "reminder_type", defaultValue = "'TIME_BEFORE'")
      val reminderType: ReminderType = ReminderType.TIME_BEFORE,

      @ColumnInfo(name = "estimated_travel_min")
      val estimatedTravelMin: Int? = null,
      ```

- [ ] **Tarefa 2.3 — Implementar a migração `MIGRATION_27_28`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/data/db/migration/Migrations.kt`
  - **Ação**:
    - Adicionar objeto `MIGRATION_27_28`:
      ```kotlin
      // FORK:travel-time
      val MIGRATION_27_28 = object : Migration(27, 28) {
          override fun migrate(db: SupportSQLiteDatabase) {
              db.beginTransaction()
              try {
                  addColumnIfNotExists(db, "events", "travel_mode", "TEXT")
                  addColumnIfNotExists(db, "events", "travel_depart_buffer_min", "INTEGER")
                  addColumnIfNotExists(db, "events", "travel_origin_fixed", "TEXT")

                  addColumnIfNotExists(db, "scheduled_reminders", "reminder_type", "TEXT NOT NULL DEFAULT 'TIME_BEFORE'")
                  addColumnIfNotExists(db, "scheduled_reminders", "estimated_travel_min", "INTEGER")

                  db.setTransactionSuccessful()
              } finally {
                  db.endTransaction()
              }
          }
      }
      ```
    - Incluir `MIGRATION_27_28` na lista `ALL_MIGRATIONS` em `Migrations.kt`.

- [ ] **Tarefa 2.4 — Incrementar versão do banco no `KashCalDatabase`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/data/db/KashCalDatabase.kt`
  - **Ação**:
    - Alterar `version = 27` para `version = 28`.
  - **Verificação**: Rodar `./gradlew kspDebugKotlin` para gerar o arquivo de schema `app/schemas/org.onekash.kashcal.data.db.KashCalDatabase/28.json`.

- [ ] **Tarefa 2.5 — Criar teste unitário da migração Room**
  - **Arquivo**: `app/src/test/kotlin/org/onekash/kashcal/data/db/migration/Migration27To28Test.kt`
  - **Ação**:
    - Validar que um banco na versão 27 com eventos e reminders existentes migra com sucesso para a versão 28 preservando todos os dados e aplicando os valores default corretos.
  - **Verificação**: Executar `./gradlew testDebugUnitTest --tests "*.Migration27To28Test"`.

---

## Fase 3 — Preferências DataStore e Backup

- [ ] **Tarefa 3.1 — Registrar chaves em `PreferencesKeys`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/data/preferences/PreferencesKeys.kt`
  - **Ação**:
    - Inserir seção `// FORK:travel-time`:
      ```kotlin
      val TRAVEL_ALERTS_STADIA_API_KEY = stringPreferencesKey("travel_alerts_stadia_api_key")
      val TRAVEL_ALERTS_STADIA_ENABLED = booleanPreferencesKey("travel_alerts_stadia_enabled")
      val TRAVEL_ALERTS_VALHALLA_ENABLED = booleanPreferencesKey("travel_alerts_valhalla_enabled")
      ```

- [ ] **Tarefa 3.2 — Atualizar allow-list em `ExportablePreferences`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/domain/backup/ExportablePreferences.kt`
  - **Ação**:
    - Adicionar as 3 chaves ao conjunto `KEYS`.
    - Incrementar o contador de expectativa de chaves e atualizar a asserção no teste unitário `ExportablePreferencesTest.kt`.
  - **Verificação**: Executar `./gradlew testDebugUnitTest --tests "*.ExportablePreferencesTest"`.

- [ ] **Tarefa 3.3 — Criar métodos de acesso em `KashCalDataStore`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/data/preferences/KashCalDataStore.kt`
  - **Ação**:
    - Adicionar flows e métodos suspend para leitura/gravação de:
      - `travelStadiaApiKey: Flow<String?>` e `setTravelStadiaApiKey(key: String?)`
      - `travelStadiaEnabled: Flow<Boolean>` (padrão true) e `setTravelStadiaEnabled(enabled: Boolean)`
      - `travelValhallaEnabled: Flow<Boolean>` (padrão true) e `setTravelValhallaEnabled(enabled: Boolean)`

---

## Fase 4 — Mecanismo de Roteamento (3 Provedores + Fallback)

- [ ] **Tarefa 4.1 — Criar `TravelMode.kt`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/data/travel/TravelMode.kt`
  - **Ação**:
    - Implementar enum `TravelMode` (`CAR`, `FOOT`, `TRANSIT`) com propriedades `valhallaCosting`, `speedKmh`, `tortuosityFactor` e helper `fromString()`.

- [ ] **Tarefa 4.2 — Criar interface `RoutingProvider` e modelo `RoutingResult`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/data/travel/RoutingProvider.kt`
  - **Ação**:
    - Definir `interface RoutingProvider { val name: String; suspend fun estimateMinutes(...): Int? }`.
    - Definir `data class RoutingResult(val minutes: Int, val providerName: String, val isEstimate: Boolean)`.

- [ ] **Tarefa 4.3 — Implementar `StadiaMapsProvider.kt` (Provedor 1)**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/data/travel/StadiaMapsProvider.kt`
  - **Ação**:
    - Injetar `OkHttpClient`, `@IoDispatcher CoroutineDispatcher` e `KashCalDataStore`.
    - Montar requisição JSON para `https://api.stadiamaps.com/route/v1` com `api_key`.
    - Fazer parse de `trip.summary.time` em segundos, converter para minutos arredondados para cima.
    - Se a chave for nula/vazia, retornar `null` imediatamente sem efetuar chamada HTTP.

- [ ] **Tarefa 4.4 — Implementar `ValhallaDemoProvider.kt` (Provedor 2)**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/data/travel/ValhallaDemoProvider.kt`
  - **Ação**:
    - Montar requisição JSON para `https://valhalla1.openstreetmap.de/route`.
    - Incluir cabeçalho `X-Client-Id: kashcal-tasks-fork`.
    - Definir timeout de 8 segundos no OkHttp. Tratar erros de rede retornando `null`.

- [ ] **Tarefa 4.5 — Implementar `LocalHaversineProvider.kt` (Provedor 3 - Fallback Offline)**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/data/travel/LocalHaversineProvider.kt`
  - **Ação**:
    - Implementar fórmula geodésica de Haversine para cálculo de distância entre duas coordenadas.
    - Multiplicar pela taxa de tortuosidade (`mode.tortuosityFactor`) e dividir pela velocidade média (`mode.speedKmh`).
    - Garantir retorno de no mínimo 1 minuto se distância > 0. Nunca lança exceção e nunca retorna `null`.

- [ ] **Tarefa 4.6 — Implementar `RoutingService.kt`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/data/travel/RoutingService.kt`
  - **Ação**:
    - Injetar os 3 provedores e o `KashCalDataStore`.
    - Orquestrar a cadeia de execução:
      1. Tentar `StadiaMapsProvider` (se habilitado e chave configurada).
      2. Tentar `ValhallaDemoProvider` (se habilitado).
      3. Fallback final para `LocalHaversineProvider`.
    - Retornar sempre `RoutingResult`.

- [ ] **Tarefa 4.7 — Criar testes unitários para a camada de roteamento**
  - **Arquivos**:
    - `app/src/test/kotlin/org/onekash/kashcal/data/travel/LocalHaversineProviderTest.kt`
    - `app/src/test/kotlin/org/onekash/kashcal/data/travel/StadiaMapsProviderTest.kt`
    - `app/src/test/kotlin/org/onekash/kashcal/data/travel/ValhallaDemoProviderTest.kt`
    - `app/src/test/kotlin/org/onekash/kashcal/data/travel/RoutingServiceTest.kt`
  - **Verificação**: Executar `./gradlew testDebugUnitTest --tests "org.onekash.kashcal.data.travel.*"`.

---

## Fase 5 — Provedor de Localização

- [ ] **Tarefa 5.1 — Implementar `TravelLocationProvider.kt`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/reminder/travel/TravelLocationProvider.kt`
  - **Ação**:
    - Injetar `@ApplicationContext context: Context` e `@IoDispatcher dispatcher: CoroutineDispatcher`.
    - Inicializar `FusedLocationProviderClient`.
    - Implementar checagem de permissões antes de chamar as APIs do Google Play Services.
    - Implementar `suspend fun getCurrentLocation(): Pair<Double, Double>?` com `Priority.PRIORITY_HIGH_ACCURACY` e `withTimeoutOrNull(8_000)`.
    - Implementar `suspend fun getLastKnownLocation(): Pair<Double, Double>?` como fallback rápido.

- [ ] **Tarefa 5.2 — Testes unitários do provedor de localização**
  - **Arquivo**: `app/src/test/kotlin/org/onekash/kashcal/reminder/travel/TravelLocationProviderTest.kt`
  - **Verificação**: Validar comportamento com permissão concedida e negada.

---

## Fase 6 — Worker e Scheduler (WorkManager + AlarmManager)

- [ ] **Tarefa 6.1 — Implementar `TravelAlarmWorker.kt`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/reminder/travel/TravelAlarmWorker.kt`
  - **Ação**:
    - `@HiltWorker` herdando de `CoroutineWorker`.
    - Obter `eventId` e `occurrenceTs` dos parâmetros de entrada.
    - Carregar o `Event` correspondente.
    - Resolver destino: `event.geoLat` / `event.geoLon` ou geocodificar `event.location` via `LocationSuggestionService`.
    - Resolver origem: geocodificar `event.travelOriginFixed` ou obter coordenadas de `travelLocationProvider.getCurrentLocation()`.
    - Consultar `routingService.estimate(...)`.
    - Calcular `triggerDepart = occurrenceTs - (routingResult.minutes * 60_000L)`.
    - Calcular `triggerBuffer = occurrenceTs - ((routingResult.minutes + bufferMin) * 60_000L)` se `bufferMin > 0`.
    - Persistir em `scheduledRemindersDao` com `reminderType = TRAVEL_DEPART` e `TRAVEL_BUFFER`.
    - Armar alarmes exatos chamando `reminderScheduler.scheduleAlarm(reminder.id, reminder.triggerTime)`.
    - Fallback de contingência caso localização falhe totalmente.

- [ ] **Tarefa 6.2 — Implementar `TravelReminderScheduler.kt`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/reminder/travel/TravelReminderScheduler.kt`
  - **Ação**:
    - Injetar `WorkManager`, `EventReader` e `OccurrencesDao`.
    - Implementar `scheduleForEvent(event: Event, occurrences: List<Occurrence>)`:
      - Para cada ocorrência futura com `event.travelMode != null`:
        - Calcular `delay = occurrence.startTs - 1_HOUR_MS - now`. Se `delay < 0`, `delay = 0`.
        - Enfileirar via `workManager.enqueueUniqueWork("travel_alarm_${event.id}_${occurrence.startTs}", ExistingWorkPolicy.REPLACE, workRequest)`.
    - Implementar `scheduleUpcomingTravelAlarms()`:
      - Consultar ocorrências nos próximos 7 dias para eventos com alertas de saída ativados.
    - Implementar `cancelForEvent(eventId: Long)`.

- [ ] **Tarefa 6.3 — Conectar o scheduler ao `EventCoordinator.kt`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/domain/coordinator/EventCoordinator.kt`
  - **Ação**:
    - Injetar `travelReminderScheduler: TravelReminderScheduler`.
    - Em `createEvent()`, `updateEvent()`, `editSingleOccurrence()` e `editThisAndFuture()`:
      - Adicionar chamada:
        ```kotlin
        // FORK:travel-time
        travelReminderScheduler.scheduleForEvent(result, occurrences)
        ```
    - Em exclusão de eventos: chamar `travelReminderScheduler.cancelForEvent(eventId)`.

- [ ] **Tarefa 6.4 — Conectar a varredura ao `ReminderRefreshWorker.kt`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/reminder/worker/ReminderRefreshWorker.kt`
  - **Ação**:
    - Injetar `travelReminderScheduler: TravelReminderScheduler`.
    - Em `doWork()`, logo após a rotina de device calendar reminders:
      ```kotlin
      // FORK:travel-time
      try {
          travelReminderScheduler.scheduleUpcomingTravelAlarms()
      } catch (e: CancellationException) {
          throw e
      } catch (e: Exception) {
          Log.w(TAG, "Travel alarm scheduling failed, continuing", e)
      }
      ```

- [ ] **Tarefa 6.5 — Testes unitários do Worker e Scheduler**
  - **Arquivos**:
    - `app/src/test/kotlin/org/onekash/kashcal/reminder/travel/TravelAlarmWorkerTest.kt`
    - `app/src/test/kotlin/org/onekash/kashcal/reminder/travel/TravelReminderSchedulerTest.kt`
  - **Verificação**: Executar `./gradlew testDebugUnitTest --tests "org.onekash.kashcal.reminder.travel.*"`.

---

## Fase 7 — Notificações de Saída Customizadas

- [ ] **Tarefa 7.1 — Criar canal de notificações em `ReminderNotificationChannels.kt`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/reminder/notification/ReminderNotificationChannels.kt`
  - **Ação**:
    - Adicionar constante `CHANNEL_TRAVEL_REMINDERS = "travel_departure_reminders" // FORK:travel-time`.
    - Em `createChannels()`: registrar canal com `IMPORTANCE_HIGH`, luzes e vibração.

- [ ] **Tarefa 7.2 — Customizar notificação em `ReminderNotificationManager.kt`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/reminder/notification/ReminderNotificationManager.kt`
  - **Ação**:
    - Em `buildNotification()`: adicionar interceptação para `reminder.reminderType`:
      ```kotlin
      // FORK:travel-time
      if (reminder.reminderType == ReminderType.TRAVEL_DEPART ||
          reminder.reminderType == ReminderType.TRAVEL_BUFFER) {
          return buildTravelNotification(reminder)
      }
      ```
    - Implementar método privado `buildTravelNotification(reminder: ScheduledReminder)`:
      - Utiliza o canal `CHANNEL_TRAVEL_REMINDERS`.
      - Texto de acordo com o modal e tempo estimado.
      - Ação rápida "Ver no mapa": cria `PendingIntent` disparando Intent de navegação com as coordenadas de destino.
      - Ação "Dispensar".

- [ ] **Tarefa 7.3 — Teste unitário de notificações**
  - **Arquivo**: `app/src/test/kotlin/org/onekash/kashcal/reminder/notification/ReminderNotificationManagerTravelTest.kt`
  - **Verificação**: Validar textos, canal e ações das notificações de saída.

---

## Fase 8 — Tela Dedicada de Configurações ("Alertas de Saída")

- [ ] **Tarefa 8.1 — Criar `TravelAlertsViewModel.kt`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/ui/screens/settings/TravelAlertsViewModel.kt`
  - **Ação**:
    - Injetar `KashCalDataStore`.
    - Expor `StateFlow` contendo:
      - Chave API Stadia Maps salva.
      - Status habilitado/desabilitado do Stadia Maps.
      - Status habilitado/desabilitado do Valhalla Demo.
      - Estado das permissões de localização do dispositivo.
    - Implementar métodos:
      - `onSetStadiaApiKey(key: String)`
      - `onToggleStadia(enabled: Boolean)`
      - `onToggleValhalla(enabled: Boolean)`
      - `onRefreshPermissions()`

- [ ] **Tarefa 8.2 — Criar `TravelAlertsScreen.kt`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/ui/screens/settings/TravelAlertsScreen.kt`
  - **Ação**:
    - Montar layout com `Scaffold` e `SettingsTopAppBar`.
    - Card Educativo: Explicação aprofundada de como o alerta funciona dinamicamente no momento do disparo.
    - Seção de Provedores em cascata:
      - **Stadia Maps**: Instruções detalhadas para obter a chave gratuita no site `https://stadiamaps.com/signup`, campo de texto com botão de salvar e toggle.
      - **Valhalla OSM**: Descrição e toggle.
      - **Estimativa Local**: Descrição e badge fixo de "Sempre Ativo (Offline)".
    - Seção de Permissões de Localização: Indicadores visuais e botão para solicitar permissão em tempo de execução.

- [ ] **Tarefa 8.3 — Integrar rota em `SettingsDestination.kt` e `SettingsRoute.kt`**
  - **Arquivos**:
    - `app/src/main/kotlin/org/onekash/kashcal/ui/screens/settings/SettingsDestination.kt`
    - `app/src/main/kotlin/org/onekash/kashcal/ui/screens/SettingsRoute.kt`
  - **Ação**:
    - Em `SettingsDestination`: adicionar `TravelAlerts(depth = 1) // FORK:travel-time`.
    - Em `SettingsRoute.kt`: adicionar `TravelAlerts` no `AnimatedContent` de transição de telas.
    - Atualizar `SettingsDestinationTest.kt`.

- [ ] **Tarefa 8.4 — Adicionar item em `AccountSettingsScreen.kt`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/ui/screens/AccountSettingsScreen.kt`
  - **Ação**:
    - Adicionar `SettingsRow` para "Alertas de saída" apontando para a nova tela.

---

## Fase 9 — Integração no Formulário de Evento (`EventFormSheet`)

- [ ] **Tarefa 9.1 — Atualizar `EventFormState`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/ui/components/EventFormSheet.kt`
  - **Ação**:
    - Adicionar campos:
      ```kotlin
      // FORK:travel-time
      val locationLat: Double? = null,
      val locationLon: Double? = null,
      val travelMode: TravelMode? = null,
      val travelBufferMin: Int = 0,
      val travelOriginFixed: String? = null,
      ```

- [ ] **Tarefa 9.2 — Capturar latitude e longitude no autocomplete de endereço**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/ui/components/EventFormSheet.kt`
  - **Ação**:
    - Ao selecionar uma `AddressSuggestion`, gravar:
      ```kotlin
      // FORK:travel-time
      state = state.copy(
          location = suggestion.displayName,
          locationLat = suggestion.latitude,
          locationLon = suggestion.longitude
      )
      ```
    - Ao digitar manualmente novo texto de endereço, redefinir `locationLat = null` e `locationLon = null`.

- [ ] **Tarefa 9.3 — Adicionar componente visual da seção "Alerta de saída"**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/ui/components/EventFormSheet.kt`
  - **Ação**:
    - Inserir logo abaixo da linha de localização.
    - Condição: `state.location.isNotBlank() && !state.isAllDay`.
    - Componentes: Switch liga/desliga, seletor de modais (Carro, A pé, Transporte público), seletor de origem ("Localização atual" vs "Endereço fixo") e menu dropdown de buffer extra (0, 5, 10, 15, 30 min).

- [ ] **Tarefa 9.4 — Persistir campos no `HomeViewModel.saveEvent()`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/ui/viewmodels/HomeViewModel.kt`
  - **Ação**:
    - Ao construir/copiar o `Event`, repassar:
      ```kotlin
      // FORK:travel-time
      geoLat = formState.locationLat ?: master.geoLat,
      geoLon = formState.locationLon ?: master.geoLon,
      travelMode = formState.travelMode?.name,
      travelDepartBufferMin = formState.travelBufferMin.takeIf { it > 0 },
      travelOriginFixed = formState.travelOriginFixed,
      ```

- [ ] **Tarefa 9.5 — Solicitação gradual de permissões na UI**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/ui/components/EventFormSheet.kt`
  - **Ação**:
    - Exibir diálogo explicativo amigável caso o usuário ative o alerta de saída com origem "Localização atual" e ainda não tenha concedido permissão de localização.

---

## Fase 10 — Verificação, Testes e Documentação Final

- [ ] **Tarefa 10.1 — Validação completa da suíte de testes unitários**
  - **Comando**: `./gradlew testDebugUnitTest`
  - **Ação**: Garantir que 100% dos testes unitários passem sem regressões.

- [ ] **Tarefa 10.2 — Validação da compilação de build APK**
  - **Comando**: `./gradlew assembleDebug`
  - **Ação**: Garantir que não existam erros de compilação, problemas com o Hilt ou conflitos de recursos.

- [ ] **Tarefa 10.3 — Documentar o relatório de integração do fork**
  - **Arquivo**: `FORK_TRAVEL_TIME_INTEGRATION.md`
  - **Ação**: Criar mapa completo de integração documentando todos os arquivos novos e os pontos de costura (`// FORK:travel-time`) para facilitar futuros rebases com o upstream do KashCal.
