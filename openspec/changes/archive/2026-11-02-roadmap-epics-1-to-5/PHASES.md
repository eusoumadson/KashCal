# Fases de Implementação — Features & Refatorações do Fork

> [!NOTE]
> **Status de Execução:** ✅ **Fases 1, 2 e 3 Concluídas** na branch `main`.
> Todas as 6 features detalhadas abaixo foram implementadas e integradas.
> A suíte completa de testes unitários (`./gradlew testDebugUnitTest`) está verde (100% de aprovação).

---


Este documento define a ordem sequencial de implementação, critérios de aceite e estratégia de testes para cada uma das frentes planejadas.

---

## 📌 Ordem de Execução

A sequência foi planejada para minimizar quebras, eliminar código morto primeiro e garantir que cada funcionalidade nova se apoie em bases estáveis:

```
┌────────────────────────────────────────────────────────┐
│ Fase 1: Limpeza & Polimento Inicial (Quick Wins)       │
│ • Feature 05: Simplificação da Gestão do Tasks.org     │
│ • Feature 06: Facelift & Pente Fino Alertas de Saída   │
└──────────────────────────┬─────────────────────────────┘
                           ▼
┌────────────────────────────────────────────────────────┐
│ Fase 2: Arquitetura de UI & Navegação                  │
│ • Feature 03: Seção "Recursos Avançados" em Settings   │
│ • Feature 04: Redesign Visual do TaskEditorSheet       │
└──────────────────────────┬─────────────────────────────┘
                           ▼
┌────────────────────────────────────────────────────────┐
│ Fase 3: Backup & Resiliência de Dados                  │
│ • Feature 01: Exportação de Configs do Fork            │
│ • Feature 02: Exportação Automática Agendada (SAF)     │
└────────────────────────────────────────────────────────┘
```

---

## 📋 Detalhamento por Fase

### Fase 1: Limpeza & Polimento Inicial (Quick Wins)

#### Feature 05: Simplificação da Gestão do Tasks.org
- **Documento**: `05_tasks_org_simplification.md`
- **Arquivos-chave**:
  - `app/src/main/kotlin/org/onekash/kashcal/ui/screens/settings/TasksOrgManagementSection.kt`
  - `app/src/main/kotlin/org/onekash/kashcal/ui/viewmodels/TasksOrgManageViewModel.kt`
  - `app/src/main/res/values/strings.xml` / `values-pt-rBR/strings.xml`
- **Ações**:
  - Remover bloco e diálogos de gerenciamento de tags.
  - Remover bloco e diálogos de gerenciamento de lugares.
  - Na lista de listas: manter apenas ação de renomear e recolorir. Remover botão de deletar lista e botão "Criar lista no app".
- **Critérios de Aceite**:
  - Configurações do Tasks.org só exibem status de versão/contas e edição de listas existentes.
  - Build e testes unitários passando sem referências quebradas.

#### Feature 06: Facelift & Pente Fino Alertas de Saída
- **Documento**: `06_departure_alerts_facelift.md`
- **Arquivos-chave**:
  - `app/src/main/kotlin/org/onekash/kashcal/ui/screens/settings/TravelAlertsScreen.kt`
  - `app/src/main/res/values/strings.xml` / `values-pt-rBR/strings.xml`
- **Ações**:
  - Corrigir cabeçalho da seção de provedores (remover `R.string.settings_tasks_org_enabled` e usar `travel_providers_header`).
  - Traduzir strings hardcoded ("Always active", "stadiamaps API key").
  - Criar componente padronizado de status badge (cantos e paddings uniformes, cores semânticas M3).
  - Alinhar espaçamentos internos dos cards para 16dp padrão.
- **Critérios de Aceite**:
  - Tela visualmente harmônica com o resto do aplicativo em tema claro e escuro.
  - Nenhuma string sem tradução.

---

### Fase 2: Arquitetura de UI & Navegação

