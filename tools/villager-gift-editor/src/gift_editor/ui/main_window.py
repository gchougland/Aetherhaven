from __future__ import annotations

import json
from pathlib import Path
from typing import Dict, List, Optional, Set

from PySide6.QtCore import (
    QEvent,
    QModelIndex,
    QObject,
    QSize,
    Qt,
    QAbstractListModel,
    QTimer,
)
from PySide6.QtGui import QIcon, QPixmap
from PySide6.QtWidgets import (
    QAbstractItemView,
    QButtonGroup,
    QComboBox,
    QFileDialog,
    QGroupBox,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QListView,
    QListWidget,
    QMainWindow,
    QMessageBox,
    QPushButton,
    QRadioButton,
    QSplitter,
    QVBoxLayout,
    QWidget,
)

from ..catalog import ItemRecord, merge_catalogs, all_categories, pick_icon_path_for_item
from ..click_mode import ClickMode
from ..config import AppConfig, config_file_path, default_item_roots, default_villagers_dir
from ..gift_template_io import load_gift_template, save_gift_template
from ..ignored_items_store import load_ignored_ids, save_ignored_ids, toggle_ignored_id
from ..preferences import apply_preference, preference_for_item, restore_gifts, snapshot_gifts
from ..villager_io import apply_gifts_to_data, extract_gift_lists, load_villager, save_villager
from .grid_delegate import GiftGridItemDelegate


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


