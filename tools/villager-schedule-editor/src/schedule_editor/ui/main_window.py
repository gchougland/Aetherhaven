from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path
from typing import Optional

from PySide6.QtCore import QTime, Qt
from PySide6.QtWidgets import (
    QComboBox,
    QDoubleSpinBox,
    QFormLayout,
    QGroupBox,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QMainWindow,
    QMessageBox,
    QPushButton,
    QScrollArea,
    QSpinBox,
    QSplitter,
    QTimeEdit,
    QVBoxLayout,
    QWidget,
)

from .. import io_json
from ..config import default_schedules_dir
from ..week_model import (
    ScheduleModel,
    pre_monday_location,
    sort_key_transitions,
    week_minute_from_parts,
    copy_monday_to_all_days,
)
from .week_calendar import EdgeMode, WeekGridInner, _slices


def _qtime_hm(t: "QTime") -> tuple[int, int]:
    return t.hour(), t.minute()


class MainWindow(QMainWindow):
    def __init__(self) -> None:
        super().__init__()
        self.setWindowTitle("Aetherhaven — Villager schedules")
        self._dir: Path = default_schedules_dir()
        self._path: Optional[Path] = None
        self._schema: int = 1
        self._model: Optional[ScheduleModel] = None
        self._snap: int = 5
        self._dirty: bool = False
        self._build_ui()

    def _build_ui(self) -> None:
        w = QWidget()
        self.setCentralWidget(w)
        main = QVBoxLayout(w)
        top = QHBoxLayout()
        self._combo = QComboBox()
        self._combo.activated.connect(self._on_combo_activated)
        b_reload = QPushButton("Rescan")
        b_reload.clicked.connect(self._scan)
        b_dir = QPushButton("Open folder")
        b_dir.clicked.connect(self._open_dir)
        top.addWidget(QLabel("Villager file:"))
        top.addWidget(self._combo, 1)
        top.addWidget(b_reload)
        top.addWidget(b_dir)
        self._b_save = QPushButton("Save")
        self._b_save.clicked.connect(self._save)
        self._b_revert = QPushButton("Revert")
        self._b_revert.clicked.connect(self._revert)
        top.addWidget(self._b_save)
        top.addWidget(self._b_revert)
        self._wrap_lbl = QLabel("")
        self._wrap_lbl.setWordWrap(True)
        self._path_lbl = QLabel("")
        self._path_lbl.setTextInteractionFlags(
            Qt.TextInteractionFlag.TextSelectableByMouse
        )
        main.addLayout(top)
        main.addWidget(self._path_lbl)
        main.addWidget(self._wrap_lbl)

        split = QSplitter(Qt.Orientation.Horizontal)
        self._scroll = QScrollArea()
        self._grid = WeekGridInner()
        self._scroll.setWidgetResizable(True)
        self._scroll.setWidget(self._grid)
        split.addWidget(self._scroll)

        side = QWidget()
        sform = QVBoxLayout(side)
        snap = QSpinBox()
        snap.setRange(1, 60)
        snap.setValue(self._snap)
        snap.valueChanged.connect(self._on_snap)
        self._zoom = QDoubleSpinBox()
        self._zoom.setRange(0.1, 3.0)
        self._zoom.setValue(0.5)
        self._zoom.setSingleStep(0.05)
        self._zoom.valueChanged.connect(self._on_zoom)
        trow = QHBoxLayout()
        trow.addWidget(QLabel("Snap (min):"))
        trow.addWidget(snap)
        trow.addWidget(QLabel("Px/min:"))
        trow.addWidget(self._zoom)
        sform.addLayout(trow)

        grp = QGroupBox("Selected segment (drag edges in calendar, or use fields)")
        fl = QFormLayout(grp)
        self._loc_edit = QLineEdit()
        self._loc_edit.setPlaceholderText("e.g. work, park, home, inn")
        self._t_st = QTimeEdit()
        self._t_st.setDisplayFormat("HH:mm")
        self._t_en = QTimeEdit()
        self._t_en.setDisplayFormat("HH:mm")
        fl.addRow("Location", self._loc_edit)
        fl.addRow("This transition (start of block)", self._t_st)
        fl.addRow("Next transition (end of block)", self._t_en)
        self._b_apply = QPushButton("Apply time and location")
        self._b_apply.clicked.connect(self._apply_properties)
        sform.addWidget(grp)
        sform.addWidget(self._b_apply)
        b_copy = QPushButton("Copy Monday to Tue through Sun")
        b_copy.clicked.connect(self._copy_mon)
        sform.addWidget(b_copy)
        sform.addStretch(1)
        split.addWidget(side)
        split.setSizes([800, 280])
        main.addWidget(split, 1)

        self._grid.set_model(None)
        self._grid.modelChanged.connect(self._refresh_wrap_and_dirty)
        self._grid.pickChanged.connect(self._sync_form_from_pick)
        self._grid.transitionTimesChanged.connect(self._refresh_wrap_and_dirty)
        self._on_snap(self._snap)
        self._on_zoom(0.5)
        self._set_dirty(False)
        self._scan()

    def _on_snap(self, v: int) -> None:
        self._snap = v
        self._grid.set_snap(v)

    def _on_zoom(self, v: float) -> None:
        self._grid.set_pixels_per_minute(v)

    def _set_title_dirty(self) -> None:
        self.setWindowTitle(
            ("* " if self._dirty else "") + "Aetherhaven — Villager schedules"
        )

    def _set_dirty(self, d: bool) -> None:
        self._dirty = d
        self._set_title_dirty()
        p = f"File: {self._path}  (modified)" if d and self._path else f"File: {self._path or ''}"
        if self._path and not d:
            p = f"File: {self._path}"
        self._path_lbl.setText(p)

    def _refresh_wrap_and_dirty(self) -> None:
        self._set_dirty(True)
        m = self._model
        if m is not None and m.sorted():
            wloc = pre_monday_location(m.sorted())
            self._wrap_lbl.setText(
                "Mon 00:00 before the first transition uses the last transition in the week: "
                f"\"{wloc}\" (matches VillagerScheduleResolver in-game logic)."
            )
        else:
            self._wrap_lbl.setText("")

    def _sync_form_from_pick(self) -> None:
        m = self._model
        p = self._grid.current_pick()
        if p is not None and p.edge != EdgeMode.none:
            self._b_apply.setEnabled(False)
        if m is None or p is None or p.edge != EdgeMode.none:
            if p is None:
                self._b_apply.setEnabled(False)
            return
        st = sort_key_transitions(m.transitions)
        n = len(st)
        if n == 0:
            return
        s = _slices(m)[p.day][p.slice_index]
        si = s.seg.start_sorted_index
        t0 = st[si]
        t1 = st[(si + 1) % n]
        self._loc_edit.setText(t0.location)
        self._t_st.setTime(QTime(t0.hour, t0.minute))
        self._t_en.setTime(QTime(t1.hour, t1.minute))
        self._b_apply.setEnabled(True)

    def _apply_properties(self) -> None:
        m = self._model
        p = self._grid.current_pick()
        if m is None or p is None or p.edge != EdgeMode.none:
            return
        st = sort_key_transitions(m.transitions)
        n = len(st)
        if n == 0:
            return
        s = _slices(m)[p.day][p.slice_index]
        si = s.seg.start_sorted_index
        t0, t1 = st[si], st[(si + 1) % n]
        loc = self._loc_edit.text()
        m.set_location(t0, loc)
        ts, te = self._t_st.time(), self._t_en.time()
        h0, m0 = _qtime_hm(ts)
        h1, m1 = _qtime_hm(te)
        wm0 = week_minute_from_parts(t0.day, h0, m0)
        wm1 = week_minute_from_parts(t1.day, h1, m1)
        m.set_transition_by_week_minute(t0, wm0, self._snap)
        m.set_transition_by_week_minute(t1, wm1, self._snap)
        err = m.validate()
        if err:
            QMessageBox.warning(self, "Schedule", err)
        self._refresh_wrap_and_dirty()
        self._grid.update()

    def _copy_mon(self) -> None:
        if self._model is None:
            return
        r = QMessageBox.question(
            self,
            "Copy",
            "Replace all Tue through Sun transitions with Monday's pattern?",
        )
        if r != QMessageBox.StandardButton.Yes:
            return
        copy_monday_to_all_days(self._model)
        self._refresh_wrap_and_dirty()
        self._grid.update()

    def _on_combo_activated(self, i: int) -> None:  # noqa: ANN202
        if i < 0 or i >= self._combo.count():
            return
        path = self._combo.itemData(i)
        if not path:
            return
        self._load_path(Path(str(path)))

    def _load_path(self, p: Path) -> None:
        if not p.is_file():
            return
        self._path = p
        v, m = io_json.load_file(p)
        self._schema, self._model = v, m
        self._grid.set_model(m)
        self._set_dirty(False)
        ptxt = f"File: {p}"
        self._path_lbl.setText(ptxt)
        if m and m.sorted():
            wloc = pre_monday_location(m.sorted())
            self._wrap_lbl.setText(
                "Mon 00:00 before the first transition uses the last transition in the week: "
                f"\"{wloc}\" (matches in-game rules)."
            )
        self._b_apply.setEnabled(False)
        self._grid.update()

    def _save(self) -> None:
        if self._path is None or self._model is None:
            return
        v = self._model.validate()
        if v:
            QMessageBox.warning(self, "Invalid", v)
            return
        try:
            io_json.save_file(self._path, self._schema, self._model)
        except OSError as e:
            QMessageBox.critical(self, "Save failed", str(e))
            return
        self._set_dirty(False)
        d = f"File: {self._path}" if self._path else ""
        self._path_lbl.setText(d)

    def _revert(self) -> None:
        if self._path and self._path.is_file():
            self._load_path(self._path)

    def _scan(self) -> None:
        prev = str(self._path) if self._path else None
        d = self._dir
        self._combo.blockSignals(True)
        self._combo.clear()
        if d.is_dir():
            for f in sorted(d.glob("*.json")):
                self._combo.addItem(f.name, str(f))
        self._combo.blockSignals(False)
        if not self._combo.count():
            self._path = None
            self._model = None
            self._grid.set_model(None)
            return
        j = 0
        if prev:
            for k in range(self._combo.count()):
                if self._combo.itemData(k) == prev:
                    j = k
                    break
        self._combo.setCurrentIndex(j)
        self._load_path(Path(str(self._combo.itemData(j))))

    def _open_dir(self) -> None:
        path = str(self._dir)
        if sys.platform == "win32":
            os.startfile(path)  # type: ignore[attr-defined]
        else:
            subprocess.Popen(["xdg-open", path], shell=False)  # noqa: S603, S607
