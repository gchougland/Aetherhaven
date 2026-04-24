from __future__ import annotations

import os
from pathlib import Path


def default_schedules_dir() -> Path:
    """`.../VillagerSchedules` in the Aetherhaven repo, or `AETHERHAVEN_SCHEDULES` override."""
    override = os.environ.get("AETHERHAVEN_SCHEDULES")
    if override:
        return Path(override).resolve()
    # This file: tools/villager-schedule-editor/src/schedule_editor/config.py -> parents[4] = repo root
    here = Path(__file__).resolve()
    root = here.parents[4]
    p = root / "src" / "main" / "resources" / "Server" / "Aetherhaven" / "VillagerSchedules"
    if p.is_dir():
        return p
    for anc in here.parents:
        q = anc / "src" / "main" / "resources" / "Server" / "Aetherhaven" / "VillagerSchedules"
        if q.is_dir():
            return q
    return p  # return expected path even if missing, so user sees a clear default
