package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

final class VillagerEatingFood {
    private VillagerEatingFood() {}
    static String forCharacter(String character) {
        return "prowl".equals(character) ? "Plant_Crop_Corn_Item" : AetherhavenConstants.CAMPFIRE_EAT_ITEM_ID;
    }
    static String forNpc(Ref<EntityStore> ref, Store<EntityStore> store) {
        var identity = store.getComponent(ref, TownsfolkCharacterBinding.getComponentType());
        return forCharacter(identity == null ? null : identity.getCharacterId());
    }
}