#### Feature 03: Seção "Recursos Avançados" em Configurações
- **Documento**: `03_advanced_features_settings_section.md`
- **Arquivos-chave**:
  - `app/src/main/kotlin/org/onekash/kashcal/ui/screens/AccountSettingsScreen.kt`
  - `app/src/main/kotlin/org/onekash/kashcal/ui/screens/SettingsRoute.kt`
  - `app/src/main/res/values/strings.xml` / `values-pt-rBR/strings.xml`
- **Ações**:
  - Criar string `settings_section_advanced_features` ("Recursos avançados").
  - Remover as 3 linhas (Tasks.org, Alertas de Saída e Widgets Experimentais) da seção `Calendários e contas`.
  - Criar a nova `SearchableSection` dedicada "Recursos avançados" após `Aparência` ou `Criação de eventos`.
  - Manter suporte à busca global de configurações para todas as 3 linhas.
- **Critérios de Aceite**:
  - Seção `Calendários e contas` contendo apenas contas e calendários reais.
  - Nova seção agrupando de forma elegante e limpa as 3 features do fork.

#### Feature 04: Redesign Visual do `TaskEditorSheet`
- **Documento**: `04_task_editor_sheet_redesign.md`
- **Arquivos-chave**:
  - `app/src/main/kotlin/org/onekash/kashcal/ui/components/TaskEditorSheet.kt`
  - `app/src/main/kotlin/org/onekash/kashcal/ui/components/TaskEditorFields.kt`
- **Ações**:
  - Migrar shell para `Dialog` full-screen / Surface com `safeDrawingPadding`.
  - Top Bar com botão "Cancelar" (com confirmação de descarte de alterações caso haja edições), título centralizado e botão pill de "Salvar" (`RoundedCornerShape(20.dp)`) colorido pela lista.
  - Harmonização de campos de texto (título, notas) e seletores de data/hora com ícones e margens de 16dp.
- **Critérios de Aceite**:
  - Criar ou editar uma tarefa possui a mesma fluidez visual e usabilidade de `EventFormSheet`.

---

### Fase 3: Backup & Resiliência de Dados

#### Feature 01: Exportação de Configs do Fork
- **Documento**: `01_export_fork_configs.md`
- **Arquivos-chave**:
  - `app/src/main/kotlin/org/onekash/kashcal/domain/backup/BackupEnvelope.kt`
  - `app/src/main/kotlin/org/onekash/kashcal/domain/backup/SettingsBackupExporter.kt`
  - `app/src/main/kotlin/org/onekash/kashcal/domain/backup/SettingsBackupImporter.kt`
  - `app/src/main/kotlin/org/onekash/kashcal/domain/backup/ExportablePreferences.kt`
- **Ações**:
  - Estruturar metadados do fork no envelope de backup.
  - Permitir opção de backup completo com as preferências das features do fork.
  - Garantir que testes de reflexão em `ExportablePreferencesTest` continuem passando.
- **Critérios de Aceite**:
  - Arquivo de backup exportado restaura fielmente o estado do fork em outra instalação.

#### Feature 02: Exportação Automática Agendada (Auto-Backup)
- **Documento**: `02_auto_export_configs.md`
- **Arquivos-chave**:
  - `app/src/main/kotlin/org/onekash/kashcal/domain/backup/AutoBackupWorker.kt` (novo)
  - `app/src/main/kotlin/org/onekash/kashcal/domain/backup/AutoBackupScheduler.kt` (novo)
  - `app/src/main/kotlin/org/onekash/kashcal/ui/screens/AccountSettingsScreen.kt` (seção de dados)
- **Ações**:
  - Implementar worker do `WorkManager` para geração periódica do JSON de backup.
  - Gravação via `DocumentFile` em diretório SAF escolhido pelo usuário.
  - Rotação com exclusão automática de arquivos excedentes ao limite de retenção.
- **Critérios de Aceite**:
  - Usuário ativa o backup automático, escolhe a pasta, e os backups são gerados sem intervenção manual.
