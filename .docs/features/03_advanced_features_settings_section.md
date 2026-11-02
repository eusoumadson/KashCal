# Feature 03: Seção "Recursos Avançados" nas Configurações

## 1. Contexto & Problema
Atualmente, as 3 funcionalidades exclusivas desenvolvidas no fork:
1. **Tarefas do Tasks.org**
2. **Alertas de Saída (Tempo de Viagem / Deslocamento)**
3. **Widgets Experimentais**

Estão todas concentradas na seção **"Calendários e contas"** (`settings_section_calendars`) em `AccountSettingsScreen.kt`.

### Problemas dessa abordagem:
- Sobrecarga cognitiva: essa seção foi desenhada para conexões de contas CalDAV, Google, iCloud, Exchange e fontes de calendários.
- Incoerência semântica: Alertas de saída é uma ferramenta de cálculo de rotas e notificações inteligentes; Widgets experimentais pertencem ao launcher e aspecto visual.

## 2. Solução Arquitetural
Criação de uma seção dedicada chamada **"Recursos Avançados"** (`settings_section_advanced_features`) em `AccountSettingsScreen.kt`.

### Benefícios:
- **Zero poluição na seção de contas**: Restaura a clareza original da lista de calendários e contas.
- **Identidade do Fork**: O usuário encontra com facilidade todos os superpoderes adicionados ao fork em um único lugar.
- **Fácil isolamento para Rebase**: Toda a seção fica demarcada com `// FORK:advanced-features`, facilitando futuros rebases com o upstream.
- **Total suporte à busca interna**: As três linhas permanecem registradas com seus IDs no `SearchableSection`, respondendo normalmente à pesquisa rápida de configurações.

## 3. Disposição na Tela
A nova seção será inserida logo após a seção de **"Aparência"** (ou entre **"Aparência"** e **"Criação de eventos"**), mantendo um fluxo natural:
1. Calendários e contas
2. Aparência
3. **Recursos avançados**
   - 📌 **Tarefas do Tasks.org** (status: Ativado / Desativado)
   - 🚗 **Alertas de saída** (status: Provedores ativos e permissões)
   - 🧩 **Widgets experimentais** (status: Modelos ativados no launcher)
4. Criação de eventos
5. Sincronização
6. Dados (Backup & Restauração)
7. Solução de problemas

## 4. Strings & Localização
- `settings_section_advanced_features`:
  - `values/strings.xml`: "Advanced features"
  - `values-pt-rBR/strings.xml`: "Recursos avançados"
  - `values-pt/strings.xml`: "Recursos avançados"
