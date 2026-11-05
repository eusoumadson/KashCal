# Especificação: Alertas de Saída por Tempo de Viagem (Travel Time Alerts)

Este diretório contém a especificação formal e o plano executável detalhado para a implementação da funcionalidade de **Alertas de Saída por Tempo de Viagem** no KashCal Tasks.

## Documentos

1. **[requirements.md](requirements.md)** — Requisitos funcionais (RF-01 a RF-10) e não funcionais (RNF-01 a RNF-05), especificando as regras de negócio, modais de transporte, estratégia em 3 camadas de roteamento, configurações e permissões.
2. **[design.md](design.md)** — Design de arquitetura técnica completo, detalhando as entidades do Room DB v28, migração `MIGRATION_27_28`, camada de roteamento (`RoutingProvider`, `StadiaMapsProvider`, `ValhallaDemoProvider`, `LocalHaversineProvider`, `RoutingService`), WorkManager (`TravelAlarmWorker`, `TravelReminderScheduler`), notificações, telas em Compose (`TravelAlertsScreen`, `EventFormSheet`), preferências no DataStore e costuras marcadas com `// FORK:travel-time`.
3. **[tasks.md](tasks.md)** — Plano de implementação sequencial em 10 fases estruturadas, com tarefas atômicas, caixas de seleção, arquivos impactados e passos de validação automatizada para o agente executor.

## Contexto da Branch

- **Branch de trabalho**: `feature/travel-time-alerts`
- **Base**: `fork/main` (commit `9b6416ca` — "KashCal Tasks" com integração Tasks.org e suporte pt-BR)
- **Status da compilação e testes**: Verificado e aprovado (`./gradlew testDebugUnitTest` executando com 100% de sucesso).
