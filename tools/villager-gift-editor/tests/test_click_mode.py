from __future__ import annotations

from gift_editor.click_mode import ClickMode
from gift_editor.preferences import Preference


def test_as_preference_maps_gift_modes() -> None:
    assert ClickMode.LOVE.as_preference() == Preference.LOVE
    assert ClickMode.IGNORE.as_preference() is None
