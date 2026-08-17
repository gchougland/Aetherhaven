package com.hexvane.aetherhaven.festival.wintertide;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerFollowPlayerSystem;
import com.hexvane.aetherhaven.npc.NpcFaceVisuals;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Walks the assigned villager to the player after they give their Wintertide gift, then opens the return gift talk.
 * Movement uses follow via the command buffer.
 */
public final class WintertideGiftSeekSystem extends EntityTickingSystem<EntityStore> {
    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
            NPCEntity.getComponentType(),
            TownVillagerBinding.getComponentType(),
            UUIDComponent.getComponentType(),
            TransformComponent.getComponentType()
        );
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (!WintertideGiftSeekState.isRegistered()) {
            return;
        }
        TownVillagerBinding binding = chunk.getComponent(index, TownVillagerBinding.getComponentType());
        UUIDComponent npcUuid = chunk.getComponent(index, UUIDComponent.getComponentType());
        NPCEntity npc = chunk.getComponent(index, NPCEntity.getComponentType());
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        if (binding == null || npcUuid == null || npc == null || ref == null || !ref.isValid()) {
            return;
        }
        if (com.hexvane.aetherhaven.festival.snowball.SnowballSessionIndex.isLivingFighter(npcUuid.getUuid())) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        if (plugin == null || world == null || binding.getTownId() == null) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(binding.getTownId());
        if (!WintertideGiftService.isWintertideActive(town)) {
            return;
        }
        WintertideSession session = WintertideSessionIndex.get(town.getTownId());
        if (session == null) {
            return;
        }
        UUID playerUuid = playerWaitingForThisVillager(session, npcUuid.getUuid());
        if (playerUuid == null) {
            return;
        }
        WintertideGiftSeekState seek = store.getComponent(ref, WintertideGiftSeekState.getComponentType());
        if (seek == null || !seek.isActive() || !playerUuid.equals(seek.getPlayerUuid())) {
            WintertideGiftSeekState next = seek == null ? new WintertideGiftSeekState() : (WintertideGiftSeekState) seek.clone();
            next.start(playerUuid);
            commandBuffer.putComponent(ref, WintertideGiftSeekState.getComponentType(), next);
            VillagerFollowPlayerSystem.startFollowFromTick(ref, store, commandBuffer, playerUuid);
            return;
        }
        if (NpcFaceVisuals.isInInteractionDialogue(npc) || seek.isDialogueOpened()) {
            return;
        }
        Ref<EntityStore> playerRef = store.getExternalData().getRefFromUUID(playerUuid);
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        if (player.getPageManager().getCustomPage() != null) {
            return;
        }
        TransformComponent playerTc = store.getComponent(playerRef, TransformComponent.getComponentType());
        TransformComponent npcTc = chunk.getComponent(index, TransformComponent.getComponentType());
        if (playerTc == null || npcTc == null) {
            return;
        }
        Vector3d playerPos = playerTc.getPosition();
        Vector3d npcPos = npcTc.getPosition();
        double dx = npcPos.x - playerPos.x;
        double dz = npcPos.z - playerPos.z;
        if (dx * dx + dz * dz > WintertideIds.SEEK_ARRIVED_DIST_SQ) {
            VillagerFollowPlayerSystem.startFollowFromTick(ref, store, commandBuffer, playerUuid);
            return;
        }
        WintertideGiftSeekState opened = (WintertideGiftSeekState) seek.clone();
        opened.markDialogueOpened();
        commandBuffer.putComponent(ref, WintertideGiftSeekState.getComponentType(), opened);
        Ref<EntityStore> villagerRef = ref;
        world.execute(
            () -> WintertideGiftService.openDialogue(
                playerRef, store, villagerRef, WintertideIds.DIALOGUE_INCOMING, "gift"
            )
        );
    }

    public static void startSeek(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID playerUuid
    ) {
        if (!WintertideGiftSeekState.isRegistered()) {
            return;
        }
        WintertideGiftSeekState seek = store.getComponent(npcRef, WintertideGiftSeekState.getComponentType());
        if (seek == null) {
            seek = new WintertideGiftSeekState();
        } else {
            seek = (WintertideGiftSeekState) seek.clone();
        }
        seek.start(playerUuid);
        store.putComponent(npcRef, WintertideGiftSeekState.getComponentType(), seek);
        VillagerFollowPlayerSystem.startFollow(npcRef, store, playerUuid);
    }

    public static void clearSeek(@Nonnull Ref<EntityStore> npcRef, @Nonnull Store<EntityStore> store) {
        if (!WintertideGiftSeekState.isRegistered()) {
            return;
        }
        WintertideGiftSeekState seek = store.getComponent(npcRef, WintertideGiftSeekState.getComponentType());
        if (seek != null && seek.isActive()) {
            WintertideGiftSeekState cleared = (WintertideGiftSeekState) seek.clone();
            cleared.clear();
            store.putComponent(npcRef, WintertideGiftSeekState.getComponentType(), cleared);
        }
        VillagerFollowPlayerSystem.stopFollow(npcRef, store, true);
    }

    public static boolean shouldSkipAutonomy(@Nullable WintertideGiftSeekState seek) {
        return seek != null && seek.isActive();
    }

    @Nullable
    private static UUID playerWaitingForThisVillager(@Nonnull WintertideSession session, @Nonnull UUID villagerUuid) {
        for (UUID playerUuid : session.assignedPlayerUuids()) {
            if (!session.hasGiven(playerUuid) || session.hasReceived(playerUuid)) {
                continue;
            }
            WintertideTarget incoming = session.getIncoming(playerUuid);
            if (incoming != null && incoming.isVillager() && villagerUuid.equals(incoming.getUuid())) {
                return playerUuid;
            }
        }
        return null;
    }
}
