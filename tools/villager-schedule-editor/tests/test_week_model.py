from __future__ import annotations

from pathlib import Path

import pytest

from schedule_editor import io_json
from schedule_editor.week_model import (
    day_slices_for_day,
    WEEK_MINUTES,
)


def _sample_path() -> Path:
    here = Path(__file__).resolve()
    for anc in [here] + list(here.parents):
        p = (
            anc
            / "src"
            / "main"
            / "resources"
            / "Server"
            / "Aetherhaven"
            / "VillagerSchedules"
            / "Aetherhaven_Merchant.json"
        )
        if p.is_file():
            return p
    pytest.skip("Merchant schedule JSON not found in repo tree")


def test_load_merchant_round_trip() -> None:
    p = _sample_path()
    v, m = io_json.load_file(p)
    assert v == 1
    n = len(m.sorted())
    w = [t.week_minute for t in m.sorted()]
    assert len(w) == len(set(w))
    for i in range(1, len(w)):
        assert w[i] > w[i - 1]
    w1 = w[0] + WEEK_MINUTES
    assert w1 - w[-1] > 0


def test_io_round_trip(tmp_path: Path) -> None:
    p = _sample_path()
    v, m = io_json.load_file(p)
    out = tmp_path / "out.json"
    io_json.save_file(out, v, m)
    v2, m2 = io_json.load_file(out)
    assert v2 == v
    a = sorted(
        (t.week_minute, t.location) for t in m.transitions
    )
    b = sorted(
        (t.week_minute, t.location) for t in m2.transitions
    )
    assert a == b


def test_day_slices_sum_to_day() -> None:
    p = _sample_path()
    _v, m = io_json.load_file(p)
    segs = m.segments()
    for d in range(7):
        sls = day_slices_for_day(d, segs)
        for s in sls:
            assert 0 <= s.start_local < s.end_local <= 24 * 60
