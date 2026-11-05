# TRAVEL_TIME_ALERTS_PLAN.md — Plano dos Alertas de Saída por Tempo de Viagem (Estratégia Refinada)

> **Status da Branch**: `feature/travel-time-alerts` pronta para atuação do agente executor.  
> **Base**: `fork/main` (`9b6416ca` - KashCal Tasks).  
> **Validação Inicial**: `./gradlew testDebugUnitTest` executado e aprovado.  
> **Especificação Completa**: [.kiro/specs/travel-time-alerts/](.kiro/specs/travel-time-alerts/)

---

## 1. Resumo Executivo e Diagnóstico de Refinamento

A funcionalidade adiciona ao aplicativo KashCal Tasks a capacidade de disparar notificações inteligentes avisando o usuário sobre a **hora exata em que deve sair** para chegar pontualmente a eventos que possuam endereço cadastrado.

### O Problema Central das Estratégias Convencionais
Soluções baseadas apenas em OpenStreetMap estático (OSRM/Valhalla) sofrem de uma limitação crítica: **ignoram congestionamentos e trânsito em tempo real**. Em centros urbanos, um trajeto de 15 minutos sem tráfego pode facilmente saltar para 40 minutos em horários de pico. Um alerta baseado em tempo estático faria o usuário chegar com 25 minutos de atraso!

### A Solução Refinada
Desenvolvemos uma arquitetura robusta de **4 níveis em cascata**, combinando **trânsito em tempo real gratuito (sem cartão de crédito)**, servidores comunitários, e **compensação inteligente de horário de pico**:

```
                       RoutingService.estimate()
                                   │
                     ┌─────────────▼─────────────┐
                     │     RouteCache (LRU)      │ (TTL 15 min, coords ~100m)
                     └─────────────┬─────────────┘
                                   │ Miss
        ┌──────────────────────────┴──────────────────────────┐
        │ 1. TomTom Routing API (Trânsito em Tempo Real)      │
        │    • 2.500 req/dia gratuitas (~75.000/mês)          │
        │    • SEM CARTÃO DE CRÉDITO no cadastro              │
        │    • Dados de congestionamento ao vivo              │
        └──────────────────────────┬──────────────────────────┘
                         falhou, sem chave ou cooldown
                                   │
        ┌──────────────────────────▼──────────────────────────┐
        │ 2. Stadia Maps / Servidor Próprio (Valhalla Cloud)  │
        │    • Nuvem dedicada (200k créditos/mês sem cartão)  │
        │    • Ou URL de instância própria (self-hosted)      │
        └──────────────────────────┬──────────────────────────┘
                         falhou ou sem chave
                                   │
        ┌──────────────────────────▼──────────────────────────┐
        │ 3. Valhalla OpenStreetMap Demo (valhalla1.osm.de)   │
        │    • Servidor público aberto (zero chave)           │
        │    • Protegido por Circuit Breaker (cooldown 15m)   │
        └──────────────────────────┬──────────────────────────┘
                         falhou ou sem internet (offline)
                                   │
        ┌──────────────────────────▼──────────────────────────┐
        │ 4. Estimativa Local Inteligente (Haversine Formula) │
        │    • 100% offline, zero requisição HTTP             │
        │    • Compensador de Horário de Pico (+30% no rush)  │
        └─────────────────────────────────────────────────────┘
```

---

## 2. Inovações e Refinamentos Introduzidos

1. **Trânsito Real com TomTom (Sem Fricção)**:
   - TomTom Developer oferece **2.500 rotas/dia grátis sem cartão**.
   - A notificação passa a informar o congestionamento: *"Saia agora → [Local] · est. 35 min de carro (inclui 12 min de trânsito)"*.
2. **Compensador Inteligente de Horário de Pico (Rush Hour Compensator)**:
   - Para quando o usuário estiver offline ou usando provedores estáticos: o app detecta se a viagem ocorre em janelas de pico (07:30–09:30 e 17:30–19:30 em dias úteis) e adiciona automaticamente um buffer prudencial de trânsito (+30% no tempo estimado de carro).
3. **Cache de Curto Prazo e Circuit Breaker**:
   - `RouteCache`: Armazena rotas recentes em memória por 15 minutos, poupando bateria e cotas de requisição.
   - `CircuitBreaker`: Monitora o servidor público comunitário; se receber HTTP 429 ou erros seguidos, entra em cooldown de 15 minutos, liberando o fluxo imediatamente para o fallback local sem congelar o worker por 8 segundos.
4. **Solução Pragmática para Transporte Público**:
   - O app calcula a estimativa de tempo base e oferece na notificação a ação de 1 toque **"Ver linhas de transporte"**, abrindo a rota de transporte público direto no Google Maps / Citymapper / Moovit (`google.navigation:q=dest&mode=transit`).

---

## 3. Onde Encontrar as Especificações Detalhadas

Toda a arquitetura e o planejamento de execução foram minuciosamente decompostos nos arquivos da pasta `.kiro/specs/travel-time-alerts/`:

1. **[requirements.md](.kiro/specs/travel-time-alerts/requirements.md)**: Requisitos Funcionais (RF-01 a RF-10) e Não Funcionais (RNF-01 a RNF-06).
2. **[design.md](.kiro/specs/travel-time-alerts/design.md)**: Diagramas técnicos, Room v28 (`MIGRATION_27_28`), contratos JSON da TomTom e Valhalla, Circuit Breaker, RouteCache, Worker e costuras `// FORK:travel-time`.
3. **[tasks.md](.kiro/specs/travel-time-alerts/tasks.md)**: Roteiro de 10 fases com tarefas atômicas e caixas de seleção `[ ]` para guiar a implementação.

---

## 4. Instruções para o Próximo Agente

1. Confirme que você está na branch:
   ```sh
   git branch --show-current # deve exibir: feature/travel-time-alerts
   ```
2. Abra `.kiro/specs/travel-time-alerts/tasks.md`.
3. Execute as tarefas fase por fase na ordem estabelecida.
4. Ao alterar arquivos existentes do core, marque cada inserção com o comentário `// FORK:travel-time`.
5. Valide cada fase executando:
   ```sh
   ./gradlew testDebugUnitTest
   ```
