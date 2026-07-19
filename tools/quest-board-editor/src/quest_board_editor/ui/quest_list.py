from __future__ import annotations

from typing import List, Optional

from PySide6.QtCore import QAbstractTableModel, QModelIndex, Qt, QSize
from PySide6.QtGui import QIcon
from PySide6.QtWidgets import QHeaderView, QTableView


class QuestListModel(QAbstractTableModel):
    TOWN_HEADERS = ["", "Rank", "Type", "Villager", "ID", "Title"]
    WORLD_HEADERS = ["Min rank", "Type", "ID", "Title"]

    def __init__(self) -> None:
        super().__init__()
        self._rows: List[tuple] = []
        self._icons: List[Optional[QIcon]] = []
        self._headers = list(self.TOWN_HEADERS)
        self._show_portrait = True

    def set_headers(self, headers: List[str], *, show_portrait: bool = False) -> None:
        self.beginResetModel()
        self._headers = list(headers)
        self._show_portrait = show_portrait
        self._rows = []
        self._icons = []
        self.endResetModel()

    def rowCount(self, parent: QModelIndex = QModelIndex()) -> int:  # type: ignore[override]
        if parent.isValid():
            return 0
        return len(self._rows)

    def columnCount(self, parent: QModelIndex = QModelIndex()) -> int:  # type: ignore[override]
        if parent.isValid():
            return 0
        return len(self._headers)

    def data(self, index: QModelIndex, role: int = Qt.ItemDataRole.DisplayRole):  # type: ignore[override]
        if not index.isValid() or index.row() >= len(self._rows):
            return None
        row = self._rows[index.row()]
        if self._show_portrait and index.column() == 0:
            if role == Qt.ItemDataRole.DecorationRole:
                if index.row() < len(self._icons):
                    return self._icons[index.row()]
                return None
            if role == Qt.ItemDataRole.DisplayRole:
                return ""
            if role == Qt.ItemDataRole.ToolTipRole:
                # Villager label is column 3 in town mode
                if len(row) > 3:
                    return row[3]
                return None
            return None
        if role in (Qt.ItemDataRole.DisplayRole, Qt.ItemDataRole.ToolTipRole):
            return row[index.column()]
        return None

    def headerData(self, section: int, orientation: Qt.Orientation, role: int = Qt.ItemDataRole.DisplayRole):  # type: ignore[override]
        if role != Qt.ItemDataRole.DisplayRole or orientation != Qt.Orientation.Horizontal:
            return None
        if 0 <= section < len(self._headers):
            return self._headers[section]
        return None

    def set_rows(
        self, rows: List[tuple], icons: Optional[List[Optional[QIcon]]] = None
    ) -> None:
        self.beginResetModel()
        self._rows = rows
        self._icons = list(icons) if icons is not None else [None] * len(rows)
        self.endResetModel()

    def ref_index_at(self, row: int) -> int:
        if 0 <= row < len(self._rows):
            return int(self._rows[row][-1])
        return -1


class QuestListView(QTableView):
    def __init__(self) -> None:
        super().__init__()
        self._model = QuestListModel()
        self.setModel(self._model)
        self.setSelectionBehavior(QTableView.SelectionBehavior.SelectRows)
        self.setSelectionMode(QTableView.SelectionMode.SingleSelection)
        self.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Interactive)
        self.horizontalHeader().setStretchLastSection(True)
        self.verticalHeader().setVisible(False)
        self.setAlternatingRowColors(True)
        self.setIconSize(QSize(36, 36))
        self.set_board_kind("town")

    def set_board_kind(self, kind: str) -> None:
        if kind == "world":
            self._model.set_headers(QuestListModel.WORLD_HEADERS, show_portrait=False)
            self.verticalHeader().setDefaultSectionSize(28)
            self.setColumnWidth(0, 70)
            self.setColumnWidth(1, 55)
            self.setColumnWidth(2, 160)
        else:
            self._model.set_headers(QuestListModel.TOWN_HEADERS, show_portrait=True)
            self.verticalHeader().setDefaultSectionSize(42)
            self.setColumnWidth(0, 44)
            self.setColumnWidth(1, 50)
            self.setColumnWidth(2, 55)
            self.setColumnWidth(3, 120)
            self.setColumnWidth(4, 140)

    def quest_model(self) -> QuestListModel:
        return self._model

    def selected_ref_index(self) -> int:
        indexes = self.selectionModel().selectedRows()
        if not indexes:
            return -1
        return self._model.ref_index_at(indexes[0].row())
