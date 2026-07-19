from __future__ import annotations

import copy
from pathlib import Path
from typing import Dict, List, Optional, Union

from PySide6.QtCore import Qt
from PySide6.QtGui import QAction, QKeySequence
from PySide6.QtWidgets import (
    QComboBox,
    QDialog,
    QDialogButtonBox,
    QFileDialog,
    QFormLayout,
    QGroupBox,
    QHBoxLayout,
    QInputDialog,
    QLabel,
    QLineEdit,
    QListWidget,
    QMainWindow,
    QMessageBox,
    QPushButton,
    QSpinBox,
    QSplitter,
    QStatusBar,
    QVBoxLayout,
    QWidget,
)

from ..config import AppConfig, detect_board_kind
from ..io_json import load_quest_board, save_quest_board
from ..io_lang import LangDocument, load_lang, save_lang
from ..item_catalog import ItemRecord, merge_catalogs
from ..lang_keys import json_key_to_lang_key as town_json_to_lang
from ..quest_model import (
    QuestBoardDocument,
    QuestFilter,
    QuestRef,
    filter_quests,
    make_template,
    quest_description_lang_key,
    quest_title_lang_key,
    resolve_title,
    sync_lang_from_quests,
    validate_document,
    villager_short_label,
)
from ..villager_catalog import merged_villager_ids
from ..world_lang_keys import json_key_to_lang_key as world_json_to_lang
from ..world_lang_keys import (
    world_quest_description_lang_key,
    world_quest_title_lang_key,
)
from ..world_quest_model import (
    WorldQuestBoardDocument,
    WorldQuestFilter,
    WorldQuestRef,
    filter_world_quests,
    make_world_template,
    resolve_world_title,
    sync_lang_from_world_quests,
    validate_world_document,
)
from .item_browser_dialog import pick_item_id
from .quest_form import QuestForm
from .quest_list import QuestListView

AnyDoc = Union[QuestBoardDocument, WorldQuestBoardDocument]
AnyRef = Union[QuestRef, WorldQuestRef]


class BoardSettingsDialog(QDialog):
    def __init__(
        self,
        doc: AnyDoc,
        board_kind: str,
        parent: Optional[QWidget] = None,
    ) -> None:
        super().__init__(parent)
        self.setWindowTitle("Board settings")
        self._doc = doc
        self._kind = board_kind
        layout = QVBoxLayout(self)

        form = QFormLayout()
        self.slot_spin = QSpinBox()
        self.slot_spin.setRange(1, 12)
        self.slot_spin.setValue(int(doc.data.get("slotCount", 3)))
        form.addRow("Slot count", self.slot_spin)

        self.profile_edit: Optional[QLineEdit] = None
        self.fetch_weight: Optional[QSpinBox] = None
        self.hunt_weight: Optional[QSpinBox] = None
        self.raid_weight: Optional[QSpinBox] = None

        if board_kind == "world":
            self.profile_edit = QLineEdit(str(doc.data.get("profileId", "")))
            form.addRow("Profile id", self.profile_edit)
        else:
            qt = doc.data.get("questTypes") or {}
            self.fetch_weight = QSpinBox()
            self.fetch_weight.setRange(0, 9999)
            self.fetch_weight.setValue(int((qt.get("fetch") or {}).get("weight", 65)))
            form.addRow("Fetch type weight", self.fetch_weight)
            self.hunt_weight = QSpinBox()
            self.hunt_weight.setRange(0, 9999)
            self.hunt_weight.setValue(int((qt.get("hunt") or {}).get("weight", 45)))
            form.addRow("Hunt type weight", self.hunt_weight)
            self.raid_weight = QSpinBox()
            self.raid_weight.setRange(0, 9999)
            self.raid_weight.setValue(int((qt.get("raid") or {}).get("weight", 20)))
            form.addRow("Raid type weight", self.raid_weight)
        layout.addLayout(form)

        buttons = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel
        )
        buttons.accepted.connect(self.accept)
        buttons.rejected.connect(self.reject)
        layout.addWidget(buttons)

    def apply(self) -> None:
        self._doc.data["slotCount"] = self.slot_spin.value()
        if self._kind == "world" and self.profile_edit is not None:
            self._doc.data["profileId"] = self.profile_edit.text().strip()
        elif (
            self.fetch_weight is not None
            and self.hunt_weight is not None
            and self.raid_weight is not None
        ):
            self._doc.data["questTypes"] = {
                "fetch": {"weight": self.fetch_weight.value()},
                "hunt": {"weight": self.hunt_weight.value()},
                "raid": {"weight": self.raid_weight.value()},
            }


