from __future__ import annotations

from typing import Callable, List, Tuple

from PySide6.QtCore import QModelIndex, QRect, QSize, Qt
from PySide6.QtGui import QColor, QFontMetrics, QIcon, QPainter, QPen, QTextDocument
from PySide6.QtWidgets import QStyledItemDelegate, QStyle, QStyleOptionViewItem, QWidget

from ..preferences import Preference, preference_for_item

# Backgrounds tuned for readability with default dark text.
_PREF_BG: dict[Preference, QColor] = {
    Preference.LOVE: QColor(120, 230, 130),  # bright green
    Preference.LIKE: QColor(35, 110, 70),  # dark green (light text)
    Preference.NEUTRAL: QColor(200, 200, 200),  # grey
    Preference.DISLIKE: QColor(230, 100, 100),  # red
}


def _pref_text_color(pref: Preference) -> QColor:
    if pref == Preference.LIKE:
        return QColor(255, 255, 255)
    return QColor(30, 30, 30)


class GiftGridItemDelegate(QStyledItemDelegate):
    """Icon on top, wrapped label below, colored by villager gift preference."""

    def __init__(
        self,
        get_gift_lists: Callable[[], Tuple[List[str], List[str], List[str]]],
        cell_width: int = 152,
        icon_size: int = 48,
        parent: QWidget | None = None,
    ) -> None:
        super().__init__(parent)
        self._get_gift_lists = get_gift_lists
        self._cell_width = cell_width
        self._icon_size = icon_size
        self._pad = 6
        self._gap = 6

    def set_cell_width(self, w: int) -> None:
        self._cell_width = max(96, int(w))

    def cell_width(self) -> int:
        return self._cell_width

    def _label_text(self, index: QModelIndex) -> str:
        t = index.data(Qt.ItemDataRole.DisplayRole)
        return str(t) if t is not None else ""

    def _preference(self, index: QModelIndex) -> Preference:
        text = self._label_text(index)
        first_line = text.split("\n", 1)[0].strip()
        loves, likes, dislikes = self._get_gift_lists()
        return preference_for_item(first_line, loves, likes, dislikes)

    def sizeHint(self, option: QStyleOptionViewItem, index: QModelIndex) -> QSize:  # type: ignore[override]
        w = max(96, self._cell_width)
        inner_w = max(32, w - 2 * self._pad)
        fm = QFontMetrics(option.font)
        text = self._label_text(index)
        # QTextDocument matches drawText word-wrap more reliably than QFontMetrics on some platforms.
        doc = QTextDocument()
        doc.setDefaultFont(option.font)
        doc.setPlainText(text)
        doc.setTextWidth(float(inner_w))
        h_text = max(int(doc.size().height() + 0.999), fm.height())
        h = self._pad + self._icon_size + self._gap + h_text + self._pad
        h = max(h, self._pad + self._icon_size + self._gap + fm.lineSpacing() * 2 + self._pad)
        return QSize(w, h)

    def paint(
        self,
        painter: QPainter,
        option: QStyleOptionViewItem,
        index: QModelIndex,
    ) -> None:  # type: ignore[override]
        self.initStyleOption(option, index)
        rect = option.rect
        if rect.width() <= 1 or rect.height() <= 1:
            return
        pref = self._preference(index)
        bg = _PREF_BG[pref]
        fg = _pref_text_color(pref)

        painter.save()
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        painter.fillRect(rect, bg)

        if option.state & QStyle.StateFlag.State_Selected:
            painter.setPen(QPen(QColor(0, 120, 215), 2))
            painter.drawRect(rect.adjusted(1, 1, -1, -1))
        elif option.state & QStyle.StateFlag.State_MouseOver:
            painter.fillRect(rect, QColor(255, 255, 255, 40))

        icon = index.data(Qt.ItemDataRole.DecorationRole)
        ix = rect.left() + (rect.width() - self._icon_size) // 2
        iy = rect.top() + self._pad
        icon_rect = QRect(ix, iy, self._icon_size, self._icon_size)
        if isinstance(icon, QIcon) and not icon.isNull():
            icon.paint(painter, icon_rect, Qt.AlignmentFlag.AlignCenter)

        text_top = iy + self._icon_size + self._gap
        text_h = max(0, rect.bottom() - self._pad - text_top)
        text_rect = QRect(rect.left() + self._pad, text_top, rect.width() - 2 * self._pad, text_h)
        if text_rect.height() <= 0 or text_rect.width() <= 0:
            painter.restore()
            return
        painter.setPen(QPen(fg))
        painter.setFont(option.font)
        painter.drawText(
            text_rect,
            int(Qt.AlignmentFlag.AlignHCenter | Qt.AlignmentFlag.AlignTop | Qt.TextFlag.TextWordWrap),
            self._label_text(index),
        )
        painter.restore()
