#!/usr/bin/env python3
"""Portuguese (Brazil, and the generic pt fallback) strings of the Tasks.org integration (FORK:tasks-org).

The English originals live in app/src/main/res/values/strings.xml. This script is the source of the
translations: edit PT below, then run it from the repository root. It rewrites one marked block at
the end of values-pt-rBR/strings.xml and values-pt/strings.xml and checks that every key exists in
the English file with the same placeholders.

    python3 app/src/fork/tools/gen_strings_pt.py
"""
import re
PT = {
'settings_tasks_org': 'Tarefas do Tasks.org',
'settings_tasks_org_hint': 'Tarefas com data de vencimento do Tasks.org',
'settings_tasks_org_enabled': 'Ativado',
'tasks_org_description': 'Mostra no calendário as tarefas com data de vencimento do app Tasks.org. Marque uma tarefa para concluí-la. As tarefas continuam no Tasks.org, que as sincroniza.',
'tasks_org_permission_required': 'É preciso acesso ao Tasks.org. Toque para conceder.',
'tasks_org_read_only_access': 'Não é possível concluir tarefas',
'tasks_org_grant_write': 'Toque para permitir que o KashCal conclua tarefas no Tasks.org',
'cd_refresh_tasks_org_lists': 'Atualizar listas de tarefas',
'tasks_org_not_installed': 'Instale o Tasks.org para ver as tarefas dele aqui.',
'tasks_org_lists_header': 'Listas de tarefas',
'tasks_org_no_lists': 'Nenhuma lista de tarefas encontrada no Tasks.org.',
'tasks_org_list_read_only': 'Somente leitura',
'task_due': 'Vence %1$s',
'task_mark_completed': 'Marcar como concluída',
'task_completed_label': 'Concluída',
'task_repeats_note': 'Ao concluir, a tarefa vai para a próxima data no Tasks.org.',
'task_open_in_tasks_org': 'Abrir no Tasks.org',
'task_future_occurrence_note': 'Uma ocorrência futura. Conclua primeiro a atual.',
'snackbar_task_update_failed': 'Não foi possível atualizar a tarefa',
'snackbar_task_completed': 'Tarefa concluída',
'snackbar_task_reopened': 'Tarefa reaberta',
'task_editor_new_title': 'Nova tarefa',
'task_editor_edit_title': 'Editar tarefa',
'task_editor_title_label': 'Título',
'task_editor_title_required': 'O título é obrigatório',
'task_editor_notes_label': 'Notas',
'task_editor_list_label': 'Lista',
'task_editor_due_label': 'Data de vencimento',
'task_editor_set_due': 'Definir data de vencimento',
'task_editor_all_day': 'Dia inteiro',
'task_editor_time_label': 'Hora',
'task_editor_priority_label': 'Prioridade',
'task_editor_tags_label': 'Tags',
'task_editor_reminders_label': 'Lembretes',
'task_reminder_at_due': 'No vencimento',
'task_reminder_10_min': '10 min antes',
'task_reminder_1_hour': '1 hora antes',
'task_reminder_1_day': '1 dia antes',
'task_reminder_mins_before': '%1$d min antes',
'task_reminder_hours_before': '%1$d h antes',
'task_reminder_days_before': '%1$d d antes',
'task_priority_high': 'Alta',
'task_priority_medium': 'Média',
'task_priority_low': 'Baixa',
'task_priority_none': 'Nenhuma',
'task_editor_save': 'Salvar',
'task_editor_cancel': 'Cancelar',
'task_editor_delete': 'Excluir',
'task_editor_edit': 'Editar',
'task_editor_new_task_action': 'Nova tarefa',
'task_delete_confirm_title': 'Excluir tarefa?',
'task_delete_confirm_message': 'Isso exclui a tarefa e as subtarefas dela no Tasks.org. Não dá para desfazer.',
'task_delete_confirm_delete': 'Excluir',
'task_delete_confirm_cancel': 'Cancelar',
'snackbar_task_read_only': 'Essa lista é somente leitura',
'snackbar_task_created': 'Tarefa criada',
'snackbar_task_saved': 'Tarefa salva',
'snackbar_task_deleted': 'Tarefa excluída',
'snackbar_task_delete_failed': 'Não foi possível excluir a tarefa',
'task_editor_recurring_note': 'Esta tarefa se repete. Mudar a data desloca a série no Tasks.org.',
'task_editor_start_label': 'Início (oculta até)',
'task_editor_set_start': 'Definir data de início',
'task_editor_repeat_label': 'Repetição',
'task_repeat_none': 'Não se repete',
'task_repeat_daily': 'Diariamente',
'task_repeat_weekly': 'Semanalmente',
'task_repeat_monthly': 'Mensalmente',
'task_repeat_yearly': 'Anualmente',
'task_repeat_custom': 'Regra personalizada: %1$s',
'task_repeat_custom_hint': 'Esta regra foi definida no Tasks.org e é mantida como está. Escolha outra opção para substituí-la.',
'task_repeat_every': 'A cada',
'task_repeat_unit_days': 'dias',
'task_repeat_unit_weeks': 'semanas',
'task_repeat_unit_months': 'meses',
'task_repeat_unit_years': 'anos',
'task_repeat_on_days': 'Em',
'task_repeat_ends_never': 'Nunca termina',
'task_repeat_ends_after': 'Termina após %1$d vezes',
'task_repeat_from_label': 'Repetir a partir de',
'task_repeat_from_due': 'Data de vencimento',
'task_repeat_from_completion': 'Data de conclusão',
'task_repeat_needs_due': 'Uma tarefa que se repete precisa de data de vencimento, então foi definida a de hoje.',
'task_editor_side_effects_note': 'Mudar a data de vencimento também pode mover a data de início ou ajustar a regra de repetição. Quem decide é o Tasks.org, e o KashCal avisa se a regra mudou.',
'snackbar_task_saved_rule_adjusted': 'Tarefa salva. O Tasks.org ajustou a regra de repetição.',
'task_editor_new_tag_hint': 'Nova tag',
'task_editor_add_tag': 'Adicionar',
'task_editor_place_label': 'Lugar',
'task_place_none': 'Sem lugar',
'task_place_on_arrival': 'Ao chegar',
'task_place_on_leaving': 'Ao sair',
'task_place_new': 'Novo lugar',
'task_place_dialog_title': 'Novo lugar',
'task_place_name': 'Nome',
'task_place_address': 'Endereço',
'task_place_latitude': 'Latitude',
'task_place_longitude': 'Longitude',
'task_place_radius': 'Raio (metros)',
'task_place_find_coordinates': 'Buscar coordenadas pelo endereço',
'task_place_not_found': 'Não foi possível encontrar esse endereço. Informe as coordenadas.',
'task_place_invalid': 'Informe um nome e coordenadas válidas.',
'task_place_save': 'Salvar lugar',
'snackbar_place_create_failed': 'Não foi possível salvar o lugar',
'task_reminder_random': 'Cerca de a cada %1$s',
'task_reminder_snoozed_until': 'Adiado até %1$s',
'task_reminder_add_random': 'Aleatório, diário',
'task_duration_minutes': '%1$d min',
'task_duration_hours': '%1$d h',
'task_duration_days': '%1$d d',
'task_subtasks_summary': '%1$d subtarefas, %2$d em aberto',
'task_subtasks_manage': 'Gerenciar subtarefas no Tasks.org',
'task_priority_dot_high': 'Prioridade alta',
'task_priority_dot_medium': 'Prioridade média',
'task_priority_dot_low': 'Prioridade baixa',
'tasks_org_version_too_old': 'O Tasks.org %1$s é mais antigo que a versão %2$s, a primeira com a API que o KashCal usa. Atualize o Tasks.org.',
'tasks_org_version_label': 'Tasks.org %1$s',
'tasks_org_account_attention': '%1$s precisa de atenção no Tasks.org (%2$s).',
'tasks_org_manage_lists': 'Gerenciar listas',
'tasks_org_create_list_in_app': 'Criar uma lista no Tasks.org',
'tasks_org_list_name': 'Nome da lista',
'tasks_org_rename': 'Renomear',
'tasks_org_delete': 'Excluir',
'tasks_org_save': 'Salvar',
'tasks_org_cancel': 'Cancelar',
'tasks_org_color_label': 'Cor',
'tasks_org_delete_list_title': 'Excluir esta lista?',
'tasks_org_delete_list_message': 'A lista e todas as tarefas dela são excluídas no Tasks.org. Não dá para desfazer.',
'tasks_org_manage_tags': 'Gerenciar tags',
'tasks_org_tag_name': 'Nome da tag',
'tasks_org_delete_tag_title': 'Excluir esta tag?',
'tasks_org_delete_tag_message': 'A tag é removida de todas as tarefas no Tasks.org.',
'tasks_org_manage_places': 'Gerenciar lugares',
'tasks_org_delete_place_title': 'Excluir este lugar?',
'tasks_org_delete_place_message': 'As tarefas nesse lugar perdem o lugar e os lembretes de local.',
'tasks_org_no_tags': 'Nenhuma tag.',
'tasks_org_no_places': 'Nenhum lugar.',
'snackbar_tasks_org_manage_done': 'Pronto',
'snackbar_tasks_org_manage_failed': 'O Tasks.org recusou a alteração',
'snackbar_tasks_org_manage_read_only': 'Essa lista é somente leitura',
}
names = list(PT)
src = open('app/src/main/res/values/strings.xml').read()
absent = [n for n in names if f'<string name="{n}"' not in src]
assert not absent, f"no English string for: {absent}"
ph = lambda s: sorted(re.findall(r'%\d\$[sd]', s))
lines = []
for n in names:
    en = re.search(r'<string name="%s"[^>]*>(.*?)</string>' % re.escape(n), src, re.S).group(1)
    assert ph(en) == ph(PT[n]), (n, en, PT[n])
    t = PT[n].replace("'", "\\'").replace('&', '&amp;').replace('<', '&lt;')
    lines.append(f'    <string name="{n}">{t}</string>')
body = '\n'.join(lines)
block = ('\n    <!-- FORK:tasks-org Strings of the Tasks.org integration. Generated from the English ones in\n'
         '         values/strings.xml; keep both in step when one of them changes. -->\n' + body + '\n')
for loc in ('values-pt-rBR', 'values-pt'):
    p = f'app/src/main/res/{loc}/strings.xml'
    s = open(p).read()
    if 'FORK:tasks-org Strings of the Tasks.org' in s:
        s = re.sub(r'\n    <!-- FORK:tasks-org Strings of the Tasks\.org.*?(?=\n</resources>)', '', s, flags=re.S)
    i = s.rindex('</resources>')
    s = s[:i].rstrip('\n') + '\n' + block + '\n' + s[i:]
    open(p, 'w').write(s)
print('ok', len(names))
