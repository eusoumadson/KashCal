# Feature 05: Simplificação da Gestão do Tasks.org

## 1. Contexto & Diagnóstico
Atualmente em `TasksOrgManagementSection.kt`, o aplicativo oferece:
- Edição de listas (renomear e trocar cor)
- Exclusão de listas (com remoção em lote de todas as tarefas da lista no Tasks.org)
- Botão "Criar lista no app Tasks.org"
- Gerenciamento completo de Tags (listar, renomear, colorir, excluir)
- Gerenciamento completo de Lugares (listar, excluir)

### Motivação para a Simplificação:
1. **Risco de Perda Acidental de Dados**: Excluir listas através do calendário é perigoso para o usuário, que muitas vezes confunde "ocultar lista do calendário" com "deletar lista".
2. **Duplicação de Responsabilidades**: O Tasks.org é o gerenciador de tarefas nativo do usuário e já possui telas excelentes para criação de listas, administração de geofences/lugares e gestão profunda de tags.
3. **Carga Cognitiva e Ruído**: A tela de configurações do KashCal fica excessivamente longa e poluída com controles que raramente são usados aqui.

## 2. Escopo das Alterações
1. **Remoção de Tags**:
   - Remover toda a subseção de gerenciamento de tags (`tasks_org_manage_tags`).
   - Remover diálogos de renomear e excluir tags dessa tela.
2. **Remoção de Lugares**:
   - Remover toda a subseção de gerenciamento de lugares (`tasks_org_manage_places`).
   - Remover diálogos de exclusão de lugares.
3. **Restrição das Listas**:
   - **Permitido**: Apenas renomear e alterar a cor da lista (`NameColorDialog`).
   - **Removido**: Botão/ação de exclusão de lista (`Deletion.OfList`).
   - **Removido**: Botão de criação de nova lista (`tasks_org_create_list_in_app`).
4. **Limpeza Técnica**:
   - Descontinuar ou limpar métodos não mais utilizados em `TasksOrgManageViewModel.kt` (`deleteList`, `deleteTag`, `updateTag`, `deletePlace`).

## 3. Resultado Esperado
Uma seção limpa e objetiva:
- Status da versão do Tasks.org instalada e saúde das contas.
- Lista de listas com opção simples de tocar para renomear ou trocar a cor de exibição no calendário.
