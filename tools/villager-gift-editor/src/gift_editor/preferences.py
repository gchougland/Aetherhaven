from __future__ import annotations

from enum import Enum
from typing import Iterable, List, Literal, Tuple


class Preference(str, Enum):
    LOVE = "love"
    LIKE = "like"
    NEUTRAL = "neutral"
    DISLIKE = "dislike"


def preference_for_item(
    item_id: str,
    loves: Iterable[str],
    likes: Iterable[str],
    dislikes: Iterable[str],
) -> Preference:
    if item_id in loves:
        return Preference.LOVE
    if item_id in likes:
        return Preference.LIKE
    if item_id in dislikes:
        return Preference.DISLIKE
    return Preference.NEUTRAL


def _unique_append(lst: List[str], item_id: str) -> None:
    if item_id not in lst:
        lst.append(item_id)


def _remove_all(lst: List[str], item_id: str) -> None:
    lst[:] = [x for x in lst if x != item_id]


def apply_preference(
    item_id: str,
    mode: Preference,
    loves: List[str],
    likes: List[str],
    dislikes: List[str],
) -> Literal["love", "like", "neutral", "dislike"]:
    """Mutates the three lists in place. Returns the resulting preference name."""
    _remove_all(loves, item_id)
    _remove_all(likes, item_id)
    _remove_all(dislikes, item_id)
    if mode == Preference.LOVE:
        _unique_append(loves, item_id)
        return "love"
    if mode == Preference.LIKE:
        _unique_append(likes, item_id)
        return "like"
    if mode == Preference.DISLIKE:
        _unique_append(dislikes, item_id)
        return "dislike"
    return "neutral"


def snapshot_gifts(
    loves: List[str], likes: List[str], dislikes: List[str]
) -> Tuple[List[str], List[str], List[str]]:
    return (list(loves), list(likes), list(dislikes))


def restore_gifts(
    target: Tuple[List[str], List[str], List[str]],
    loves: List[str],
    likes: List[str],
    dislikes: List[str],
) -> None:
    a, b, c = target
    loves[:] = list(a)
    likes[:] = list(b)
    dislikes[:] = list(c)
