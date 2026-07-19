from __future__ import annotations

from pathlib import Path
from typing import Dict, Optional

from PySide6.QtCore import Qt
from PySide6.QtGui import QIcon, QPixmap


class IconCache:
    """Caches scaled item/portrait icons for Qt widgets."""

    def __init__(self, size: int = 40) -> None:
        self._size = size
        self._by_path: Dict[str, QIcon] = {}
        self._empty = self._make_placeholder()

    def _make_placeholder(self) -> QIcon:
        pix = QPixmap(self._size, self._size)
        pix.fill(Qt.GlobalColor.lightGray)
        return QIcon(pix)

    def icon_for_path(self, path: Optional[Path]) -> QIcon:
        if path is None or not path.is_file():
            return self._empty
        key = f"{path.resolve()}:{self._size}"
        hit = self._by_path.get(key)
        if hit is not None:
            return hit
        p = QPixmap(str(path))
        if p.isNull():
            self._by_path[key] = self._empty
            return self._empty
        scaled = p.scaled(
            self._size,
            self._size,
            Qt.AspectRatioMode.KeepAspectRatio,
            Qt.TransformationMode.SmoothTransformation,
        )
        ic = QIcon(scaled)
        self._by_path[key] = ic
        return ic

    def pixmap_for_path(self, path: Optional[Path], size: Optional[int] = None) -> QPixmap:
        sz = size or self._size
        if path is None or not path.is_file():
            pix = QPixmap(sz, sz)
            pix.fill(Qt.GlobalColor.lightGray)
            return pix
        p = QPixmap(str(path))
        if p.isNull():
            pix = QPixmap(sz, sz)
            pix.fill(Qt.GlobalColor.lightGray)
            return pix
        return p.scaled(
            sz,
            sz,
            Qt.AspectRatioMode.KeepAspectRatio,
            Qt.TransformationMode.SmoothTransformation,
        )
