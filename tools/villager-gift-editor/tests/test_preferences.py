from __future__ import annotations

from gift_editor.preferences import (
    Preference,
    apply_preference,
    preference_for_item,
    restore_gifts,
    snapshot_gifts,
)


def test_preference_for_item() -> None:
    loves = ["A", "B"]
    likes = ["C"]
    dislikes = ["D"]
    assert preference_for_item("A", loves, likes, dislikes) == Preference.LOVE
    assert preference_for_item("C", loves, likes, dislikes) == Preference.LIKE
    assert preference_for_item("D", loves, likes, dislikes) == Preference.DISLIKE
    assert preference_for_item("X", loves, likes, dislikes) == Preference.NEUTRAL


def test_apply_love_moves_from_other_lists() -> None:
    loves: list[str] = []
    likes = ["Ore"]
    dislikes = ["Ore"]
    apply_preference("Ore", Preference.LOVE, loves, likes, dislikes)
    assert loves == ["Ore"]
    assert likes == []
    assert dislikes == []


def test_apply_neutral_clears() -> None:
    loves = ["X"]
    likes = ["Y"]
    dislikes = ["Z"]
    apply_preference("Y", Preference.NEUTRAL, loves, likes, dislikes)
    assert likes == []
    assert loves == ["X"]
    apply_preference("X", Preference.NEUTRAL, loves, likes, dislikes)
    assert loves == []


def test_snapshot_restore() -> None:
    loves = ["1"]
    likes = ["2"]
    dislikes: list[str] = []
    snap = snapshot_gifts(loves, likes, dislikes)
    loves.clear()
    likes.append("9")
    restore_gifts(snap, loves, likes, dislikes)
    assert loves == ["1"]
    assert likes == ["2"]
