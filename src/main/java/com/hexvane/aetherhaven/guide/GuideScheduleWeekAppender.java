package com.hexvane.aetherhaven.guide;

import com.hexvane.aetherhaven.schedule.VillagerScheduleDefinition;
import com.hexvane.aetherhaven.schedule.VillagerScheduleTransition;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Appends a compact Mon–Sun “bar chart” of weekly schedule segments into a Custom UI list (journal guide), using the
 * same JSON transitions as {@link com.hexvane.aetherhaven.schedule.VillagerScheduleRegistry}.
 */
public final class GuideScheduleWeekAppender {
    private static final int MAX_SEGMENTS = 10;
    private static final int MINUTES_PER_DAY = 24 * 60;
    private static final DayOfWeek[] WEEK_ORDER = {
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
        DayOfWeek.SUNDAY,
    };

    private GuideScheduleWeekAppender() {}

    /**
     * Clears {@code rowsSelector}, then appends one row per in-game day that has transitions, plus a legend row.
     *
     * @param gameNow world game date-time when the panel was built (e.g. {@code WorldTimeResource#getGameDateTime()}).
     *        When non-null, the row for the matching weekday shows a thin red vertical line at the current clock time
     *        (drawn over the timeline; snapshot only). Horizontal position matches the same window as the colored
     *        segments: from this day’s first transition through midnight (not from 00:00 unless the first transition is midnight).
     * @return number of appended list rows
     */
    public static int appendWeek(
        @Nonnull UICommandBuilder cmd,
        @Nonnull String rowsSelector,
        @Nonnull VillagerScheduleDefinition def,
        @Nullable LocalDateTime gameNow
    ) {
        cmd.clear(rowsSelector);
        List<VillagerScheduleTransition> raw = def.getTransitions();
        if (raw.isEmpty()) {
            return 0;
        }
        Map<DayOfWeek, List<VillagerScheduleTransition>> byDay = new EnumMap<>(DayOfWeek.class);
        for (VillagerScheduleTransition t : raw) {
            DayOfWeek dow = parseDayOfWeek(t.getDayOfWeek());
            byDay.computeIfAbsent(dow, k -> new ArrayList<>()).add(t);
        }
        int rows = 0;
        for (DayOfWeek dow : WEEK_ORDER) {
            List<VillagerScheduleTransition> day = byDay.get(dow);
            if (day == null || day.isEmpty()) {
                continue;
            }
            day.sort(Comparator.comparingInt(GuideScheduleWeekAppender::dayStartMinute));
            List<Segment> segs = buildSegments(day);
            if (segs.isEmpty()) {
                continue;
            }
            if (rows >= 32) {
                break;
            }
            appendDayRow(cmd, rowsSelector, rows, dow, segs, gameNow, dayStartMinute(day.get(0)));
            rows++;
        }
        if (rows == 0) {
            return 0;
        }
        cmd.append(rowsSelector, "Aetherhaven/GuideScheduleLegendRow.ui");
        String leg = rowsSelector + "[" + rows + "]";
        cmd.set(
            leg + " #Legend.TextSpans",
            Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.guide.scheduleLegend")
        );
        rows++;
        return rows;
    }

    private static final String STRIP_PREFIX = " #StripArea #StripHost #Strip";

    private static void appendDayRow(
        @Nonnull UICommandBuilder cmd,
        @Nonnull String rowsSelector,
        int rowIndex,
        @Nonnull DayOfWeek dow,
        @Nonnull List<Segment> segs,
        @Nullable LocalDateTime gameNow,
        int dayFirstTransitionMinute
    ) {
        cmd.append(rowsSelector, "Aetherhaven/GuideScheduleDayRow.ui");
        String row = rowsSelector + "[" + rowIndex + "]";
        cmd.set(row + " #DayLabel.TextSpans", Message.raw(dayAbbrev(dow)));
        String overlay = row + " #StripArea #StripHost #NowMarkerOverlay";
        boolean showNow = gameNow != null && gameNow.getDayOfWeek() == dow;
        if (showNow) {
            int m = Math.max(0, Math.min(MINUTES_PER_DAY - 1, gameNow.getHour() * 60 + gameNow.getMinute()));
            int t0 = Math.max(0, Math.min(MINUTES_PER_DAY - 1, dayFirstTransitionMinute));
            int span = Math.max(1, MINUTES_PER_DAY - t0);
            int rel = m - t0;
            if (rel < 0) {
                rel = 0;
            } else if (rel > span) {
                rel = span;
            }
            int leftFlex = Math.max(1, rel);
            int rightFlex = Math.max(1, span - rel);
            cmd.set(overlay + ".Visible", true);
            cmd.set(overlay + " #NowMarkerRow #NowSpacerLeft.FlexWeight", leftFlex);
            cmd.set(overlay + " #NowMarkerRow #NowSpacerRight.FlexWeight", rightFlex);
            cmd.set(overlay + " #NowMarkerRow #NowLine.Visible", true);
            cmd.set(
                overlay + " #NowMarkerRow #NowLine.TooltipTextSpans",
                Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.guide.scheduleNowMarker")
            );
        } else {
            cmd.set(overlay + ".Visible", false);
        }
        for (int s = 0; s < MAX_SEGMENTS; s++) {
            String cell = row + STRIP_PREFIX + " #S" + s;
            if (s >= segs.size()) {
                cmd.set(cell + ".Visible", false);
                continue;
            }
            Segment sg = segs.get(s);
            cmd.set(cell + ".Visible", true);
            cmd.set(cell + ".Background", segmentColor(sg.locationNorm));
            cmd.set(cell + " #L" + s + ".TextSpans", Message.raw(sg.shortLabel));
            cmd.set(cell + ".TooltipTextSpans", Message.raw(sg.tooltip));
            int flex = Math.max(1, sg.durationMinutes / 5);
            cmd.set(cell + ".FlexWeight", flex);
        }
    }

