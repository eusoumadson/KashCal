#!/usr/bin/env python3
"""Unit tests for the release automation tools."""

import sys
import unittest
from pathlib import Path
from unittest.mock import patch

# Ensure app/src/fork/tools is importable
TOOLS_DIR = Path(__file__).resolve().parent
if str(TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(TOOLS_DIR))

import release


class TestVersionResolution(unittest.TestCase):
    def test_bump_version_code_integer(self):
        self.assertEqual(release.bump_version_code(819), 820)
        self.assertEqual(release.bump_version_code(0), 1)

    def test_bump_version_code_string(self):
        self.assertEqual(release.bump_version_code("819"), 820)
        self.assertEqual(release.bump_version_code("1000"), 1001)

    def test_resolve_version_name_no_collision(self):
        existing_tags = ["v2026.11.04", "v2026.11.03"]
        resolved = release.resolve_version_name("2026.11.05", existing_tags)
        self.assertEqual(resolved, "2026.11.05")

    def test_resolve_version_name_single_collision(self):
        existing_tags = ["v2026.11.05", "v2026.11.04"]
        resolved = release.resolve_version_name("2026.11.05", existing_tags)
        self.assertEqual(resolved, "2026.11.05.1")

    def test_resolve_version_name_multiple_collisions(self):
        existing_tags = ["v2026.11.05", "v2026.11.05.1", "v2026.11.05.2"]
        resolved = release.resolve_version_name("2026.11.05", existing_tags)
        self.assertEqual(resolved, "2026.11.05.3")

    def test_resolve_version_name_without_v_prefix_in_tags(self):
        existing_tags = ["2026.11.05"]
        resolved = release.resolve_version_name("2026.11.05", existing_tags)
        self.assertEqual(resolved, "2026.11.05.1")

    def test_parse_and_serialize_version_properties(self):
        content = (
            "# KashCal version properties\n"
            "VERSION_CODE=819\n"
            "VERSION_NAME=2026.11.05\n"
        )
        props = release.parse_version_properties(content)
        self.assertEqual(props.get("VERSION_CODE"), "819")
        self.assertEqual(props.get("VERSION_NAME"), "2026.11.05")

        new_content = release.serialize_version_properties(820, "2026.11.05.1")
        self.assertEqual(
            new_content,
            "VERSION_CODE=820\nVERSION_NAME=2026.11.05.1\n",
        )


class TestPreflightChecks(unittest.TestCase):
    def test_preflight_clean_and_main(self):
        is_clean, errors = release.check_preflight("", "main")
        self.assertTrue(is_clean)
        self.assertEqual(errors, [])

    def test_preflight_dirty_working_tree(self):
        is_clean, errors = release.check_preflight(" M app/build.gradle.kts", "main")
        self.assertFalse(is_clean)
        self.assertTrue(any("não comitadas" in err for err in errors))

    def test_preflight_wrong_branch(self):
        is_clean, errors = release.check_preflight("", "feat/something")
        self.assertFalse(is_clean)
        self.assertTrue(any("branch 'main'" in err for err in errors))

    def test_preflight_dirty_and_wrong_branch(self):
        is_clean, errors = release.check_preflight("?? new_file.txt", "feat/something")
        self.assertFalse(is_clean)
        self.assertEqual(len(errors), 2)


