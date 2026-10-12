package com.hexvane.aetherhaven.ui;

import com.hypixel.hytale.protocol.packets.interface_.CustomUICommand;
import com.hypixel.hytale.protocol.packets.interface_.CustomUICommandType;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBinding;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Remembers what a page last sent, so a refresh sends only what differs and nothing at all when nothing does.
 *
 * <p>Why it matters: every update a page sends makes the server ignore that player's button presses until the client
 * has answered it ({@code PageManager.updateLegacyCustomPage} raises a counter, and
 * {@code handleLegacyCustomPageEvent} returns at once while it is not zero). A page that redraws on a beat therefore
 * swallows a share of all presses. The counter cannot be read and no page update avoids it, so the only cure is to
 * send fewer updates: none while the page looks the same, and a few values when a few values moved.
 *
 * <p>How it decides. A build is a list of commands: some make or remove elements (Append, Clear and so on), the
 * rest set one value each. If a new build makes the same elements in the same order as the one the client already
 * has, those elements are simply kept and only the values that differ are sent. If it makes different elements it is
 * sent whole. A build that makes no elements at all is a plain list of values and is always compared value by value.
 * World thread only.
 */
public final class PageDelta {
    /** What to send. {@code whole} is true when the list is a complete build rather than a few changed values. */
    public record Update(@Nonnull CustomUICommand[] commands, @Nonnull CustomUIEventBinding[] events, boolean whole) {}

    private static final CustomUICommand[] NO_COMMANDS = new CustomUICommand[0];
    private static final CustomUIEventBinding[] NO_EVENTS = new CustomUIEventBinding[0];

    /** The element making commands of the last whole build, without the page document itself. */
    private List<CustomUICommand> structure = List.of();
    /** Every value the client is known to hold, by selector. */
    private final Map<String, String> values = new LinkedHashMap<>();
    /** Every event binding the client is known to hold, by kind and selector. */
    private final Map<String, CustomUIEventBinding> bindings = new LinkedHashMap<>();
    private boolean known;

    /**
     * A share from 0 to 1 moved down to one of {@code count} equal steps, for a bar or a countdown that a page
     * refreshes by itself. A value that creeps sends an update every time it is looked at; one that steps sends an
     * update only when a step is reached.
     */
    public static float stepped(double share, int count) {
        if (!(share > 0) || count < 1) return 0f;
        return (float) (Math.floor(count * Math.min(1, share) + 1e-9) / count);
    }

    /** Forget everything, for example when the page is drawn again from nothing. */
    public void reset() {
        structure = List.of(); values.clear(); bindings.clear(); known = false;
    }

    /** True once something sent has been recorded, so a refresh has something to compare with. */
    public boolean known() {
        return known;
    }

    /** Record a build that is being sent whole: the opening build, or the answer to a press. */
    public void sent(@Nullable CustomUICommand[] commands, @Nullable CustomUIEventBinding[] events) {
        record(commands == null ? NO_COMMANDS : commands, events == null ? NO_EVENTS : events);
    }

    /**
     * What a refresh must send to turn what the client holds into this build, or null when the client already shows
     * exactly this. Whatever is returned is recorded as sent.
     */
    @Nullable
    public Update next(@Nullable CustomUICommand[] commands, @Nullable CustomUIEventBinding[] events) {
        CustomUICommand[] all = commands == null ? NO_COMMANDS : commands;
        CustomUIEventBinding[] bound = events == null ? NO_EVENTS : events;
        List<CustomUICommand> made = structure(all);
        Map<String, String> set = values(all);
        Map<String, CustomUIEventBinding> wanted = bindings(bound);
        boolean valuesOnly = made.isEmpty();
        if (!known || !valuesOnly && (!made.equals(structure) || !repeatable(made) || dropsSomething(made, set, wanted))) {
            record(all, bound);
            return new Update(all, bound, true);
        }
        var changed = new ArrayList<CustomUICommand>();
        for (var entry : set.entrySet()) {
            if (values.containsKey(entry.getKey()) && Objects.equals(values.get(entry.getKey()), entry.getValue())) continue;
            changed.add(new CustomUICommand(CustomUICommandType.Set, entry.getKey(), entry.getValue(), null));
        }
        var rebound = new ArrayList<CustomUIEventBinding>();
        for (var entry : wanted.entrySet()) if (!entry.getValue().equals(bindings.get(entry.getKey()))) rebound.add(entry.getValue());
        if (changed.isEmpty() && rebound.isEmpty()) return null;
        values.putAll(set); bindings.putAll(wanted);
        return new Update(changed.toArray(NO_COMMANDS), rebound.toArray(NO_EVENTS), false);
    }

