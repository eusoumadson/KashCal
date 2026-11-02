# Feature 06: Facelift & Pente Fino nos Alertas de Saída

## 1. Contexto & Diagnóstico
A tela `TravelAlertsScreen.kt` foi implementada na branch de travel-time e contém pequenas falhas visuais e inconsistências com o padrão de design do KashCal.

### Inconsistências Identificadas:
1. **Cabeçalho de Seção Incorreto**:
   - Na linha 254:
     ```kotlin
     // Providers Section Header
     Text(
         text = stringResource(R.string.settings_tasks_org_enabled), // Reusable or header
         ...
     )
     ```
     Utiliza a string de tarefas ("Tarefas do Tasks.org ativadas") como título para os provedores de rotas.
2. **Strings Hardcoded**:
   - `"Always active"` (linha 370) no badge do estimador offline está em inglês puro, sem entrada em `strings.xml`.
   - `"stadiamaps API key"` (linha 285) hardcoded no hint do campo de texto.
3. **Badges de Status sem Padrão Visual**:
   - Card de Permissões: `RoundedCornerShape(16.dp)`, padding `horizontal = 10.dp, vertical = 4.dp`, cor fixa `Color(0xFF2E7D32)`.
   - Card do Valhalla: `RoundedCornerShape(12.dp)`, padding `horizontal = 8.dp, vertical = 2.dp`, cor fixa `Color(0xFFF57C00)`.
   - Card Local Offline: `RoundedCornerShape(12.dp)`, padding `horizontal = 8.dp, vertical = 2.dp`, `primaryContainer`.
4. **Espaçamentos Internos e Tipografia**:
   - Alguns cards utilizam `Arrangement.spacedBy(10.dp)`, outros `12.dp`.
   - Falta de uniformidade com os títulos e subtítulos de `NestedSettingsHeading` e `SettingsRow`.

## 2. Ações de Melhoria
1. **Componente de Status Unificado (`TravelStatusBadge`)**:
   - Criação de um badge uniforme com formato pill ou cantos arredondados padronizados (`8.dp` ou `12.dp`).
   - Padding padrão (`horizontal = 8.dp, vertical = 4.dp`).
   - Cores semânticas do tema M3:
     - Sucesso / Ativo: `primaryContainer` / `onPrimaryContainer` ou verde semântico acessível.
     - Alerta / Circuito Aberto: `tertiaryContainer` / `onTertiaryContainer`.
     - Erro / Permissão Ausente: `errorContainer` / `onErrorContainer`.
2. **Correção de Strings e Localização**:
   - Adicionar chave `travel_providers_section_title` ("Provedores de rotas e trânsito" / "Routing & traffic providers").
   - Adicionar chave `travel_provider_local_status_active` ("Sempre ativo" / "Always active").
   - Adicionar chave `travel_provider_stadia_key_hint` ("Chave de API do Stadia Maps" / "Stadia Maps API key").
3. **Polimento Visual dos Cards**:
   - Padronizar paddings em `16.dp`.
   - Harmonizar switches e botões alinhados às margens padrão.