class ItemRootsDialog(QDialog):
    def __init__(self, roots: List[Path], parent: Optional[QWidget] = None) -> None:
        super().__init__(parent)
        self.setWindowTitle("Item asset roots")
        self.resize(560, 360)
        self._roots = [Path(p) for p in roots]
        layout = QVBoxLayout(self)
        layout.addWidget(
            QLabel("Later roots override earlier for the same item id.")
        )
        self.list = QListWidget()
        for r in self._roots:
            self.list.addItem(str(r))
        layout.addWidget(self.list)
        row = QHBoxLayout()
        add_btn = QPushButton("Add folder")
        add_btn.clicked.connect(self._add)
        rem_btn = QPushButton("Remove")
        rem_btn.clicked.connect(self._remove)
        row.addWidget(add_btn)
        row.addWidget(rem_btn)
        row.addStretch()
        layout.addLayout(row)
        buttons = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel
        )
        buttons.accepted.connect(self.accept)
        buttons.rejected.connect(self.reject)
        layout.addWidget(buttons)

    def _add(self) -> None:
        path = QFileDialog.getExistingDirectory(self, "Add item asset root")
        if path:
            self.list.addItem(path)

    def _remove(self) -> None:
        for item in self.list.selectedItems():
            self.list.takeItem(self.list.row(item))

    def roots(self) -> List[Path]:
        out: List[Path] = []
        for i in range(self.list.count()):
            text = self.list.item(i).text().strip()
            if text:
                out.append(Path(text))
        return out


