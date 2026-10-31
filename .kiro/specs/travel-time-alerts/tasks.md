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
      - Toggle no formulário: "Departure alert" / "Alerta de saída"
      - Modais: Carro ("Car"), A pé ("On foot"), Transporte Público ("Public transit")
      - Origem: "Current location" / "Localização atual", "Fixed address" / "Endereço fixo"
      - Opções de Buffer: "No extra buffer", "5 min before", "10 min before", "15 min before", "30 min before"
      - Textos enriquecidos da notificação:
        - "Leave now → %1$s · est. %2$d min by %3$s (incl. %4$d min traffic delay)" / "Saia agora → %1$s · est. %2$d min de %3$s (inclui %4$d min de trânsito)"
        - "Leave now → %1$s · est. %2$d min by %3$s (rush hour adjusted)" / "Saia agora → %1$s · est. %2$d min de %3$s (ajustado para horário de pico)"
        - "Leave in %1$d min → %2$s · est. %3$d min by %4$s" / "Saia em %1$d min → %2$s · est. %3$d min de %4$s"
      - Ações da notificação: "View on map" / "Ver no mapa", "Transit lines" / "Linhas de transporte", "Dismiss" / "Dispensar"
      - Textos do TomTom (2.500 req/dia, trânsito real, link de cadastro), Stadia Maps, Valhalla OSM, Estimativa Local com Compensador de Pico, e status de permissões.
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
    - Validar que um banco na versão 27 com eventos e reminders existentes migra com sucesso para a versão 28 preservando dados e defaults.
  - **Verificação**: Executar `./gradlew testDebugUnitTest --tests "*.Migration27To28Test"`.

---

## Fase 3 — Preferências DataStore e Backup

- [ ] **Tarefa 3.1 — Registrar chaves em `PreferencesKeys`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/data/preferences/PreferencesKeys.kt`
  - **Ação**:
    - Inserir seção `// FORK:travel-time`:
      ```kotlin
      val TRAVEL_ALERTS_TOMTOM_API_KEY = stringPreferencesKey("travel_alerts_tomtom_api_key")
      val TRAVEL_ALERTS_TOMTOM_ENABLED = booleanPreferencesKey("travel_alerts_tomtom_enabled")
      val TRAVEL_ALERTS_STADIA_API_KEY = stringPreferencesKey("travel_alerts_stadia_api_key")
      val TRAVEL_ALERTS_STADIA_ENABLED = booleanPreferencesKey("travel_alerts_stadia_enabled")
      val TRAVEL_ALERTS_VALHALLA_ENABLED = booleanPreferencesKey("travel_alerts_valhalla_enabled")
      val TRAVEL_ALERTS_RUSH_HOUR_ENABLED = booleanPreferencesKey("travel_alerts_rush_hour_enabled")
      ```

- [ ] **Tarefa 3.2 — Atualizar allow-list em `ExportablePreferences`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/domain/backup/ExportablePreferences.kt`
  - **Ação**:
    - Adicionar as 6 novas chaves ao conjunto `KEYS`.
    - Atualizar a contagem esperada e a asserção no teste unitário `ExportablePreferencesTest.kt`.
  - **Verificação**: Executar `./gradlew testDebugUnitTest --tests "*.ExportablePreferencesTest"`.

- [ ] **Tarefa 3.3 — Criar métodos de acesso em `KashCalDataStore`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/data/preferences/KashCalDataStore.kt`
  - **Ação**:
    - Implementar flows e métodos suspend para leitura/gravação das chaves TomTom, Stadia, Valhalla e Rush Hour.

---

## Fase 4 — Mecanismo de Roteamento Inteligente (4 Níveis, Cache e Trânsito)

- [ ] **Tarefa 4.1 — Criar `TravelMode.kt`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/data/travel/TravelMode.kt`
  - **Ação**:
    - Implementar enum `TravelMode` (`CAR`, `FOOT`, `TRANSIT`) com propriedades `tomtomMode`, `valhallaCosting`, `speedKmh`, `tortuosityFactor`.

- [ ] **Tarefa 4.2 — Criar interface `RoutingProvider` e modelos de resultado**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/data/travel/RoutingProvider.kt`
  - **Ação**:
    - Definir `interface RoutingProvider { val name: String; suspend fun estimate(...): ProviderResult? }`.
    - Definir `data class ProviderResult(val minutes: Int, val trafficDelayMinutes: Int = 0, val hasLiveTraffic: Boolean = false)`.
    - Definir `data class RoutingResult(val minutes: Int, val trafficDelayMinutes: Int, val providerName: String, val hasLiveTraffic: Boolean, val isRushHourAdjusted: Boolean, val isEstimate: Boolean)`.

