from __future__ import annotations

from pathlib import Path
from typing import Dict, List, Optional

from PySide6.QtCore import QAbstractListModel, QModelIndex, QSize, Qt, QTimer
from PySide6.QtGui import QIcon, QPixmap
from PySide6.QtWidgets import (
    QComboBox,
    QDialog,
    QDialogButtonBox,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QListView,
    QVBoxLayout,
    QWidget,
)

from ..item_catalog import (
    ItemRecord,
    all_categories,
    pick_icon_path_for_item,
)


class _IconCache:
    def __init__(self, icon_size: int = 48) -> None:
        self._icon_size = icon_size
        self._by_path: Dict[str, QIcon] = {}
        self._empty = self._make_placeholder()

    def _make_placeholder(self) -> QIcon:
        pix = QPixmap(self._icon_size, self._icon_size)
        pix.fill(Qt.GlobalColor.lightGray)
        return QIcon(pix)

    def icon_for_path(self, path: Optional[Path]) -> QIcon:
        if path is None or not path.is_file():
            return self._empty
        key = str(path.resolve())
        hit = self._by_path.get(key)
        if hit is not None:
            return hit
        p = QPixmap(str(path))
        if p.isNull():
            self._by_path[key] = self._empty
            return self._empty
        scaled = p.scaled(
            self._icon_size,
            self._icon_size,
            Qt.AspectRatioMode.KeepAspectRatio,
            Qt.TransformationMode.SmoothTransformation,
        )
        ic = QIcon(scaled)
        self._by_path[key] = ic
        return ic


class _ItemGridModel(QAbstractListModel):
    def __init__(self) -> None:
        super().__init__()
        self._ids: List[str] = []
        self.catalog: Dict[str, ItemRecord] = {}
        self.roots: List[Path] = []
        self._cache = _IconCache()

    def rowCount(self, parent: QModelIndex = QModelIndex()) -> int:  # type: ignore[override]
        if parent.isValid():
            return 0
        return len(self._ids)

    def data(self, index: QModelIndex, role: int = Qt.ItemDataRole.DisplayRole):  # type: ignore[override]
        if not index.isValid() or index.row() >= len(self._ids):
            return None
        item_id = self._ids[index.row()]
        if role == Qt.ItemDataRole.DisplayRole:
            rec = self.catalog.get(item_id)
            if rec is not None and rec.translation_name:
                return f"{item_id}\n{rec.translation_name}"
            return item_id
        if role == Qt.ItemDataRole.DecorationRole:
            rec = self.catalog.get(item_id)
            if rec is None:
                return self._cache.icon_for_path(None)
            path = pick_icon_path_for_item(rec, self.roots)
            return self._cache.icon_for_path(path)
        if role == Qt.ItemDataRole.ToolTipRole:
            rec = self.catalog.get(item_id)
            if rec is None:
                return item_id
            parts = [item_id]
            if rec.translation_name:
                parts.append(rec.translation_name)
            if rec.categories:
                parts.append("Categories: " + ", ".join(rec.categories))
            return "\n".join(parts)
        return None

    def item_id_at(self, row: int) -> Optional[str]:
        if 0 <= row < len(self._ids):
            return self._ids[row]
        return None

    def set_rows(self, ids: List[str]) -> None:
        self.beginResetModel()
        self._ids = list(ids)
        self.endResetModel()


class ItemBrowserDialog(QDialog):
    """Modal icon-grid picker returning a Hytale item id."""

    def __init__(
        self,
        catalog: Dict[str, ItemRecord],
        roots: List[Path],
        parent: Optional[QWidget] = None,
        title: str = "Pick item",
    ) -> None:
        super().__init__(parent)
        self.setWindowTitle(title)
        self.resize(900, 640)
        self._catalog = catalog
        self._roots = roots
        self._selected_id: Optional[str] = None
        self._model = _ItemGridModel()
        self._model.catalog = catalog
        self._model.roots = roots

        layout = QVBoxLayout(self)
        filt = QHBoxLayout()
        self.search = QLineEdit()
        self.search.setPlaceholderText("Search id or name...")
        self.search.textChanged.connect(self._refresh)
        filt.addWidget(QLabel("Search"))
        filt.addWidget(self.search, 1)
        self.category = QComboBox()
        self.category.addItem("All categories")
        for c in all_categories(catalog):
            self.category.addItem(c)
        self.category.currentIndexChanged.connect(self._refresh)
        filt.addWidget(QLabel("Category"))
        filt.addWidget(self.category)
        layout.addLayout(filt)

        self.count_label = QLabel("")
        layout.addWidget(self.count_label)

        self.list = QListView()
        self.list.setViewMode(QListView.ViewMode.IconMode)
        self.list.setResizeMode(QListView.ResizeMode.Adjust)
        self.list.setMovement(QListView.Movement.Static)
        self.list.setUniformItemSizes(True)
        self.list.setIconSize(QSize(48, 48))
        self.list.setGridSize(QSize(140, 110))
        self.list.setWordWrap(True)
        self.list.setModel(self._model)
        self.list.doubleClicked.connect(self._accept_index)
        self.list.clicked.connect(self._on_click)
        layout.addWidget(self.list, 1)

        buttons = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel
        )
        buttons.accepted.connect(self._accept_selected)
        buttons.rejected.connect(self.reject)
        layout.addWidget(buttons)

        self._refresh()
        QTimer.singleShot(0, self._refresh)

    def selected_item_id(self) -> Optional[str]:
        return self._selected_id

    def _on_click(self, index: QModelIndex) -> None:
        self._selected_id = self._model.item_id_at(index.row())

    def _accept_index(self, index: QModelIndex) -> None:
        self._selected_id = self._model.item_id_at(index.row())
        if self._selected_id:
            self.accept()

    def _accept_selected(self) -> None:
        indexes = self.list.selectedIndexes()
        if indexes:
            self._selected_id = self._model.item_id_at(indexes[0].row())
        if self._selected_id:
            self.accept()

    def _refresh(self) -> None:
        q = self.search.text().strip().lower()
        cat = self.category.currentText()
        ids: List[str] = []
        for item_id, rec in sorted(self._catalog.items(), key=lambda x: x[0].lower()):
            if cat != "All categories" and cat not in rec.categories:
                continue
            if q:
                name = (rec.translation_name or "").lower()
                if q not in item_id.lower() and q not in name:
                    continue
            ids.append(item_id)
        self._model.set_rows(ids)
        self.count_label.setText(f"{len(ids)} items")


def pick_item_id(
    parent: QWidget,
    catalog: Dict[str, ItemRecord],
    roots: List[Path],
    title: str = "Pick item",
) -> Optional[str]:
    if not catalog:
        return None
    dlg = ItemBrowserDialog(catalog, roots, parent, title=title)
    if dlg.exec() == QDialog.DialogCode.Accepted:
        return dlg.selected_item_id()
    return None
