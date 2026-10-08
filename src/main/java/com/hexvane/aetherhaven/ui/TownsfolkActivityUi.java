package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.shopspot.ShopClerkState;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.warehouse.CourierTravelState;
import com.hexvane.aetherhaven.workplace.ApprenticeWorkState;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Locale;
import java.util.UUID;

/** The journal reports live activity, rather than treating enabled work priorities as current work. */
final class TownsfolkActivityUi {
    private TownsfolkActivityUi() {}
    static Message current(Store<EntityStore> store, TownRecord town, UUID resident) {
        var saved = town.getResidentNpcRecords().stream().filter(r -> resident.equals(r.getLastEntityUuid())).findFirst().orElse(null);
        if (saved != null && saved.isPendingDawnRevival()) return TownWorkforceUi.t("dead");
        var npc = store.getExternalData().getRefFromUUID(resident);
        if (npc == null || !npc.isValid()) return Message.raw("Not nearby");
        if (store.getComponent(npc, DeathComponent.getComponentType()) != null) return TownWorkforceUi.t("dead");
        var clerk = store.getComponent(npc, ShopClerkState.getComponentType());
        if (clerk != null && clerk.active) return Message.raw(clerk.attending ? "Serving the shop" : "Walking to the shop");
        var courier = store.getComponent(npc, CourierTravelState.getComponentType());
        if (courier != null && courier.active) return Message.raw(delivery(courier.action));
        var work = store.getComponent(npc, ApprenticeWorkState.getComponentType());
        if (work != null && work.active) {
            UUID siteId = work.buildingSite != null ? work.buildingSite : work.excavationSite != null ? work.excavationSite : work.foodSite;
            if (siteId == null && work.batch != null) {
                var batch = town.getWarehouse().batch(work.batch);
                if (batch != null) siteId = batch.workplaceId();
            }
            var site = siteId == null ? null : town.getApprenticeWorkplaces().find(siteId);
            return Message.raw(work(work, site == null ? null : site.type(), site == null ? null : site.name()));
        }
        var identity = store.getComponent(npc, TownsfolkCharacterBinding.getComponentType());
        String character = identity != null ? identity.getCharacterId() : saved != null && saved.getTownsfolkIdentity() != null ? saved.getTownsfolkIdentity().getCharacterId() : null;
        var progress = character == null ? null : town.getTownsfolkProgress().get(character.toLowerCase(Locale.ROOT));
        if (progress == null || !progress.isEmployed()) return Message.raw("Relaxing");
        if (town.getPayroll().overdue(character)) return Message.raw("Waiting for wages");
        if(progress.priority(TownsfolkWorkType.MINING)>0){
            var plugin=com.hexvane.aetherhaven.AetherhavenPlugin.get();
            String miningIssue=null;boolean ready=false;
            for(var site:town.getApprenticeWorkplaces().sites())if(site.type()==TownsfolkWorkType.MINING){
                if(site.mineBaseId()==null&&(site.existingExcavation()==null||site.existingExcavation().id()==null)&&!town.getApprenticeWorkplaces().tunnels(site.id()).isEmpty())continue;
                String issue=com.hexvane.aetherhaven.workplace.NativeExcavation.workIssue(store.getExternalData().getWorld(),town,site,plugin,progress.skill(TownsfolkWorkType.MINING.skill));
                if(issue==null){ready=true;break;}if(miningIssue==null)miningIssue=issue;
            }
            if(!ready&&miningIssue!=null)return Message.raw("Mining: "+miningIssue);
        }
        boolean enabled = java.util.Arrays.stream(TownsfolkWorkType.values()).anyMatch(type -> type.availableForNewWork() && progress.priority(type) > 0 && progress.skill(type.skill) > 0);
        return Message.raw(enabled ? "Waiting for work" : "No work enabled");
    }

    static String delivery(String action) {
        if (action == null) return "Moving supplies";
        return switch (action) {
            case "pickup" -> "Collecting supplies";
            case "deliver" -> "Delivering supplies";
            case "collect" -> "Collecting returned supplies";
            case "return" -> "Returning supplies";
            default -> "Moving supplies";
        };
    }

    static String work(ApprenticeWorkState state, TownsfolkWorkType type, String site) {
        if (state.excavationSite != null) {
            if (state.excavationDelivery || state.excavationCarrying) return "Delivering mined material";
            if (state.excavationPickup) return "Collecting mined material";
        }
        String location = site == null || site.isBlank() ? "work" : site;
        if (!state.working) return "Walking to " + location;
        String action = state.buildingSite != null ? "Building" : state.excavationSite != null ? "Mining"
            : state.foodSite != null && type == TownsfolkWorkType.FARMING ? state.foodHarvest ? "Harvesting crops" : "Watering crops"
            : state.foodSite != null && type == TownsfolkWorkType.RANCHING ? "Tending animals" : "Working";
        return action + " at " + location;
    }
}