class TestOpenSpecChangelogExtraction(unittest.TestCase):
    def test_extract_changelog_from_what_changes(self):
        proposal = (
            "# Proposal: refine-split-widget\n\n"
            "## Why\nAlguma razão.\n\n"
            "## What Changes\n"
            "- Widget: compactar cabeçalho horizontalmente.\n"
            "- App: empilhar tarefas de dia todo.\n\n"
            "## Confirmed Acceptance Criteria\n"
            "- AC-01: OK\n"
        )
        extracted = release.extract_changelog_from_proposal(proposal)
        self.assertIn("### Refine Split Widget", extracted)
        self.assertIn("- Widget: compactar cabeçalho horizontalmente.", extracted)
        self.assertIn("- App: empilhar tarefas de dia todo.", extracted)

    def test_extract_changelog_from_scope_in_fallback(self):
        proposal = (
            "# Proposal: quick-fix\n\n"
            "## Scope\n"
            "- **In**:\n"
            "  - Corrigido alinhamento do texto.\n"
            "  - Removido espaçamento extra.\n"
            "- **Out**:\n"
            "  - Nenhuma outra tela afetada.\n"
        )
        extracted = release.extract_changelog_from_proposal(proposal)
        self.assertIn("### Quick Fix", extracted)
        self.assertIn("- Corrigido alinhamento do texto.", extracted)
        self.assertIn("- Removido espaçamento extra.", extracted)
        self.assertNotIn("Nenhuma outra tela", extracted)

    def test_extract_changelog_empty_content(self):
        self.assertEqual(release.extract_changelog_from_proposal(""), "")
        self.assertEqual(release.extract_changelog_from_proposal("   "), "")

    def test_extract_changelog_no_bullets(self):
        proposal = "# Proposal: empty\n\nTexto descritivo sem listas de marcadores."
        self.assertEqual(release.extract_changelog_from_proposal(proposal), "")

    def test_fallback_manual_notes(self):
        inputs = iter(["Item manual 1", "Item manual 2", "", ""])
        result = release.fallback_manual_notes(input_func=lambda: next(inputs), show_prompt=False)
        self.assertEqual(result, "Item manual 1\nItem manual 2")


class TestChangelogPrepend(unittest.TestCase):
    def test_prepend_to_existing_changelog(self):
        existing = (
            "## [2026.11.05]\n\n"
            "### Versão Anterior\n"
            "- Nota anterior\n"
        )
        notes = (
            "### Nova Funcionalidade\n"
            "- Item novo 1\n"
            "- Item novo 2"
        )
        prepended = release.prepend_changelog(existing, "2026.11.05.1", notes)
        expected = (
            "## [2026.11.05.1]\n\n"
            "### Nova Funcionalidade\n"
            "- Item novo 1\n"
            "- Item novo 2\n\n"
            "## [2026.11.05]\n\n"
            "### Versão Anterior\n"
            "- Nota anterior\n"
        )
        self.assertEqual(prepended, expected)

    def test_prepend_preserves_top_level_h1_header(self):
        existing = (
            "# Changelog\n\n"
            "## [2026.11.05]\n\n"
            "- Item antigo\n"
        )
        notes = "- Item novo"
        prepended = release.prepend_changelog(existing, "2026.11.06", notes)
        self.assertTrue(prepended.startswith("# Changelog\n\n## [2026.11.06]\n\n- Item novo\n\n## [2026.11.05]"))


class TestFindLatestOpenSpecProposal(unittest.TestCase):
    def test_find_latest_in_temp_dir(self):
        import tempfile
        with tempfile.TemporaryDirectory() as tmpdir:
            tmppath = Path(tmpdir)
            archive = tmppath / "openspec" / "changes" / "archive"
            dir1 = archive / "2026-11-01-feature-a"
            dir2 = archive / "2026-11-03-feature-b"
            dir1.mkdir(parents=True)
            dir2.mkdir(parents=True)
            (dir1 / "proposal.md").write_text("# Proposal: feature-a\n")
            (dir2 / "proposal.md").write_text("# Proposal: feature-b\n")

            latest = release.find_latest_openspec_proposal(tmppath)
            self.assertIsNotNone(latest)
            change_name, proposal_path = latest
            self.assertEqual(change_name, "2026-11-03-feature-b")
            self.assertEqual(proposal_path, dir2 / "proposal.md")


class TestReleaseCLI(unittest.TestCase):
    def test_dry_run_cli_execution(self):
        import subprocess
        root = release.get_repo_root()
        script = root / "app" / "src" / "fork" / "tools" / "release.py"
        res = subprocess.run(
            [sys.executable, str(script), "--dry-run"],
            cwd=root,
            capture_output=True,
            text=True,
        )
        self.assertEqual(res.returncode, 0, f"stdout: {res.stdout}\nstderr: {res.stderr}")
        self.assertIn("PLANO DE EXECUÇÃO (DRY-RUN", res.stdout)
        self.assertIn("Simulação concluída com sucesso", res.stdout)
        self.assertIn("git commit -am \"chore(release):", res.stdout)
        self.assertIn("git push fork main", res.stdout)


if __name__ == "__main__":
    unittest.main()
