from __future__ import annotations

from dataclasses import dataclass
from enum import Enum, auto
from typing import List, Optional, Tuple

from PySide6.QtCore import QPointF, QRectF, Qt, Signal
from PySide6.QtGui import QColor, QFont, QPainter, QPen
from PySide6.QtWidgets import QScrollArea, QWidget

from ..week_model import (
    DAY_MINUTES,
    DAY_NAMES,
    ScheduleModel,
    clamp_transition_week_minute,
    day_slices_for_day,
    sort_key_transitions,
    parts_from_week_minute,
    snap_minutes,
)


def _loc_color(loc: str) -> QColor:
    p = {
        "home": QColor(120, 160, 200),
        "work": QColor(150, 150, 150),
        "inn": QColor(200, 160, 100),
        "park": QColor(100, 180, 100),
        "gaia_altar": QColor(160, 220, 200),
    }
    return p.get(loc.lower(), QColor(160, 140, 200))


class EdgeMode(Enum):
    none = auto()
    top = auto()
    bottom = auto()


@dataclass
class Pick:
    day: int
    slice_index: int
    edge: EdgeMode


def _slices(m: Optional[ScheduleModel]) -> List[List]:
    if m is None:
        return [[] for _ in range(7)]
    segs = m.segments()
    return [day_slices_for_day(d, segs) for d in range(7)]


def _edge_grab_thickness_px() -> float:
    return 5.0


def _classify_edge(
    m: ScheduleModel, d: int, s, local: int, px: float
) -> EdgeMode:
    st = sort_key_transitions(m.transitions)
    n = len(st)
    if n == 0:
        return EdgeMode.none
    si = s.seg.start_sorted_index
    slice_swm = d * DAY_MINUTES + s.start_local
    slice_ewm = d * DAY_MINUTES + s.end_local
    t0_wm = st[si].week_minute
    t1_wm = st[(si + 1) % n].week_minute
    th = _edge_grab_thickness_px() / max(px, 0.01)
    if abs(slice_swm - t0_wm) < 1 and (local - s.start_local) < th + 0.1:
        return EdgeMode.top
    if abs(slice_ewm - t1_wm) < 1 and (s.end_local - 1 - local) < th and (
        s.end_local - local
    ) < th + 0.2:
        return EdgeMode.bottom
    return EdgeMode.none


