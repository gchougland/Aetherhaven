from __future__ import annotations

from typing import List

from PySide6.QtCore import QAbstractTableModel, QModelIndex, Qt
from PySide6.QtWidgets import QHeaderView, QTableView


class QuestListModel(QAbstractTableModel):
    TOWN_HEADERS = ["Rank", "Type", "Villager", "ID", "Title"]
    WORLD_HEADERS = ["Min rank", "Type", "ID", "Title"]

    def __init__(self) -> None:
        super().__init__()
        self._rows: List[tuple] = []
        self._headers = list(self.TOWN_HEADERS)

    def set_headers(self, headers: List[str]) -> None:
        self.beginResetModel()
        self._headers = list(headers)
        self._rows = []
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
        if role in (Qt.ItemDataRole.DisplayRole, Qt.ItemDataRole.ToolTipRole):
            return row[index.column()]
        return None

    def headerData(self, section: int, orientation: Qt.Orientation, role: int = Qt.ItemDataRole.DisplayRole):  # type: ignore[override]
        if role != Qt.ItemDataRole.DisplayRole or orientation != Qt.Orientation.Horizontal:
            return None
        if 0 <= section < len(self._headers):
            return self._headers[section]
        return None

    def set_rows(self, rows: List[tuple]) -> None:
        self.beginResetModel()
        self._rows = rows
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
        self.set_board_kind("town")

    def set_board_kind(self, kind: str) -> None:
        if kind == "world":
            self._model.set_headers(QuestListModel.WORLD_HEADERS)
            self.setColumnWidth(0, 70)
            self.setColumnWidth(1, 55)
            self.setColumnWidth(2, 160)
        else:
            self._model.set_headers(QuestListModel.TOWN_HEADERS)
            self.setColumnWidth(0, 50)
            self.setColumnWidth(1, 55)
            self.setColumnWidth(2, 110)
            self.setColumnWidth(3, 140)

    def quest_model(self) -> QuestListModel:
        return self._model

    def selected_ref_index(self) -> int:
        indexes = self.selectionModel().selectedRows()
        if not indexes:
            return -1
        return self._model.ref_index_at(indexes[0].row())
