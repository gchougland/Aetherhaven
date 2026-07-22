package com.hexvane.aetherhaven.floatinggift;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.command.AetherhavenFloatingGiftCommand;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import javax.annotation.Nonnull;

public final class FloatingGiftsBootstrap {
    private FloatingGiftsBootstrap() {}

    public static void registerAssetCodecs(@Nonnull AetherhavenPlugin core) {
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenOpenFloatingGiftChest",
                OpenFloatingGiftChestInteraction.class,
                OpenFloatingGiftChestInteraction.CODEC
            );
    }

    public static void register(@Nonnull AetherhavenPlugin core, @Nonnull JavaPlugin plugin) {
        FloatingGiftLootFiles.ensureDefaultLootFile(core);
        FloatingGiftComponent.register(plugin.getEntityStoreRegistry());
        plugin.getEntityStoreRegistry().registerSystem(new FloatingGiftSchedulerSystem());
        plugin.getEntityStoreRegistry().registerSystem(new FloatingGiftSystem());
        plugin.getEntityStoreRegistry().registerSystem(new FloatingGiftDamagePopSystem());
        core.registerAetherhavenSubcommand(new AetherhavenFloatingGiftCommand());
    }
}
