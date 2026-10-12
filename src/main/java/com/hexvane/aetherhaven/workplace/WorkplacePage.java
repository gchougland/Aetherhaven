package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkSkill;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;

/**
 * Names a workplace and chooses what it is. Every type is a card: the ones the town can use first, the locked ones after
 * them with what unlocks each. A skill filter and a search box narrow the cards down.
 */
public final class WorkplacePage extends AetherhavenInteractiveCustomUIPage<WorkplacePage.Data> {
    private static final String LANG = "aetherhaven_workplace.aetherhaven.workplace.";
    private final UUID townId, editing;
    private final WorkplaceArea area;
    private TownsfolkWorkType selected;
    private String name;
    private Message hint = t("defaultHint");
    private boolean appended;
    private boolean confirmRemoval;
    /** Narrowing of the cards: one skill, or null for all, and the words typed into the search box. */
    private TownsfolkSkill filter;
    private String search = "";
    /** The cards on screen. They are only appended again when this changes, so choosing one keeps the scroll position. */
    private String shownCards;
    public WorkplacePage(PlayerRef pr, UUID townId, UUID editing, WorkplaceArea area) {
        super(pr, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.townId = townId; this.editing = editing; this.area = area;
    }
    static Message t(String key) { return Message.translation(LANG + key); }
    static Message villager(String kind) { return t("villager." + kind); }
    private static Message skillName(TownsfolkSkill skill) { return Message.translation("aetherhaven_townsfolk_profile." + skill.id); }
    @Override public void build(Ref<EntityStore> ref, UICommandBuilder c, UIEventBuilder e, Store<EntityStore> store) {
        boolean first = !appended;
        if (first) { c.append("Aetherhaven/WorkplacePage.ui"); appended = true; }
        var plugin = AetherhavenPlugin.get(); if (plugin == null) return;
        var manager = AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(), plugin);
        var town = manager.getTown(townId); if (town == null || !town.hasMemberOrOwner(playerRef.getUuid()) || !town.playerCanManageConstructions(playerRef.getUuid())) { close(); return; }
        var sites = town.getApprenticeWorkplaces();
        if (name == null) {
            var old = editing == null ? null : sites.find(editing);
            name = old == null ? "New workplace" : old.name();
            selected = old == null ? null : old.type();
        }
        if (first) {
            // Text fields are written once. Sending a value back while the player types would undo their latest keys.
            c.set("#NameField.Value", name);
            e.addEventBinding(CustomUIEventBindingType.ValueChanged, "#TypeSearch",
                new EventData().append("Action", "Search").append("@Search", "#TypeSearch.Value").append("@Name", "#NameField.Value"), false);
            buildFilters(c);
        }
        c.set("#Area.Text", (area.maxX() - area.minX()) + " by " + (area.maxZ() - area.minZ()) + " blocks | Floor " + area.floorY() + " | Height " + WorkplaceArea.HEIGHT);
        c.set("#Hint.TextSpans", hint);
        c.set("#DetailTitle.TextSpans", selected == null ? t("choosePrompt") : Message.raw(WorkplaceRules.label(selected)));
        c.set("#MainBench.Text", selected == null ? "" : WorkplaceRules.mainBench(selected));
        c.set("#Description.TextSpans", selected == null ? t("chooseHelp") : Message.raw(WorkplaceRules.description(selected)));
        c.set("#Remove.Visible", editing != null);
        c.set("#Remove.Text", confirmRemoval ? "Confirm removal" : "Remove workplace");
        e.addEventBinding(CustomUIEventBindingType.Activating, "#Remove", EventData.of("Action", "Remove"), false);
        bindFilters(c, e);
        buildCards(c, e, town);
        c.set("#Save.Disabled", selected == null || !sites.unlocked(selected));
        c.set("#Tasks.Visible", editing != null);
        var saved = editing == null ? null : sites.find(editing);
        c.set("#Tasks.Text", saved!=null&&saved.type()==TownsfolkWorkType.MINING?"Open mineshaft planner":saved != null && GrowerPlan.supports(saved.type()) ? "Beds and harvest" : saved != null && WorkplaceFood.supports(saved.type()) ? "Food support and tending" : "Tasks for saved workplace");
        e.addEventBinding(CustomUIEventBindingType.Activating, "#Tasks", EventData.of("Action", "Tasks"), false);
        e.addEventBinding(CustomUIEventBindingType.Activating, "#Save", new EventData().append("Action", "Save").append("@Name", "#NameField.Value"), false);
        e.addEventBinding(CustomUIEventBindingType.Activating, "#Cancel", EventData.of("Action", "Cancel"), false);
    }
    /** "All", then one button for each skill that has workplaces. */
    private void buildFilters(UICommandBuilder c) {
        c.append("#Filters", "Aetherhaven/WorkplaceTypeFilter.ui");
        c.set("#Filters[0] #Icon.Visible", false);
        c.set("#Filters[0] #Text.Visible", true);
        c.set("#Filters[0] #Text.TextSpans", t("filterAll"));
        c.set("#Filters[0].TooltipTextSpans", t("filterAllHint"));
        int index = 1;
        for (var skill : WorkplaceTypePicker.skills()) {
            c.append("#Filters", "Aetherhaven/WorkplaceTypeFilter.ui");
            String tab = "#Filters[" + index++ + "]";
            c.set(tab + " #Icon.AssetPath", "UI/Custom/" + skill.icon);
            c.set(tab + ".TooltipTextSpans", t("filterHint").param("skill", skillName(skill)));
        }
    }
    private void bindFilters(UICommandBuilder c, UIEventBuilder e) {
        c.set("#Filters[0] #Active.Visible", filter == null);
        e.addEventBinding(CustomUIEventBindingType.Activating, "#Filters[0]",
            new EventData().append("Action", "Filter").append("Filter", "all").append("@Name", "#NameField.Value"), false);
        int index = 1;
        for (var skill : WorkplaceTypePicker.skills()) {
            String tab = "#Filters[" + index++ + "]";
            c.set(tab + " #Active.Visible", filter == skill);
            e.addEventBinding(CustomUIEventBindingType.Activating, tab,
                new EventData().append("Action", "Filter").append("Filter", skill.id).append("@Name", "#NameField.Value"), false);
        }
    }
    /** The grid: a caption and rows of three for the types that can be chosen, then the same for the locked ones. */
    private void buildCards(UICommandBuilder c, UIEventBuilder e, TownRecord town) {
        var cards = WorkplaceTypePicker.cards(town, editing, filter, search);
        int ready = WorkplaceTypePicker.choosable(cards);
        var key = new StringBuilder();
        for (var card : cards) key.append(card.type().id).append(card.state().choosable() ? "+" : "-");
        boolean fresh = !key.toString().equals(shownCards);
        if (fresh) { c.clear("#Types"); shownCards = key.toString(); }
        c.set("#NoTypes.Visible", cards.isEmpty());
        c.set("#NoTypes.TextSpans", t("noMatch"));
        c.set("#Shown.TextSpans", t("shown").param("shown", cards.size()).param("total", WorkplaceTypePicker.creatable().size()));
        int child = 0;
        for (int group = 0; group < 2; group++) {
            var run = group == 0 ? cards.subList(0, ready) : cards.subList(ready, cards.size());
            if (run.isEmpty()) continue;
            if (fresh) c.append("#Types", "Aetherhaven/WorkplaceTypeHeading.ui");
            c.set("#Types[" + child++ + "] #Title.TextSpans", t(group == 0 ? "typesReady" : "typesLocked"));
            int row = 0;
            for (int i = 0; i < run.size(); i++) {
                if (i % WorkplaceTypePicker.COLUMNS == 0) {
                    if (fresh) c.append("#Types", "Aetherhaven/WorkplaceTypeCardRow.ui");
                    row = child++;
                }
                if (fresh) c.append("#Types[" + row + "]", "Aetherhaven/WorkplaceTypeCard.ui");
                card(c, e, "#Types[" + row + "][" + i % WorkplaceTypePicker.COLUMNS + "]", run.get(i));
            }
        }
    }
    private void card(UICommandBuilder c, UIEventBuilder e, String at, WorkplaceTypePicker.Card card) {
        var type = card.type();
        boolean locked = !card.state().choosable();
        c.set(at + " #Icon.AssetPath", "UI/Custom/" + type.icon);
        c.set(at + " #Skill.TextSpans", skillName(type.skill));
        c.set(at + " #Name.Text", WorkplaceRules.label(type));
        c.set(at + " #Line.TextSpans", t("line." + type.id));
        c.set(at + " #State.TextSpans", stateText(card));
        c.set(at + " #State.Style.TextColor", stateColour(card.state()));
        c.set(at + " #LockedShade.Visible", locked);
        c.set(at + " #Lock.Visible", locked);
        c.set(at + " #Selected.Visible", selected == type);
        c.set(at + " #Chosen.Visible", selected == type);
        c.set(at + " #Chosen.TextSpans", t("chosen"));
        c.set(at + ".TooltipText", WorkplaceRules.description(type));
        e.addEventBinding(CustomUIEventBindingType.Activating, at,
            new EventData().append("Action", "Type").append("Type", type.id).append("@Name", "#NameField.Value"), false);
    }
    static String stateColour(WorkplaceTypePicker.State state) {
        return switch (state) {
            case READY -> "#9cdeb4";
            case FULL -> "#e8c984";
            case LOCKED, NO_VILLAGER -> "#d9c9a3";
        };
    }
    static Message stateText(WorkplaceTypePicker.Card card) {
        return switch (card.state()) {
            case READY -> t("stateFree").param("used", card.used()).param("slots", card.slots());
            case FULL -> t("stateFull").param("villager", villager(card.villager()));
            case LOCKED -> t("stateLocked").param("gold", TownWorkplaces.TYPE_UNLOCK_COST).param("villager", villager(card.villager()));
            case NO_VILLAGER -> t("stateNoVillager").param("villager", villager(card.villager()));
        };
    }
    @Override public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, Data data) {
        if ("Cancel".equals(data.action)) {
            var state = WorkplaceToolState.obtain(ref, store); state.clearSelection(); state.hint = "Selection cancelled. Your saved workplaces are unchanged.";
            close(); return;
        }
        if (data.name != null) name = data.name;
        if (data.search != null) search = data.search.strip().substring(0, Math.min(data.search.strip().length(), 40));
        if ("Filter".equals(data.action)) {
            filter = null;
            for (var skill : WorkplaceTypePicker.skills()) if (skill.id.equals(data.filter)) filter = skill;
        }
        var plugin = AetherhavenPlugin.get(); if (plugin == null) return;
        var manager = AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(), plugin);
        var town = manager.getTown(townId); if (town == null || !town.hasMemberOrOwner(playerRef.getUuid()) || !town.playerCanManageConstructions(playerRef.getUuid())) { close(); return; }
        if ("Tasks".equals(data.action) && editing != null && town.getApprenticeWorkplaces().find(editing) != null) {
            var player = store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
            if(town.getApprenticeWorkplaces().find(editing).type()==TownsfolkWorkType.BUILDING){player.getPageManager().openCustomPage(ref,store,new BuildingProjectsPage(playerRef,townId,editing,null));return;}
            if(town.getApprenticeWorkplaces().find(editing).type()==TownsfolkWorkType.MINING){player.getPageManager().openCustomPage(ref,store,new MineshaftPlannerPage(playerRef,townId,editing));return;}
            if (GrowerPlan.supports(town.getApprenticeWorkplaces().find(editing).type())) {
                player.getPageManager().openCustomPage(ref,store,new GrowerSitePage(playerRef,townId,editing,null)); return;
            }
            if (WorkplaceFood.supports(town.getApprenticeWorkplaces().find(editing).type())) {
                player.getPageManager().openCustomPage(ref,store,new FoodSitePage(playerRef,townId,editing)); return;
            }
            player.getPageManager().openCustomPage(ref,store,new WorkplaceOrdersPage(playerRef,townId,editing)); return;
        }
        if ("Remove".equals(data.action) && editing != null) {
            if (!confirmRemoval) {
                confirmRemoval = true;
                hint = Message.raw("Remove this workplace and its task list? Blocks stay in place. Any supplies must be returned safely first.");
            } else {
                com.hexvane.aetherhaven.quest.WorkplaceQuestHooks.workplaceRemoving(store.getExternalData().getWorld(), manager, town);
                String refused = town.getApprenticeWorkplaces().remove(town, playerRef.getUuid(), editing);
                manager.updateTown(town);
                confirmRemoval = false;
                if (refused == null) { WorkplaceToolState.obtain(ref, store).clearSelection(); close(); return; }
                hint = Message.raw(refused);
            }
        } else confirmRemoval = false;
        if ("Type".equals(data.action)) {
            var next = TownsfolkWorkType.byId(data.type);
            if (next != null && next.requiresWorkplace() && next.availableForNewWork()) {
                var state = WorkplaceTypePicker.state(town, next, editing);
                if (state.choosable()) { selected = next; hint = t("defaultHint"); }
                // A locked card answers with what unlocks it and leaves the choice as it was.
                else hint = t(state == WorkplaceTypePicker.State.LOCKED ? "lockedHint" : "noVillagerHint")
                    .param("type", WorkplaceRules.label(next)).param("villager", villager(WorkplaceRules.supervisor(next)))
                    .param("gold", TownWorkplaces.TYPE_UNLOCK_COST);
            }
        }
        if ("Save".equals(data.action)) {
            String error = town.getApprenticeWorkplaces().save(town, editing, selected, area, name);
            if (error == null) {
                manager.updateTown(town);
                com.hexvane.aetherhaven.quest.WorkplaceQuestHooks.workplaceSaved(store.getExternalData().getWorld(), manager, town);
                var state = WorkplaceToolState.obtain(ref, store); state.clearSelection(); state.hint = "Workplace saved. Use Select mode to reopen it.";
                if(selected==TownsfolkWorkType.MINING){
                    var saved=editing==null?town.getApprenticeWorkplaces().sites().stream().filter(s->s.area().equals(area)&&s.type()==selected).findFirst().orElse(null):town.getApprenticeWorkplaces().find(editing);
                    if(saved!=null){store.getComponent(ref,com.hypixel.hytale.server.core.entity.entities.Player.getComponentType()).getPageManager().openCustomPage(ref,store,new MineshaftPlannerPage(playerRef,townId,saved.id()));return;}
                }
                close(); return;
            }
            hint = Message.raw(error);
        }
        var c = new UICommandBuilder(); var e = new UIEventBuilder(); build(ref, c, e, store); sendUpdate(c, e, false);
    }
    public static final class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d,v) -> d.action=v, d -> d.action).add()
            .append(new KeyedCodec<>("Type", Codec.STRING), (d,v) -> d.type=v, d -> d.type).add()
            .append(new KeyedCodec<>("@Name", Codec.STRING), (d,v) -> d.name=v, d -> d.name).add()
            .append(new KeyedCodec<>("@Search", Codec.STRING), (d,v) -> d.search=v, d -> d.search).add()
            .append(new KeyedCodec<>("Filter", Codec.STRING), (d,v) -> d.filter=v, d -> d.filter).add().build();
        String action, type, name, search, filter;
    }
}