- [ ] **Tarefa 4.3 — Implementar Cache LRU e Circuit Breaker**
  - **Arquivos**:
    - `app/src/main/kotlin/org/onekash/kashcal/data/travel/RouteCache.kt`
    - `app/src/main/kotlin/org/onekash/kashcal/data/travel/CircuitBreaker.kt`
  - **Ação**:
    - `RouteCache`: Armazena rotas com base em coordenadas quantizadas (~100m) e TTL de 15 minutos.
    - `CircuitBreaker`: Registra falhas e aplica cooldown de 15 minutos em provedores que retornarem HTTP 429 ou erros consecutivos.

- [ ] **Tarefa 4.4 — Implementar Compensador de Horário de Pico (`RushHourEstimator.kt`)**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/data/travel/RushHourEstimator.kt`
  - **Ação**:
    - Detecta se o timestamp de saída cai em janela de trânsito intenso (segunda a sexta, 07:30–09:30 e 17:30–19:30).
    - Aplica acréscimo inteligente de +30% no tempo estimado de carro para estimativas estáticas/offline.

- [ ] **Tarefa 4.5 — Implementar `TomTomRoutingProvider.kt` (Nível 1 - Trânsito Real)**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/data/travel/TomTomRoutingProvider.kt`
  - **Ação**:
    - Chamada POST para `https://api.tomtom.com/routing/1/calculateRoute/...` com `traffic=true`.
    - Parse de `travelTimeInSeconds` e `trafficDelayInSeconds`.
    - Se chave não estiver configurada, retorna `null` imediatamente.

- [ ] **Tarefa 4.6 — Implementar `StadiaMapsProvider.kt` (Nível 2 - Valhalla Nuvem)**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/data/travel/StadiaMapsProvider.kt`
  - **Ação**:
    - Chamada POST para `https://api.stadiamaps.com/route/v1`.
    - Parse de `trip.summary.time`.

- [ ] **Tarefa 4.7 — Implementar `ValhallaDemoProvider.kt` (Nível 3 - Servidor Público)**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/data/travel/ValhallaDemoProvider.kt`
  - **Ação**:
    - Chamada POST para `https://valhalla1.openstreetmap.de/route` com `X-Client-Id`.
    - Integrado ao `CircuitBreaker` para evitar sobrecarga do servidor comunitário.

- [ ] **Tarefa 4.8 — Implementar `LocalHaversineProvider.kt` (Nível 4 - Offline)**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/data/travel/LocalHaversineProvider.kt`
  - **Ação**:
    - Distância geodésica $\times$ `tortuosityFactor` / velocidade média.
    - Acopla o `RushHourEstimator` para ajustar o tempo durante o pico se ativado.
    - Nunca falha; sempre retorna valor válido.

- [ ] **Tarefa 4.9 — Implementar `RoutingService.kt`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/data/travel/RoutingService.kt`
  - **Ação**:
    - Orquestra: Checagem de Cache -> TomTom -> Stadia Maps -> Valhalla Demo -> Local Haversine -> Atualização de Cache.

- [ ] **Tarefa 4.10 — Testes unitários para toda a camada de roteamento**
  - **Arquivos em `app/src/test/kotlin/org/onekash/kashcal/data/travel/`**:
    - `RouteCacheTest.kt`
    - `CircuitBreakerTest.kt`
    - `RushHourEstimatorTest.kt`
    - `TomTomRoutingProviderTest.kt`
    - `StadiaMapsProviderTest.kt`
    - `ValhallaDemoProviderTest.kt`
    - `LocalHaversineProviderTest.kt`
    - `RoutingServiceTest.kt`
  - **Verificação**: Executar `./gradlew testDebugUnitTest --tests "org.onekash.kashcal.data.travel.*"`.

---

## Fase 5 — Provedor de Localização

