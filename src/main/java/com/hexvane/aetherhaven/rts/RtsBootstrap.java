package com.hexvane.aetherhaven.rts;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import javax.annotation.Nonnull;

public final class RtsBootstrap {
    private RtsBootstrap() {}

    public static void registerAssetCodecs(@Nonnull AetherhavenPlugin core) {
        core
            .getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenCommandPostUse", CommandPostUseInteraction.class, CommandPostUseInteraction.CODEC);
        core
            .getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenRtsToolPrimary", RtsToolPrimaryInteraction.class, RtsToolPrimaryInteraction.CODEC);
        core
            .getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenRtsToolSecondary", RtsToolSecondaryInteraction.class, RtsToolSecondaryInteraction.CODEC);
        core
            .getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenRtsFlagOrderCycle", RtsFlagOrderCycleInteraction.class, RtsFlagOrderCycleInteraction.CODEC);
        core
            .getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenRtsFlagStop", RtsFlagStopInteraction.class, RtsFlagStopInteraction.CODEC);
        core
            .getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenRtsStanceCycle", RtsStanceCycleInteraction.class, RtsStanceCycleInteraction.CODEC);
        core
            .getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenRtsExit", RtsExitInteraction.class, RtsExitInteraction.CODEC);
    }

    public static void register(@Nonnull AetherhavenPlugin core, @Nonnull JavaPlugin plugin) {
        CommandPostBlock.register(plugin.getChunkStoreRegistry());
        RtsCommandPlayerComponent.register(plugin.getEntityStoreRegistry());
        GuardRtsCommandState.register(plugin.getEntityStoreRegistry());
        plugin.getEntityStoreRegistry().registerSystem(new GuardRtsCommandSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new GuardCombatCounterAttackSystem());
        plugin.getEntityStoreRegistry().registerSystem(new RtsCommanderCameraSystem.Follow(core));
        plugin.getEntityStoreRegistry().registerSystem(new RtsExitMovementGuardSystem());
        plugin.getEntityStoreRegistry().registerSystem(new RtsCameraMousePollSystem());
        plugin.getEntityStoreRegistry().registerSystem(new RtsHudRefreshSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new RtsUncleanSessionRecoverySystem());
        plugin.getEntityStoreRegistry().registerSystem(new RtsOrphanedGuardRecoverySystem());
        plugin.getEntityStoreRegistry().registerSystem(new RtsCommanderNpcDamageFilterSystem());
        plugin.getEntityStoreRegistry().registerSystem(new RtsMarkerVisualSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new RtsMoveOrderVisualSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new CommandPostPlaceEventSystem(core));
        RtsMouseInputListener.register(plugin.getEventRegistry());
        RtsInputGuardListener.register(plugin.getEventRegistry());
        core.registerRtsClientMovementPacketAdapter();
        core.registerRtsCommandHotbarSlotInboundAdapter();
    }
}
