"""Regression examples for the documentation failures found in the audit."""
from pathlib import Path
import hashlib
import importlib.util
import json
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("docs_check", Path(__file__).parents[1] / "docs_check.py")
docs = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(docs)


class DocumentationRegressionTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for name in docs.GUIDANCE_FILES:
            self.write(name, "# Documento vigente\n")
        self.write("docs/STATUS.md", "\n".join([
            "# Estado", "- Bloque autorizado: DEMO", "- Estado: EN_CURSO",
            "- Contrato: docs/prompts/DEMO.md", "- Base verificada: " + "a" * 40,
            "- Actualización: 2026-09-06", "",
        ]))
        self.write("docs/prompts/DEMO.md", "# Alcance autorizado\n")
        self.write("docs/prompts/README.md", "| `docs/prompts/DEMO.md` | AUTORIZADO |\n")
        self.write("docs/adr/0001-example.md", "# ADR 0001\n- Vigencia: VIGENTE\n- Sucesores: ninguno\n- Alcance vigente: Datos locales\n")
        self.index()

    def write(self, name, content):
        p = self.root / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(content, encoding="utf-8")

    def index(self):
        self.write("docs/adr/README.md", docs.adr_index(self.root))

    def result(self):
        return docs.check(self.root, snapshots=False)

    def test_consistent_single_source_passes(self):
        self.assertTrue(self.result()["ok"], self.result()["problems"])

    def test_second_authorized_contract_is_not_silently_accepted(self):
        self.write("docs/prompts/README.md", "| `docs/prompts/DEMO.md` | AUTORIZADO |\n| `docs/prompts/OTHER.md` | AUTORIZADO |\n")
        self.assertTrue(any("sólo el contrato" in p for p in self.result()["problems"]))

    def test_two_current_status_fields_are_rejected(self):
        p = self.root / "docs/STATUS.md"
        p.write_text(p.read_text(encoding="utf-8") + "- Estado: CERRADO\n", encoding="utf-8")
        self.assertTrue(any("exactamente un campo Estado" in p for p in self.result()["problems"]))

    def test_old_next_step_is_rejected_in_status_but_not_history(self):
        phrase = "Permanece sin integrar hasta completar su puerta física"
        self.write("docs/archive/old.md", phrase)
        self.assertTrue(self.result()["ok"])
        self.write("AGENTS.md", phrase)
        self.assertFalse(self.result()["ok"])

    def test_missing_source_is_not_treated_as_a_valid_link(self):
        self.write("AGENTS.md", "Leer `docs/does-not-exist.md`.\n")
        self.assertTrue(any("Referencia rota" in p for p in self.result()["problems"]))

    def test_new_contract_in_index_is_checked_without_changing_tool(self):
        self.write("docs/prompts/DEMO.md", "Leer `docs/missing-contract.md`.\n")
        self.assertTrue(any("DEMO.md -> docs/missing-contract.md" in p for p in self.result()["problems"]))

    def test_markdown_parent_escape_is_rejected(self):
        self.write("AGENTS.md", "[Otro](../outside.md)\n")
        self.assertTrue(any("Enlace roto" in p for p in self.result()["problems"]))

    def test_reading_budget_catches_return_to_full_history(self):
        self.write("AGENTS.md", "historia " * 5001)
        self.assertTrue(any("supera" in p for p in self.result()["problems"]))

    def test_replaced_adr_needs_successor(self):
        self.write("docs/adr/0001-example.md", "# ADR 0001\n- Vigencia: HISTORICO\n- Sucesores: ninguno\n- Alcance vigente: Antecedente\n")
        self.index()
        self.assertTrue(any("sin sucesor" in p for p in self.result()["problems"]))

    def test_adr_successor_cycle_is_rejected(self):
        for n, other in ((1, 2), (2, 1)):
            self.write(f"docs/adr/000{n}-example.md", f"# ADR {n}\n- Vigencia: PARCIAL\n- Sucesores: `docs/adr/000{other}-example.md`\n- Alcance vigente: Parcial\n")
        self.index()
        self.assertTrue(any("Ciclo" in p for p in self.result()["problems"]))

    def test_index_must_follow_adr_metadata(self):
        self.write("docs/adr/README.md", "Vieja tabla aceptada\n")
        self.assertTrue(any("desactualizado" in p for p in self.result()["problems"]))

    def test_snapshot_hash_detects_rewritten_history(self):
        snapshot = "docs/archive/original.md"
        self.write(snapshot, "Original")
        self.write(docs.SNAPSHOT, json.dumps({"files": [{"source": "AGENTS.md", "snapshot": snapshot,
                   "sha256": hashlib.sha256(b"Original").hexdigest()}]}))
        self.assertTrue(docs.check(self.root)["ok"])
        self.write(snapshot, "Cambiado")
        self.assertTrue(any("alterado" in p for p in docs.check(self.root)["problems"]))


if __name__ == "__main__":
    unittest.main()
