package com.hexvane.aetherhaven.dialogue;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.command.DialogueCommand;
import com.hexvane.aetherhaven.npc.BuilderActionOpenAetherhavenDialogue;
import com.hexvane.aetherhaven.npc.movement.BuilderBodyMotionWanderInRectGroundPreference;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.LoadAssetEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.npc.NPCPlugin;
import javax.annotation.Nonnull;

public final class DialogueBootstrap {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Before {@link NPCPlugin#PRIORITY_LOAD_NPC} ({@code -8}) so custom motion/actions exist when roles validate. */
    public static final short PRIORITY_REGISTER_NPC_CODECS = -9;

    private static boolean npcCodecsRegistered;

    private DialogueBootstrap() {}

    public static void registerLoadHooks(@Nonnull AetherhavenPlugin plugin) {
        plugin
            .getEventRegistry()
            .register(PRIORITY_REGISTER_NPC_CODECS, LoadAssetEvent.class, event -> registerAssetCodecs(plugin));
    }

    public static void registerAssetCodecs(@Nonnull AetherhavenPlugin core) {
        if (npcCodecsRegistered) {
            return;
        }
        NPCPlugin npc = NPCPlugin.get();
        if (npc != null) {
            npc.registerCoreComponentType("OpenAetherhavenDialogue", BuilderActionOpenAetherhavenDialogue::new);
            npc.registerCoreComponentType("WanderInRectGroundPreference", BuilderBodyMotionWanderInRectGroundPreference::new);
            npcCodecsRegistered = true;
            LOGGER.atInfo().log("Registered NPC action OpenAetherhavenDialogue and body motion WanderInRectGroundPreference");
        } else {
            LOGGER.atWarning().log("NPCPlugin not loaded; OpenAetherhavenDialogue action unavailable");
        }
    }

    public static void register(@Nonnull AetherhavenPlugin core, @Nonnull JavaPlugin plugin) {
        core.registerAetherhavenSubcommand(new DialogueCommand());
    }
}
