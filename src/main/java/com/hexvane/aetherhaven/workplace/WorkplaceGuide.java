package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.production.ProductionWorkplaceKinds;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * What a town should do next about workplaces, said with the town's own names: which villager unlocks a type today
 * and at which building, which types are on offer, which bench the chosen type needs and where that bench is made.
 *
 * <p>No screen code and no world here. A step is a wording to use and the words that fill its gaps; the quest text
 * turns those into messages. That keeps every sentence in the language files and lets the choice of sentence be tested.
 */
public final class WorkplaceGuide {
    private WorkplaceGuide() {}

    /** Lang key of each supervising villager's trade, as the workplace page already names them. */
    public static final String ROLE_LANG = "aetherhaven_workplace.aetherhaven.workplace.villager.";

    /** How one gap in a sentence is filled. */
    public record Word(Kind kind, String value) {
        public enum Kind {
            /** Text as it is: a name a player typed, a number, a list of type names. */
            RAW,
            /** A language key. */
            LANG,
            /** An item id, shown by the item's own translated name. */
            ITEM,
            /** A construction id, shown by the building's own translated name. */
            BUILDING
        }
        public static Word raw(String text) { return new Word(Kind.RAW, text == null ? "" : text); }
        public static Word lang(String key) { return new Word(Kind.LANG, key); }
        public static Word item(String id) { return new Word(Kind.ITEM, id); }
        public static Word building(String id) { return new Word(Kind.BUILDING, id); }
    }

    /**
     * One step of the walk, ready to be said.
     *
     * @param variant    which wording the journal uses: a suffix on the objective's lang key
     * @param hudVariant which wording the HUD tracker uses: a suffix on the objective's HUD lang key
     * @param itemId     an item to show beside the step, or null
     */
    public record Step(String variant, String hudVariant, Map<String, Word> words, String itemId) {
        public Step { words = Map.copyOf(words); }

        /** Different whenever the sentence would read differently, so a display that redraws on change can tell. */
        public String signature() {
            var text = new StringBuilder(variant);
            words.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(word -> text.append('|').append(word.getKey()).append('=').append(word.getValue().value()));
            return text.toString();
        }
    }

    /**
     * A villager who can unlock workplace types today: they live in town and their building stands finished.
     *
     * @param locked   their types the town has not unlocked yet
     * @param unlocked their types the town already has
     */
    public record Supervisor(String kind, String npcRoleId, String constructionId, List<TownsfolkWorkType> locked,
                             List<TownsfolkWorkType> unlocked) {}

    /** The supervisors who can be asked today, in the order their types are listed on the workplace page. */
    public static List<Supervisor> supervisors(TownRecord town, ConstructionCatalog catalog) {
        var ready = new ArrayList<Supervisor>();
        var seen = new HashSet<String>();
        var sites = town.getApprenticeWorkplaces();
        for (var first : WorkplaceTypePicker.creatable()) {
            String kind = WorkplaceRules.supervisor(first);
            if (!seen.add(kind)) continue;
            var resident = resident(town, kind);
            if (resident == null) continue;
            var building = building(town, catalog, kind, resident);
            if (building == null) continue;
            var locked = new ArrayList<TownsfolkWorkType>();
            var unlocked = new ArrayList<TownsfolkWorkType>();
            for (var type : WorkplaceTypePicker.creatable())
                if (WorkplaceRules.supervisor(type).equals(kind)) (sites.unlocked(type) ? unlocked : locked).add(type);
            ready.add(new Supervisor(kind, resident.getNpcRoleId(), building.getConstructionId(), List.copyOf(locked), List.copyOf(unlocked)));
        }
        return ready;
    }

    /** True once the town has a finished building whose villager can supervise a workplace type. */
    public static boolean supervisorReady(TownRecord town, ConstructionCatalog catalog) {
        return !supervisors(town, catalog).isEmpty();
    }

    private static ResidentNpcRecord resident(TownRecord town, String kind) {
        for (var record : town.getResidentNpcRecords()) if (kind.equals(record.getKind())) return record;
        return null;
    }

