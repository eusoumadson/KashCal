# Feature 02: Exportação Automática das Configurações (Auto-Backup)

## 1. Contexto & Motivação
A realização de backups manuais depende da lembrança e proatividade do usuário. Para garantir que nenhuma customização de calendários, contas, assinaturas ICS, categorias e regras do fork seja perdida em caso de quebra ou perda do aparelho, uma rotina de auto-backup agendada é essencial.

## 2. Requisitos Técnicos & Scoped Storage (Android 10+)
O Android impõe regras rígidas de acesso ao sistema de arquivos:
- **SAF (Storage Access Framework)**: Utilização de `ActivityResultContracts.OpenDocumentTree()` para que o usuário escolha uma pasta (ex.: em "Documentos", "Downloads" ou em nuvem sincronizada como Google Drive/Nextcloud).
- **Permissão Persistente**: A URI da árvore selecionada recebe `takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION)`.
- **Manipulação**: Uso de `androidx.documentfile.provider.DocumentFile` para criar novos arquivos de backup e gerenciar os existentes.

## 3. Estratégia de Agendamento
- **Mecanismo**: `WorkManager` com `PeriodicWorkRequestBuilder` (Ex: a cada 24 horas ou 7 dias).
- **Constraints**:
  - `setRequiresBatteryNotLow(true)`
  - `setRequiresDeviceIdle(true)` (preferencialmente durante a madrugada).
- **Nome do Arquivo**:
  `kashcal_backup_auto_yyyyMMdd_HHmmss.json`

## 4. Política de Retenção (Rotation / Pruning)
Para não consumir espaço indefinidamente:
- Limite configurável (padrão: manter os últimos 5 backups automáticos).
- O worker lista os arquivos que iniciam com `kashcal_backup_auto_` na pasta configurada, ordena por data de modificação (`lastModified()`) e deleta os mais antigos excedentes ao limite.

## 5. UI nas Configurações
Em *Configurações > Dados (Backup & Restauração)*:
- Switch: "Backup automático" (ligado/desligado).
- Linha de destino: Pasta selecionada (abre seletor SAF se não houver pasta configurada).
- Seletor de periodicidade: Diária / Semanal.
- Indicador de status: "Último backup realizado em: 31 de out., 03:00".
