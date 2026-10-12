package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkSkill;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * What the workplace page offers when a player chooses what a workplace is: one card for every type that can be built,
 * with what stands between the town and that type. Kept free of screen code so the order and the states can be tested.
 */
public final class WorkplaceTypePicker {
    private WorkplaceTypePicker() {}

    /** Cards on one row of the grid. */
    public static final int COLUMNS = 3;

    public enum State {
        /** Unlocked, and its villager has a site to spare. */
        READY,
        /** Unlocked, but every site of its villager is taken. Saving a new one needs another slot first. */
        FULL,
        /** Its villager lives in town and sells the unlock. */
        LOCKED,
        /** Its villager has not moved in, so it cannot be unlocked yet. */
        NO_VILLAGER;

        /** Whether a player may pick this type for the workplace on screen. */
        public boolean choosable() { return this == READY || this == FULL; }
    }

    /** One type as the page shows it: its state, the villager behind it and how many of that villager's sites are taken. */
    public record Card(TownsfolkWorkType type, State state, String villager, long used, int slots) {}

    /** Every type a player can mark a workplace for, in the order the skills are listed. */
    public static List<TownsfolkWorkType> creatable() {
        var types = new ArrayList<TownsfolkWorkType>();
        for (var skill : TownsfolkSkill.values())
            for (var type : TownsfolkWorkType.values())
                if (type.skill == skill && type.requiresWorkplace() && type.availableForNewWork()) types.add(type);
        return types;
    }

    /** The skills that have a workplace of their own, one filter each. */
    public static List<TownsfolkSkill> skills() {
        var skills = new ArrayList<TownsfolkSkill>();
        for (var type : creatable()) if (!skills.contains(type.skill)) skills.add(type.skill);
        return skills;
    }

    public static State state(TownRecord town, TownsfolkWorkType type, UUID editing) {
        var sites = town.getApprenticeWorkplaces();
        String villager = WorkplaceRules.supervisor(type);
        if (!TownWorkplaces.hasSupervisor(town, villager)) return State.NO_VILLAGER;
        if (!sites.unlocked(type)) return State.LOCKED;
        return used(sites, villager, editing) >= sites.slots(villager) ? State.FULL : State.READY;
    }

    /** Sites that count against a villager's slots when {@code editing} is saved: the same sum saving uses. */
    static long used(TownWorkplaces sites, String villager, UUID editing) {
        return sites.sites().stream().filter(site -> !site.id().equals(editing)
            && (editing == null || !editing.equals(site.mineBaseId()))
            && site.mineBaseId() == null && site.type() != null
            && WorkplaceRules.supervisor(site.type()).equals(villager)).count();
    }

    /**
     * The cards to show: those that can be chosen first, then the locked ones, each run in skill order.
     *
     * @param skill only types of this skill, or null for all
     * @param query words the player typed; a card stays when each word is found in its name, skill, bench or description
     */
    public static List<Card> cards(TownRecord town, UUID editing, TownsfolkSkill skill, String query) {
        var sites = town.getApprenticeWorkplaces();
        var ready = new ArrayList<Card>();
        var locked = new ArrayList<Card>();
        for (var type : creatable()) {
            if (skill != null && type.skill != skill) continue;
            if (!matches(type, query)) continue;
            String villager = WorkplaceRules.supervisor(type);
            var state = state(town, type, editing);
            var card = new Card(type, state, villager, sites.used(villager), sites.slots(villager));
            (state.choosable() ? ready : locked).add(card);
        }
        ready.addAll(locked);
        return ready;
    }

    /** How many of the given cards can be chosen; they are the first ones. */
    public static int choosable(List<Card> cards) {
        return (int) cards.stream().filter(card -> card.state().choosable()).count();
    }

    public static boolean matches(TownsfolkWorkType type, String query) {
        if (query == null || query.isBlank()) return true;
        String text = (WorkplaceRules.label(type) + " " + type.id + " " + type.skill.id + " " + WorkplaceRules.supervisor(type)
            + " " + WorkplaceRules.mainBench(type) + " " + WorkplaceRules.description(type)).toLowerCase(Locale.ROOT);
        for (String word : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) if (!text.contains(word)) return false;
        return true;
    }
}
