from __future__ import annotations

from typing import Callable, List, Optional

from PySide6.QtCore import Qt
from PySide6.QtGui import QIcon
from PySide6.QtWidgets import (
    QAbstractItemView,
    QHBoxLayout,
    QHeaderView,
    QLabel,
    QPushButton,
    QTableWidget,
    QTableWidgetItem,
    QVBoxLayout,
    QWidget,
)

from ...rewards_util import (
    GRANT_TO_PLAYER,
    apply_kind_change,
    apply_primary_value,
    apply_secondary_value,
    default_item_reward,
    default_reputation_reward,
    normalize_reward,
    reward_primary_label,
    reward_primary_value,
    reward_secondary_label,
    reward_secondary_value,
)


class RewardsEditor(QWidget):
    COL_ICON = 0
    COL_KIND = 1
    COL_PRIMARY = 2
    COL_SECONDARY = 3
    COL_GRANT = 4

    def __init__(
        self,
        on_change: Optional[Callable[[], None]] = None,
        default_npc_role_id: str = "",
        browse_item: Optional[Callable[[], Optional[str]]] = None,
        icon_for_item: Optional[Callable[[str], QIcon]] = None,
    ) -> None:
        super().__init__()
        self._on_change = on_change
        self._default_npc_role_id = default_npc_role_id
        self._browse_item = browse_item
        self._icon_for_item = icon_for_item
        self._loading = False
        self._data: List[dict] = []

        layout = QVBoxLayout(self)
        self._hint = QLabel(
            "item: itemId + count | reputation: amount + npcRoleId (optional) | "
            "grantTo: player, quest_giver_npc, quest_beneficiary_npc"
        )
        self._hint.setWordWrap(True)
        layout.addWidget(self._hint)

        self.table = QTableWidget(0, 5)
        self._refresh_headers()
        self.table.horizontalHeader().setSectionResizeMode(
            self.COL_ICON, QHeaderView.ResizeMode.Fixed
        )
        self.table.setColumnWidth(self.COL_ICON, 48)
        self.table.horizontalHeader().setSectionResizeMode(
            self.COL_PRIMARY, QHeaderView.ResizeMode.Stretch
        )
        self.table.verticalHeader().setDefaultSectionSize(48)
        self.table.setSelectionBehavior(QAbstractItemView.SelectionBehavior.SelectRows)
        layout.addWidget(self.table)

        row = QHBoxLayout()
        add_item = QPushButton("Add item reward")
        add_item.clicked.connect(self._add_item)
        browse_btn = QPushButton("Browse item…")
        browse_btn.clicked.connect(self._browse_primary)
        add_rep = QPushButton("Add reputation reward")
        add_rep.clicked.connect(self._add_reputation)
        rem_btn = QPushButton("Remove selected")
        rem_btn.clicked.connect(self._remove_selected)
        row.addWidget(add_item)
        row.addWidget(browse_btn)
        row.addWidget(add_rep)
        row.addWidget(rem_btn)
        row.addStretch()
        layout.addLayout(row)

        self.table.cellChanged.connect(self._cell_changed)

    def set_browse_item(self, browse_item: Optional[Callable[[], Optional[str]]]) -> None:
        self._browse_item = browse_item

    def set_icon_for_item(self, icon_for_item: Optional[Callable[[str], QIcon]]) -> None:
        self._icon_for_item = icon_for_item
        if self._data:
            self.set_rewards(self._data)

    def set_default_npc_role_id(self, npc_role_id: str) -> None:
        self._default_npc_role_id = npc_role_id

    def _refresh_headers(self) -> None:
        self.table.setHorizontalHeaderLabels(["", "kind", "primary", "secondary", "grantTo"])

    def _icon_for_reward(self, reward: dict) -> QIcon:
        kind = str(reward.get("kind", "item"))
        if kind == "item":
            item_id = str(reward.get("itemId", ""))
        elif kind == "learn_recipe":
            item_id = str(reward.get("recipeItemId", ""))
        else:
            return QIcon()
        if self._icon_for_item and item_id:
            return self._icon_for_item(item_id)
        return QIcon()

    def set_rewards(self, rewards: List[dict]) -> None:
        self._loading = True
        self._data = [normalize_reward(r) for r in rewards]
        self.table.setRowCount(0)
        for r in self._data:
            self._append_row(r)
        self._loading = False

    def get_rewards(self) -> List[dict]:
        self._flush_table_to_data()
        return [normalize_reward(r) for r in self._data]

    def _flush_table_to_data(self) -> None:
        if self._loading:
            return
        self._loading = True
        try:
            self.table.clearFocus()
            row_count = min(len(self._data), self.table.rowCount())
            for row in range(row_count):
                reward = self._data[row]
                kind_item = self.table.item(row, self.COL_KIND)
                kind = (kind_item.text() if kind_item else "item").strip() or "item"
                if kind != str(reward.get("kind", "item")):
                    self._data[row] = apply_kind_change(reward, kind)
                    reward = self._data[row]
                primary = self.table.item(row, self.COL_PRIMARY)
                apply_primary_value(reward, primary.text() if primary else "")
                secondary = self.table.item(row, self.COL_SECONDARY)
                apply_secondary_value(reward, secondary.text() if secondary else "")
                grant = self.table.item(row, self.COL_GRANT)
                reward["grantTo"] = (
                    (grant.text() if grant else GRANT_TO_PLAYER).strip() or GRANT_TO_PLAYER
                )
        finally:
            self._loading = False

    def _append_row(self, reward: dict) -> None:
        row = self.table.rowCount()
        self.table.insertRow(row)
        kind = str(reward.get("kind", "item"))
        icon_item = QTableWidgetItem()
        icon_item.setFlags(Qt.ItemFlag.ItemIsEnabled | Qt.ItemFlag.ItemIsSelectable)
        icon_item.setIcon(self._icon_for_reward(reward))
        self.table.setItem(row, self.COL_ICON, icon_item)

        values = (
            kind,
            reward_primary_value(reward),
            reward_secondary_value(reward),
            str(reward.get("grantTo", GRANT_TO_PLAYER)),
        )
        for offset, val in enumerate(values):
            col = self.COL_KIND + offset
            item = QTableWidgetItem(val)
            if col == self.COL_PRIMARY and kind == "reputation":
                item.setTextAlignment(Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter)
            elif col == self.COL_SECONDARY and kind == "item":
                item.setTextAlignment(Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter)
            if col == self.COL_PRIMARY:
                item.setToolTip(reward_primary_label(kind))
            if col == self.COL_SECONDARY:
                label = reward_secondary_label(kind)
                item.setToolTip(label if label else "unused for this kind")
            self.table.setItem(row, col, item)

    def _add_item(self) -> None:
        self._data.append(default_item_reward())
        self._append_row(self._data[-1])
        self._emit_change()

    def _browse_primary(self) -> None:
        if not self._browse_item:
            return
        picked = self._browse_item()
        if not picked:
            return
        rows = sorted({i.row() for i in self.table.selectedIndexes()})
        if rows and 0 <= rows[0] < len(self._data):
            reward = self._data[rows[0]]
            kind = str(reward.get("kind", "item"))
            if kind == "learn_recipe":
                apply_primary_value(reward, picked)
            else:
                if kind != "item":
                    self._data[rows[0]] = apply_kind_change(reward, "item")
                    reward = self._data[rows[0]]
                apply_primary_value(reward, picked)
            self._rewrite_row(rows[0])
        else:
            reward = default_item_reward()
            reward["itemId"] = picked
            self._data.append(reward)
            self._append_row(reward)
        self._emit_change()

    def _add_reputation(self) -> None:
        self._data.append(default_reputation_reward(self._default_npc_role_id))
        self._append_row(self._data[-1])
        self._emit_change()

    def _remove_selected(self) -> None:
        rows = sorted({i.row() for i in self.table.selectedIndexes()}, reverse=True)
        for row in rows:
            if 0 <= row < len(self._data):
                self._data.pop(row)
                self.table.removeRow(row)
        self._emit_change()

    def _rewrite_row(self, row: int) -> None:
        if row >= len(self._data):
            return
        reward = self._data[row]
        self._loading = True
        kind = str(reward.get("kind", "item"))
        icon_item = self.table.item(row, self.COL_ICON)
        if icon_item is not None:
            icon_item.setIcon(self._icon_for_reward(reward))
        self.table.item(row, self.COL_KIND).setText(kind)
        self.table.item(row, self.COL_PRIMARY).setText(reward_primary_value(reward))
        self.table.item(row, self.COL_SECONDARY).setText(reward_secondary_value(reward))
        self.table.item(row, self.COL_GRANT).setText(str(reward.get("grantTo", GRANT_TO_PLAYER)))
        self.table.item(row, self.COL_PRIMARY).setToolTip(reward_primary_label(kind))
        sec_label = reward_secondary_label(kind)
        self.table.item(row, self.COL_SECONDARY).setToolTip(
            sec_label if sec_label else "unused for this kind"
        )
        self._loading = False

    def _cell_changed(self, row: int, col: int) -> None:
        if self._loading or row >= len(self._data) or col == self.COL_ICON:
            return
        item = self.table.item(row, col)
        text = item.text() if item else ""
        reward = self._data[row]
        if col == self.COL_KIND:
            new_kind = text.strip() or "item"
            self._data[row] = apply_kind_change(reward, new_kind)
            self._rewrite_row(row)
        elif col == self.COL_PRIMARY:
            apply_primary_value(reward, text)
            icon_item = self.table.item(row, self.COL_ICON)
            if icon_item is not None:
                icon_item.setIcon(self._icon_for_reward(reward))
        elif col == self.COL_SECONDARY:
            apply_secondary_value(reward, text)
        elif col == self.COL_GRANT:
            reward["grantTo"] = text.strip() or GRANT_TO_PLAYER
        self._emit_change()

    def _emit_change(self) -> None:
        if self._on_change:
            self._on_change()