- [ ] **Tarefa 5.1 — Implementar `TravelLocationProvider.kt`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/reminder/travel/TravelLocationProvider.kt`
  - **Ação**:
    - Inicializar `FusedLocationProviderClient`.
    - Checagem prévia de permissões de localização em tempo de execução.
    - `getCurrentLocation()` com `Priority.PRIORITY_HIGH_ACCURACY` e timeout de 8 segundos.
    - `getLastKnownLocation()` para recuperação instantânea do último ponto conhecido.

- [ ] **Tarefa 5.2 — Testes unitários do provedor de localização**
  - **Arquivo**: `app/src/test/kotlin/org/onekash/kashcal/reminder/travel/TravelLocationProviderTest.kt`
  - **Verificação**: Validar retornos com permissões ativas e revogadas.

---

## Fase 6 — Worker e Scheduler (WorkManager + AlarmManager)

- [ ] **Tarefa 6.1 — Implementar `TravelAlarmWorker.kt`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/reminder/travel/TravelAlarmWorker.kt`
  - **Ação**:
    - `@HiltWorker` com execução `setExpedited`.
    - Resolver destino (`geoLat`/`geoLon` persistidos ou Geocoder).
    - Resolver origem (`travelOriginFixed` ou `TravelLocationProvider`).
    - Consultar `routingService.estimate(...)`.
    - Agendar `ScheduledReminder` para hora de sair e buffer adicional.
    - Registrar alarmes exatos via `ReminderScheduler.scheduleAlarm`.

- [ ] **Tarefa 6.2 — Implementar `TravelReminderScheduler.kt`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/reminder/travel/TravelReminderScheduler.kt`
  - **Ação**:
    - Enfileirar `TravelAlarmWorker` para ~1h antes de cada ocorrência futura (`ExistingWorkPolicy.REPLACE`).
    - `scheduleUpcomingTravelAlarms()`: varredura dos próximos 7 dias.
    - `cancelForEvent(eventId: Long)`: cancelamento em cascata.

- [ ] **Tarefa 6.3 — Conectar o scheduler ao `EventCoordinator.kt`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/domain/coordinator/EventCoordinator.kt`
  - **Ação**:
    - Injetar `travelReminderScheduler`.
    - Chamar `travelReminderScheduler.scheduleForEvent(result, occurrences)` em `createEvent`, `updateEvent`, etc.
    - Chamar `cancelForEvent(eventId)` ao excluir eventos.

- [ ] **Tarefa 6.4 — Conectar a varredura ao `ReminderRefreshWorker.kt`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/reminder/worker/ReminderRefreshWorker.kt`
  - **Ação**:
    - Injetar `travelReminderScheduler` e acionar `scheduleUpcomingTravelAlarms()` na rotina diária.

- [ ] **Tarefa 6.5 — Testes unitários do Worker e Scheduler**
  - **Arquivos**:
    - `app/src/test/kotlin/org/onekash/kashcal/reminder/travel/TravelAlarmWorkerTest.kt`
    - `app/src/test/kotlin/org/onekash/kashcal/reminder/travel/TravelReminderSchedulerTest.kt`
  - **Verificação**: Executar `./gradlew testDebugUnitTest --tests "org.onekash.kashcal.reminder.travel.*"`.

---

## Fase 7 — Notificações Enriquecidas de Saída

- [ ] **Tarefa 7.1 — Criar canal de notificações em `ReminderNotificationChannels.kt`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/reminder/notification/ReminderNotificationChannels.kt`
  - **Ação**:
    - Constante `CHANNEL_TRAVEL_REMINDERS = "travel_departure_reminders" // FORK:travel-time`.
    - Configurar canal com `IMPORTANCE_HIGH`, som e vibração.

- [ ] **Tarefa 7.2 — Customizar notificação em `ReminderNotificationManager.kt`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/reminder/notification/ReminderNotificationManager.kt`
  - **Ação**:
    - Formatar corpo com dados de trânsito em tempo real quando disponíveis: *"Saia agora → [Local] · est. 35 min de carro (inclui 12 min de trânsito)"*.
    - Ação rápida "Ver no mapa" (navegação direta).
    - Para transporte público: ação rápida "Linhas de transporte" (`google.navigation:q=dest&mode=transit`).
    - Ação "Dispensar".

- [ ] **Tarefa 7.3 — Teste unitário de notificações de viagem**
  - **Arquivo**: `app/src/test/kotlin/org/onekash/kashcal/reminder/notification/ReminderNotificationManagerTravelTest.kt`
  - **Verificação**: Validar textos, menção ao tráfego e intents associadas.

---

## Fase 8 — Painel Completo de Configurações ("Alertas de Saída")

- [ ] **Tarefa 8.1 — Criar `TravelAlertsViewModel.kt`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/ui/screens/settings/TravelAlertsViewModel.kt`
  - **Ação**:
    - Injetar `KashCalDataStore` e `CircuitBreaker`.
    - Expor estado reativo para TomTom, Stadia, Valhalla, Rush Hour e permissões de GPS.
    - Implementar salvamento de chaves e toggles de provedores.

