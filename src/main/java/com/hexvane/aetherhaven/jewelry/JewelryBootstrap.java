package com.hexvane.aetherhaven.jewelry;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plugin.AetherhavenFeatures;
import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.ui.JewelryAppraisalPage;
import com.hexvane.aetherhaven.ui.JewelryCraftingPage;
import com.hexvane.aetherhaven.ui.OpenHandMirrorUiInteraction;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import org.joml.Vector3i;
import javax.annotation.Nonnull;

public final class JewelryBootstrap {
    private JewelryBootstrap() {}

    public static void registerAssetCodecs(@Nonnull AetherhavenPlugin core) {
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenOpenHandMirror",
                OpenHandMirrorUiInteraction.class,
                OpenHandMirrorUiInteraction.CODEC
            );
        OpenCustomUIInteraction.registerSimple(
            core,
            JewelryAppraisalPage.class,
            AetherhavenConstants.PAGE_JEWELRY_APPRAISAL_BENCH,
            playerRef ->
                AetherhavenFeatures.isLoaded(AetherhavenPluginIds.JEWELRY)
                    ? new JewelryAppraisalPage(playerRef, false)
                    : null
        );
        OpenCustomUIInteraction.registerCustomPageSupplier(
            core,
            JewelryCraftingPage.class,
            AetherhavenConstants.PAGE_JEWELRY_CRAFTING_BENCH,
            (ref, componentAccessor, playerRef, context) -> {
                if (!AetherhavenFeatures.isLoaded(AetherhavenPluginIds.JEWELRY)) {
                    return null;
                }
                BlockPosition targetBlock = context.getTargetBlock();
                Vector3i benchPos =
                    targetBlock != null
                        ? new Vector3i(targetBlock.x, targetBlock.y, targetBlock.z)
                        : null;
                return new JewelryCraftingPage(playerRef, benchPos);
            }
        );
    }

    public static void register(@Nonnull AetherhavenPlugin core, @Nonnull JavaPlugin plugin) {
        JewelryRolling.bind(() -> core.getConfig().get());
        PlayerJewelryLoadout.register(plugin.getEntityStoreRegistry());
        plugin.getEntityStoreRegistry().registerSystem(new JewelryPlayerInitSystem());
        plugin.getEntityStoreRegistry().registerSystem(new JewelryInventoryTooltipSyncSystem());
        plugin.getEntityStoreRegistry().registerSystem(new JewelryLoadoutEffectSyncSystem());
        LootChestWorldLootPending.register(plugin.getChunkStoreRegistry());
        LootChestWorldGenerated.register(plugin.getChunkStoreRegistry());
        LootChestBonusApplied.register(plugin.getChunkStoreRegistry());
        LootChestSupplementalBonusApplied.register(plugin.getChunkStoreRegistry());
        LootrChestProcessedPlayers.register(plugin.getChunkStoreRegistry());
        Loot4EveryoneChestProcessedPlayers.register(plugin.getChunkStoreRegistry());
        plugin.getChunkStoreRegistry().registerSystem(new LootChestWorldLootMarkSystem());
        plugin.getChunkStoreRegistry().registerSystem(new LootChestBonusInjectSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new LootChestOpenBonusInjectPlayerSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new LootrPerPlayerLootInjectPlayerSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new LootrIntegrationStartupSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new Loot4EveryonePerPlayerLootInjectPlayerSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new Loot4EveryoneIntegrationStartupSystem());
        core.registerJewelryNativeTooltipHooks();
        core.registerJewelryRarityBorderPackets();
        core.registerAetherhavenSubcommand(new com.hexvane.aetherhaven.command.AetherhavenJewelryCommand());
    }
}