class WeekGridInner(QWidget):
    modelChanged = Signal()
    pickChanged = Signal(object)  # Optional[Pick]
    transitionTimesChanged = Signal()

    def __init__(self, parent: Optional[QWidget] = None) -> None:
        super().__init__(parent)
        self._model: Optional[ScheduleModel] = None
        self._snap = 5
        self._px_per_min = 0.5
        self._header_h = 22.0
        self._time_col_w = 40.0
        self._col_w = 88.0
        self._pick: Optional[Pick] = None
        self._drag: Optional[Tuple[EdgeMode, int, int, int, int, int]] = None
        # mode, d, si, y0, start_wm, edge_top_or_next 1 = top, 0 = next trans
        self.setMouseTracking(True)
        self._resized()

    def _resized(self) -> None:
        h = int(self._header_h + DAY_MINUTES * self._px_per_min) + 8
        w = int(self._time_col_w + 7 * self._col_w) + 4
        self.setMinimumSize(w, h)

    def current_pick(self) -> Optional[Pick]:
        return self._pick

    def set_model(self, m: Optional[ScheduleModel]) -> None:
        self._model = m
        self._resized()
        self.update()

    def set_snap(self, s: int) -> None:
        self._snap = max(1, int(s))

    def set_pixels_per_minute(self, v: float) -> None:
        self._px_per_min = max(0.1, float(v))
        self._resized()
        self.update()

    def _day_left(self, d: int) -> float:
        return self._time_col_w + d * self._col_w

    def _day_rect(self, d: int) -> QRectF:
        x0 = self._day_left(d)
        return QRectF(
            x0, self._header_h, self._col_w, float(DAY_MINUTES) * self._px_per_min
        )

    def _hit(self, x: float, y: float) -> Optional[Pick]:
        m = self._model
        if m is None or y < self._header_h:
            return None
        for d in range(7):
            r = self._day_rect(d)
            if not r.contains(QPointF(x, y)):
                continue
            local = int((y - r.top()) / self._px_per_min)
            local = max(0, min(DAY_MINUTES - 1, local))
            sls = _slices(m)[d]
            for i, s in enumerate(sls):
                if s.start_local <= local < s.end_local:
                    em = _classify_edge(m, d, s, local, self._px_per_min)
                    return Pick(d, i, em)
        return None

    def paintEvent(self, e) -> None:  # type: ignore[no-untyped-def]
        p = QPainter(self)
        p.setRenderHint(QPainter.RenderHint.Antialiasing)
        w, h = self.width(), self.height()
        p.fillRect(0, 0, w, h, QColor(32, 32, 36))

        m = self._model
        for d in range(7):
            r = self._day_rect(d)
            p.setPen(QPen(QColor(70, 70, 78)))
            p.drawRect(r)
            p.setFont(QFont("Segoe UI", 8))
            p.setPen(QColor(200, 200, 210))
            p.drawText(int(r.left() + 2), int(self._header_h - 4), DAY_NAMES[d][:3])
        for h2 in (0, 6, 12, 18):
            yl = self._header_h + h2 * 60 * self._px_per_min
            p.setPen(QPen(QColor(55, 55, 62), 1, Qt.PenStyle.DotLine))
            p.drawLine(int(self._time_col_w), int(yl), int(w - 1), int(yl))
            p.setPen(QColor(160, 160, 170))
            p.drawText(3, int(yl + 10), f"{h2:02d}:00")

        if m is None:
            p.end()
            return
        for d in range(7):
            sls = _slices(m)[d]
            for i, s in enumerate(sls):
                c = _loc_color(s.location)
                c = QColor(c)
                c.setAlpha(210)
                r = self._day_rect(d)
                y0 = r.top() + s.start_local * self._px_per_min
                y1 = r.top() + s.end_local * self._px_per_min
                rr = QRectF(r.left() + 1, y0, r.width() - 2, max(1.0, y1 - y0))
                pk = self._pick
                if pk and pk.day == d and pk.slice_index == i:
                    c = c.lighter(118)
                p.setPen(QPen(QColor(18, 18, 22), 1))
                p.setBrush(c)
                p.drawRoundedRect(rr, 2, 2)
                if rr.height() > 11:
                    p.setPen(QColor(250, 250, 255))
                    p.setFont(QFont("Segoe UI", 8))
                    p.drawText(rr.adjusted(2, 1, -2, -2), s.location)
        p.end()

    def _apply_drag(
        self, d: int, em: EdgeMode, si: int, dy: float, y0: float, wm0: int
    ) -> None:
        m = self._model
        if m is None:
            return
        st = sort_key_transitions(m.transitions)
        n = len(st)
        if n == 0:
            return
        dm = int(round(dy / self._px_per_min))
        new_wm = int(wm0 + dm)
        new_wm = snap_minutes(new_wm, self._snap)
        if em == EdgeMode.top:
            target = st[si]
            cwm = clamp_transition_week_minute(
                st, si, new_wm, self._snap
            )
            d0, h0, m0 = parts_from_week_minute(cwm)
            target.day, target.hour, target.minute = d0, h0, m0
        else:
            j = (si + 1) % n
            target = st[j]
            cwm = clamp_transition_week_minute(
                st, j, new_wm, self._snap
            )
            d0, h0, m0 = parts_from_week_minute(cwm)
            target.day, target.hour, target.minute = d0, h0, m0
        m.validate()
        self.modelChanged.emit()
        self.transitionTimesChanged.emit()

    def mousePressEvent(self, e) -> None:  # type: ignore[no-untyped-def]
        if e.button() == Qt.MouseButton.LeftButton and self._model:
            pk = self._hit(e.position().x(), e.position().y())
            self._pick = pk
            if (
                pk
                and pk.edge in (EdgeMode.top, EdgeMode.bottom)
                and self._model is not None
            ):
                st = sort_key_transitions(self._model.transitions)
                n = len(st)
                if n:
                    s = _slices(self._model)[pk.day][pk.slice_index]
                    si = s.seg.start_sorted_index
                    if pk.edge == EdgeMode.top:
                        WM = st[si].week_minute
                    else:
                        WM = st[(si + 1) % n].week_minute
                    self._drag = (pk.edge, pk.day, si, int(e.position().y()), WM, WM)
            self.update()
            self.pickChanged.emit(self._pick)
        super().mousePressEvent(e)

    def mouseMoveEvent(self, e) -> None:  # type: ignore[no-untyped-def]
        if (e.buttons() & Qt.MouseButton.LeftButton) and self._drag and self._model:
            dge, d, si, y0, wm, _ = self._drag
            self._apply_drag(
                d, dge, si, float(e.position().y() - y0), float(y0), wm
            )
            self.update()
        else:
            pk = self._hit(e.position().x(), e.position().y())
            if pk and self._model is not None:
                m = self._model
                r = self._day_rect(pk.day)
                local = int((e.position().y() - r.top()) / self._px_per_min)
                local = max(0, min(DAY_MINUTES - 1, local))
                s = _slices(m)[pk.day][pk.slice_index]
                e2 = _classify_edge(m, pk.day, s, local, self._px_per_min)
                if e2 != EdgeMode.none:
                    self.setCursor(Qt.CursorShape.SizeVerCursor)
                else:
                    self.setCursor(Qt.CursorShape.ArrowCursor)
            else:
                self.setCursor(Qt.CursorShape.ArrowCursor)
        super().mouseMoveEvent(e)

    def mouseReleaseEvent(self, e) -> None:  # type: ignore[no-untyped-def]
        self._drag = None
        self.update()
        super().mouseReleaseEvent(e)


def build_week_scroll() -> Tuple[QScrollArea, WeekGridInner]:
    w = WeekGridInner()
    sa = QScrollArea()
    sa.setWidgetResizable(True)
    sa.setWidget(w)
    sa.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOn)
    sa.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOn)
    return sa, w


__all__ = [
    "build_week_scroll",
    "EdgeMode",
    "Pick",
    "WeekGridInner",
]