class MainWindow(QMainWindow):
    def __init__(self) -> None:
        super().__init__()
        self._config = AppConfig.load()
        self._json_path: Optional[Path] = None
        self._lang_path: Optional[Path] = None
        self._board_kind = "town"
        self._doc: Optional[AnyDoc] = None
        self._lang_doc: Optional[LangDocument] = None
        self._all_refs: List[AnyRef] = []
        self._filtered_indices: List[int] = []
        self._all_villager_ids: List[str] = []
        self._dirty = False
        self._pending_lang: Dict[str, str] = {}
        self._removed_lang_json_keys: set[str] = set()
        self._item_roots: List[Path] = self._config.resolved_item_roots()
        self._catalog: Dict[str, ItemRecord] = {}

        self.setWindowTitle("Aetherhaven Quest Board Editor")
        self.resize(1280, 820)
        self.setStatusBar(QStatusBar())
        self._build_menu()
        self._build_ui()
        self._rescan_catalog()

        if self._config.last_board_kind == "world":
            qb = self._config.resolved_world_board_path()
            lg = self._config.resolved_world_lang_path()
        else:
            qb = self._config.resolved_quest_board_path()
            lg = self._config.resolved_lang_path()
        if qb.is_file():
            self._load_files(qb, lg)
        else:
            QMessageBox.warning(
                self,
                "Quest board not found",
                f"Could not find board JSON at:\n{qb}",
            )

    def _build_menu(self) -> None:
        file_menu = self.menuBar().addMenu("&File")
        open_act = QAction("&Open JSON...", self)
        open_act.setShortcut(QKeySequence.StandardKey.Open)
        open_act.triggered.connect(self._open_json)
        file_menu.addAction(open_act)
        open_town = QAction("Open &town board…", self)
        open_town.triggered.connect(self._open_town_default)
        file_menu.addAction(open_town)
        open_world = QAction("Open &world board…", self)
        open_world.triggered.connect(self._open_world)
        file_menu.addAction(open_world)
        save_act = QAction("&Save", self)
        save_act.setShortcut(QKeySequence.StandardKey.Save)
        save_act.triggered.connect(self._save)
        file_menu.addAction(save_act)
        save_as_act = QAction("Save &As...", self)
        save_as_act.setShortcut(QKeySequence.StandardKey.SaveAs)
        save_as_act.triggered.connect(self._save_as)
        file_menu.addAction(save_as_act)
        file_menu.addSeparator()
        reload_act = QAction("&Reload", self)
        reload_act.triggered.connect(self._reload)
        file_menu.addAction(reload_act)
        file_menu.addSeparator()
        settings_act = QAction("Board &settings...", self)
        settings_act.triggered.connect(self._board_settings)
        file_menu.addAction(settings_act)

        tools_menu = self.menuBar().addMenu("&Tools")
        roots_act = QAction("Item asset &roots…", self)
        roots_act.triggered.connect(self._edit_item_roots)
        tools_menu.addAction(roots_act)
        rescan_act = QAction("&Rescan item catalog", self)
        rescan_act.triggered.connect(self._rescan_catalog)
        tools_menu.addAction(rescan_act)

    def _build_ui(self) -> None:
        central = QWidget()
        self.setCentralWidget(central)
        root = QHBoxLayout(central)
        splitter = QSplitter(Qt.Orientation.Horizontal)
        root.addWidget(splitter)

        left = QWidget()
        left_layout = QVBoxLayout(left)

        filt_box = QGroupBox("Filters")
        filt_form = QFormLayout(filt_box)
        self.villager_filter = QComboBox()
        self.villager_filter.currentIndexChanged.connect(self._refresh_list)
        self._villager_filter_label = QLabel("Villager")
        filt_form.addRow(self._villager_filter_label, self.villager_filter)
        self.type_filter = QComboBox()
        self.type_filter.addItems(["All", "fetch", "hunt", "raid"])
        self.type_filter.currentIndexChanged.connect(self._refresh_list)
        filt_form.addRow("Quest type", self.type_filter)
        self.rank_filter = QComboBox()
        self.rank_filter.currentIndexChanged.connect(self._refresh_list)
        filt_form.addRow("Rank", self.rank_filter)
        self.search_edit = QLineEdit()
        self.search_edit.setPlaceholderText("Search id or title...")
        self.search_edit.textChanged.connect(self._refresh_list)
        filt_form.addRow("Search", self.search_edit)
        left_layout.addWidget(filt_box)

        self.count_label = QLabel("")
        left_layout.addWidget(self.count_label)

        self.quest_list = QuestListView()
        self.quest_list.selectionModel().selectionChanged.connect(self._on_selection_changed)
        left_layout.addWidget(self.quest_list)

        btn_row = QHBoxLayout()
        new_btn = QPushButton("New quest")
        new_btn.clicked.connect(self._new_quest)
        dup_btn = QPushButton("Duplicate")
        dup_btn.clicked.connect(self._duplicate_quest)
        del_btn = QPushButton("Delete")
        del_btn.clicked.connect(self._delete_quest)
        btn_row.addWidget(new_btn)
        btn_row.addWidget(dup_btn)
        btn_row.addWidget(del_btn)
        left_layout.addLayout(btn_row)

        splitter.addWidget(left)

        self.quest_form = QuestForm(
            ranks=[],
            villager_ids=[],
            lang_getter=self._lang_text,
            on_dirty=self._mark_dirty,
            browse_item=self._browse_item,
        )
        splitter.addWidget(self.quest_form)
        splitter.setStretchFactor(0, 2)
        splitter.setStretchFactor(1, 3)

    def _browse_item(self) -> Optional[str]:
        if not self._catalog:
            QMessageBox.information(
                self,
                "No items",
                "Item catalog is empty. Use Tools → Item asset roots… and Rescan.",
            )
            return None
        return pick_item_id(self, self._catalog, self._item_roots)

    def _rescan_catalog(self) -> None:
        self._catalog = merge_catalogs(self._item_roots)
        self.statusBar().showMessage(
            f"Item catalog: {len(self._catalog)} items from {len(self._item_roots)} root(s)",
            5000,
        )

    def _edit_item_roots(self) -> None:
        dlg = ItemRootsDialog(self._item_roots, self)
        if dlg.exec() != QDialog.DialogCode.Accepted:
            return
        self._item_roots = dlg.roots()
        self._config.item_roots = [str(p) for p in self._item_roots]
        self._config.save()
        self._rescan_catalog()

    def _json_to_lang(self, json_key: str) -> str:
        if self._board_kind == "world":
            return world_json_to_lang(json_key)
        return town_json_to_lang(json_key)

    def _lang_text(self, lang_key: str) -> str:
        if self._lang_doc is None:
            return ""
        pending_json = {self._json_to_lang(k): v for k, v in self._pending_lang.items()}
        if lang_key in pending_json:
            return pending_json[lang_key]
        return self._lang_doc.get(lang_key, "")

    def _lang_getter_for_filter(self, lang_key: str, default: str = "") -> str:
        return self._lang_text(lang_key) or default

    def _apply_board_kind_ui(self) -> None:
        is_world = self._board_kind == "world"
        self.villager_filter.setVisible(not is_world)
        self._villager_filter_label.setVisible(not is_world)
        self.quest_list.set_board_kind(self._board_kind)
        self.quest_form.set_board_kind(self._board_kind)

    def _load_files(self, json_path: Path, lang_path: Optional[Path] = None) -> None:
        try:
            data = load_quest_board(json_path)
        except Exception as e:
            QMessageBox.critical(self, "Load failed", str(e))
            return

        kind = detect_board_kind(data)
        if lang_path is None:
            lang_path = (
                self._config.resolved_world_lang_path()
                if kind == "world"
                else self._config.resolved_lang_path()
            )
        try:
            lang_doc = load_lang(lang_path) if lang_path.is_file() else LangDocument()
        except Exception as e:
            QMessageBox.critical(self, "Load failed", str(e))
            return

        self._json_path = json_path
        self._lang_path = lang_path
        self._board_kind = kind
        self._doc = (
            WorldQuestBoardDocument(data)
            if kind == "world"
            else QuestBoardDocument(data)
        )
        self._lang_doc = lang_doc
        self._pending_lang.clear()
        self._removed_lang_json_keys.clear()
        self._dirty = False
        self._apply_board_kind_ui()
        self._rebuild_index()
        self._update_filters()
        self._refresh_list()
        self._update_title()
        self._update_status()

        self._config.last_board_kind = kind
        if kind == "world":
            self._config.world_board_path = str(json_path)
            self._config.world_lang_path = str(lang_path)
        else:
            self._config.quest_board_path = str(json_path)
            self._config.lang_path = str(lang_path)
        self._config.save()

    def _update_status(self) -> None:
        kind = self._board_kind
        path = str(self._json_path) if self._json_path else ""
        self.statusBar().showMessage(
            f"{kind} board — {path} — catalog {len(self._catalog)} items"
        )

    def _rebuild_index(self) -> None:
        if self._doc is None:
            self._all_refs = []
            return
        self._all_refs = list(self._doc.flatten())  # type: ignore[arg-type]

    def _update_filters(self) -> None:
        if self._doc is None:
            return
        self.rank_filter.blockSignals(True)
        self.villager_filter.blockSignals(True)
        self.rank_filter.clear()
        self.rank_filter.addItem("All")
        for r in self._doc.ranks:
            self.rank_filter.addItem(r)

        if isinstance(self._doc, QuestBoardDocument):
            self._all_villager_ids = merged_villager_ids(self._doc.villager_ids())
            cur_villager = self.villager_filter.currentText()
            self.villager_filter.clear()
            self.villager_filter.addItem("All")
            for vid in self._all_villager_ids:
                self.villager_filter.addItem(vid)
            if cur_villager and self.villager_filter.findText(cur_villager) >= 0:
                self.villager_filter.setCurrentText(cur_villager)
            self.quest_form.set_villagers(self._all_villager_ids)
        else:
            self._all_villager_ids = []
            self.quest_form.set_villagers([])

        self.villager_filter.blockSignals(False)
        self.rank_filter.blockSignals(False)
        self.quest_form.set_ranks(self._doc.ranks)

    def _refresh_list(self) -> None:
        if self._doc is None:
            return
        self._filtered_indices = []
        rows = []
        if self._board_kind == "world":
            tf = self.type_filter.currentText()
            rf = self.rank_filter.currentText()
            filt = WorldQuestFilter(
                quest_type=None if tf == "All" else tf,
                rank=None if rf == "All" else rf,
                search=self.search_edit.text(),
            )
            filtered = filter_world_quests(
                [r for r in self._all_refs if isinstance(r, WorldQuestRef)],
                filt,
                self._lang_getter_for_filter,
            )
            for ref in filtered:
                try:
                    idx = self._all_refs.index(ref)
                except ValueError:
                    continue
                self._filtered_indices.append(idx)
                title = resolve_world_title(self._lang_getter_for_filter, ref)
                rows.append((ref.rank, ref.quest_type, ref.quest_id, title, idx))
        else:
            vf = self.villager_filter.currentText()
            tf = self.type_filter.currentText()
            rf = self.rank_filter.currentText()
            filt = QuestFilter(
                villager_id=None if vf == "All" else vf,
                quest_type=None if tf == "All" else tf,
                rank=None if rf == "All" else rf,
                search=self.search_edit.text(),
            )
            filtered = filter_quests(
                [r for r in self._all_refs if isinstance(r, QuestRef)],
                filt,
                self._lang_getter_for_filter,
            )
            for ref in filtered:
                try:
                    idx = self._all_refs.index(ref)
                except ValueError:
                    continue
                self._filtered_indices.append(idx)
                title = resolve_title(self._lang_getter_for_filter, ref)
                rows.append(
                    (
                        ref.rank,
                        ref.quest_type,
                        villager_short_label(ref.villager_id),
                        ref.quest_id,
                        title,
                        idx,
                    )
                )
        self.quest_list.quest_model().set_rows(rows)
        total = len(self._all_refs)
        self.count_label.setText(f"{len(rows)} / {total} quests")

    def _on_selection_changed(self) -> None:
        idx = self.quest_list.selected_ref_index()
        if idx < 0 or idx >= len(self._all_refs):
            self.quest_form.load_quest(None)
            return
        self.quest_form.load_quest(self._all_refs[idx])

    def _selected_ref(self) -> Optional[AnyRef]:
        idx = self.quest_list.selected_ref_index()
        if idx < 0 or idx >= len(self._all_refs):
            return None
        return self._all_refs[idx]

    def _mark_dirty(self) -> None:
        self._dirty = True
        self._update_title()
        pending = self.quest_form.pending_lang_texts()
        self._pending_lang.update(pending)
        for stale in self.quest_form.consume_stale_lang_keys():
            self._pending_lang.pop(stale, None)
            self._removed_lang_json_keys.add(stale)

    def _update_title(self) -> None:
        name = self._json_path.name if self._json_path else "untitled"
        star = " *" if self._dirty else ""
        kind = self._board_kind
        self.setWindowTitle(f"{name}{star} — {kind} — Aetherhaven Quest Board Editor")

    def _apply_form_to_selected(self) -> None:
        ref = self._selected_ref()
        if ref is None or self._doc is None:
            self.quest_form.apply()
            self._pending_lang.update(self.quest_form.pending_lang_texts())
            return
        if isinstance(self._doc, QuestBoardDocument) and isinstance(ref, QuestRef):
            old_villager = ref.villager_id
            old_type = ref.quest_type
            self.quest_form.apply()
            self._pending_lang.update(self.quest_form.pending_lang_texts())
            new_villager = self.quest_form.current_villager()
            new_type = self.quest_form.current_quest_type()
            if new_villager != old_villager or new_type != old_type:
                moved = self._doc.move_quest(ref, new_villager, new_type)
                self._rebuild_index()
                idx = self._all_refs.index(moved)
                self._refresh_list()
                self._select_ref_index(idx)
        else:
            self.quest_form.apply()
            self._pending_lang.update(self.quest_form.pending_lang_texts())
            self._rebuild_index()
            self._refresh_list()

    def _save(self) -> bool:
        if self._doc is None or self._json_path is None or self._lang_doc is None:
            return False
        self._apply_form_to_selected()

        lang_getter = lambda k, d="": self._lang_doc.get(k, d) if self._lang_doc else d  # noqa: E731
        for json_key in self._removed_lang_json_keys:
            self._lang_doc.remove(self._json_to_lang(json_key))
        for json_key, text in self._pending_lang.items():
            self._lang_doc.set(self._json_to_lang(json_key), text)

        if isinstance(self._doc, WorldQuestBoardDocument):
            sync_lang_from_world_quests(self._doc, self._lang_doc, self._pending_lang)
            errors = validate_world_document(self._doc, lang_getter)
        else:
            sync_lang_from_quests(self._doc, self._lang_doc, self._pending_lang)
            errors = validate_document(self._doc, lang_getter)

        if errors:
            QMessageBox.warning(
                self,
                "Validation failed",
                "Fix these issues before saving:\n\n" + "\n".join(errors[:20]),
            )
            return False

        try:
            save_quest_board(self._json_path, self._doc.data)
            if self._lang_path:
                save_lang(self._lang_path, self._lang_doc)
        except Exception as e:
            QMessageBox.critical(self, "Save failed", str(e))
            return False

        self._dirty = False
        self._pending_lang.clear()
        self._removed_lang_json_keys.clear()
        self._update_title()
        self._refresh_list()
        self._update_status()
        return True

    def _save_as(self) -> None:
        path, _ = QFileDialog.getSaveFileName(
            self,
            "Save quest board JSON",
            str(self._json_path or ""),
            "JSON (*.json)",
        )
        if not path:
            return
        self._json_path = Path(path)
        self._save()

    def _open_json(self) -> None:
        if not self._confirm_discard():
            return
        start = (
            self._config.resolved_world_board_path().parent
            if self._board_kind == "world"
            else self._config.resolved_quest_board_path().parent
        )
        path, _ = QFileDialog.getOpenFileName(
            self,
            "Open quest board JSON",
            str(start),
            "JSON (*.json)",
        )
        if not path:
            return
        self._load_files(Path(path))

    def _open_town_default(self) -> None:
        if not self._confirm_discard():
            return
        start = str(self._config.resolved_quest_board_path().parent)
        path, _ = QFileDialog.getOpenFileName(
            self, "Open town quest board", start, "JSON (*.json)"
        )
        if not path:
            return
        self._load_files(Path(path), self._config.resolved_lang_path())

    def _open_world(self) -> None:
        if not self._confirm_discard():
            return
        start = str(self._config.resolved_world_board_path().parent)
        path, _ = QFileDialog.getOpenFileName(
            self, "Open world quest board", start, "JSON (*.json)"
        )
        if not path:
            return
        self._load_files(Path(path), self._config.resolved_world_lang_path())

    def _reload(self) -> None:
        if self._json_path is None or self._lang_path is None:
            return
        if not self._confirm_discard():
            return
        self._load_files(self._json_path, self._lang_path)

    def _confirm_discard(self) -> bool:
        if not self._dirty:
            return True
        ans = QMessageBox.question(
            self,
            "Unsaved changes",
            "Discard unsaved changes?",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
        )
        return ans == QMessageBox.StandardButton.Yes

    def _board_settings(self) -> None:
        if self._doc is None:
            return
        dlg = BoardSettingsDialog(self._doc, self._board_kind, self)
        if dlg.exec() == QDialog.DialogCode.Accepted:
            dlg.apply()
            self._mark_dirty()
            self._update_status()

    def _new_quest(self) -> None:
        if self._doc is None:
            return
        tf = self.type_filter.currentText()
        quest_type = tf if tf != "All" else "fetch"

        qid, ok = QInputDialog.getText(self, "New quest", "Quest id:", text="new_quest")
        if not ok or not qid.strip():
            return
        qid = qid.strip()

        if isinstance(self._doc, WorldQuestBoardDocument):
            entry = make_world_template(qid, quest_type)
            ref = self._doc.insert_quest(entry)
            self._pending_lang[entry["titleLangKey"]] = "New quest title"
            self._pending_lang[entry["descriptionLangKey"]] = "New quest description."
        else:
            vf = self.villager_filter.currentText()
            villager_id = vf if vf != "All" else (
                self._all_villager_ids[0] if self._all_villager_ids else "Aetherhaven_Miner"
            )
            entry = make_template(villager_id, quest_type, qid)
            ref = self._doc.insert_quest(villager_id, quest_type, entry)
            self._pending_lang[entry["titleLangKey"]] = "New quest title"
            self._pending_lang[entry["descriptionLangKey"]] = "New quest description."
            if quest_type in ("hunt", "raid"):
                tk = entry.get("targetLabelLangKey")
                if isinstance(tk, str):
                    self._pending_lang[tk] = "targets"

        self._rebuild_index()
        self._mark_dirty()
        self._update_filters()
        self._refresh_list()
        idx = self._all_refs.index(ref)
        self._select_ref_index(idx)

    def _duplicate_quest(self) -> None:
        ref = self._selected_ref()
        if ref is None or self._doc is None:
            return
        self._apply_form_to_selected()
        qid, ok = QInputDialog.getText(
            self, "Duplicate quest", "New quest id:", text=f"{ref.quest_id}_copy"
        )
        if not ok or not qid.strip():
            return
        qid = qid.strip()
        entry = copy.deepcopy(ref.entry)
        entry["id"] = qid
        old_title_key = str(ref.entry.get("titleLangKey", ""))
        old_desc_key = str(ref.entry.get("descriptionLangKey", ""))

        if isinstance(self._doc, WorldQuestBoardDocument):
            entry["titleLangKey"] = world_quest_title_lang_key(qid)
            entry["descriptionLangKey"] = world_quest_description_lang_key(qid)
            new_ref = self._doc.insert_quest(entry)
        else:
            assert isinstance(ref, QuestRef)
            entry["titleLangKey"] = quest_title_lang_key(ref.villager_id, qid)
            entry["descriptionLangKey"] = quest_description_lang_key(ref.villager_id, qid)
            new_ref = self._doc.insert_quest(ref.villager_id, ref.quest_type, entry)
            target_key = entry.get("targetLabelLangKey")
            old_target_key = ref.entry.get("targetLabelLangKey")
            if isinstance(target_key, str) and isinstance(old_target_key, str):
                self._pending_lang[target_key] = self._lang_text(
                    self._json_to_lang(old_target_key)
                )

        self._pending_lang[str(entry["titleLangKey"])] = (
            self._lang_text(self._json_to_lang(old_title_key)) + " (copy)"
        )
        self._pending_lang[str(entry["descriptionLangKey"])] = self._lang_text(
            self._json_to_lang(old_desc_key)
        )
        self._rebuild_index()
        self._mark_dirty()
        self._refresh_list()
        self._select_ref_index(self._all_refs.index(new_ref))

    def _delete_quest(self) -> None:
        ref = self._selected_ref()
        if ref is None or self._doc is None:
            return
        if isinstance(ref, QuestRef):
            msg = f"Delete {ref.quest_id} ({ref.quest_type}) from {ref.villager_id}?"
        else:
            msg = f"Delete pool entry {ref.quest_id} ({ref.quest_type})?"
        ans = QMessageBox.question(
            self,
            "Delete quest",
            msg,
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
        )
        if ans != QMessageBox.StandardButton.Yes:
            return
        self._doc.remove_quest(ref)  # type: ignore[arg-type]
        self._rebuild_index()
        self._mark_dirty()
        self._refresh_list()
        self.quest_form.load_quest(None)

    def _select_ref_index(self, idx: int) -> None:
        for row, ref_idx in enumerate(self._filtered_indices):
            if ref_idx == idx:
                self.quest_list.selectRow(row)
                return
        self._refresh_list()
        for row, ref_idx in enumerate(self._filtered_indices):
            if ref_idx == idx:
                self.quest_list.selectRow(row)
                return

    def closeEvent(self, event) -> None:  # type: ignore[override]
        if self._dirty:
            ans = QMessageBox.question(
                self,
                "Unsaved changes",
                "Save before closing?",
                QMessageBox.StandardButton.Save
                | QMessageBox.StandardButton.Discard
                | QMessageBox.StandardButton.Cancel,
            )
            if ans == QMessageBox.StandardButton.Save:
                if not self._save():
                    event.ignore()
                    return
            elif ans == QMessageBox.StandardButton.Cancel:
                event.ignore()
                return
        event.accept()