    private static List<Segment> buildSegments(@Nonnull List<VillagerScheduleTransition> daySorted) {
        List<Segment> out = new ArrayList<>();
        for (int i = 0; i < daySorted.size(); i++) {
            VillagerScheduleTransition t = daySorted.get(i);
            int start = dayStartMinute(t);
            int end = i + 1 < daySorted.size() ? dayStartMinute(daySorted.get(i + 1)) : MINUTES_PER_DAY;
            if (end <= start) {
                continue;
            }
            String loc = t.getLocation();
            String norm = normalizeLocation(loc);
            out.add(
                new Segment(
                    norm,
                    shortLabel(norm),
                    tooltipLine(norm, start, end),
                    end - start
                )
            );
        }
        while (out.size() > MAX_SEGMENTS) {
            mergeSmallestAdjacent(out);
        }
        return out;
    }

    private static void mergeSmallestAdjacent(@Nonnull List<Segment> segs) {
        if (segs.size() < 2) {
            return;
        }
        int best = 0;
        int bestSum = Integer.MAX_VALUE;
        for (int i = 0; i < segs.size() - 1; i++) {
            int sum = segs.get(i).durationMinutes + segs.get(i + 1).durationMinutes;
            if (sum < bestSum) {
                bestSum = sum;
                best = i;
            }
        }
        Segment a = segs.get(best);
        Segment b = segs.get(best + 1);
        String norm = a.locationNorm.equals(b.locationNorm) ? a.locationNorm : "?";
        int dur = a.durationMinutes + b.durationMinutes;
        String tip = a.tooltip + "\n" + b.tooltip;
        Segment merged = new Segment(norm, shortLabel(norm), tip, dur);
        segs.set(best, merged);
        segs.remove(best + 1);
    }

    private record Segment(@Nonnull String locationNorm, @Nonnull String shortLabel, @Nonnull String tooltip, int durationMinutes) {}

    @Nonnull
    private static String dayAbbrev(@Nonnull DayOfWeek dow) {
        return switch (dow) {
            case MONDAY -> "Mon";
            case TUESDAY -> "Tue";
            case WEDNESDAY -> "Wed";
            case THURSDAY -> "Thu";
            case FRIDAY -> "Fri";
            case SATURDAY -> "Sat";
            case SUNDAY -> "Sun";
        };
    }

    private static int dayStartMinute(@Nonnull VillagerScheduleTransition t) {
        int h = Math.max(0, Math.min(23, t.getHour()));
        int m = Math.max(0, Math.min(59, t.getMinute()));
        return h * 60 + m;
    }

    @Nonnull
    private static DayOfWeek parseDayOfWeek(@Nullable Object raw) {
        if (raw == null) {
            return DayOfWeek.MONDAY;
        }
        if (raw instanceof Number n) {
            int v = n.intValue();
            if (v >= 1 && v <= 7) {
                return DayOfWeek.of(v);
            }
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) {
            return DayOfWeek.MONDAY;
        }
        try {
            return DayOfWeek.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            try {
                int v = Integer.parseInt(s);
                if (v >= 1 && v <= 7) {
                    return DayOfWeek.of(v);
                }
            } catch (NumberFormatException ignored) {
            }
            return DayOfWeek.MONDAY;
        }
    }

    @Nonnull
    private static String normalizeLocation(@Nullable String location) {
        if (location == null || location.isBlank()) {
            return "?";
        }
        return location.trim().toLowerCase();
    }

    @Nonnull
    private static String shortLabel(@Nonnull String locNorm) {
        return switch (locNorm) {
            case "?" -> "…";
            case "home" -> "Home";
            case "work" -> "Work";
            case "inn" -> "Inn";
            case "park" -> "Park";
            case "gaia_altar" -> "Altar";
            default -> locNorm.length() > 5 ? locNorm.substring(0, 5) : locNorm;
        };
    }

    @Nonnull
    private static String tooltipLine(@Nonnull String locNorm, int startMin, int endMin) {
        return friendlyLocation(locNorm) + " · " + formatClock(startMin) + "–" + formatClock(endMin);
    }

    @Nonnull
    private static String friendlyLocation(@Nonnull String locNorm) {
        return switch (locNorm) {
            case "home" -> "Rest at home";
            case "work" -> "Work (job site)";
            case "inn" -> "Visit the inn";
            case "park" -> "Town commons";
            case "gaia_altar" -> "Gaia altar";
            default -> locNorm;
        };
    }

    @Nonnull
    private static String formatClock(int minuteOfDay) {
        int h = minuteOfDay / 60;
        int m = minuteOfDay % 60;
        return String.format("%02d:%02d", h, m);
    }

    @Nonnull
    private static String segmentColor(@Nonnull String locNorm) {
        return switch (locNorm) {
            case "home" -> "#3d4f6a";
            case "work" -> "#4a6b42";
            case "inn" -> "#5c4a72";
            case "park" -> "#4a6d62";
            case "gaia_altar" -> "#7a5c3a";
            default -> "#3a4558";
        };
    }
}
