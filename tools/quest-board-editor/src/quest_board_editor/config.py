from __future__ import annotations

import json
import os
from dataclasses import dataclass, field
from pathlib import Path
from typing import List


def _repo_root_from_here() -> Path:
    # quest_board_editor/config.py -> parents[4] = repo
    here = Path(__file__).resolve()
    return here.parents[4]


def _find_file(*parts: str) -> Path:
    root = _repo_root_from_here()
    p = root.joinpath(*parts)
    if p.is_file():
        return p
    for anc in Path(__file__).resolve().parents:
        q = anc.joinpath(*parts)
        if q.is_file():
            return q
    return p


def _find_dir(*parts: str) -> Path:
    root = _repo_root_from_here()
    p = root.joinpath(*parts)
    if p.is_dir():
        return p
    for anc in Path(__file__).resolve().parents:
        q = anc.joinpath(*parts)
        if q.is_dir():
            return q
    return p


def default_quest_board_path() -> Path:
    override = os.environ.get("AETHERHAVEN_QUEST_BOARD")
    if override:
        return Path(override).resolve()
    sub = _find_file(
        "subplugin-assets", "Quests", "Server", "Aetherhaven", "quest_board.json"
    )
    if sub.is_file():
        return sub
    return _find_file(
        "src", "main", "resources", "Server", "Aetherhaven", "quest_board.json"
    )


def default_world_board_path() -> Path:
    override = os.environ.get("AETHERHAVEN_WORLD_QUEST_BOARD")
    if override:
        return Path(override).resolve()
    return _find_file(
        "subplugin-assets",
        "WorldNpcs",
        "Server",
        "Aetherhaven",
        "WorldQuestBoards",
        "hub_default.json",
    )


def default_villagers_dir() -> Path:
    override = os.environ.get("AETHERHAVEN_VILLAGERS")
    if override:
        return Path(override).resolve()
    return _find_dir("src", "main", "resources", "Server", "Aetherhaven", "Villagers")


def default_lang_path() -> Path:
    override = os.environ.get("AETHERHAVEN_QUEST_BOARD_LANG")
    if override:
        return Path(override).resolve()
    return _find_file(
        "src",
        "main",
        "resources",
        "Server",
        "Languages",
        "en-US",
        "aetherhaven_quest_board.lang",
    )


def default_world_lang_path() -> Path:
    override = os.environ.get("AETHERHAVEN_WORLD_QUEST_BOARD_LANG")
    if override:
        return Path(override).resolve()
    return _find_file(
        "src",
        "main",
        "resources",
        "Server",
        "Languages",
        "en-US",
        "aetherhaven_world_quest_board.lang",
    )


def default_item_roots() -> List[Path]:
    env = os.environ.get("AETHERHAVEN_QUEST_ITEM_ROOTS") or os.environ.get(
        "AETHERHAVEN_GIFT_ITEM_ROOTS"
    )
    if env:
        return [Path(s.strip()).resolve() for s in env.split(os.pathsep) if s.strip()]

    roots: List[Path] = []
    repo = _repo_root_from_here()

    # Vanilla Hytale assets (sibling checkout / known local path).
    hytale_candidates = [
        repo.parent / "HytaleSourceCode" / "hytale-shared-source" / "HytaleAssets",
        Path(
            r"C:\Users\gchou\OneDrive\Documents\Hytale-Modding\HytaleSourceCode"
            r"\hytale-shared-source\HytaleAssets"
        ),
    ]
    for c in hytale_candidates:
        if c.is_dir() and c not in roots:
            roots.append(c.resolve())

    res = repo / "src" / "main" / "resources"
    if res.is_dir():
        roots.append(res.resolve())
    else:
        hit = _find_dir("src", "main", "resources")
        if hit.is_dir():
            roots.append(hit.resolve())

    return roots


def config_file_path() -> Path:
    return Path(__file__).resolve().parents[2] / "quest_board_editor_config.json"


def detect_board_kind(data: dict) -> str:
    """Return 'town' or 'world' from JSON shape."""
    if isinstance(data.get("pool"), list) and "villagers" not in data:
        return "world"
    if data.get("profileId") and isinstance(data.get("pool"), list):
        return "world"
    return "town"


@dataclass
class AppConfig:
    quest_board_path: str | None = None
    world_board_path: str | None = None
    lang_path: str | None = None
    world_lang_path: str | None = None
    item_roots: List[str] = field(default_factory=list)
    last_board_kind: str = "town"

    def resolved_quest_board_path(self) -> Path:
        if self.quest_board_path and self.quest_board_path.strip():
            return Path(self.quest_board_path).resolve()
        return default_quest_board_path()

    def resolved_world_board_path(self) -> Path:
        if self.world_board_path and self.world_board_path.strip():
            return Path(self.world_board_path).resolve()
        return default_world_board_path()

    def resolved_lang_path(self) -> Path:
        if self.lang_path and self.lang_path.strip():
            return Path(self.lang_path).resolve()
        return default_lang_path()

    def resolved_world_lang_path(self) -> Path:
        if self.world_lang_path and self.world_lang_path.strip():
            return Path(self.world_lang_path).resolve()
        return default_world_lang_path()

    def resolved_item_roots(self) -> List[Path]:
        roots = [Path(p).resolve() for p in self.item_roots if p.strip()]
        if not roots:
            roots = default_item_roots()
        return roots

    @staticmethod
    def load() -> "AppConfig":
        path = config_file_path()
        if not path.is_file():
            return AppConfig(item_roots=[str(p) for p in default_item_roots()])
        try:
            raw = json.loads(path.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, OSError):
            return AppConfig(item_roots=[str(p) for p in default_item_roots()])

        def _str(key: str) -> str | None:
            v = raw.get(key)
            return v.strip() if isinstance(v, str) and v.strip() else None

        roots = raw.get("item_roots")
        if not isinstance(roots, list):
            roots = [str(p) for p in default_item_roots()]
        else:
            roots = [str(x) for x in roots if isinstance(x, str) and x.strip()]
            if not roots:
                roots = [str(p) for p in default_item_roots()]

        kind = raw.get("last_board_kind")
        if kind not in ("town", "world"):
            kind = "town"

        return AppConfig(
            quest_board_path=_str("quest_board_path"),
            world_board_path=_str("world_board_path"),
            lang_path=_str("lang_path"),
            world_lang_path=_str("world_lang_path"),
            item_roots=roots,
            last_board_kind=kind,
        )

    def save(self) -> None:
        path = config_file_path()
        data = {
            "quest_board_path": self.quest_board_path,
            "world_board_path": self.world_board_path,
            "lang_path": self.lang_path,
            "world_lang_path": self.world_lang_path,
            "item_roots": self.item_roots,
            "last_board_kind": self.last_board_kind,
        }
        path.write_text(
            json.dumps(data, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
