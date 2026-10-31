# TRAVEL_TIME_ALERTS_PLAN.md — Plano dos Alertas de Saída por Tempo de Viagem

> **Status da Branch**: `feature/travel-time-alerts` pronta para atuação do agente executor.  
> **Base**: `fork/main` (`9b6416ca` - KashCal Tasks).  
> **Validação Inicial**: `./gradlew testDebugUnitTest` executado e aprovado.  
> **Especificação Completa**: [.kiro/specs/travel-time-alerts/](.kiro/specs/travel-time-alerts/)

---

## 1. Resumo Executivo

Esta funcionalidade adiciona ao aplicativo KashCal Tasks a capacidade de disparar notificações inteligentes avisando o usuário sobre a **hora exata em que deve sair** para chegar pontualmente a eventos que possuam endereço cadastrado.

### O Diferencial Chave
O cálculo do trajeto e do tempo estimado **não é estático**: ele é disparado dinamicamente **~1 hora antes do evento** (via `TravelAlarmWorker`), capturando a localização geográfica em tempo real do aparelho naquele dia. Eventos recorrentes (ex: ir para o trabalho) calculam o tempo de partida de forma independente a cada ocorrência, adaptando-se a qualquer lugar onde o usuário tenha acordado ou esteja naquele instante.

---

## 2. Estratégia de Roteamento em 3 Camadas (Fallback Resiliente)

Para eliminar dependências de serviços pagos com cartão ou risco de indisponibilidade, a arquitetura implementa uma cadeia estrita de 3 provedores (*Provider Chain*):

```
                       RoutingService.estimate()
                                   │
              ┌────────────────────▼────────────────────┐
              │ 1. Stadia Maps Cloud (Valhalla Hosted)  │
              │    • Requer chave de API gratuita       │
              │    • Alta precisão e SLA dedicado       │
              └────────────────────┬────────────────────┘
                       falhou ou sem chave
                                   │
              ┌────────────────────▼────────────────────┐
              │ 2. Valhalla OpenStreetMap Demo          │
              │    • Servidor público comunitário       │
              │    • Gratuito e sem necessidade de chave│
              └────────────────────┬────────────────────┘
                       falhou ou sem internet
                                   │
              ┌────────────────────▼────────────────────┐
              │ 3. Estimativa Local (Haversine Formula) │
              │    • 100% offline, zero requisição      │
              │    • Linha reta x tortuosidade viária   │
              └─────────────────────────────────────────┘
```

---

## 3. Tela de Configurações Dedicada

Assim como a integração com o Tasks.org possui sua própria área de gerenciamento, foi desenhada a tela **"Alertas de Saída"** em `Configurações`:

- **Área Educativa**: Texto amigável detalhando como funciona o cálculo no momento do alarme e os benefícios práticos para o dia a dia.
- **Configuração do Stadia Maps**:
  - Instruções passo a passo para criar conta gratuita sem cartão em `https://stadiamaps.com/signup`.
  - Campo de texto para salvar a chave de API no DataStore com validação.
  - Switch para ativar ou desativar o provedor.
- **Configuração do Valhalla Demo**: Descrição e switch de ativação.
- **Estimativa Local**: Status permanente de "Sempre Ativo (Garantia Offline)".
- **Gestão de Permissões**: Status de `ACCESS_FINE_LOCATION` e `ACCESS_BACKGROUND_LOCATION` com botões diretos de solicitação.

---

## 4. Onde Encontrar as Especificações Detalhadas

Toda a arquitetura e o planejamento de execução foram minuciosamente decompostos nos arquivos da pasta `.kiro/specs/travel-time-alerts/`:

1. **[requirements.md](.kiro/specs/travel-time-alerts/requirements.md)**: Requisitos Funcionais (RF-01 a RF-10) e Não Funcionais (RNF-01 a RNF-05).
2. **[design.md](.kiro/specs/travel-time-alerts/design.md)**: Diagramas de sequência, schemas Room v28 (`MIGRATION_27_28`), contratos de API, estrutura do worker, canais de notificação e mapeamento de costuras `// FORK:travel-time`.
3. **[tasks.md](.kiro/specs/travel-time-alerts/tasks.md)**: Roteiro de 10 fases com tarefas atômicas e caixas de seleção `[ ]` para guiar a implementação pelo próximo agente.

---

## 5. Instruções para o Próximo Agente

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