- [ ] **Tarefa 8.2 — Criar `TravelAlertsScreen.kt`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/ui/screens/settings/TravelAlertsScreen.kt`
  - **Ação**:
    - Onboarding educativo explicando o cálculo dinâmico no momento do alarme.
    - Cartão TomTom: link de cadastro gratuito sem cartão (`developer.tomtom.com`), input da chave e toggle.
    - Cartão Stadia Maps / Servidor Próprio: input de chave/URL e toggle.
    - Cartão Valhalla Demo: toggle e badge de status do Circuit Breaker.
    - Cartão Estimativa Local: toggle do compensador de horário de pico e badge "Sempre Ativo".
    - Cartão de Permissões: status e botão de solicitação direta de permissão.

- [ ] **Tarefa 8.3 — Integrar rota em `SettingsDestination.kt` e `SettingsRoute.kt`**
  - **Arquivos**:
    - `app/src/main/kotlin/org/onekash/kashcal/ui/screens/settings/SettingsDestination.kt`
    - `app/src/main/kotlin/org/onekash/kashcal/ui/screens/SettingsRoute.kt`
  - **Ação**:
    - Adicionar `TravelAlerts(depth = 1) // FORK:travel-time`.
    - Inserir transição animada e atualizar `SettingsDestinationTest.kt`.

- [ ] **Tarefa 8.4 — Adicionar atalho em `AccountSettingsScreen.kt`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/ui/screens/AccountSettingsScreen.kt`
  - **Ação**:
    - Inserir linha "Alertas de saída" apontando para a nova tela.

---

## Fase 9 — Integração no Formulário de Evento (`EventFormSheet`)

- [ ] **Tarefa 9.1 — Atualizar `EventFormState`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/ui/components/EventFormSheet.kt`
  - **Ação**:
    - Adicionar `locationLat`, `locationLon`, `travelMode`, `travelBufferMin`, `travelOriginFixed`.

- [ ] **Tarefa 9.2 — Capturar latitude e longitude no autocomplete**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/ui/components/EventFormSheet.kt`
  - **Ação**:
    - Gravar coordenadas no clique da sugestão e resetar para null na edição manual.

- [ ] **Tarefa 9.3 — Adicionar componente visual da seção "Alerta de saída"**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/ui/components/EventFormSheet.kt`
  - **Ação**:
    - Renderizar switch, modais (Carro, A pé, Transporte), seletor de origem e buffer.

- [ ] **Tarefa 9.4 — Persistir campos no `HomeViewModel.saveEvent()`**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/ui/viewmodels/HomeViewModel.kt`
  - **Ação**:
    - Repassar `geoLat`, `geoLon`, `travelMode`, `travelDepartBufferMin`, `travelOriginFixed` para a entidade `Event`.

- [ ] **Tarefa 9.5 — Solicitação gradual de permissões na UI**
  - **Arquivo**: `app/src/main/kotlin/org/onekash/kashcal/ui/components/EventFormSheet.kt`
  - **Ação**:
    - Exibir diálogo explicativo ao ativar alerta de saída com "Localização atual".

---

## Fase 10 — Verificação, Testes e Documentação Final

- [ ] **Tarefa 10.1 — Validação completa da suíte de testes unitários**
  - **Comando**: `./gradlew testDebugUnitTest`
  - **Ação**: Garantir 100% de aprovação sem regressões.

- [ ] **Tarefa 10.2 — Validação da compilação de build APK**
  - **Comando**: `./gradlew assembleDebug`
  - **Ação**: Garantir ausência de erros de compilação ou conflitos de recursos.

- [ ] **Tarefa 10.3 — Documentar o relatório de integração do fork**
  - **Arquivo**: `FORK_TRAVEL_TIME_INTEGRATION.md`
  - **Ação**: Criar mapa completo de integração documentando todos os pontos `// FORK:travel-time`.
