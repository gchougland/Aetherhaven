from __future__ import annotations

import json
from pathlib import Path
from typing import List, Tuple

from .week_model import ScheduleModel, Transition, ordered_for_json


def load_file(path: Path) -> Tuple[int, ScheduleModel]:
    raw = json.loads(path.read_text(encoding="utf-8"))
    ver = int(raw.get("schemaVersion", 1))
    arr = raw.get("transitions", [])
    if not isinstance(arr, list):
        arr = []
    out: List[Transition] = []
    for i, o in enumerate(arr):
        t = Transition.from_dict(o, i)  # type: ignore[arg-type]
        if t is not None:
            out.append(t)
    return ver, ScheduleModel.from_list(out)


def save_file(path: Path, schema_version: int, model: ScheduleModel) -> None:
    ord_list = ordered_for_json(model)
    data = {
        "schemaVersion": int(schema_version),
        "transitions": [t.to_dict() for t in ord_list],
    }
    text = json.dumps(data, indent=2, ensure_ascii=False) + "\n"
    path.write_text(text, encoding="utf-8")
