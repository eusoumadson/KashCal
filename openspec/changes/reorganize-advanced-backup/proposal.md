# Proposal: reorganize-advanced-backup

## Metadata
- **Tier**: light
- **Architecture References**: `AGENTS.md`, `docs/agents/architecture.md`, `docs/agents/domain_rules.md`, `docs/fork/FORK_INTEGRATION.md`
- **Capability Policy**: ignore_for_this_run

## Why

A sincronização recente com o upstream (v2026.10.30 a v2026.11.03) introduziu o recurso "Backup automático de calendários" (arquivos `.ics` de calendários offline salvos via SAF) na seção *Dados e armazenamento*.
O fork já possuía o recurso "Backup automático de configurações" (arquivo `.json` de preferências e chaves do fork salvo via SAF), alocado nessa mesma seção. Essa duplicidade visual causa atrito e confusão no usuário sobre qual backup faz o quê.
Mover o backup automático de configurações para a seção *Recursos avançados* do fork elimina a concorrência na seção de dados e unifica todos os recursos exclusivos do fork em um único local.

## Scope
- **In**:
  - Mover o ponto de enxerto do item de backup automático de configurações (`auto-backup`) da seção `settings_section_data` para `settings_section_advanced_features` em `AccountSettingsScreen.kt`.
  - Explicitar no `AutoBackupSheet.kt` que o backup de configurações preserva opções do KashCal e dos recursos avançados do fork (Tasks.org, Travel Time, widgets experimentais).
  - Atualizar os testes unitários de Compose e navegação de configurações para validar a nova posição da linha.
  - Manter intactos os recursos de impressão e calendários locais/offline conforme estabelecido pelo upstream.
- **Out**:
  - Alterações no motor de backup em segundo plano (`AutoBackupWorker`, `AutoBackupScheduler`).
  - Alterações no esquema JSON de preferências (`ForkBackupPreferences`).
  - Modificações no sistema de backup de calendários offline do upstream (`CalendarBackupRunner`).
  - Inclusão de tarefas do Tasks.org na rotina de impressão (mantido padrão upstream).

## What Changes

- **UI de Configurações (`AccountSettingsScreen.kt`)**: a linha `auto-backup` é transferida da seção de dados para o grupo de recursos avançados (ao lado de Tasks.org, Travel Alerts e Experimental Widgets).
- **UI do Sheet (`AutoBackupSheet.kt`)**: texto informativo clarifying que as configurações do fork são preservadas no arquivo exportado.
- **Testes Unitários**: adequação de testes de hierarquia de tela e verificação visual de configurações.

## Capabilities

### New Capabilities
*Nenhuma (não há criação de nova capacidade durável).*

### Modified Capabilities
- `auto-backup`: Atualização da especificação de apresentação na interface (a entrada de menu passa a residir na seção de recursos avançados).

## Confirmed Acceptance Criteria (Source of Truth)
- **AC-01**: A linha de "Backup automático de configurações" (`auto-backup`) é renderizada dentro da seção "Recursos avançados" (`settings_section_advanced_features`) em `AccountSettingsScreen.kt`.
- **AC-02**: A seção "Dados e armazenamento" (`settings_section_data`) renderiza apenas as 5 opções nativas do upstream (Exportar calendários, Backup automático de calendários, Backup das configurações, Importar de arquivo, Restaurar configurações).
- **AC-03**: Tocar na linha de "Backup automático de configurações" abre a `AutoBackupSheet` sem quebra de estado ou navegação.
- **AC-04**: Todas as strings estão devidamente localizadas em `values`, `values-pt` e `values-pt-rBR`.
- **AC-05**: Validação completa da suíte de testes unitários (`./gradlew testDebugUnitTest`) sem regressões.

## Impact
- **Affected Code Paths**: `AccountSettingsScreen.kt`, `AutoBackupSheet.kt`, `AccountSettingsScreenComposeTest.kt`, `strings.xml`.
- **Database / Migrations**: Nenhum impacto em Room ou DataStore.
- **External Contracts**: Nenhuma alteração de contratos.
