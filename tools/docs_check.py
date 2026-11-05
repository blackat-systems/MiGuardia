"""Check the live documentation contract; archived text is never an instruction.

Standard-library only. Does not invoke Git, Gradle, network or device tooling.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
ENTRY_FILES = ("AGENTS.md", "docs/STATUS.md")
GUIDANCE_FILES = ENTRY_FILES + (
    "docs/LECTURAS_POR_TAREA.md", "docs/WORKFLOW.md", "docs/VALIDACION.md",
    "docs/prompts/README.md",
)
LIMIT = 40_000
SNAPSHOT = "docs/archive/manifest.json"
ARCHIVE_LOCATION = ".local-evidence/archive-location.json"
STALE = (
    "MAIN 2.0 activo", "deuda temporal del árbol actual",
    "Permanece sin integrar hasta completar su puerta física",
    "Internet se usa únicamente para Clima",
)


def read(root: Path, name: str) -> str:
    return (root / name).read_text(encoding="utf-8-sig")


def metadata(text: str, field: str) -> str | None:
    match = re.search(rf"^- {re.escape(field)}: (.+)$", text, re.M)
    return match.group(1).strip() if match else None


def inside(root: Path, name: str) -> Path | None:
    path = (root / name).resolve()
    return path if path.is_relative_to(root.resolve()) else None


def external_backup_root(root: Path) -> Path | None:
    try:
        locator = json.loads(read(root, ARCHIVE_LOCATION))
        backup = Path(locator["checkout_root"])
        if not backup.is_absolute():
            return None
        backup = backup.resolve()
    except (OSError, ValueError, KeyError, TypeError, RuntimeError):
        return None
    return backup if backup.is_dir() and not backup.is_relative_to(root.resolve()) else None


def adr_index(root: Path) -> str:
    lines = [
        "# Decisiones técnicas por tema", "",
        "Índice generado desde los metadatos de cada ADR por `python tools/docs_check.py --update-index`.",
        "VIGENTE define un contrato; PARCIAL conserva sólo su alcance indicado; HISTORICO remite a sus sucesores.",
        "La ejecución y evidencia actuales viven únicamente en `docs/STATUS.md`. Los cuerpos con decisiones antiguas se leen con esa vigencia.",
        "", "| ADR / tema | Vigencia | Alcance actual | Sucesores |", "|---|---|---|---|",
    ]
    for path in sorted((root / "docs/adr").glob("[0-9]*.md")):
        text = path.read_text(encoding="utf-8-sig")
        title = text.splitlines()[0].lstrip("# ")
        scope = (metadata(text, "Alcance vigente") or "FALTA METADATO").replace("|", "\\|")
        successors = metadata(text, "Sucesores") or "FALTA METADATO"
        lines.append(f"| [{title}]({path.name}) | {metadata(text, 'Vigencia')} | {scope} | {successors} |")
    lines += ["", "Los contratos laborales principales son ADR 0020 y 0023–0030; proyección y consumidores 0031–0033; informes, copias, bloqueo, Ayuda y Clima 0034–0038; distribución y actualizaciones 0039–0040.", ""]
    return "\n".join(lines)


def live_prompt_names(root: Path) -> set[str]:
    """The contract index owns this list; adding a task needs no code change."""
    index = root / "docs/prompts/README.md"
    content = index.read_text(encoding="utf-8-sig") if index.is_file() else ""
    return {"README.md", "TEMPLATE.md"} | set(re.findall(
        r"\| `docs/prompts/([A-Za-z0-9_-]+\.md)` \|", content
    ))


def active_markdown(root: Path) -> list[Path]:
    paths = [root / "AGENTS.md", root / "README.md"]
    paths += list((root / "docs").glob("*.md"))
    paths += list((root / "docs/adr").glob("*.md"))
    paths += list((root / "docs/sectores").glob("*.md"))
    paths += [root / "docs/prompts" / name for name in sorted(live_prompt_names(root))]
    return [p for p in paths if p.is_file()]


def check(root: Path, *, snapshots: bool = True) -> dict:
    problems: list[str] = []
    sizes = {}
    for name in GUIDANCE_FILES:
        path = root / name
        if not path.is_file():
            problems.append(f"Falta fuente de entrada: {name}")
        else:
            sizes[name] = len(path.read_text(encoding="utf-8-sig"))
    if sum(sizes.values()) > LIMIT:
        problems.append(f"Guía de arranque supera {LIMIT} caracteres; mover detalle a lectura por impacto")

    status_path = root / "docs/STATUS.md"
    if status_path.exists():
        status = status_path.read_text(encoding="utf-8-sig")
        for field in ("Bloque autorizado", "Estado", "Contrato", "Base verificada", "Actualización"):
            if len(re.findall(rf"^- {re.escape(field)}:", status, re.M)) != 1:
                problems.append(f"STATUS necesita exactamente un campo {field}")
        block = metadata(status, "Bloque autorizado")
        contract = metadata(status, "Contrato")
        if metadata(status, "Estado") not in {
            "EN_CURSO", "VERIFICADO_LOCALMENTE", "CERRADO", "BLOQUEADO", "PAUSADO_POR_USUARIO",
        }:
            problems.append("Estado operativo no reconocido")
        if not re.fullmatch(r"[0-9a-f]{40}", metadata(status, "Base verificada") or ""):
            problems.append("STATUS requiere SHA completo de base verificada")
        if not contract or not inside(root, contract) or not (root / contract).is_file():
            problems.append("Contrato autorizado ausente o fuera del proyecto")
        elif Path(contract).stem != block:
            problems.append("Bloque y contrato de STATUS no coinciden")
        index = root / "docs/prompts/README.md"
        authorized = re.findall(r"\| `([^`]+)` \| AUTORIZADO", index.read_text(encoding="utf-8-sig")) if index.exists() else []
        if authorized != [contract]:
            problems.append("El índice debe autorizar sólo el contrato indicado por STATUS")

    refs = 0
    for path in active_markdown(root):
        text = path.read_text(encoding="utf-8-sig")
        relative = path.relative_to(root).as_posix()
        # Exact repository paths only. Excludes prose, examples, globs and external URLs.
        for target in re.findall(r"`([^`\n]+)`", text):
            if re.fullmatch(r"(?:docs|tools|app|core)/[A-Za-z0-9_./-]+\.(?:md|json|kt|py)", target):
                refs += 1
                dest = inside(root, target)
                if not dest or not dest.is_file():
                    problems.append(f"Referencia rota: {relative} -> {target}")
        for target in re.findall(r"\[[^\]]+\]\(([^)]+)\)", text):
            target = target.split("#", 1)[0]
            if not target or "://" in target or target.startswith("mailto:"):
                continue
            refs += 1
            dest = (path.parent / target).resolve()
            if not dest.is_relative_to(root.resolve()) or not dest.exists():
                problems.append(f"Enlace roto: {relative} -> {target}")
        # State prohibitions apply to live entrypoints, not dated ADR history.
        if relative in GUIDANCE_FILES or relative == "README.md":
            for phrase in STALE:
                if phrase in text:
                    problems.append(f"Estado antiguo en fuente activa {relative}: {phrase}")

    graph = {}
    for path in sorted((root / "docs/adr").glob("[0-9]*.md")):
        text = path.read_text(encoding="utf-8-sig")
        kind = metadata(text, "Vigencia")
        targets = re.findall(r"`([^`]+)`", metadata(text, "Sucesores") or "")
        if kind not in {"VIGENTE", "PARCIAL", "HISTORICO"}:
            problems.append(f"ADR sin vigencia válida: {path.name}")
        if kind in {"PARCIAL", "HISTORICO"} and not targets:
            problems.append(f"ADR reemplazado sin sucesor: {path.name}")
        if not metadata(text, "Alcance vigente"):
            problems.append(f"ADR sin alcance de vigencia: {path.name}")
        key = path.relative_to(root).as_posix()
        graph[key] = targets
        for target in targets:
            if target == key or not (root / target).is_file():
                problems.append(f"Sucesor inválido: {key} -> {target}")

    def visit(node: str, stack: frozenset[str]) -> None:
        if node in stack:
            problems.append(f"Ciclo de sucesión ADR: {node}")
            return
        for child in graph.get(node, []):
            visit(child, stack | {node})
    for node in graph:
        visit(node, frozenset())
    index = root / "docs/adr/README.md"
    if not index.is_file() or index.read_text(encoding="utf-8") != adr_index(root):
        problems.append("Índice ADR desactualizado; ejecutar --update-index")

    verified_snapshots = 0
    manifest_path = root / SNAPSHOT
    if snapshots and manifest_path.is_file():
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        storage = manifest.get("storage", "internal")
        if storage == "internal":
            snapshot_root = root
        elif storage == "external-backup":
            snapshot_root = external_backup_root(root)
            if snapshot_root is None:
                problems.append(f"Localizador de archivo histórico externo ausente o inválido: {ARCHIVE_LOCATION}")
        else:
            snapshot_root = None
            problems.append("Almacenamiento del archivo histórico no reconocido")
        if snapshot_root is not None:
            for item in manifest["files"]:
                name = item["snapshot"]
                path = None if storage == "external-backup" and Path(name).anchor else inside(snapshot_root, name)
                if not path or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != item["sha256"]:
                    problems.append(f"Snapshot histórico alterado: {item['source']}")
                else:
                    verified_snapshots += 1
                source = item["source"]
                if storage == "internal" and source.startswith("docs/prompts/") and Path(source).name not in live_prompt_names(root):
                    stub = root / source
                    if not stub.is_file():
                        problems.append(f"Falta ruta de compatibilidad: {source}")
                    else:
                        content = stub.read_text(encoding="utf-8-sig")
                        if name not in content or len(content) > 1600:
                            problems.append(f"Contrato cerrado vuelve a cargar instrucciones: {source}")
        if list((root / "docs/archive").rglob("AGENTS.md")):
            problems.append("El archivo histórico no debe introducir un AGENTS.md ejecutable por ámbito")
    elif snapshots:
        problems.append("Falta manifiesto de preservación histórica")

    return {"ok": not problems, "problems": problems, "snapshot_check": "requested" if snapshots else "not_requested",
            "entry_chars": sum(sizes.get(n, 0) for n in ENTRY_FILES),
            "guidance_chars": sum(sizes.values()), "budget_chars": LIMIT, "reference_count": refs,
            "adr_count": len(graph), "snapshots_verified": verified_snapshots}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--update-index", action="store_true", help="Regenera sólo el índice ADR desde sus metadatos")
    parser.add_argument("--live-only", action="store_true", help="Comprueba sólo documentos vigentes, sin acreditar el respaldo histórico local")
    args = parser.parse_args()
    if args.update_index:
        (ROOT / "docs/adr/README.md").write_text(adr_index(ROOT), encoding="utf-8")
    result = check(ROOT, snapshots=not args.live_only)
    print(json.dumps(result, ensure_ascii=True, indent=2))
    return 0 if result["ok"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
