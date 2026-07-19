from __future__ import annotations

import copy
from typing import Callable, List, Optional

from PySide6.QtCore import Qt
from PySide6.QtGui import QIcon
from PySide6.QtWidgets import (
    QGroupBox,
    QHBoxLayout,
    QHeaderView,
    QLabel,
    QPushButton,
    QScrollArea,
    QSizePolicy,
    QSpinBox,
    QTableWidget,
    QTableWidgetItem,
    QVBoxLayout,
    QWidget,
)


class ItemSetsEditor(QWidget):
    def __init__(
        self,
        on_change: Optional[Callable[[], None]] = None,
        browse_item: Optional[Callable[[], Optional[str]]] = None,
        icon_for_item: Optional[Callable[[str], QIcon]] = None,
    ) -> None:
        super().__init__()
        self._on_change = on_change
        self._browse_item = browse_item
        self._icon_for_item = icon_for_item
        self._loading = False
        self._sets: List[dict] = []
        self._current_set = 0

        layout = QVBoxLayout(self)
        sel_row = QHBoxLayout()
        sel_row.addWidget(QLabel("Item set:"))
        self.set_spin = QSpinBox()
        self.set_spin.setMinimum(1)
        self.set_spin.valueChanged.connect(self._on_set_changed)
        sel_row.addWidget(self.set_spin)
        add_set = QPushButton("Add set")
        add_set.clicked.connect(self._add_set)
        rem_set = QPushButton("Remove set")
        rem_set.clicked.connect(self._remove_set)
        sel_row.addWidget(add_set)
        sel_row.addWidget(rem_set)
        sel_row.addStretch()
        layout.addLayout(sel_row)

        weight_row = QHBoxLayout()
        weight_row.addWidget(QLabel("Set weight:"))
        self.weight_spin = QSpinBox()
        self.weight_spin.setMinimum(1)
        self.weight_spin.setMaximum(9999)
        self.weight_spin.valueChanged.connect(self._weight_changed)
        weight_row.addWidget(self.weight_spin)
        weight_row.addStretch()
        layout.addLayout(weight_row)

        preview_box = QGroupBox("Required items preview")
        preview_layout = QVBoxLayout(preview_box)
        self._preview_scroll = QScrollArea()
        self._preview_scroll.setWidgetResizable(True)
        self._preview_scroll.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAsNeeded)
        self._preview_scroll.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self._preview_scroll.setFixedHeight(88)
        self._preview_host = QWidget()
        self._preview_row = QHBoxLayout(self._preview_host)
        self._preview_row.setContentsMargins(4, 4, 4, 4)
        self._preview_row.setSpacing(10)
        self._preview_row.addStretch()
        self._preview_scroll.setWidget(self._preview_host)
        preview_layout.addWidget(self._preview_scroll)
        layout.addWidget(preview_box)

        box = QGroupBox("Items in set")
        box_layout = QVBoxLayout(box)
        self.table = QTableWidget(0, 3)
        self.table.setHorizontalHeaderLabels(["", "itemId", "count"])
        self.table.horizontalHeader().setSectionResizeMode(0, QHeaderView.ResizeMode.Fixed)
        self.table.setColumnWidth(0, 48)
        self.table.horizontalHeader().setSectionResizeMode(1, QHeaderView.ResizeMode.Stretch)
        self.table.verticalHeader().setDefaultSectionSize(48)
        box_layout.addWidget(self.table)
        btn_row = QHBoxLayout()
        add_item = QPushButton("Add item")
        add_item.clicked.connect(self._add_item)
        browse_btn = QPushButton("Browse…")
        browse_btn.clicked.connect(self._browse_add_or_replace)
        rem_item = QPushButton("Remove item")
        rem_item.clicked.connect(self._remove_item)
        btn_row.addWidget(add_item)
        btn_row.addWidget(browse_btn)
        btn_row.addWidget(rem_item)
        btn_row.addStretch()
        box_layout.addLayout(btn_row)
        layout.addWidget(box)

        self.table.cellChanged.connect(self._cell_changed)

    def set_browse_item(self, browse_item: Optional[Callable[[], Optional[str]]]) -> None:
        self._browse_item = browse_item

    def set_icon_for_item(self, icon_for_item: Optional[Callable[[str], QIcon]]) -> None:
        self._icon_for_item = icon_for_item
        self._refresh_table()

    def set_item_sets(self, item_sets: List[dict]) -> None:
        self._loading = True
        self._sets = copy.deepcopy(item_sets) if item_sets else [{"weight": 1, "items": []}]
        self.set_spin.setMaximum(max(1, len(self._sets)))
        self._current_set = 0
        self.set_spin.setValue(1)
        self._refresh_table()
        self._loading = False

    def get_item_sets(self) -> List[dict]:
        self._flush_table_to_data()
        return copy.deepcopy(self._sets)

    def _flush_table_to_data(self) -> None:
        if self._loading:
            return
        self._loading = True
        try:
            self.table.clearFocus()
            items = self._current().setdefault("items", [])
            row_count = min(len(items), self.table.rowCount())
            for row in range(row_count):
                id_item = self.table.item(row, 1)
                cnt_item = self.table.item(row, 2)
                items[row]["itemId"] = id_item.text() if id_item else ""
                try:
                    items[row]["count"] = int(cnt_item.text()) if cnt_item else 1
                except ValueError:
                    items[row]["count"] = 1
        finally:
            self._loading = False

    def _current(self) -> dict:
        if not self._sets:
            self._sets = [{"weight": 1, "items": []}]
        return self._sets[self._current_set]

    def _icon(self, item_id: str) -> QIcon:
        if self._icon_for_item:
            return self._icon_for_item(item_id)
        return QIcon()

    def _refresh_preview(self, items: List[dict]) -> None:
        while self._preview_row.count():
            item = self._preview_row.takeAt(0)
            w = item.widget()
            if w is not None:
                w.deleteLater()
        if not items:
            empty = QLabel("No items in this set")
            empty.setStyleSheet("color: #888;")
            self._preview_row.addWidget(empty)
            self._preview_row.addStretch()
            return
        for it in items:
            item_id = str(it.get("itemId", ""))
            count = it.get("count", 1)
            chip = QWidget()
            chip.setSizePolicy(QSizePolicy.Policy.Fixed, QSizePolicy.Policy.Fixed)
            chip_l = QVBoxLayout(chip)
            chip_l.setContentsMargins(0, 0, 0, 0)
            chip_l.setSpacing(2)
            icon_lbl = QLabel()
            icon_lbl.setFixedSize(48, 48)
            icon_lbl.setAlignment(Qt.AlignmentFlag.AlignCenter)
            pix = self._icon(item_id).pixmap(48, 48)
            icon_lbl.setPixmap(pix)
            icon_lbl.setToolTip(item_id)
            chip_l.addWidget(icon_lbl, alignment=Qt.AlignmentFlag.AlignCenter)
            name = QLabel(f"×{count}")
            name.setAlignment(Qt.AlignmentFlag.AlignCenter)
            name.setToolTip(item_id)
            chip_l.addWidget(name)
            self._preview_row.addWidget(chip)
        self._preview_row.addStretch()

    def _refresh_table(self) -> None:
        self._loading = True
        cur = self._current()
        self.weight_spin.setValue(int(cur.get("weight", 1)))
        items = cur.get("items") or []
        self.table.setRowCount(0)
        for it in items:
            row = self.table.rowCount()
            self.table.insertRow(row)
            item_id = str(it.get("itemId", ""))
            icon_item = QTableWidgetItem()
            icon_item.setFlags(Qt.ItemFlag.ItemIsEnabled | Qt.ItemFlag.ItemIsSelectable)
            icon_item.setIcon(self._icon(item_id))
            icon_item.setToolTip(item_id)
            self.table.setItem(row, 0, icon_item)
            self.table.setItem(row, 1, QTableWidgetItem(item_id))
            cnt = QTableWidgetItem(str(it.get("count", 1)))
            cnt.setTextAlignment(Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter)
            self.table.setItem(row, 2, cnt)
        self._refresh_preview(items)
        self._loading = False

    def _on_set_changed(self, one_based: int) -> None:
        if self._loading:
            return
        self._current_set = max(0, one_based - 1)
        if self._current_set >= len(self._sets):
            self._current_set = len(self._sets) - 1
        self._refresh_table()

    def _add_set(self) -> None:
        self._sets.append({"weight": 1, "items": [{"itemId": "Rock_Stone", "count": 1}]})
        self.set_spin.setMaximum(len(self._sets))
        self.set_spin.setValue(len(self._sets))
        self._emit_change()

    def _remove_set(self) -> None:
        if len(self._sets) <= 1:
            return
        self._sets.pop(self._current_set)
        self._current_set = min(self._current_set, len(self._sets) - 1)
        self.set_spin.setMaximum(len(self._sets))
        self.set_spin.setValue(self._current_set + 1)
        self._emit_change()

    def _weight_changed(self, val: int) -> None:
        if self._loading:
            return
        self._current()["weight"] = val
        self._emit_change()

    def _add_item(self) -> None:
        items = self._current().setdefault("items", [])
        items.append({"itemId": "Rock_Stone", "count": 1})
        self._refresh_table()
        self._emit_change()

    def _browse_add_or_replace(self) -> None:
        if not self._browse_item:
            return
        picked = self._browse_item()
        if not picked:
            return
        items = self._current().setdefault("items", [])
        rows = sorted({i.row() for i in self.table.selectedIndexes()})
        if rows and 0 <= rows[0] < len(items):
            items[rows[0]]["itemId"] = picked
        else:
            items.append({"itemId": picked, "count": 1})
        self._refresh_table()
        self._emit_change()

    def _remove_item(self) -> None:
        rows = sorted({i.row() for i in self.table.selectedIndexes()}, reverse=True)
        items = self._current().get("items") or []
        for row in rows:
            if 0 <= row < len(items):
                items.pop(row)
        self._current()["items"] = items
        self._refresh_table()
        self._emit_change()

    def _cell_changed(self, row: int, col: int) -> None:
        if self._loading or col == 0:
            return
        items = self._current().setdefault("items", [])
        if row >= len(items):
            return
        item = self.table.item(row, col)
        text = item.text() if item else ""
        if col == 1:
            items[row]["itemId"] = text
            icon_item = self.table.item(row, 0)
            if icon_item is not None:
                icon_item.setIcon(self._icon(text))
                icon_item.setToolTip(text)
            self._refresh_preview(items)
        else:
            try:
                items[row]["count"] = int(text)
            except ValueError:
                items[row]["count"] = 1
            self._refresh_preview(items)
        self._emit_change()

    def _emit_change(self) -> None:
        if self._on_change:
            self._on_change()
