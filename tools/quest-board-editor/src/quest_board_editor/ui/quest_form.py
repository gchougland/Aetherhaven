from __future__ import annotations

from typing import Callable, Dict, List, Optional, Union

from PySide6.QtWidgets import (
    QComboBox,
    QFormLayout,
    QGroupBox,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QPushButton,
    QScrollArea,
    QSpinBox,
    QTextEdit,
    QVBoxLayout,
    QWidget,
)

from ..lang_keys import json_key_to_lang_key as town_json_key_to_lang_key
from ..quest_model import QuestRef, migrate_entry_type, regenerate_entry_lang_keys
from ..world_lang_keys import json_key_to_lang_key as world_json_key_to_lang_key
from ..world_quest_model import WorldQuestRef, regenerate_world_lang_keys
from .widgets.item_sets_editor import ItemSetsEditor
from .widgets.kill_sets_editor import KillSetsEditor
from .widgets.raid_sets_editor import RaidSetsEditor
from .widgets.rewards_editor import RewardsEditor

AnyQuestRef = Union[QuestRef, WorldQuestRef]


class QuestForm(QWidget):
    def __init__(
        self,
        ranks: List[str],
        villager_ids: List[str],
        lang_getter: Callable[[str], str],
        on_dirty: Callable[[], None],
        browse_item: Optional[Callable[[], Optional[str]]] = None,
    ) -> None:
        super().__init__()
        self._ranks = ranks
        self._villager_ids = villager_ids
        self._lang_getter = lang_getter
        self._on_dirty = on_dirty
        self._browse_item = browse_item
        self._board_kind = "town"
        self._ref: Optional[AnyQuestRef] = None
        self._loading = False
        self._pending_lang: Dict[str, str] = {}
        self._stale_lang_keys: List[str] = []

        outer = QVBoxLayout(self)
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        container = QWidget()
        self._layout = QVBoxLayout(container)

        id_box = QGroupBox("Identity")
        id_form = QFormLayout(id_box)
        self.villager_combo = QComboBox()
        self.villager_combo.addItems(villager_ids)
        self.villager_combo.currentTextChanged.connect(self._identity_changed)
        self._villager_row_label = QLabel("Villager")
        id_form.addRow(self._villager_row_label, self.villager_combo)
        self.type_combo = QComboBox()
        self.type_combo.addItems(["fetch", "hunt", "raid"])
        self.type_combo.currentTextChanged.connect(self._type_changed)
        id_form.addRow("Quest type", self.type_combo)
        self.world_type_note = QLabel(
            "World boards store questType as metadata only — "
            "objectives (items/kills/raids) apply to the town board."
        )
        self.world_type_note.setWordWrap(True)
        id_form.addRow(self.world_type_note)
        self.id_edit = QLineEdit()
        self.id_edit.editingFinished.connect(self._scalar_changed)
        id_form.addRow("ID", self.id_edit)
        self._layout.addWidget(id_box)

        common_box = QGroupBox("Common fields")
        common_form = QFormLayout(common_box)
        self.rank_combo = self._rank_combo()
        self.min_rank_combo = self._rank_combo()
        self.max_rank_combo = self._rank_combo()
        self._rank_row_label = QLabel("Rank")
        common_form.addRow(self._rank_row_label, self.rank_combo)
        common_form.addRow("Min rank", self.min_rank_combo)
        common_form.addRow("Max rank", self.max_rank_combo)
        self.weight_spin = QSpinBox()
        self.weight_spin.setRange(1, 9999)
        self.weight_spin.valueChanged.connect(self._scalar_changed)
        common_form.addRow("Weight", self.weight_spin)
        self.days_spin = QSpinBox()
        self.days_spin.setRange(1, 999)
        self.days_spin.valueChanged.connect(self._scalar_changed)
        common_form.addRow("Days limit", self.days_spin)
        self.rank_xp_spin = QSpinBox()
        self.rank_xp_spin.setRange(0, 99999)
        self.rank_xp_spin.valueChanged.connect(self._scalar_changed)
        common_form.addRow("Rank XP reward (0 = omit)", self.rank_xp_spin)
        self._layout.addWidget(common_box)

        lang_box = QGroupBox("Localization")
        lang_form = QFormLayout(lang_box)
        self.title_edit = QLineEdit()
        self.title_edit.textChanged.connect(self._lang_changed)
        lang_form.addRow("Title", self.title_edit)
        self.title_key_label = QLabel("")
        self.title_key_label.setWordWrap(True)
        lang_form.addRow("Title lang key", self.title_key_label)
        self.desc_edit = QTextEdit()
        self.desc_edit.setAcceptRichText(False)
        self.desc_edit.setMaximumHeight(100)
        self.desc_edit.textChanged.connect(self._lang_changed)
        lang_form.addRow("Description", self.desc_edit)
        self.desc_key_label = QLabel("")
        self.desc_key_label.setWordWrap(True)
        lang_form.addRow("Description lang key", self.desc_key_label)
        self.target_edit = QLineEdit()
        self.target_edit.textChanged.connect(self._lang_changed)
        self.target_key_label = QLabel("")
        self.target_key_label.setWordWrap(True)
        self._target_label = QLabel("Target label (hunt/raid)")
        lang_form.addRow(self._target_label, self.target_edit)
        lang_form.addRow("Target lang key", self.target_key_label)
        regen_row = QHBoxLayout()
        regen_btn = QPushButton("Regenerate lang keys")
        regen_btn.clicked.connect(self._regenerate_keys)
        regen_row.addWidget(regen_btn)
        regen_row.addStretch()
        lang_form.addRow(regen_row)
        self._layout.addWidget(lang_box)

        self.fetch_group = QGroupBox("Fetch — item sets")
        fetch_layout = QVBoxLayout(self.fetch_group)
        self.item_sets_editor = ItemSetsEditor(
            on_change=self._nested_changed, browse_item=browse_item
        )
        fetch_layout.addWidget(self.item_sets_editor)
        self._layout.addWidget(self.fetch_group)

        self.hunt_group = QGroupBox("Hunt — kill sets")
        hunt_layout = QVBoxLayout(self.hunt_group)
        self.kill_sets_editor = KillSetsEditor(on_change=self._nested_changed)
        hunt_layout.addWidget(self.kill_sets_editor)
        self._layout.addWidget(self.hunt_group)

        self.raid_group = QGroupBox("Raid — raid sets")
        raid_layout = QVBoxLayout(self.raid_group)
        self.raid_sets_editor = RaidSetsEditor(on_change=self._nested_changed)
        raid_layout.addWidget(self.raid_sets_editor)
        self._layout.addWidget(self.raid_group)

        rewards_box = QGroupBox("Rewards")
        rewards_layout = QVBoxLayout(rewards_box)
        self.rewards_editor = RewardsEditor(
            on_change=self._nested_changed, browse_item=browse_item
        )
        rewards_layout.addWidget(self.rewards_editor)
        self._layout.addWidget(rewards_box)

        apply_row = QHBoxLayout()
        apply_btn = QPushButton("Apply to quest")
        apply_btn.clicked.connect(self.apply)
        apply_row.addWidget(apply_btn)
        apply_row.addStretch()
        self._layout.addLayout(apply_row)
        self._layout.addStretch()

        scroll.setWidget(container)
        outer.addWidget(scroll)
        self.set_board_kind("town")

    def _json_to_lang(self, json_key: str) -> str:
        if self._board_kind == "world":
            return world_json_key_to_lang_key(json_key)
        return town_json_key_to_lang_key(json_key)

    def set_browse_item(self, browse_item: Optional[Callable[[], Optional[str]]]) -> None:
        self._browse_item = browse_item
        self.item_sets_editor.set_browse_item(browse_item)
        self.rewards_editor.set_browse_item(browse_item)

    def set_board_kind(self, kind: str) -> None:
        self._board_kind = kind if kind in ("town", "world") else "town"
        is_world = self._board_kind == "world"
        self.villager_combo.setVisible(not is_world)
        self._villager_row_label.setVisible(not is_world)
        self.world_type_note.setVisible(is_world)
        self.rank_combo.setVisible(not is_world)
        self._rank_row_label.setVisible(not is_world)
        self.target_edit.setVisible(not is_world)
        self._target_label.setVisible(not is_world)
        self.target_key_label.setVisible(not is_world)
        if is_world:
            self.fetch_group.setVisible(False)
            self.hunt_group.setVisible(False)
            self.raid_group.setVisible(False)
        elif self._ref is not None and isinstance(self._ref, QuestRef):
            self._update_type_panels(self._ref.quest_type)

    def _rank_combo(self) -> QComboBox:
        c = QComboBox()
        c.addItems(self._ranks)
        c.currentTextChanged.connect(self._scalar_changed)
        return c

    def set_ranks(self, ranks: List[str]) -> None:
        self._ranks = ranks
        for combo in (self.rank_combo, self.min_rank_combo, self.max_rank_combo):
            cur = combo.currentText()
            combo.blockSignals(True)
            combo.clear()
            combo.addItems(ranks)
            if cur in ranks:
                combo.setCurrentText(cur)
            combo.blockSignals(False)

    def set_villagers(self, villager_ids: List[str]) -> None:
        self._villager_ids = villager_ids
        cur = self.villager_combo.currentText()
        self.villager_combo.blockSignals(True)
        self.villager_combo.clear()
        self.villager_combo.addItems(villager_ids)
        if cur in villager_ids:
            self.villager_combo.setCurrentText(cur)
        self.villager_combo.blockSignals(False)

    def load_quest(self, ref: Optional[AnyQuestRef]) -> None:
        self._loading = True
        self._ref = ref
        self._pending_lang.clear()
        if ref is None:
            self.setEnabled(False)
            self._loading = False
            return
        self.setEnabled(True)
        e = ref.entry
        if isinstance(ref, QuestRef):
            self.rewards_editor.set_default_npc_role_id(ref.villager_id)
            self.villager_combo.setCurrentText(ref.villager_id)
            self.type_combo.setCurrentText(ref.quest_type)
        else:
            self.rewards_editor.set_default_npc_role_id("")
            self.type_combo.setCurrentText(ref.quest_type)
        self.id_edit.setText(str(e.get("id", "")))
        if self._board_kind == "town":
            self._set_combo(self.rank_combo, str(e.get("rank", "E")))
        self._set_combo(self.min_rank_combo, str(e.get("minRank", "E")))
        self._set_combo(self.max_rank_combo, str(e.get("maxRank", "C")))
        self.weight_spin.setValue(int(e.get("weight", 10)))
        self.days_spin.setValue(int(e.get("daysLimit", 3)))
        default_xp = 10 if self._board_kind == "world" else 0
        self.rank_xp_spin.setValue(int(e.get("rankXpReward", default_xp)))

        title_key = str(e.get("titleLangKey", ""))
        desc_key = str(e.get("descriptionLangKey", ""))
        self.title_key_label.setText(title_key)
        self.desc_key_label.setText(desc_key)
        self.title_edit.setText(self._lang_getter(self._json_to_lang(title_key)))
        self.desc_edit.setPlainText(self._lang_getter(self._json_to_lang(desc_key)))

        target_key = str(e.get("targetLabelLangKey", ""))
        self.target_key_label.setText(target_key)
        self.target_edit.setText(
            self._lang_getter(self._json_to_lang(target_key)) if target_key else ""
        )

        if self._board_kind == "world":
            self.fetch_group.setVisible(False)
            self.hunt_group.setVisible(False)
            self.raid_group.setVisible(False)
        else:
            self._update_type_panels(self.type_combo.currentText())
            self.item_sets_editor.set_item_sets(e.get("itemSets") or [])
            self.kill_sets_editor.set_kill_sets(e.get("killSets") or [])
            self.raid_sets_editor.set_raid_sets(e.get("raidSets") or [])
        self.rewards_editor.set_rewards(e.get("rewards") or [])
        self._loading = False

    def pending_lang_texts(self) -> Dict[str, str]:
        return dict(self._pending_lang)

    def consume_stale_lang_keys(self) -> List[str]:
        stale = self._stale_lang_keys
        self._stale_lang_keys = []
        return stale

    def apply(self) -> None:
        if self._ref is None:
            return
        e = self._ref.entry
        e["id"] = self.id_edit.text().strip()
        if self._board_kind == "town":
            e["rank"] = self.rank_combo.currentText()
        e["minRank"] = self.min_rank_combo.currentText()
        e["maxRank"] = self.max_rank_combo.currentText()
        e["weight"] = self.weight_spin.value()
        e["daysLimit"] = self.days_spin.value()
        xp = self.rank_xp_spin.value()
        if self._board_kind == "world":
            e["rankXpReward"] = xp
            e["questType"] = self.type_combo.currentText()
        else:
            if xp > 0:
                e["rankXpReward"] = xp
            elif "rankXpReward" in e:
                del e["rankXpReward"]

        title_key = str(e.get("titleLangKey", ""))
        desc_key = str(e.get("descriptionLangKey", ""))
        if title_key:
            self._pending_lang[title_key] = self.title_edit.text()
        if desc_key:
            self._pending_lang[desc_key] = self.desc_edit.toPlainText()
        target_key = str(e.get("targetLabelLangKey", ""))
        if target_key and self._board_kind == "town":
            self._pending_lang[target_key] = self.target_edit.text()

        qtype = self.type_combo.currentText()
        if self._board_kind == "town":
            if qtype == "fetch":
                e["itemSets"] = self.item_sets_editor.get_item_sets()
                e.pop("killSets", None)
                e.pop("raidSets", None)
                e.pop("targetLabelLangKey", None)
            elif qtype == "hunt":
                e["killSets"] = self.kill_sets_editor.get_kill_sets()
                e.pop("itemSets", None)
                e.pop("raidSets", None)
            elif qtype == "raid":
                e["raidSets"] = self.raid_sets_editor.get_raid_sets()
                e.pop("itemSets", None)
                e.pop("killSets", None)

        e["rewards"] = self.rewards_editor.get_rewards()
        self._on_dirty()

    def _set_combo(self, combo: QComboBox, value: str) -> None:
        if value in self._ranks:
            combo.setCurrentText(value)
        elif self._ranks:
            combo.setCurrentIndex(0)

    def _update_type_panels(self, quest_type: str) -> None:
        if self._board_kind == "world":
            self.fetch_group.setVisible(False)
            self.hunt_group.setVisible(False)
            self.raid_group.setVisible(False)
            return
        self.fetch_group.setVisible(quest_type == "fetch")
        self.hunt_group.setVisible(quest_type == "hunt")
        self.raid_group.setVisible(quest_type == "raid")
        self.target_edit.setEnabled(quest_type in ("hunt", "raid"))

    def _identity_changed(self) -> None:
        if self._loading:
            return
        self.rewards_editor.set_default_npc_role_id(self.villager_combo.currentText())
        self._on_dirty()

    def _type_changed(self, quest_type: str) -> None:
        if self._loading or self._ref is None:
            return
        if self._board_kind == "world":
            self._ref.entry["questType"] = quest_type
            self._on_dirty()
            return
        if not isinstance(self._ref, QuestRef):
            return
        self._update_type_panels(quest_type)
        migrated = migrate_entry_type(
            self._ref.entry, quest_type, self.villager_combo.currentText()
        )
        self._ref.entry.clear()
        self._ref.entry.update(migrated)
        self.load_quest(self._ref)
        self._on_dirty()

    def _scalar_changed(self) -> None:
        if self._loading:
            return
        self._on_dirty()

    def _lang_changed(self) -> None:
        if self._loading or self._ref is None:
            return
        e = self._ref.entry
        title_key = str(e.get("titleLangKey", ""))
        desc_key = str(e.get("descriptionLangKey", ""))
        if title_key:
            self._pending_lang[title_key] = self.title_edit.text()
        if desc_key:
            self._pending_lang[desc_key] = self.desc_edit.toPlainText()
        target_key = str(e.get("targetLabelLangKey", ""))
        if target_key and self._board_kind == "town":
            self._pending_lang[target_key] = self.target_edit.text()
        self._on_dirty()

    def _nested_changed(self) -> None:
        if self._loading:
            return
        self._on_dirty()

    def _regenerate_keys(self) -> None:
        if self._ref is None:
            return
        qid = self.id_edit.text().strip() or "new_quest"
        if self._board_kind == "world":
            pending, stale = regenerate_world_lang_keys(
                self._ref.entry,
                qid,
                title_text=self.title_edit.text(),
                desc_text=self.desc_edit.toPlainText(),
            )
        else:
            vid = self.villager_combo.currentText()
            qtype = self.type_combo.currentText()
            pending, stale = regenerate_entry_lang_keys(
                self._ref.entry,
                vid,
                qid,
                qtype,
                title_text=self.title_edit.text(),
                desc_text=self.desc_edit.toPlainText(),
                target_text=self.target_edit.text(),
            )
        self._stale_lang_keys = stale
        self.load_quest(self._ref)
        self._loading = True
        self._pending_lang = pending
        self.title_edit.setText(pending.get(str(self._ref.entry.get("titleLangKey", "")), ""))
        self.desc_edit.setPlainText(
            pending.get(str(self._ref.entry.get("descriptionLangKey", "")), "")
        )
        if self._board_kind == "town":
            target_key = str(self._ref.entry.get("targetLabelLangKey", ""))
            if target_key:
                self.target_edit.setText(pending.get(target_key, ""))
        self._loading = False
        self._on_dirty()

    def current_villager(self) -> str:
        return self.villager_combo.currentText()

    def current_quest_type(self) -> str:
        return self.type_combo.currentText()
