#!/usr/bin/env python3
"""Release automation helper for KashCal Tasks fork.

Automates version bumping, changelog drafting from OpenSpec proposals,
git committing, tagging, and safe pushing.
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import tempfile
from datetime import datetime
from pathlib import Path


def get_repo_root() -> Path:
    """Find repository root by looking for version.properties and .git."""
    current = Path(__file__).resolve().parent
    while current != current.parent:
        if (current / "version.properties").exists() and (current / ".git").exists():
            return current
        current = current.parent
    return Path(__file__).resolve().parents[4]


def parse_version_properties(content: str) -> dict[str, str]:
    """Parse key-value pairs from version.properties content."""
    properties = {}
    for line in content.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" in line:
            key, val = line.split("=", 1)
            properties[key.strip()] = val.strip()
    return properties


def serialize_version_properties(version_code: int, version_name: str) -> str:
    """Format version.properties content."""
    return f"VERSION_CODE={version_code}\nVERSION_NAME={version_name}\n"


def bump_version_code(current_code: int | str) -> int:
    """Increment VERSION_CODE by 1."""
    return int(current_code) + 1


def resolve_version_name(base_date: str, existing_tags: list[str] | set[str]) -> str:
    """Calculate the next VERSION_NAME for a given base_date (YYYY.MM.DD).

    If vYYYY.MM.DD does not exist in existing_tags, returns base_date.
    If it exists, finds the next available suffix .1, .2, etc.
    """
    tag_set = set(existing_tags)
    tag_candidate = f"v{base_date}"
    if tag_candidate not in tag_set and base_date not in tag_set:
        return base_date

    suffix = 1
    while True:
        candidate_name = f"{base_date}.{suffix}"
        if f"v{candidate_name}" not in tag_set and candidate_name not in tag_set:
            return candidate_name
        suffix += 1


def check_preflight(status_output: str, current_branch: str) -> tuple[bool, list[str]]:
    """Validate git working tree cleanliness and current branch.

    Returns (is_valid, error_messages).
    """
    errors = []
    if status_output.strip():
        errors.append("Working tree possui alterações não comitadas.")
    if current_branch.strip() != "main":
        errors.append(
            f"Branch atual é '{current_branch.strip()}', mas releases devem ser executados a partir da branch 'main'."
        )
    return (len(errors) == 0, errors)


def extract_changelog_from_proposal(proposal_text: str) -> str:
    """Extract changelog highlights from proposal.md content."""
    if not proposal_text or not proposal_text.strip():
        return ""

    # 1. Extract proposal title
    title_match = re.search(r"^#\s+Proposal:\s*(.+)$", proposal_text, re.MULTILINE | re.IGNORECASE)
    title = ""
    if title_match:
        raw_slug = title_match.group(1).strip()
        words = raw_slug.replace("-", " ").replace("_", " ").split()
        title = " ".join(w.capitalize() for w in words)

    bullets: list[str] = []

    # 2. Look for '## What Changes' section
    what_changes_match = re.search(
        r"^##\s+What Changes\s*\n(.*?)(?=\n##|\Z)",
        proposal_text,
        re.MULTILINE | re.DOTALL | re.IGNORECASE,
    )
    if what_changes_match:
        section_text = what_changes_match.group(1)
        for line in section_text.splitlines():
            line_str = line.strip()
            if line_str.startswith(("- ", "* ")):
                bullet = line_str[2:].strip()
                if bullet:
                    bullets.append(bullet)

    # 3. If no bullets found in 'What Changes', look for '## Scope' -> 'In:'
    if not bullets:
        scope_match = re.search(
            r"^##\s+Scope\s*\n(.*?)(?=\n##|\Z)",
            proposal_text,
            re.MULTILINE | re.DOTALL | re.IGNORECASE,
        )
        if scope_match:
            scope_text = scope_match.group(1)
            in_scope_match = re.search(
                r"-\s+\*\*In\*\*:(.*?)(?=\n-\s+\*\*Out\*\*|\Z)",
                scope_text,
                re.MULTILINE | re.DOTALL | re.IGNORECASE,
            )
            target_text = in_scope_match.group(1) if in_scope_match else scope_text
            for line in target_text.splitlines():
                line_str = line.strip()
                if line_str.startswith(("- ", "* ")):
                    bullet = line_str[2:].strip()
                    if bullet and not bullet.startswith("**In**") and not bullet.startswith("**Out**"):
                        bullets.append(bullet)

    if not bullets:
        return ""

    header = f"### {title}" if title else "### Mudanças"
    bullet_lines = "\n".join(f"- {b}" for b in bullets)
    return f"{header}\n{bullet_lines}"


def prepend_changelog(existing_content: str, version_name: str, notes: str) -> str:
    """Prepend a new release section to CHANGELOG.md content."""
    entry = f"## [{version_name}]\n\n{notes.strip()}\n\n"
    lines = existing_content.splitlines(keepends=True)
    if lines and lines[0].startswith("# "):
        idx = 1
        while idx < len(lines) and lines[idx].strip() == "":
            idx += 1
        return "".join(lines[:idx]) + entry + "".join(lines[idx:])
    return entry + existing_content


def find_latest_openspec_proposal(repo_root: Path) -> tuple[str, Path] | None:
    """Find the most recent proposal.md in openspec/changes/archive/ or openspec/changes/."""
    search_dirs = [
        repo_root / "openspec" / "changes" / "archive",
        repo_root / "openspec" / "changes",
    ]
    candidates = []
    for s_dir in search_dirs:
        if not s_dir.exists() or not s_dir.is_dir():
            continue
        for item in s_dir.iterdir():
            if item.is_dir() and item.name != "archive":
                proposal = item / "proposal.md"
                if proposal.exists() and proposal.is_file():
                    candidates.append((item.name, proposal))

    if not candidates:
        return None

    # Sort descending by directory name
    candidates.sort(key=lambda c: c[0], reverse=True)
    return candidates[0]


def fallback_manual_notes(input_func=input, show_prompt: bool = True) -> str:
    """Collect changelog notes manually from user."""
    if show_prompt:
        print("Digite as notas do changelog (linha vazia duas vezes para encerrar):")
    lines = []
    empty_count = 0
    while True:
        try:
            line = input_func()
        except EOFError:
            break
        if not line.strip():
            empty_count += 1
            if empty_count >= 2:
                break
        else:
            empty_count = 0
            lines.append(line)
    return "\n".join(lines).strip()


def edit_in_editor(initial_content: str) -> str:
    """Open initial_content in $EDITOR and return edited result."""
    editor = os.environ.get("EDITOR") or os.environ.get("VISUAL") or "nano"
    with tempfile.NamedTemporaryFile(suffix=".md", mode="w+", delete=False, encoding="utf-8") as tf:
        tf.write(initial_content)
        tf_path = Path(tf.name)
    try:
        subprocess.run([editor, str(tf_path)], check=True)
        edited = tf_path.read_text(encoding="utf-8").strip()
        return edited if edited else initial_content
    finally:
        if tf_path.exists():
            tf_path.unlink()


def ask_input(prompt_text: str, default: str = "") -> str:
    """Prompt user for input with default fallback for non-tty environments."""
    if not sys.stdin.isatty():
        return default
    try:
        val = input(prompt_text).strip()
        return val if val else default
    except (EOFError, KeyboardInterrupt):
        print("\nOperação cancelada pelo usuário.")
        sys.exit(1)


def run_command(cmd: list[str], cwd: Path | None = None) -> str:
    """Run a shell command and return stdout."""
    res = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, check=True)
    return res.stdout


def main() -> None:
    parser = argparse.ArgumentParser(description="Assistente de Release - KashCal Tasks")
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Simula o processo completo sem efetuar alterações em disco ou no git",
    )
    parser.add_argument(
        "--version",
        "-v",
        dest="custom_version",
        help="Versão explícita para o release (ex: 2026.11.05.1)",
    )
    args = parser.parse_args()

    repo_root = get_repo_root()
    print(f"📦 Repositório: {repo_root}")

    # 1. Pre-flight checks
    git_status = run_command(["git", "status", "--porcelain"], cwd=repo_root)
    current_branch = run_command(["git", "branch", "--show-current"], cwd=repo_root).strip()

    is_clean, errors = check_preflight(git_status, current_branch)
    if not is_clean:
        if args.dry_run:
            print("⚠️  [DRY-RUN] Avisos de pré-voo detectados (ignorados para simulação):")
            for err in errors:
                print(f"    - {err}")
        else:
            print("❌ Checagens de pré-voo falharam:")
            for err in errors:
                print(f"    - {err}")
            sys.exit(1)
    else:
        print(f"✅ Checagens de pré-voo OK (branch: '{current_branch}', working tree limpo)")

    # 2. Version determination
    version_file = repo_root / "version.properties"
    if not version_file.exists():
        print(f"❌ Arquivo não encontrado: {version_file}")
        sys.exit(1)

    version_props = parse_version_properties(version_file.read_text(encoding="utf-8"))
    curr_code = version_props.get("VERSION_CODE", "0")
    curr_name = version_props.get("VERSION_NAME", "")
    next_code = bump_version_code(curr_code)

    today_str = datetime.now().strftime("%Y.%m.%d")
    existing_tags = run_command(["git", "tag", "-l"], cwd=repo_root).splitlines()
    suggested_name = resolve_version_name(today_str, existing_tags)

    if args.custom_version:
        final_version_name = args.custom_version
    elif args.dry_run:
        final_version_name = suggested_name
    else:
        prompt_msg = (
            f"Versão atual: {curr_name} ({curr_code})\n"
            f"Nova versão sugerida: [{suggested_name}] (código: {next_code})\n"
            "Pressione [Enter] para aceitar ou digite uma versão customizada: "
        )
        entered = ask_input(prompt_msg, default=suggested_name)
        final_version_name = entered if entered else suggested_name

    print(f"\n🎯 Versão de lançamento definida: {final_version_name} (VERSION_CODE={next_code})")

    # 3. OpenSpec proposal extraction & changelog draft
    latest_proposal = find_latest_openspec_proposal(repo_root)
    draft_notes = ""
    change_slug = ""
    if latest_proposal:
        change_slug, proposal_path = latest_proposal
        draft_notes = extract_changelog_from_proposal(proposal_path.read_text(encoding="utf-8"))

    final_notes = ""
    if args.dry_run:
        final_notes = draft_notes if draft_notes else f"### Lançamento {final_version_name}\n- Atualizações e melhorias gerais."
    else:
        if draft_notes:
            print(f"\n📝 Minuta gerada a partir da proposta OpenSpec '{change_slug}':")
            print("--------------------------------------------------")
            print(draft_notes)
            print("--------------------------------------------------")
            print("Como deseja prosseguir com as notas do changelog?")
            print("  [1] Aceitar minuta (padrão)")
            print("  [2] Abrir no $EDITOR para ajustes")
            print("  [3] Redigir manualmente do zero")
            choice = ask_input("Escolha [1/2/3] (padrão 1): ", default="1")
            if choice == "2":
                final_notes = edit_in_editor(draft_notes)
            elif choice == "3":
                final_notes = fallback_manual_notes()
            else:
                final_notes = draft_notes
        else:
            print("\n⚠️  Nenhuma proposta OpenSpec recente com notas automáticas encontrada.")
            print("Como deseja informar as notas?")
            print("  [1] Abrir no $EDITOR (padrão)")
            print("  [2] Digitar diretamente no terminal")
            choice = ask_input("Escolha [1/2] (padrão 1): ", default="1")
            if choice == "2":
                final_notes = fallback_manual_notes()
            else:
                final_notes = edit_in_editor(f"### Lançamento {final_version_name}\n- ")

    if not final_notes.strip():
        final_notes = f"### Lançamento {final_version_name}\n- Atualizações e melhorias gerais."

    commit_message = f"chore(release): bump version to {final_version_name} ({next_code})"
    tag_name = f"v{final_version_name}"

    # 4. Dry-run display or mutation
    if args.dry_run:
        print("\n" + "=" * 60)
        print("📋 PLANO DE EXECUÇÃO (DRY-RUN - NENHUMA ALTERAÇÃO REALIZADA):")
        print("=" * 60)
        print(f"\n[1] Atualização em 'version.properties':")
        print(serialize_version_properties(next_code, final_version_name).strip())
        print(f"\n[2] Inserção no topo de 'CHANGELOG.md':")
        print(f"## [{final_version_name}]\n\n{final_notes.strip()}")
        print(f"\n[3] Criação de commit git:")
        print(f"    git commit -am \"{commit_message}\"")
        print(f"\n[4] Criação de tag git:")
        print(f"    git tag -a {tag_name} -m \"Release {tag_name}\"")
        print(f"\n[5] Publicação remota:")
        print(f"    git push fork main")
        print(f"    git push fork {tag_name}")
        print("=" * 60)
        print("✨ [DRY-RUN] Simulação concluída com sucesso.")
        return

    # Real execution
    print("\n💾 Aplicando alterações nos arquivos locais...")
    version_file.write_text(
        serialize_version_properties(next_code, final_version_name),
        encoding="utf-8",
    )

    changelog_file = repo_root / "CHANGELOG.md"
    existing_changelog = changelog_file.read_text(encoding="utf-8") if changelog_file.exists() else ""
    new_changelog = prepend_changelog(existing_changelog, final_version_name, final_notes)
    changelog_file.write_text(new_changelog, encoding="utf-8")

    print(f"📌 Criando commit: {commit_message}")
    subprocess.run(["git", "add", "version.properties", "CHANGELOG.md"], cwd=repo_root, check=True)
    subprocess.run(["git", "commit", "-m", commit_message], cwd=repo_root, check=True)

    print(f"🏷️  Criando tag anotada: {tag_name}")
    subprocess.run(["git", "tag", "-a", tag_name, "-m", f"Release {tag_name}"], cwd=repo_root, check=True)

    print("\n" + "=" * 60)
    print("🚀 Pronto para envio ao repositório remoto:")
    print(f"    git push fork main")
    print(f"    git push fork {tag_name}")
    print("=" * 60)

    push_confirm = ask_input("\nDeseja publicar agora no repositório remoto? [s/N]: ", default="n")
    if push_confirm.lower() in ("s", "sim", "y", "yes"):
        print("Enviando branch e tag para o remote 'fork'...")
        subprocess.run(["git", "push", "fork", "main"], cwd=repo_root, check=True)
        subprocess.run(["git", "push", "fork", tag_name], cwd=repo_root, check=True)
        print("🎉 Lançamento publicado com sucesso!")
    else:
        print("⚠️  Push cancelado pelo usuário. O commit e a tag locais foram mantidos.")


if __name__ == "__main__":
    main()
