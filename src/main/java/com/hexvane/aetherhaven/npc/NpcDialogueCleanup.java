package com.hexvane.aetherhaven.npc;

import com.hexvane.aetherhaven.npc.NpcSupportUtil;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Vanilla dialogue roles transition into {@code $Interaction} when opening UI. Our custom {@link com.hexvane.aetherhaven.ui.DialoguePage}
 * does not run the engine's dialogue teardown, so the NPC can remain in a busy state and stop wandering / accepting interactions.
 *
 * <p>Several players can talk to the same NPC at once, so the NPC only leaves {@code $Interaction} once the last of them
 * has closed their dialogue. Without that count the first player to walk away would set the NPC wandering while everyone
 * else was still mid conversation.
 */
public final class NpcDialogueCleanup {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String INTERACTION_STATE = "$Interaction";

    /** NPC entity uuid to the players currently reading its dialogue. */
    private static final Map<UUID, Set<UUID>> TALKERS_BY_NPC = new ConcurrentHashMap<>();

    private NpcDialogueCleanup() {}

    /**
     * Puts the NPC into {@code $Interaction} on the world thread after dialogue UI is open (watch player, pause autonomy).
     */
    public static void scheduleEnterInteraction(
        @Nullable Ref<EntityStore> npcRef,
        @Nullable Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        if (npcRef == null) {
            return;
        }
        addTalker(npcRef, playerRef, store);
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
                    NpcSupportUtil.setState(npcRef, INTERACTION_STATE, null, store);
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Failed to set NPC to $Interaction for dialogue");
                }
            }
        );
    }

    /**
     * Returns the NPC to {@code Idle} on the world thread so interaction sensors and BodyMotion under {@code Idle} resume,
     * but only once nobody else is still talking to it.
     */
    public static void scheduleReturnToIdle(
        @Nullable Ref<EntityStore> npcRef,
        @Nullable Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        if (npcRef == null) {
            return;
        }
        if (!removeTalker(npcRef, playerRef, store)) {
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
                    NpcFaceVisuals.clearDialogueFace(npcRef, store);
                    NpcSupportUtil.setState(npcRef, "Idle", null, store);
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Failed to reset NPC to Idle after dialogue");
                }
            }
        );
    }

    private static void addTalker(
        @Nonnull Ref<EntityStore> npcRef,
        @Nullable Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        UUID npcId = entityUuid(npcRef, store);
        UUID playerId = playerRef != null ? entityUuid(playerRef, store) : null;
        if (npcId == null || playerId == null) {
            return;
        }
        Set<UUID> talkers = TALKERS_BY_NPC.computeIfAbsent(npcId, k -> ConcurrentHashMap.newKeySet());
        dropDepartedTalkers(talkers, store);
        talkers.add(playerId);
    }

    /**
     * A player who disconnects mid conversation never dismisses their page, so their entry would sit in the set
     * forever and keep the NPC in {@code $Interaction} for everybody else. Anyone no longer in the world is gone.
     */
    private static void dropDepartedTalkers(@Nonnull Set<UUID> talkers, @Nonnull Store<EntityStore> store) {
        talkers.removeIf(talker -> {
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(talker);
            return ref == null || !ref.isValid();
        });
    }

    /** @return whether the NPC should now go back to idle, meaning nobody is left talking to it. */
    private static boolean removeTalker(
        @Nonnull Ref<EntityStore> npcRef,
        @Nullable Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        UUID npcId = entityUuid(npcRef, store);
        if (npcId == null) {
            return true;
        }
        UUID playerId = playerRef != null ? entityUuid(playerRef, store) : null;
        Set<UUID> talkers = TALKERS_BY_NPC.get(npcId);
        if (talkers == null) {
            return true;
        }
        if (playerId != null) {
            talkers.remove(playerId);
            dropDepartedTalkers(talkers, store);
        } else {
            talkers.clear();
        }
        if (talkers.isEmpty()) {
            TALKERS_BY_NPC.remove(npcId);
            return true;
        }
        return false;
    }

    @Nullable
    private static UUID entityUuid(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (!ref.isValid()) {
            return null;
        }
        UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
        return uuid != null ? uuid.getUuid() : null;
    }
}
