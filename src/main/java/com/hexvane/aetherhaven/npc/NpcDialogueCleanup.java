package com.hexvane.aetherhaven.npc;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Vanilla dialogue roles transition into {@code $Interaction} when opening UI. Our custom {@link com.hexvane.aetherhaven.ui.DialoguePage}
 * does not run the engine's dialogue teardown, so the NPC can remain in a busy state and stop wandering / accepting interactions.
 */
public final class NpcDialogueCleanup {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private NpcDialogueCleanup() {}

    /**
     * Returns the NPC to {@code Idle} on the world thread so interaction sensors and BodyMotion under {@code Idle} resume.
     */
    public static void scheduleReturnToIdle(@Nullable Ref<EntityStore> npcRef, @Nonnull Store<EntityStore> store) {
        if (npcRef == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        world.execute(
            () -> {
                if (!npcRef.isValid()) {
                    return;
                }
                NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
                if (npc == null || npc.getRole() == null) {
                    return;
                }
                try {
                    npc.getRole().getStateSupport().setState(npcRef, "Idle", null, store);
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Failed to reset NPC to Idle after dialogue");
                }
            }
        );
    }
}
