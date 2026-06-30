package com.hexvane.aetherhaven.dialogue;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.command.DialogueCommand;
import com.hexvane.aetherhaven.npc.BuilderActionOpenAetherhavenDialogue;
import com.hexvane.aetherhaven.npc.movement.BuilderBodyMotionWanderInRectGroundPreference;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.npc.NPCPlugin;
import javax.annotation.Nonnull;

public final class DialogueBootstrap {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private DialogueBootstrap() {}

    public static void registerAssetCodecs(@Nonnull AetherhavenPlugin core) {
        NPCPlugin npc = NPCPlugin.get();
        if (npc != null) {
            npc.registerCoreComponentType("OpenAetherhavenDialogue", BuilderActionOpenAetherhavenDialogue::new);
            npc.registerCoreComponentType("WanderInRectGroundPreference", BuilderBodyMotionWanderInRectGroundPreference::new);
            LOGGER.atInfo().log("Registered NPC action OpenAetherhavenDialogue and body motion WanderInRectGroundPreference");
        } else {
            LOGGER.atWarning().log("NPCPlugin not loaded; OpenAetherhavenDialogue action unavailable");
        }
    }

    public static void register(@Nonnull AetherhavenPlugin core, @Nonnull JavaPlugin plugin) {
        core.registerAetherhavenSubcommand(new DialogueCommand());
    }
}