class MainWindow(QMainWindow):
    def __init__(self) -> None:
        super().__init__()
        self.setWindowTitle("Aetherhaven — Villager gift preferences")
        self._cfg = AppConfig.load()
        self._catalog: Dict[str, ItemRecord] = {}
        self._item_roots: List[Path] = self._cfg.resolved_item_roots()
        self._villager_paths: List[Path] = []
        self._villager_path: Optional[Path] = None
        self._villager_data: Dict = {}
        self._loves: List[str] = []
        self._likes: List[str] = []
        self._dislikes: List[str] = []
        self._baseline: tuple[List[str], List[str], List[str]] = ([], [], [])
        self._dirty = False
        self._ignored_ids: Set[str] = load_ignored_ids()
        self._mode = ClickMode.LIKE
        self._combo_guard = False
        self._last_stable_combo_index = -1
        self._grid_model = _ItemGridModel()
        self._build_ui()
        self._sync_villagers_dir_display()
        self._reload_config_lists()
        self._rescan_catalog()
        self._scan_villagers()

    def _build_ui(self) -> None:
        central = QWidget()
        self.setCentralWidget(central)
        outer = QVBoxLayout(central)

        split = QSplitter(Qt.Orientation.Horizontal)

        left = QWidget()
        left_l = QVBoxLayout(left)
        grp_roots = QGroupBox("Item asset roots (later entries override earlier for same item id)")
        rl = QVBoxLayout(grp_roots)
        self._roots_list = QListWidget()
        self._roots_list.setMinimumWidth(280)
        rl.addWidget(self._roots_list)
        rbtn = QHBoxLayout()
        b_add = QPushButton("Add folder")
        b_add.clicked.connect(self._add_root)
        b_rm = QPushButton("Remove")
        b_rm.clicked.connect(self._remove_root)
        b_save_cfg = QPushButton("Save settings")
        b_save_cfg.clicked.connect(self._save_settings)
        rbtn.addWidget(b_add)
        rbtn.addWidget(b_rm)
        rbtn.addWidget(b_save_cfg)
        rl.addLayout(rbtn)
        left_l.addWidget(grp_roots)

        grp_vdir = QGroupBox("Villagers folder")
        vdir_outer = QVBoxLayout(grp_vdir)
        vdir_row = QHBoxLayout()
        self._villagers_edit = QLineEdit()
        self._villagers_edit.setReadOnly(True)
        b_browse_v = QPushButton("Browse…")
        b_browse_v.clicked.connect(self._browse_villagers_dir)
        vdir_row.addWidget(self._villagers_edit, 1)
        vdir_row.addWidget(b_browse_v)
        vdir_outer.addLayout(vdir_row)
        left_l.addWidget(grp_vdir)

        b_rescan = QPushButton("Rescan item catalog")
        b_rescan.clicked.connect(self._rescan_catalog)
        left_l.addWidget(b_rescan)
        left_l.addStretch(1)
        split.addWidget(left)

        right = QWidget()
        rr = QVBoxLayout(right)
        top = QHBoxLayout()
        self._villager_combo = QComboBox()
        self._villager_combo.setMinimumWidth(240)
        self._villager_combo.currentIndexChanged.connect(self._on_villager_changed)
        top.addWidget(QLabel("Villager:"))
        top.addWidget(self._villager_combo, 1)
        b_reload_v = QPushButton("Rescan villagers")
        b_reload_v.clicked.connect(self._scan_villagers)
        top.addWidget(b_reload_v)
        self._b_save = QPushButton("Save")
        self._b_save.clicked.connect(self._save_villager)
        self._b_revert = QPushButton("Revert")
        self._b_revert.clicked.connect(self._revert_villager)
        top.addWidget(self._b_save)
        top.addWidget(self._b_revert)
        rr.addLayout(top)

        self._path_lbl = QLabel("")
        self._path_lbl.setTextInteractionFlags(
            Qt.TextInteractionFlag.TextSelectableByMouse
        )
        rr.addWidget(self._path_lbl)

        tpl_row = QHBoxLayout()
        tpl_row.addWidget(QLabel("Gift template:"))
        b_save_tpl = QPushButton("Save template…")
        b_save_tpl.clicked.connect(self._save_gift_template)
        tpl_row.addWidget(b_save_tpl)
        b_load_tpl = QPushButton("Load template…")
        b_load_tpl.clicked.connect(self._load_gift_template)
        tpl_row.addWidget(b_load_tpl)
        tpl_row.addStretch(1)
        rr.addLayout(tpl_row)

        mode_box = QGroupBox(
            "Click mode (Love/Like/Neutral/Dislike edit villager gifts; Ignore toggles hide list, saved to disk)"
        )
        mode_l = QHBoxLayout(mode_box)
        self._mode_group = QButtonGroup(self)
        for mode, label in (
            (ClickMode.LOVE, "Love"),
            (ClickMode.LIKE, "Like"),
            (ClickMode.NEUTRAL, "Neutral"),
            (ClickMode.DISLIKE, "Dislike"),
            (ClickMode.IGNORE, "Ignore"),
        ):
            rb = QRadioButton(label)
            rb.toggled.connect(
                lambda checked, m=mode: self._on_mode_toggled(m, checked)
            )
            self._mode_group.addButton(rb)
            mode_l.addWidget(rb)
            if mode == ClickMode.LIKE:
                rb.setChecked(True)
        rr.addWidget(mode_box)

        filt = QHBoxLayout()
        filt.addWidget(QLabel("Search:"))
        self._search = QLineEdit()
        self._search.setPlaceholderText("Item id or translation name substring…")
        self._search.textChanged.connect(lambda _t: self._rebuild_filter())
        filt.addWidget(self._search, 1)
        filt.addWidget(QLabel("Category:"))
        self._category = QComboBox()
        self._category.addItem("(All categories)", "")
        self._category.currentIndexChanged.connect(lambda _i: self._rebuild_filter())
        filt.addWidget(self._category)
        filt.addWidget(QLabel("Preference:"))
        self._pref_filter = QComboBox()
        for key, lab in (
            ("all", "All"),
            ("love", "Love"),
            ("like", "Like"),
            ("neutral", "Neutral"),
            ("dislike", "Dislike"),
            ("ignored", "Ignored only"),
        ):
            self._pref_filter.addItem(lab, key)
        self._pref_filter.currentIndexChanged.connect(lambda _i: self._rebuild_filter())
        filt.addWidget(self._pref_filter)
        rr.addLayout(filt)

        self._list = QListView()
        self._list.setViewMode(QListView.IconMode)
        self._list.setUniformItemSizes(False)
        self._list.setIconSize(QSize(48, 48))
        self._list.setResizeMode(QListView.Adjust)
        self._list.setMovement(QListView.Static)
        self._list.setWordWrap(True)
        self._list.setTextElideMode(Qt.TextElideMode.ElideNone)
        self._list.setSpacing(6)
        self._list.setMouseTracking(True)
        self._list.setSelectionMode(QAbstractItemView.SelectionMode.SingleSelection)
        self._list.setModel(self._grid_model)
        self._gift_delegate = GiftGridItemDelegate(
            lambda: (self._loves, self._likes, self._dislikes),
            cell_width=168,
            icon_size=48,
            parent=self._list,
        )
        self._list.setItemDelegate(self._gift_delegate)
        self._list.viewport().installEventFilter(self)
        self._list.clicked.connect(self._on_item_clicked)
        self._cell_layout_timer = QTimer(self)
        self._cell_layout_timer.setSingleShot(True)
        self._cell_layout_timer.setInterval(50)
        self._cell_layout_timer.timeout.connect(self._apply_delegate_cell_width)
        rr.addWidget(self._list, 1)

        split.addWidget(right)
        split.setStretchFactor(0, 0)
        split.setStretchFactor(1, 1)
        outer.addWidget(split)
        self._update_title_dirty()
        self._sync_villagers_dir_display()
        QTimer.singleShot(0, self._request_delegate_cell_layout)

    def _request_delegate_cell_layout(self) -> None:
        self._cell_layout_timer.start()

    def _apply_delegate_cell_width(self) -> None:
        vw = self._list.viewport().width()
        if vw < 60:
            return
        cell_w = max(120, min(200, vw // 4))
        self._gift_delegate.set_cell_width(cell_w)
        self._list.scheduleDelayedItemsLayout()

    def eventFilter(self, obj: QObject, event: QEvent) -> bool:
        if obj is self._list.viewport() and event.type() == QEvent.Type.Resize:
            self._request_delegate_cell_layout()
        return super().eventFilter(obj, event)

    def _gift_template_start_dir(self) -> str:
        p = self._cfg.last_gift_template_path
        if p:
            parent = Path(p).expanduser().resolve().parent
            if parent.is_dir():
                return str(parent)
        return str(Path.home())

    def _persist_last_template_path(self, path: Path) -> None:
        try:
            self._cfg.last_gift_template_path = str(path.resolve())
            self._cfg.save()
        except OSError:
            pass

    def _save_gift_template(self) -> None:
        start = self._gift_template_start_dir()
        default_name = str(Path(start) / "gift_defaults.json")
        path, _ = QFileDialog.getSaveFileName(
            self,
            "Save gift template",
            default_name,
            "JSON (*.json);;All files (*.*)",
        )
        if not path:
            return
        pp = Path(path)
        try:
            save_gift_template(pp, self._loves, self._likes, self._dislikes)
        except OSError as e:
            QMessageBox.warning(self, "Save template", str(e))
            return
        self._persist_last_template_path(pp)
        QMessageBox.information(self, "Save template", f"Saved:\n{pp}")

    def _load_gift_template(self) -> None:
        if not self._villager_path:
            QMessageBox.information(
                self,
                "Load template",
                "Select a villager first, then load a template to replace that villager’s gift lists.",
            )
            return
        if self._dirty:
            r = QMessageBox.question(
                self,
                "Unsaved changes",
                "Discard unsaved edits and replace gifts from the template?",
                QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
                QMessageBox.StandardButton.No,
            )
            if r != QMessageBox.StandardButton.Yes:
                return
        start = self._gift_template_start_dir()
        last = self._cfg.last_gift_template_path
        open_in = start
        if last:
            lp = Path(last).expanduser()
            if lp.is_file():
                open_in = str(lp)
        path, _ = QFileDialog.getOpenFileName(
            self,
            "Load gift template",
            open_in,
            "JSON (*.json);;All files (*.*)",
        )
        if not path:
            return
        pp = Path(path)
        try:
            loves, likes, dislikes = load_gift_template(pp)
        except (OSError, ValueError, json.JSONDecodeError) as e:
            QMessageBox.warning(self, "Load template", str(e))
            return
        self._loves[:] = loves
        self._likes[:] = likes
        self._dislikes[:] = dislikes
        self._dirty = True
        self._persist_last_template_path(pp)
        self._update_title_dirty()
        self._rebuild_filter()

    def _reload_config_lists(self) -> None:
        self._roots_list.clear()
        for s in self._cfg.item_roots:
            if s.strip():
                self._roots_list.addItem(s.strip())
        if self._roots_list.count() == 0:
            for p in default_item_roots():
                self._roots_list.addItem(str(p))

    def _roots_from_list(self) -> List[Path]:
        out: List[Path] = []
        for i in range(self._roots_list.count()):
            t = self._roots_list.item(i).text().strip()
            if t:
                out.append(Path(t).resolve())
        return out

    def _save_settings(self) -> None:
        roots = [self._roots_list.item(i).text() for i in range(self._roots_list.count())]
        self._cfg.item_roots = [r for r in roots if r.strip()]
        vd = self._villagers_edit.text().strip()
        self._cfg.villagers_dir = vd if vd else None
        try:
            self._cfg.save()
        except OSError as e:
            QMessageBox.warning(self, "Save settings", str(e))
            return
        self._item_roots = self._cfg.resolved_item_roots()
        QMessageBox.information(
            self,
            "Settings",
            f"Saved to {config_file_path()}",
        )

    def _add_root(self) -> None:
        d = QFileDialog.getExistingDirectory(self, "Add item asset root")
        if not d:
            return
        self._roots_list.addItem(str(Path(d).resolve()))

    def _remove_root(self) -> None:
        row = self._roots_list.currentRow()
        if row >= 0:
            self._roots_list.takeItem(row)

    def _browse_villagers_dir(self) -> None:
        d = QFileDialog.getExistingDirectory(self, "Villagers JSON folder")
        if not d:
            return
        self._villagers_edit.setText(str(Path(d).resolve()))

    def _sync_villagers_dir_display(self) -> None:
        self._villagers_edit.setText(str(self._cfg.resolved_villagers_dir()))

    def _rescan_catalog(self) -> None:
        self._item_roots = self._roots_from_list()
        if not self._item_roots:
            self._item_roots = default_item_roots()
        try:
            self._catalog = merge_catalogs(self._item_roots)
        except OSError as e:
            QMessageBox.warning(self, "Catalog", str(e))
            self._catalog = {}
        self._grid_model.catalog = self._catalog
        self._grid_model.roots = list(self._item_roots)
        cats = all_categories(self._catalog)
        cur = self._category.currentData()
        self._category.blockSignals(True)
        self._category.clear()
        self._category.addItem("(All categories)", "")
        for c in cats:
            self._category.addItem(c, c)
        if cur:
            idx = self._category.findData(cur)
            if idx >= 0:
                self._category.setCurrentIndex(idx)
        self._category.blockSignals(False)
        self._rebuild_filter()

    def _scan_villagers(self) -> None:
        vd = Path(self._villagers_edit.text().strip()) if self._villagers_edit.text().strip() else self._cfg.resolved_villagers_dir()
        if not vd.is_dir():
            QMessageBox.warning(self, "Villagers", f"Not a directory: {vd}")
            return
        paths = sorted(vd.glob("*.json"), key=lambda p: p.name.lower())
        self._villager_paths = [p for p in paths if p.is_file()]
        self._villager_combo.blockSignals(True)
        self._villager_combo.clear()
        for p in self._villager_paths:
            label = p.stem
            try:
                raw = load_villager(p)
                dn = raw.get("displayName")
                rid = raw.get("npcRoleId")
                if isinstance(dn, str) and dn.strip():
                    label = f"{dn.strip()} ({rid or p.stem})"
                elif isinstance(rid, str) and rid.strip():
                    label = rid.strip()
            except (OSError, ValueError):
                pass
            self._villager_combo.addItem(label, str(p))
        self._villager_combo.blockSignals(False)
        if self._villager_combo.count() > 0:
            self._combo_guard = True
            self._villager_combo.setCurrentIndex(0)
            self._combo_guard = False
            self._load_villager_at(0)
        else:
            self._villager_path = None
            self._villager_data = {}
            self._loves.clear()
            self._likes.clear()
            self._dislikes.clear()
            self._baseline = snapshot_gifts(self._loves, self._likes, self._dislikes)
            self._last_stable_combo_index = -1
            self._path_lbl.setText("")
            self._dirty = False
            self._update_title_dirty()

    def _on_villager_changed(self, idx: int) -> None:
        if self._combo_guard or idx < 0:
            return
        if (
            self._dirty
            and self._villager_path is not None
            and idx != self._last_stable_combo_index
        ):
            r = QMessageBox.question(
                self,
                "Unsaved changes",
                "Discard changes for the current villager?",
                QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
                QMessageBox.StandardButton.No,
            )
            if r != QMessageBox.StandardButton.Yes:
                self._combo_guard = True
                self._villager_combo.setCurrentIndex(self._last_stable_combo_index)
                self._combo_guard = False
                return
        self._load_villager_at(idx)

    def _on_mode_toggled(self, mode: ClickMode, checked: bool) -> None:
        if checked:
            self._mode = mode

    def _load_villager_at(self, idx: int) -> None:
        if idx < 0 or idx >= len(self._villager_paths):
            return
        path = self._villager_paths[idx]
        self._villager_path = path
        try:
            self._villager_data = load_villager(path)
        except (OSError, ValueError) as e:
            QMessageBox.warning(self, "Load", str(e))
            return
        loves, likes, dislikes = extract_gift_lists(self._villager_data)
        self._loves = loves
        self._likes = likes
        self._dislikes = dislikes
        self._baseline = snapshot_gifts(self._loves, self._likes, self._dislikes)
        self._dirty = False
        self._last_stable_combo_index = idx
        self._path_lbl.setText(str(path))
        self._update_title_dirty()
        self._rebuild_filter()

    def _current_mode(self) -> ClickMode:
        return self._mode

    def _rebuild_filter(self) -> None:
        q = self._search.text().strip().lower()
        cat = self._category.currentData()
        pfil = self._pref_filter.currentData() or "all"
        ids: List[str] = []
        if pfil == "ignored":
            pool = sorted(self._ignored_ids, key=str.lower)
        else:
            pool = sorted(self._catalog.keys(), key=str.lower)
        for item_id in pool:
            if pfil != "ignored" and item_id in self._ignored_ids:
                continue
            rec = self._catalog.get(item_id)
            if rec is not None:
                if cat and cat not in rec.categories:
                    continue
            elif cat:
                continue
            pref = preference_for_item(item_id, self._loves, self._likes, self._dislikes)
            if pfil not in ("all", "ignored") and pref.value != pfil:
                continue
            if q:
                blob = item_id.lower()
                if rec and rec.translation_name:
                    blob += " " + rec.translation_name.lower()
                if q not in blob:
                    continue
            ids.append(item_id)
        self._grid_model.set_rows(ids)

    def _on_item_clicked(self, index: QModelIndex) -> None:
        if not index.isValid():
            return
        item_id = self._grid_model.item_id_at(index.row())
        if not item_id:
            return
        mode = self._current_mode()
        if mode == ClickMode.IGNORE:
            toggle_ignored_id(self._ignored_ids, item_id)
            try:
                save_ignored_ids(self._ignored_ids)
            except OSError as e:
                QMessageBox.warning(self, "Ignored items", str(e))
            self._rebuild_filter()
            return
        pref = mode.as_preference()
        assert pref is not None
        apply_preference(item_id, pref, self._loves, self._likes, self._dislikes)
        self._dirty = True
        self._update_title_dirty()
        self._rebuild_filter()

    def _save_villager(self) -> None:
        if not self._villager_path:
            return
        apply_gifts_to_data(self._villager_data, self._loves, self._likes, self._dislikes)
        try:
            save_villager(self._villager_path, self._villager_data)
        except OSError as e:
            QMessageBox.warning(self, "Save", str(e))
            return
        self._baseline = snapshot_gifts(self._loves, self._likes, self._dislikes)
        self._dirty = False
        self._update_title_dirty()
        QMessageBox.information(self, "Save", "Saved.")

    def _revert_villager(self) -> None:
        restore_gifts(self._baseline, self._loves, self._likes, self._dislikes)
        self._dirty = False
        self._update_title_dirty()
        self._rebuild_filter()

    def _update_title_dirty(self) -> None:
        base = "Aetherhaven — Villager gift preferences"
        self.setWindowTitle(base + (" *" if self._dirty else ""))
        self._b_save.setEnabled(self._dirty and self._villager_path is not None)
        self._b_revert.setEnabled(self._dirty)

    def closeEvent(self, event) -> None:  # type: ignore[no-untyped-def]
        if self._dirty:
            r = QMessageBox.question(
                self,
                "Unsaved changes",
                "Save before closing?",
                QMessageBox.StandardButton.Save
                | QMessageBox.StandardButton.Discard
                | QMessageBox.StandardButton.Cancel,
                QMessageBox.StandardButton.Save,
            )
            if r == QMessageBox.StandardButton.Cancel:
                event.ignore()
                return
            if r == QMessageBox.StandardButton.Save:
                self._save_villager()
                if self._dirty:
                    event.ignore()
                    return
        event.accept()
