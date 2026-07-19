from __future__ import annotations

import copy
from typing import Callable, List, Optional

from PySide6.QtCore import Qt
from PySide6.QtWidgets import (
    QGroupBox,
    QHBoxLayout,
    QHeaderView,
    QLabel,
    QPushButton,
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
    ) -> None:
        super().__init__()
        self._on_change = on_change
        self._browse_item = browse_item
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

        box = QGroupBox("Items in set")
        box_layout = QVBoxLayout(box)
        self.table = QTableWidget(0, 2)
        self.table.setHorizontalHeaderLabels(["itemId", "count"])
        self.table.horizontalHeader().setSectionResizeMode(0, QHeaderView.ResizeMode.Stretch)
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
                id_item = self.table.item(row, 0)
                cnt_item = self.table.item(row, 1)
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

    def _refresh_table(self) -> None:
        self._loading = True
        cur = self._current()
        self.weight_spin.setValue(int(cur.get("weight", 1)))
        items = cur.get("items") or []
        self.table.setRowCount(0)
        for it in items:
            row = self.table.rowCount()
            self.table.insertRow(row)
            self.table.setItem(row, 0, QTableWidgetItem(str(it.get("itemId", ""))))
            cnt = QTableWidgetItem(str(it.get("count", 1)))
            cnt.setTextAlignment(Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter)
            self.table.setItem(row, 1, cnt)
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
        if self._loading:
            return
        items = self._current().setdefault("items", [])
        if row >= len(items):
            return
        item = self.table.item(row, col)
        text = item.text() if item else ""
        if col == 0:
            items[row]["itemId"] = text
        else:
            try:
                items[row]["count"] = int(text)
            except ValueError:
                items[row]["count"] = 1
        self._emit_change()

    def _emit_change(self) -> None:
        if self._on_change:
            self._on_change()
