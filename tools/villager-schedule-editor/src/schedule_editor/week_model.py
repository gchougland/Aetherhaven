from __future__ import annotations

from dataclasses import dataclass, field
from typing import List, Optional, Sequence, Tuple

WEEK_MINUTES = 7 * 24 * 60
DAY_MINUTES = 24 * 60

DAY_NAMES = ("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")


def parse_day_of_week(raw: object) -> int:
    if raw is None:
        return 0
    if isinstance(raw, (int, float)):
        v = int(raw)
        if 1 <= v <= 7:
            return v - 1
        return 0
    s = str(raw).strip().upper()
    if s.isdigit():
        v = int(s)
        if 1 <= v <= 7:
            return v - 1
    try:
        return DAY_NAMES.index(s)
    except ValueError:
        return 0


def day_name(d: int) -> str:
    return DAY_NAMES[max(0, min(6, d))]


def normalize_location(loc: Optional[str]) -> Optional[str]:
    if loc is None or not str(loc).strip():
        return None
    return str(loc).strip().lower()


def week_minute_from_parts(day: int, hour: int, minute: int) -> int:
    h = max(0, min(23, hour))
    m = max(0, min(59, minute))
    d = max(0, min(6, day))
    return d * DAY_MINUTES + h * 60 + m


def parts_from_week_minute(wm: int) -> Tuple[int, int, int]:
    wm = wm % WEEK_MINUTES
    d = wm // DAY_MINUTES
    rem = wm % DAY_MINUTES
    return d, rem // 60, rem % 60


