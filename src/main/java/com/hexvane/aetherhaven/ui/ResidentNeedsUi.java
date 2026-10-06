package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;

/** Read-only needs shared by profiles and the resident overview. Missing data is never a guessed meter. */
public final class ResidentNeedsUi {
    private ResidentNeedsUi() {}
    record Snapshot(String status, boolean available, float hunger, float energy, float fun) {}

    static Snapshot snapshot(boolean supported, VillagerNeeds live, ResidentNpcRecord saved) {
        if (!supported) return new Snapshot("This resident does not use civilian needs.", false, 0, 0, 0);
        if (live != null) return new Snapshot("Current needs", true, fraction(live.getHunger()), fraction(live.getEnergy()), fraction(live.getFun()));
        if (saved != null && saved.hasLastKnownNeeds())
            return new Snapshot("Last known needs", true, fraction(saved.getLastKnownHunger()), fraction(saved.getLastKnownEnergy()), fraction(saved.getLastKnownFun()));
        return new Snapshot("Needs are not available yet.", false, 0, 0, 0);
    }

    static Snapshot read(Store<EntityStore> store, TownRecord town, UUID residentId, boolean supported) {
        var saved = town.getResidentNpcRecords().stream().filter(r -> residentId != null && residentId.equals(r.getLastEntityUuid())).findFirst().orElse(null);
        if (saved != null && TownVillagerBinding.KIND_GUARD.equals(saved.getKind())) supported = false;
        var ref = residentId == null ? null : store.getExternalData().getRefFromUUID(residentId);
        VillagerNeeds live = null;
        if (ref != null && ref.isValid()) {
            var binding = store.getComponent(ref, TownVillagerBinding.getComponentType());
            if (binding != null && town.getTownId().equals(binding.getTownId())) {
                if (TownVillagerBinding.KIND_GUARD.equals(binding.getKind())) supported = false;
                live = store.getComponent(ref, VillagerNeeds.getComponentType());
            }
        }
        return snapshot(supported, live, saved);
    }

    public static void render(UICommandBuilder c, Store<EntityStore> store, TownRecord town, UUID residentId, boolean supported) {
        var needs = read(store, town, residentId, supported);
        c.set("#NeedsStatus.Text", needs.status());
        c.set("#NeedsMeters.Visible", needs.available());
        c.set("#ProfileHunger.Text", "Hunger " + Math.round(needs.hunger() * 100) + "%");
        c.set("#ProfileEnergy.Text", "Energy " + Math.round(needs.energy() * 100) + "%");
        c.set("#ProfileFun.Text", "Fun " + Math.round(needs.fun() * 100) + "%");
        c.set("#ProfileHungerBar.Value", needs.hunger());
        c.set("#ProfileEnergyBar.Value", needs.energy());
        c.set("#ProfileFunBar.Value", needs.fun());
    }

    private static float fraction(float value) { return Float.isFinite(value) ? Math.clamp(value / VillagerNeeds.MAX, 0f, 1f) : 0f; }
}
