from __future__ import annotations

import json
import os
from dataclasses import dataclass, field
from pathlib import Path
from typing import List


def _repo_root_from_here() -> Path:
    # gift_editor/config.py -> parents[4] = repo (gift_editor, src, villager-gift-editor, tools, repo)
    here = Path(__file__).resolve()
    return here.parents[4]


def default_villagers_dir() -> Path:
    override = os.environ.get("AETHERHAVEN_VILLAGERS")
    if override:
        return Path(override).resolve()
    root = _repo_root_from_here()
    p = root / "src" / "main" / "resources" / "Server" / "Aetherhaven" / "Villagers"
    if p.is_dir():
        return p
    for anc in Path(__file__).resolve().parents:
        q = anc / "src" / "main" / "resources" / "Server" / "Aetherhaven" / "Villagers"
        if q.is_dir():
            return q
    return p


def default_item_roots() -> List[Path]:
    env = os.environ.get("AETHERHAVEN_GIFT_ITEM_ROOTS")
    if env:
        return [Path(s.strip()).resolve() for s in env.split(os.pathsep) if s.strip()]
    root = _repo_root_from_here()
    res = root / "src" / "main" / "resources"
    if res.is_dir():
        return [res.resolve()]
    for anc in Path(__file__).resolve().parents:
        q = anc / "src" / "main" / "resources"
        if q.is_dir():
            return [q.resolve()]
    return [res.resolve()]


def config_file_path() -> Path:
    """Per-tool checkout: `tools/villager-gift-editor/gift_editor_config.json`."""
    return Path(__file__).resolve().parents[2] / "gift_editor_config.json"


@dataclass
class AppConfig:
    item_roots: List[str] = field(default_factory=list)
    villagers_dir: str | None = None
    # Last path used for Save/Load gift template (optional).
    last_gift_template_path: str | None = None

    def resolved_item_roots(self) -> List[Path]:
        roots = [Path(p).resolve() for p in self.item_roots if p.strip()]
        if not roots:
            roots = default_item_roots()
        return roots

    def resolved_villagers_dir(self) -> Path:
        if self.villagers_dir and self.villagers_dir.strip():
            return Path(self.villagers_dir).resolve()
        return default_villagers_dir()

    @staticmethod
    def load() -> "AppConfig":
        path = config_file_path()
        if not path.is_file():
            return AppConfig(
                item_roots=[str(p) for p in default_item_roots()],
                villagers_dir=None,
                last_gift_template_path=None,
            )
        try:
            raw = json.loads(path.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, OSError):
            return AppConfig(
                item_roots=[str(p) for p in default_item_roots()],
                villagers_dir=None,
                last_gift_template_path=None,
            )
        roots = raw.get("item_roots")
        if not isinstance(roots, list):
            roots = [str(p) for p in default_item_roots()]
        else:
            roots = [str(x) for x in roots if isinstance(x, str) and x.strip()]
        vd = raw.get("villagers_dir")
        if vd is not None and not isinstance(vd, str):
            vd = None
        lgt = raw.get("last_gift_template_path")
        if lgt is not None and not isinstance(lgt, str):
            lgt = None
        return AppConfig(
            item_roots=roots,
            villagers_dir=vd,
            last_gift_template_path=lgt.strip() if isinstance(lgt, str) and lgt.strip() else None,
        )

    def save(self) -> None:
        path = config_file_path()
        data = {
            "item_roots": self.item_roots,
            "villagers_dir": self.villagers_dir,
            "last_gift_template_path": self.last_gift_template_path,
        }
        path.write_text(
            json.dumps(data, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
