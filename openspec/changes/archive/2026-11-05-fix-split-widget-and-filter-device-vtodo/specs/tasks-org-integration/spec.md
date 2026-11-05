# Spec Delta: tasks-org-integration

## ADDED Requirements

### Requirement: RF-05 - Filtragem de Listas VTODO nos Calendários do Dispositivo
WHEN os calendários do dispositivo forem carregados para exibição nas configurações ou na gaveta lateral, IF a integração Tasks.org estiver ativa (`tasksOrgEnabled == true`) e com permissão concedida, THE SYSTEM SHALL omitir qualquer `DeviceCalendar` cuja tupla `(accountName, displayName)` corresponder à tupla `(TasksOrgAccount.name, TasksOrgList.title)`.
IF `tasksOrgEnabled == false`, THE SYSTEM SHALL listar todos os calendários do `CalendarProvider` nativo sem nenhuma filtragem.

#### Scenario: Filtragem de lista de tarefas sincronizada via CalDAV / DAVx5
- **WHEN** a integração com Tasks.org estiver ativada, a permissão concedida, e o Tasks.org possuir uma lista "Lembretes Comuns" na conta "Abacate Calendários"
- **THEN** a coleção "Lembretes Comuns" da conta "Abacate Calendários" exposta pelo provedor de calendários do Android é ocultada da lista de calendários do dispositivo no KashCal, sendo apresentada exclusivamente na seção do Tasks.org.

#### Scenario: Integração com Tasks.org desativada
- **WHEN** o usuário desativar o toggle "Integração com Tasks.org ativada" (`tasksOrgEnabled == false`)
- **THEN** a tela de calendários do dispositivo e a gaveta lateral voltam a listar todas as coleções expostas pelo `CalendarContract.Calendars` nativo sem aplicar nenhum filtro, assegurando paridade total com o upstream.

#### Scenario: Calendário de eventos em outra conta com nome idêntico
- **WHEN** o usuário possuir uma lista de tarefas "Compras" na conta "Abacate Calendários" (Tasks.org) e simultaneamente um calendário de eventos "Compras" na conta "Google" (`com.google`)
- **THEN** o calendário "Compras" da conta "Google" permanece visível e listado normalmente nos calendários do dispositivo, pois o nome da conta (`accountName`) não confere com o da conta Tasks.org.
