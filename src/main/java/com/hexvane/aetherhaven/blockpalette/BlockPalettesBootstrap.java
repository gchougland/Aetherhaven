package com.hexvane.aetherhaven.blockpalette;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.command.AetherhavenPaletteCommand;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import javax.annotation.Nonnull;

/** Wires block palettes into the plugin. */
public final class BlockPalettesBootstrap {
    private BlockPalettesBootstrap() {}

    public static void registerAssetCodecs(@Nonnull AetherhavenPlugin core) {
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                BlockPaletteConstants.INTERACTION_UNLOCK,
                BlockPaletteUnlockUseInteraction.class,
                BlockPaletteUnlockUseInteraction.CODEC
            );
    }

    public static void register(@Nonnull AetherhavenPlugin core, @Nonnull JavaPlugin plugin) {
        core.registerAetherhavenSubcommand(new AetherhavenPaletteCommand());
    }
}
