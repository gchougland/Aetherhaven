package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.mastery.CoreMasteryPage;
import com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;

/** Resolve each click against current town membership, including unloaded saved residents. */
final class ResidentProfileNavigation {
    private ResidentProfileNavigation() {}
    static boolean supports(Store<EntityStore> store,TownRecord town,TownVillagerRow row) {
        return row.bindingKind()!=null||characterId(store,town,row.entityUuid())!=null;
    }
    static void open(PlayerRef player,Ref<EntityStore> ref,Store<EntityStore> store,TownRecord town,UUID resident) {
        open(player,ref,store,town,resident,true);
    }
    static void open(PlayerRef player,Ref<EntityStore> ref,Store<EntityStore> store,TownRecord town,UUID resident,boolean fromJournal) {
        if(!town.hasMemberOrOwner(player.getUuid()))return;
        var row=TownVillagerDirectory.listResidents(store,town).stream().filter(r->resident.equals(r.entityUuid())).findFirst().orElse(null);
        var entity=store.getComponent(ref,Player.getComponentType());
        if(row==null||entity==null)return;
        String character=characterId(store,town,resident);
        if(character!=null) {
            entity.getPageManager().openCustomPage(ref,store,new TownsfolkProfilePage(player,town.getTownId(),character,resident,fromJournal));
        } else if(row.bindingKind()!=null) {
            var npc=store.getExternalData().getRefFromUUID(resident);
            entity.getPageManager().openCustomPage(ref,store,new CoreMasteryPage(player,town.getTownId(),row.bindingKind(),npc,resident,fromJournal));
        }
    }
    private static String characterId(Store<EntityStore> store,TownRecord town,UUID resident) {
        var npc=store.getExternalData().getRefFromUUID(resident);
        if(npc!=null&&npc.isValid()) {
            var binding=store.getComponent(npc,TownVillagerBinding.getComponentType());
            var identity=store.getComponent(npc,TownsfolkCharacterBinding.getComponentType());
            if(binding!=null&&town.getTownId().equals(binding.getTownId())&&identity!=null)return identity.getCharacterId();
        }
        for(var saved:town.getResidentNpcRecords())if(resident.equals(saved.getLastEntityUuid())&&saved.getTownsfolkIdentity()!=null)
            return saved.getTownsfolkIdentity().getCharacterId();
        return null;
    }
}
