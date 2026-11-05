# Feature 01: Exportação das Configurações do Fork

## 1. Contexto & Motivação
O KashCal original possui funcionalidade de backup de configurações em JSON via `SettingsBackupExporter` e `SettingsBackupImporter`. No entanto, as configurações criadas exclusivamente no fork (integração Tasks.org, tempo de deslocamento/alertas de saída e widgets experimentais) foram adicionadas após ou deliberadamente excluídas da lista `ExportablePreferences.KEYS` do upstream.

Quando o usuário migra para outro dispositivo ou reinstala o aplicativo, ele perde todas as configurações do fork, necessitando reconfigurar chaves de API, preferências de widgets e listas.

## 2. Configurações a Serem Exportadas
1. **Integração Tasks.org**:
   - `tasks_org_enabled` (Boolean)
   - `tasks_in_widgets_enabled` (Boolean)
   - `hidden_tasks_org_list_ids` (Set<String>)
2. **Alertas de Saída (Tempo de Deslocamento)**:
   - `travel_tomtom_enabled` (Boolean)
   - `travel_stadia_enabled` (Boolean)
   - `travel_valhalla_enabled` (Boolean)
   - `travel_rush_hour_enabled` (Boolean)
   - `travel_tomtom_api_key` & `travel_stadia_api_key`: Permitir inclusão opcional pelo usuário via toggle "Incluir chaves de API (sensível)".
3. **Widgets Experimentais**:
   - `experimental_widgets_enabled` (Boolean)

## 3. Arquitetura de Dados
O `BackupEnvelope` atualmente possui:
```kotlin
@Serializable
data class BackupEnvelope(
    @SerialName("file_format_version") val fileFormatVersion: Int,
    @SerialName("app_version") val appVersion: String,
    @SerialName("exported_at") val exportedAt: String,
    val preferences: Map<String, BackupPreferenceValue>,
    val subscriptions: List<BackupSubscription>,
    val categories: List<BackupCategory> = emptyList(),
    // FORK: configurações estendidas opcionais
    val forkPreferences: Map<String, BackupPreferenceValue> = emptyMap()
)
```
Ao adotar `forkPreferences` com valor padrão `emptyMap()`, garantimos que:
- Backups antigos continuam sendo importados sem quebrar.
- Backups gerados pelo upstream continuam sendo importados.
- Testes upstream como `ExportablePreferencesTest` permanecem íntegros.

## 4. Interface com o Usuário
No diálogo de exportação manual de configurações:
- Caixa de seleção opcional: "Incluir chaves de API nos dados exportados" (desmarcada por padrão por segurança).

## 5. Plano de Testes
- Teste de Round-trip (Exportar -> Importar -> Validar igualdade de estado).
- Teste de compatibilidade com arquivo gerado pelo app upstream (sem `forkPreferences`).
- Teste de restauração em aparelho limpo.
