package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;

public final class WorkplacePage extends AetherhavenInteractiveCustomUIPage<WorkplacePage.Data> {
    private final UUID townId, editing;
    private final WorkplaceArea area;
    private TownsfolkWorkType selected;
    private String name, hint = "One Apprentice can work here at a time. Unlock types through their supervising villager.";
    private boolean appended;
    private boolean confirmRemoval;
    public WorkplacePage(PlayerRef pr, UUID townId, UUID editing, WorkplaceArea area) {
        super(pr, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.townId = townId; this.editing = editing; this.area = area;
    }
    @Override public void build(Ref<EntityStore> ref, UICommandBuilder c, UIEventBuilder e, Store<EntityStore> store) {
        if (!appended) { c.append("Aetherhaven/WorkplacePage.ui"); appended = true; }
        var plugin = AetherhavenPlugin.get(); if (plugin == null) return;
        var manager = AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(), plugin);
        var town = manager.getTown(townId); if (town == null || !town.hasMemberOrOwner(playerRef.getUuid()) || !town.playerCanManageConstructions(playerRef.getUuid())) { close(); return; }
        var sites = town.getApprenticeWorkplaces();
        if (name == null) {
            var old = editing == null ? null : sites.find(editing);
            name = old == null ? "New workplace" : old.name();
            selected = old == null ? null : old.type();
        }
        c.set("#Name.Value", name);
        c.set("#Area.Text", (area.maxX() - area.minX()) + " by " + (area.maxZ() - area.minZ()) + " blocks | Floor " + area.floorY() + " | Height " + WorkplaceArea.HEIGHT);
        c.set("#Hint.Text", hint);
        c.set("#MainBench.Text", WorkplaceRules.mainBench(selected));
        c.set("#Remove.Visible", editing != null);
        c.set("#Remove.Text", confirmRemoval ? "Confirm removal" : "Remove workplace");
        e.addEventBinding(CustomUIEventBindingType.Activating, "#Remove", EventData.of("Action", "Remove"), false);
        c.clear("#Types"); int index = 0;
        for (var type : TownsfolkWorkType.values()) {
            if(!type.requiresWorkplace()||!type.availableForNewWork()||!sites.unlocked(type))continue;
            c.append("#Types", "Aetherhaven/WorkplaceTypeRow.ui"); String row = "#Types[" + index++ + "]";
            String supervisor = WorkplaceRules.supervisor(type);
            c.set(row + " #Label.Text", WorkplaceRules.label(type) + " | " + sites.used(supervisor) + "/" + sites.slots(supervisor) + " sites");
            c.set(row + " #RequiredBench.Text", WorkplaceRules.mainBench(type));
            c.set(row + " #Description.Text", WorkplaceRules.description(type));
            c.set(row + " #Choose.Text", selected == type ? "Selected" : "Choose");
            e.addEventBinding(CustomUIEventBindingType.Activating, row + " #Choose", new EventData().append("Action", "Type").append("Type", type.id).append("@Name", "#Name.Value"), false);
        }
        c.set("#NoTypes.Visible", index == 0);
        c.set("#Save.Disabled", selected == null || !sites.unlocked(selected));
        c.set("#Tasks.Visible", editing != null);
        var saved = editing == null ? null : sites.find(editing);
        c.set("#Tasks.Text", saved != null && WorkplaceFood.supports(saved.type()) ? "Food support and tending" : "Tasks for saved workplace");
        e.addEventBinding(CustomUIEventBindingType.Activating, "#Tasks", EventData.of("Action", "Tasks"), false);
        e.addEventBinding(CustomUIEventBindingType.Activating, "#Save", new EventData().append("Action", "Save").append("@Name", "#Name.Value"), false);
        e.addEventBinding(CustomUIEventBindingType.Activating, "#Cancel", EventData.of("Action", "Cancel"), false);
    }
    @Override public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, Data data) {
        if ("Cancel".equals(data.action)) {
            var state = WorkplaceToolState.obtain(ref, store); state.clearSelection(); state.hint = "Selection cancelled. Your saved workplaces are unchanged.";
            close(); return;
        }
        if (data.name != null) name = data.name;
        var plugin = AetherhavenPlugin.get(); if (plugin == null) return;
        var manager = AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(), plugin);
        var town = manager.getTown(townId); if (town == null || !town.hasMemberOrOwner(playerRef.getUuid()) || !town.playerCanManageConstructions(playerRef.getUuid())) { close(); return; }
        if ("Tasks".equals(data.action) && editing != null && town.getApprenticeWorkplaces().find(editing) != null) {
            var player = store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
            if(town.getApprenticeWorkplaces().find(editing).type()==TownsfolkWorkType.BUILDING){player.getPageManager().openCustomPage(ref,store,new BuildingProjectsPage(playerRef,townId,editing,null));return;}
            if(town.getApprenticeWorkplaces().find(editing).type()==TownsfolkWorkType.MINING){player.getPageManager().openCustomPage(ref,store,new ExcavationPage(playerRef,townId,editing,null));return;}
            if (WorkplaceFood.supports(town.getApprenticeWorkplaces().find(editing).type())) {
                player.getPageManager().openCustomPage(ref,store,new FoodSitePage(playerRef,townId,editing)); return;
            }
            player.getPageManager().openCustomPage(ref,store,new WorkplaceOrdersPage(playerRef,townId,editing)); return;
        }
        if ("Remove".equals(data.action) && editing != null) {
            if (!confirmRemoval) {
                confirmRemoval = true;
                hint = "Remove this workplace and its task list? Blocks stay in place. Any supplies must be returned safely first.";
            } else {
                hint = town.getApprenticeWorkplaces().remove(town, playerRef.getUuid(), editing);
                manager.updateTown(town);
                confirmRemoval = false;
                if (hint == null) { WorkplaceToolState.obtain(ref, store).clearSelection(); close(); return; }
            }
        } else confirmRemoval = false;
        if ("Type".equals(data.action)) {
            var next = TownsfolkWorkType.byId(data.type);
            if(next!=null&&next.requiresWorkplace()&&next.availableForNewWork()&&town.getApprenticeWorkplaces().unlocked(next))selected=next;
        }
        if ("Save".equals(data.action)) {
            String error = town.getApprenticeWorkplaces().save(town, editing, selected, area, name);
            if (error == null) {
                manager.updateTown(town);
                var state = WorkplaceToolState.obtain(ref, store); state.clearSelection(); state.hint = "Workplace saved. Use Select mode to reopen it.";
                close(); return;
            }
            hint = error;
        }
        var c = new UICommandBuilder(); var e = new UIEventBuilder(); build(ref, c, e, store); sendUpdate(c, e, false);
    }
    public static final class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d,v) -> d.action=v, d -> d.action).add()
            .append(new KeyedCodec<>("Type", Codec.STRING), (d,v) -> d.type=v, d -> d.type).add()
            .append(new KeyedCodec<>("@Name", Codec.STRING), (d,v) -> d.name=v, d -> d.name).add().build();
        String action, type, name;
    }
}