    /** The villager's own finished building: the plot they work at, else any finished plot that is their workplace. */
    private static PlotInstance building(TownRecord town, ConstructionCatalog catalog, String kind, ResidentNpcRecord resident) {
        var job = resident.getJobPlotId();
        var own = job == null ? null : town.findPlotById(job);
        if (own != null && own.getState() == PlotInstanceState.COMPLETE) return own;
        for (var plot : town.getPlotInstances())
            if (plot.getState() == PlotInstanceState.COMPLETE
                && ProductionWorkplaceKinds.residentBindingKindsForPlot(catalog, plot.getConstructionId()).contains(kind)) return plot;
        return null;
    }

    static String names(List<TownsfolkWorkType> types) {
        var names = new ArrayList<String>();
        for (var type : types) names.add(WorkplaceRules.label(type));
        return String.join(", ", names);
    }

    private static boolean wanted(TownsfolkWorkType type, TownsfolkWorkType only) { return only == null || type == only; }

    /**
     * Unlock a type: the first villager who still has one to sell, by name, at their building, with what they offer.
     *
     * @param only         the one type a quest asks for, or null for any
     * @param villagerName a villager's own name by NPC role id, or null when it is not known
     */
    public static Step unlockStep(TownRecord town, ConstructionCatalog catalog, TownsfolkWorkType only, Function<String, String> villagerName) {
        Supervisor offering = null;
        int others = 0;
        for (var supervisor : supervisors(town, catalog)) {
            if (supervisor.locked().stream().noneMatch(type -> wanted(type, only))) continue;
            if (offering == null) offering = supervisor; else others++;
        }
        if (offering == null) return new Step("waiting", "waiting", Map.of(), null);
        var words = new LinkedHashMap<String, Word>();
        String name = offering.npcRoleId() == null || offering.npcRoleId().isBlank() ? null : villagerName.apply(offering.npcRoleId());
        boolean named = name != null && !name.isBlank();
        if (named) words.put("villager", Word.raw(name.strip()));
        words.put("role", Word.lang(ROLE_LANG + offering.kind()));
        words.put("building", Word.building(offering.constructionId()));
        words.put("gold", Word.raw(Long.toString(TownWorkplaces.TYPE_UNLOCK_COST)));
        words.put("types", Word.raw(names(offering.locked().stream().filter(type -> wanted(type, only)).toList())));
        String variant = named ? "ready" : "readyUnnamed";
        return new Step(others > 0 ? variant + "More" : variant, variant, words, null);
    }

    /** Mark an area. Nothing in the town changes how this is said. */
    public static Step markStep() { return new Step("mark", "mark", Map.of(), null); }

    /** Choose a type on the workplace page: the types the town has unlocked, by name. */
    public static Step chooseStep(TownRecord town, TownsfolkWorkType only) {
        var unlocked = WorkplaceQuestSteps.unlockedTypes(town).stream().filter(type -> wanted(type, only)).toList();
        if (unlocked.isEmpty()) return new Step("locked", "choose", Map.of(), null);
        return new Step("ready", "choose", Map.of("types", Word.raw(names(unlocked))), null);
    }

    /**
     * Set the station down: the newest workplace by its own name, the item its type needs, and where that item is made.
     *
     * @param stations the station of a type, or null when none is known
     */
    public static Step benchStep(TownRecord town, TownsfolkWorkType only, Function<TownsfolkWorkType, WorkplaceStations.Station> stations) {
        var sites = WorkplaceQuestSteps.markedSites(town, only);
        if (sites.isEmpty()) return new Step("noSite", "noSite", Map.of(), null);
        var site = sites.getLast();
        var words = new LinkedHashMap<String, Word>();
        words.put("site", Word.raw(site.name()));
        words.put("type", Word.raw(WorkplaceRules.label(site.type())));
        var station = stations.apply(site.type());
        if (station == null) return new Step("placeUnknown", "placeUnknown", words, null);
        words.put("bench", Word.item(station.itemId()));
        var made = station.craftedAt();
        String variant;
        if (made == null) variant = "placeNoRecipe";
        else if (made.pocket()) variant = "placePocket";
        else {
            // The bench is named by its own item where one is loaded, else by the name the workplace pages use.
            words.put("craftedAt", station.craftedAtItemId() != null ? Word.item(station.craftedAtItemId())
                : Word.raw(WorkplaceEquipment.label(made.benchId())));
            if (made.tier() > 1) words.put("tier", Word.raw(Integer.toString(made.tier())));
            variant = made.tier() > 1 ? "placeTier" : "place";
        }
        return new Step(variant, "place", words, station.itemId());
    }
}
