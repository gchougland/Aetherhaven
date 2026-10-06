package com.hexvane.aetherhaven.clinic;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.inventory.*;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.UUID;

/** Supply cabinet and local triage, reached through the Doctor's dialogue. */
public final class ClinicPage extends AetherhavenInteractiveCustomUIPage<ClinicPage.Data> {
    private final UUID townId;
    private final Ref<EntityStore> doctor;
    private boolean appended;
    private long seenRevision;
    private boolean liveRefreshStarted;
    private String hint = "Injured civilians visit after danger passes. Guards can follow you here and wait while they recover.";
    public ClinicPage(PlayerRef player, UUID town, Ref<EntityStore> doctor) {
        super(player, CustomPageLifetime.CanDismissOrCloseThroughInteraction, Data.CODEC);
        this.townId = town; this.doctor = doctor;
    }
    @Override public void build(Ref<EntityStore> ref, UICommandBuilder c, UIEventBuilder e, Store<EntityStore> store) {
        if (!appended) { c.append("Aetherhaven/ClinicPage.ui"); appended = true; }
        var plugin = AetherhavenPlugin.get(); if (plugin == null) return;
        var manager = AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(), plugin);
        var town = manager.getTown(townId);
        if (town == null || !valid(ref, store, town)) {
            // Closing during the initial build precedes page activation. Render a safe
            // explanation instead of leaving the template's controls unbound and blank.
            c.set("#State.Text", "DOCTOR UNAVAILABLE");
            c.set("#Supply.Text", "Speak to the Doctor nearby.");
            c.set("#Hint.Text", "Close this screen and speak to the Doctor again.");
            c.set("#CareDetail.Text", "The Doctor is no longer nearby.");
            c.clear("#Patients");
            c.set("#PatientCount.Text", "");
            c.set("#Empty.Visible", false);
            c.set("#SupplyOne.Disabled", true);
            c.set("#SupplyFive.Disabled", true);
            c.set("#Toggle.Disabled", true);
            c.set("#Refresh.Disabled", true);
            return;
        }
        var state = town.getClinic(); seenRevision = state.revision();
        c.set("#Supply.Text", state.medicine() + " / " + TownClinic.CAPACITY + " medicine");
        bar(c, "#StockFill", 250 * state.medicine() / TownClinic.CAPACITY, 12);
        c.set("#Toggle.Text", state.paused() ? "Resume care" : "Pause care");
        c.set("#SupplyOne.Disabled", !state.canSupply(1));
        c.set("#SupplyFive.Disabled", !state.canSupply(5));
        c.set("#Hint.Text", hint);
        var clinic = town.findCompletePlotWithConstruction(plugin.getConstructionCatalog(), ClinicCareSystem.CONSTRUCTION);
        var availability = ClinicCareSystem.availability(town, clinic, doctor, store);
        c.set("#State.Text", availability.title);
        c.set("#CareDetail.Text", availability.detail);
        var patients = clinic == null ? java.util.List.<Ref<EntityStore>>of() : ClinicCareSystem.patients(town, clinic, doctor, store);
        c.set("#PatientCount.Text", patients.size() + (patients.size() == 1 ? " resident needing care nearby" : " residents needing care nearby"));
        c.clear("#Patients");
        for (int i = 0; i < Math.min(8, patients.size()); i++) {
            var patient = patients.get(i);
            var identity = store.getComponent(patient, com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding.getComponentType());
            var character = identity == null ? null : plugin.getTownsfolkCharacterCatalog().byId(identity.getCharacterId());
            var npc = store.getComponent(patient, NPCEntity.getComponentType());
            var health = store.getComponent(patient, EntityStatMap.getComponentType()).get(DefaultEntityStatTypes.getHealth());
            c.append("#Patients", "Aetherhaven/ClinicPatient.ui");
            String path = "#Patients[" + i + "]";
            c.set(path + " #Name.Text", character == null ? npc.getRoleName().replace("Aetherhaven_", "").replace('_', ' ') : character.getDisplayName());
            c.set(path + " #Health.Text", String.format(java.util.Locale.ROOT, "%.0f / %.0f health", health.get(), health.getMax()));
            bar(c, path + " #HealthFill", Math.round(330 * Math.clamp(health.get() / health.getMax(), 0, 1)), 10);
            double progress = availability == ClinicCareSystem.Availability.READY ? ClinicCareSystem.progress(doctor, patient, store) : 0;
            c.set(path + " #Course.Text", progress > 0 ? "Receiving care   " + (int)Math.ceil(TownClinic.TREATMENT_SECONDS * (1 - progress)) + "s remaining" : "Waiting for care");
            bar(c, path + " #CourseFill", (int)Math.round(330 * progress), 5);
        }
        c.set("#Empty.Visible", patients.isEmpty());
        for (String action : new String[]{"SupplyOne", "SupplyFive", "Toggle", "Refresh"})
            e.addEventBinding(CustomUIEventBindingType.Activating, "#" + action, new EventData().append("Action", action), false);
        if (!liveRefreshStarted) {
            liveRefreshStarted = true;
            scheduleRefresh(store.getExternalData().getWorld());
        }
    }
    private void scheduleRefresh(com.hypixel.hytale.server.core.universe.world.World world) {
        var plugin = AetherhavenPlugin.get();
        if (plugin == null || isDismissed()) return;
        plugin.scheduleOnWorld(world, () -> {
            if (isDismissed()) return;
            var ref = playerRef.getReference();
            if (ref == null || !ref.isValid() || ref.getStore().getExternalData().getWorld() != world) return;
            var store = ref.getStore();
            var player = store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
            if (player == null || player.getPageManager().getCustomPage() != this) return;
            var commands = new UICommandBuilder();
            var events = new UIEventBuilder();
            build(ref, commands, events, store);
            sendUpdate(commands, events, false);
            var town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).getTown(townId);
            if (town != null && valid(ref, store, town)) scheduleRefresh(world);
        }, 1000);
    }
    private static void bar(UICommandBuilder commands, String selector, int width, int height) {
        var anchor = new com.hypixel.hytale.server.core.ui.Anchor();
        anchor.setLeft(com.hypixel.hytale.server.core.ui.Value.of(0));
        anchor.setWidth(com.hypixel.hytale.server.core.ui.Value.of(width));
        anchor.setHeight(com.hypixel.hytale.server.core.ui.Value.of(height));
        commands.setObject(selector + ".Anchor", anchor);
    }
    private boolean valid(Ref<EntityStore> player, Store<EntityStore> store, TownRecord town) {
        if (!town.hasMemberOrOwner(playerRef.getUuid()) || !town.playerCanManageConstructions(playerRef.getUuid())
                || doctor == null || !doctor.isValid() || store.getComponent(doctor, DeathComponent.getComponentType()) != null) return false;
        var binding = store.getComponent(doctor, TownVillagerBinding.getComponentType());
        var a = store.getComponent(player, TransformComponent.getComponentType());
        var b = store.getComponent(doctor, TransformComponent.getComponentType());
        return binding != null && ClinicCareSystem.KIND.equals(binding.getKind()) && townId.equals(binding.getTownId())
            && a != null && b != null && a.getPosition().distanceSquared(b.getPosition()) <= 144;
    }
    @Override public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, Data data) {
        var plugin = AetherhavenPlugin.get(); if (plugin == null) return;
        var manager = AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(), plugin);
        var town = manager.getTown(townId); if (town == null || !valid(ref, store, town)) { close(); return; }
        var state = town.getClinic();
        if (!"Refresh".equals(data.action) && state.revision() != seenRevision) hint = "The medicine supply changed. Choose again.";
        else if ("Toggle".equals(data.action)) { state.toggle(); manager.updateTown(town); }
        else if ("SupplyOne".equals(data.action) || "SupplyFive".equals(data.action)) {
            int amount = "SupplyOne".equals(data.action) ? 1 : 5;
            var inventory = InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING);
            if (!state.canSupply(amount)) hint = "There is not enough room in the medicine cabinet.";
            else if (inventory == null || !inventory.removeItemStack(new ItemStack(TownClinic.SUPPLY_ITEM, amount), true, true).succeeded())
                hint = "Carry enough Lesser Health Potions to supply the clinic.";
            else { state.supply(amount); manager.updateTown(town); hint = "Medicine stocked. One point restores one health."; }
        }
        var c = new UICommandBuilder(); var e = new UIEventBuilder(); build(ref, c, e, store); sendUpdate(c, e, false);
    }
    public static final class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d,v) -> d.action = v, d -> d.action).add().build();
        String action;
    }
}
