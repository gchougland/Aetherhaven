package com.hexvane.aetherhaven.reputation;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerReputationWaveSystem;
import com.hexvane.aetherhaven.command.AetherhavenGiftCommand;
import com.hexvane.aetherhaven.command.AetherhavenReputationDebugCommand;
import com.hexvane.aetherhaven.npc.NpcReputationWaveState;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import javax.annotation.Nonnull;

public final class ReputationBootstrap {
    private ReputationBootstrap() {}

    public static void registerAssetCodecs(@Nonnull AetherhavenPlugin core) {}

    public static void register(@Nonnull AetherhavenPlugin core, @Nonnull JavaPlugin plugin) {
        NpcReputationWaveState.register(plugin.getEntityStoreRegistry());
        plugin.getEntityStoreRegistry().registerSystem(new VillagerReputationWaveSystem(core));
        core.registerAetherhavenSubcommand(new AetherhavenGiftCommand());
        core.registerAetherhavenSubcommand(new AetherhavenReputationDebugCommand());
    }
}
