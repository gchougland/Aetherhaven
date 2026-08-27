package com.hexvane.aetherhaven.villagercosmetic;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownMemberBlockAccess;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.VillagerWardrobeCustomizePage;
import com.hexvane.aetherhaven.ui.VillagerWardrobeResidentPage;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import javax.annotation.Nonnull;

public final class VillagerCosmeticsBootstrap {
    private VillagerCosmeticsBootstrap() {}

    public static void register(@Nonnull AetherhavenPlugin plugin) {
        plugin
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenVillagerCosmeticUnlockUse",
                VillagerCosmeticUnlockUseInteraction.class,
                VillagerCosmeticUnlockUseInteraction.CODEC
            );

        OpenCustomUIInteraction.registerCustomPageSupplier(
            plugin,
            VillagerWardrobeResidentPage.class,
            AetherhavenConstants.PAGE_VILLAGER_WARDROBE,
            (ref, componentAccessor, playerRef, context) -> {
                BlockPosition targetBlock = context.getTargetBlock();
                if (targetBlock == null) {
                    return null;
                }
                var store = ref.getStore();
                World world = store.getExternalData().getWorld();
                BlockType bt = ChunkSectionBlockUtil.blockType(world, targetBlock.x, targetBlock.y, targetBlock.z);
                if (bt == null
                    || bt == BlockType.EMPTY
                    || !AetherhavenConstants.BLOCK_VILLAGER_WARDROBE.equals(bt.getId())) {
                    return null;
                }
                AetherhavenPlugin p = AetherhavenPlugin.get();
                if (p == null) {
                    return null;
                }
                UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
                if (uc == null) {
                    return null;
                }
                TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, p);
                TownRecord town = tm.findTownContainingBlock(world.getName(), targetBlock.x, targetBlock.z);
                if (TownMemberBlockAccess.denyIfNotMember(playerRef, town, uc.getUuid())) {
                    return null;
                }
                return new VillagerWardrobeResidentPage(
                    playerRef,
                    town.getTownId(),
                    targetBlock.x,
                    targetBlock.y,
                    targetBlock.z
                );
            }
        );
        OpenCustomUIInteraction.registerCustomPageSupplier(
            plugin,
            VillagerWardrobeCustomizePage.class,
            AetherhavenConstants.PAGE_VILLAGER_WARDROBE_CUSTOMIZE,
            (ref, componentAccessor, playerRef, context) -> null
        );
    }
}
