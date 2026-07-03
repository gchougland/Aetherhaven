package com.hexvane.aetherhaven.builder;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.equipment.VillagerEquipmentService;
import com.hexvane.aetherhaven.npc.NpcAnimationPlayback;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Hammer equip and swing animation while the builder assists plot assembly. */
public final class BuilderConstructionVisuals {
    private static final String HAMMER_ITEM_ID = "Tool_Hammer_Iron";
    private static final String WORK_PROFILE_ID = "work_builder";

    private BuilderConstructionVisuals() {}

    public static void beginAssist(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull NPCEntity npc
    ) {
        VillagerEquipmentService.applyProfile(
            npcRef,
            store,
            commandBuffer,
            plugin.getEquipmentProfileCatalog(),
            WORK_PROFILE_ID
        );
    }

    public static void swingHammer(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc
    ) {
        Item item = Item.getAssetMap().getAsset(HAMMER_ITEM_ID);
        if (item == null) {
            return;
        }
        String pid = item.getPlayerAnimationsId();
        if (pid == null || pid.isBlank()) {
            return;
        }
        ItemPlayerAnimations ipa = ItemPlayerAnimations.getAssetMap().getAsset(pid);
        if (ipa != null) {
            NpcAnimationPlayback.playItem(npcRef, AnimationSlot.Action, ipa, "SwingLeft", commandBuffer);
        } else {
            NpcAnimationPlayback.play(npcRef, npc, AnimationSlot.Action, "SwingLeft", commandBuffer);
        }
        commandBuffer.putComponent(npcRef, NPCEntity.getComponentType(), npc);
    }

    public static void endAssist(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nullable NPCEntity npc
    ) {
        if (npc != null) {
            NpcAnimationPlayback.stop(npcRef, AnimationSlot.Action, commandBuffer);
            NpcAnimationPlayback.stop(npcRef, AnimationSlot.Status, commandBuffer);
            NpcAnimationPlayback.play(npcRef, npc, AnimationSlot.Action, null, commandBuffer);
            NpcAnimationPlayback.play(npcRef, npc, AnimationSlot.Status, null, commandBuffer);
            commandBuffer.putComponent(npcRef, NPCEntity.getComponentType(), npc);
        }
    }
}