    /**
     * True when keeping the client's elements would leave something on them that a whole build would have wiped:
     * a value or a binding the client holds on a remade element that this build no longer sets.
     */
    private boolean dropsSomething(List<CustomUICommand> made, Map<String, String> set, Map<String, CustomUIEventBinding> wanted) {
        for (String selector : values.keySet()) if (!set.containsKey(selector) && remade(made, selector)) return true;
        for (var held : bindings.entrySet())
            if (!wanted.containsKey(held.getKey()) && remade(made, held.getValue().selector)) return true;
        return false;
    }

    private void record(CustomUICommand[] commands, CustomUIEventBinding[] events) {
        List<CustomUICommand> made = structure(commands);
        if (!made.isEmpty()) {
            // Remade elements start blank: what was set or bound on them before is gone.
            values.keySet().removeIf(selector -> remade(made, selector));
            bindings.values().removeIf(binding -> remade(made, binding.selector));
            structure = made;
        }
        values.putAll(values(commands));
        bindings.putAll(bindings(events));
        known = true;
    }

    /** Commands that make or remove elements. The page document itself is appended once and is left out. */
    private static List<CustomUICommand> structure(CustomUICommand[] commands) {
        var made = new ArrayList<CustomUICommand>();
        for (var command : commands) {
            if (command == null || command.type == CustomUICommandType.Set) continue;
            if (command.type == CustomUICommandType.Append && command.selector == null) continue;
            made.add(command);
        }
        return made;
    }

    /** The values a build sets. When it sets one twice the later one counts, as it does on the client. */
    private static Map<String, String> values(CustomUICommand[] commands) {
        var set = new LinkedHashMap<String, String>();
        for (var command : commands) {
            if (command == null || command.type != CustomUICommandType.Set || command.selector == null) continue;
            set.remove(command.selector);
            set.put(command.selector, command.data);
        }
        return set;
    }

    private static Map<String, CustomUIEventBinding> bindings(CustomUIEventBinding[] events) {
        var bound = new LinkedHashMap<String, CustomUIEventBinding>();
        for (var event : events) if (event != null) bound.put(event.type + " " + event.selector, event);
        return bound;
    }

    /** True when the selector reaches into an element that one of these commands makes, empties or removes. */
    private static boolean remade(List<CustomUICommand> made, @Nullable String selector) {
        if (selector == null) return false;
        for (var command : made) if (within(command.selector, selector, command.type == CustomUICommandType.Remove)) return true;
        return false;
    }

    /**
     * True when {@code selector} names something inside {@code host}: "#List" hosts "#List[0] #Name.Text".
     * "#List.Visible" and "#List" are the host itself, which only counts when {@code self} is true.
     */
    private static boolean within(@Nullable String host, String selector, boolean self) {
        if (host == null || !selector.startsWith(host)) return false;
        if (selector.length() == host.length()) return self;
        char next = selector.charAt(host.length());
        return next == '[' || next == ' ' || self && next == '.';
    }

    /**
     * True when sending these commands twice leaves the same elements as sending them once: everything they add goes
     * into something they emptied first. Only then may a later, identical list be skipped.
     */
    private static boolean repeatable(List<CustomUICommand> made) {
        var emptied = new ArrayList<String>();
        for (var command : made) {
            if (command.type == CustomUICommandType.Clear) { if (command.selector != null) emptied.add(command.selector); continue; }
            if (command.type == CustomUICommandType.Remove || command.selector == null) return false;
            boolean inside = false;
            for (String host : emptied) if (command.selector.equals(host) || within(host, command.selector, false)) { inside = true; break; }
            if (!inside) return false;
        }
        return true;
    }
}
