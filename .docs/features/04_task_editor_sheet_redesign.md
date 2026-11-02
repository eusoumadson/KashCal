# Feature 04: Redesign Visual do TaskEditorSheet

## 1. Contexto & Problema
O editor de tarefas atual (`TaskEditorSheet.kt`) foi implementado utilizando `ModalBottomSheet` padrão com drag handle no topo e botões de ação ("Cancelar" e "Salvar") empilhados no final do formulário após a rolagem.

Em contrapartida, o formulário de eventos principal do app (`EventFormSheet.kt`) utiliza um padrão muito mais polido:
- Janela `Dialog` com `safeDrawingPadding` e subida direta do teclado IME.
- Barra superior fixa:
  - Botão à esquerda: "Cancelar" (ou botão tonal "Descartar?" caso haja alterações não salvas).
  - Centro: Título em destaque ("Nova tarefa" / "Editar tarefa").
  - Botão à direita: Botão pill de "Salvar" arredondado (`RoundedCornerShape(20.dp)`), com a cor da lista da tarefa e texto em alto contraste.
- Blocos visuais bem delineados com ícones correspondentes alinhados à esquerda (título, notas, data, repetição, tags, local, lembretes).
- Margens padronizadas em `16.dp`.

## 2. Metas de Design
Transformar a experiência de criação/edição de tarefas para ser esteticamente indistinguível da criação de eventos, garantindo consistência visual em todo o app.

## 3. Alterações Planejadas no Componente
1. **Shell do Diálogo**:
   - Transicionar de `ModalBottomSheet` para `Dialog` de tela inteira com `Surface` (como no `EventFormSheet`), proporcionando foco imediato e teclado responsivo.
   - Adicionar confirmação de descarte de edições de 2 toques: botão "Cancelar" vira "Descartar?" caso o usuário tenha digitado algo e tente sair sem salvar.
2. **Top Bar**:
   - `Cancel` / `Discard?` à esquerda.
   - `Title` ("Nova tarefa" / "Editar tarefa") centralizado em `titleLarge` negrito.
   - `Save` em pill (`RoundedCornerShape(20.dp)`) à direita, colorido com a cor da lista selecionada da tarefa.
3. **Corpo do Formulário**:
   - Campo de título limpo, direto, com suporte a validação visual.
   - Campo de notas/descrição com ícone alinhado ao topo.
   - Linha de lista da tarefa com chip selecionável e indicador visual da cor da lista.
   - Campos de data de vencimento e início formatados em cards ou linhas com ícones de calendário/relógio.
   - Seção de repetição com resumo legível da regra.
   - Seção de prioridade estilizada com indicador de cor e chips uniformes.
   - Seção de tags com chips arredondados e botão compacto de adicionar tag.
   - Seção de lembretes e local formatados harmonicamente.
4. **Exclusão de Tarefa**:
   - No modo de edição, botão discreto "Excluir tarefa" no rodapé com cor de erro ou ícone de lixeira no cabeçalho secundário, acompanhado de diálogo de confirmação.