def snap_minutes(value: int, step: int) -> int:
    if step <= 0:
        return int(value) % WEEK_MINUTES
    v = int(value) % WEEK_MINUTES
    a = (v + step // 2) // step
    a = a * step
    return a % WEEK_MINUTES


@dataclass
class Transition:
    day: int
    hour: int
    minute: int
    location: str
    source_index: int = 0

    @property
    def week_minute(self) -> int:
        return week_minute_from_parts(self.day, self.hour, self.minute)

    @classmethod
    def from_dict(cls, d: object, source_index: int) -> Optional[Transition]:
        if not isinstance(d, dict):
            return None
        loc = normalize_location(d.get("location"))  # type: ignore[union-attr]
        if loc is None:
            return None
        h = d.get("hour", 0)
        m = d.get("minute", 0)
        h = int(h) if not isinstance(h, int) else h
        m = int(m) if not isinstance(m, int) else m
        return cls(
            day=parse_day_of_week(d.get("dayOfWeek")),
            hour=int(h),
            minute=int(m),
            location=loc,
            source_index=source_index,
        )

    def to_dict(self) -> dict:
        return {
            "dayOfWeek": day_name(self.day),
            "hour": int(max(0, min(23, self.hour))),
            "minute": int(max(0, min(59, self.minute))),
            "location": self.location,
        }


def sort_key_transitions(trans: Sequence[Transition]) -> List[Transition]:
    return sorted(trans, key=lambda t: (t.week_minute, t.source_index, id(t)))


@dataclass
class Segment:
    start_wm: int
    end_wm: int
    location: str
    start_sorted_index: int
    is_wrap: bool  # if true, this segment is [start, 10080) u [0, end_lo)


@dataclass
class DaySlice:
    start_local: int
    end_local: int
    location: str
    seg: Segment


def build_segments(sorted_t: List[Transition]) -> List[Segment]:
    n = len(sorted_t)
    if n == 0:
        return []
    out: List[Segment] = []
    for i in range(n - 1):
        a, b = sorted_t[i].week_minute, sorted_t[i + 1].week_minute
        out.append(
            Segment(a, b, sorted_t[i].location, i, is_wrap=False),
        )
    last = sorted_t[-1]
    end = sorted_t[0].week_minute + WEEK_MINUTES
    out.append(Segment(last.week_minute, end, last.location, n - 1, is_wrap=True))
    return out


def _linear_parts(seg: Segment) -> List[Tuple[int, int, Segment]]:
    if seg.end_wm <= WEEK_MINUTES and not seg.is_wrap:
        return [(seg.start_wm, seg.end_wm, seg)]
    if seg.is_wrap:
        return [
            (seg.start_wm, WEEK_MINUTES, seg),
            (0, seg.end_wm - WEEK_MINUTES, seg),
        ]
    return [(seg.start_wm, min(seg.end_wm, WEEK_MINUTES), seg)]


def day_slices_for_day(d: int, segs: List[Segment]) -> List[DaySlice]:
    d = max(0, min(6, d))
    D, E = d * DAY_MINUTES, (d + 1) * DAY_MINUTES
    res: List[DaySlice] = []
    for seg in segs:
        for a, b, s in _linear_parts(seg):
            lo, hi = max(a, D), min(b, E)
            if lo < hi:
                res.append(
                    DaySlice(
                        start_local=lo - D,
                        end_local=hi - D,
                        location=s.location,
                        seg=s,
                    )
                )
    res.sort(key=lambda x: x.start_local)
    return res


def pre_monday_location(sorted_t: List[Transition]) -> str:
    if not sorted_t:
        return ""
    return sorted_t[-1].location


def _circular_gaps_ok(ws: List[int], g: int) -> bool:
    n = len(ws)
    if n < 2:
        return True
    for j in range(n - 1):
        if ws[j + 1] - ws[j] < g:
            return False
    if ws[0] + WEEK_MINUTES - ws[n - 1] < g:
        return False
    return True


def clamp_transition_week_minute(
    st: List[Transition], i: int, new_wm: int, min_gap: int
) -> int:
    n = len(st)
    g = max(1, int(min_gap))
    new_wm = (int(new_wm) % WEEK_MINUTES + WEEK_MINUTES) % WEEK_MINUTES
    if n == 0:
        return 0
    if n == 1:
        return new_wm
    w = [t.week_minute for t in st]
    i = (i + n) % n
    w2 = w[:]
    w2[i] = new_wm
    w2.sort()
    if _circular_gaps_ok(w2, g):
        return (new_wm % WEEK_MINUTES + WEEK_MINUTES) % WEEK_MINUTES
    w = [t.week_minute for t in st]
    if i == 0:
        lo = int(max(0, w[n - 1] - WEEK_MINUTES + g))
        hi = int(min(WEEK_MINUTES - 1, w[1] - g))
    elif i == n - 1:
        lo = int(w[n - 2] + g)
        hi = int(min(10080 - 1, w[0] - g + WEEK_MINUTES))
    else:
        lo, hi = int(w[i - 1] + g), int(w[i + 1] - g)
    if lo > hi:
        return w[i] % WEEK_MINUTES
    return min(max(new_wm, lo), hi) % WEEK_MINUTES


@dataclass
class ScheduleModel:
    """Working copy: transitions list mutated in place."""

    transitions: List[Transition] = field(default_factory=list)

    def sorted(self) -> List[Transition]:
        return sort_key_transitions(self.transitions)

    def segments(self) -> List[Segment]:
        return build_segments(self.sorted())

    @classmethod
    def from_list(cls, trans: List[Transition]) -> "ScheduleModel":
        return cls(transitions=[t for t in trans if t.location])

    def validate(self) -> str:
        w = [t.week_minute for t in self.transitions]
        if len(w) != len(set(w)):
            return "Duplicate week minute: two or more transitions share the same time."
        return ""

    def set_transition_dhm(self, t: Transition, day: int, hour: int, minute: int) -> str:
        t.day = max(0, min(6, int(day)))
        t.hour = max(0, min(23, int(hour)))
        t.minute = max(0, min(59, int(minute)))
        return self.validate()

    def set_location(self, t: Transition, loc: str) -> str:
        n = normalize_location(loc)
        if n is None:
            return "Location may not be empty"
        t.location = n
        return ""

    def set_transition_by_week_minute(
        self, t: Transition, new_wm: int, snap: int
    ) -> str:
        new_wm = snap_minutes(int(new_wm), int(snap))
        d, h, m = parts_from_week_minute(new_wm)
        t.day, t.hour, t.minute = d, h, m
        return self.validate()


def ordered_for_json(m: ScheduleModel) -> List[Transition]:
    by_day: List[List[Transition]] = [[] for _ in range(7)]
    for t in m.transitions:
        if 0 <= t.day <= 6:
            by_day[t.day].append(t)
    for lst in by_day:
        lst.sort(
            key=lambda x: (x.hour * 60 + x.minute, x.source_index, id(x)),
        )
    return [t for d in range(7) for t in by_day[d]]


def copy_monday_to_all_days(m: ScheduleModel) -> None:
    mon = [t for t in m.transitions if t.day == 0]
    if not mon:
        return
    mon.sort(key=lambda t: (t.hour * 60 + t.minute, t.source_index, id(t)))
    nsi = max((t.source_index for t in m.transitions), default=0) + 1
    out: List[Transition] = []
    for t in mon:
        out.append(
            Transition(
                day=0,
                hour=t.hour,
                minute=t.minute,
                location=t.location,
                source_index=t.source_index,
            )
        )
    for d in range(1, 7):
        for t in mon:
            out.append(
                Transition(
                    day=d,
                    hour=t.hour,
                    minute=t.minute,
                    location=t.location,
                    source_index=nsi,
                )
            )
            nsi += 1
    m.transitions = out
