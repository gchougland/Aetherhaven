from __future__ import annotations

from enum import Enum

from .preferences import Preference


class ClickMode(str, Enum):
    """How a grid item click is interpreted (gift lists or global ignore list)."""

    LOVE = "love"
    LIKE = "like"
    NEUTRAL = "neutral"
    DISLIKE = "dislike"
    IGNORE = "ignore"

    def as_preference(self) -> Preference | None:
        if self == ClickMode.IGNORE:
            return None
        return Preference(self.value)
