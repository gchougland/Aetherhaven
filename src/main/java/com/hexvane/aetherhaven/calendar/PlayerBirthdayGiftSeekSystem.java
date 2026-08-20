package com.hexvane.aetherhaven.calendar;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerFollowPlayerSystem;
import com.hexvane.aetherhaven.festival.wintertide.WintertideGiftSeekState;
import com.hexvane.aetherhaven.festival.wintertide.WintertideGiftSeekSystem;
import com.hexvane.aetherhaven.hud.AetherhavenCalendar;
import com.hexvane.aetherhaven.hud.AetherhavenCalendar.CalendarDate;
import com.hexvane.aetherhaven.npc.NpcFaceVisuals;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.PlayerTownJournalState;
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
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Walks max-friendship town villagers to a player on their birthday, then opens the gift talk.
 * Movement uses follow via the command buffer.
 */
public final class PlayerBirthdayGiftSeekSystem extends EntityTickingSystem<EntityStore> {
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
        if (!PlayerBirthdayGiftSeekState.isRegistered()) {
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
        if (WintertideGiftSeekState.isRegistered()) {
            WintertideGiftSeekState wintertide =
                store.getComponent(ref, WintertideGiftSeekState.getComponentType());
            if (WintertideGiftSeekSystem.shouldSkipAutonomy(wintertide)) {
                return;
            }
        }
        PlayerBirthdayGiftSeekState seek = store.getComponent(ref, PlayerBirthdayGiftSeekState.getComponentType());
        if (!PlayerBirthdayService.isGiftableKind(binding.getKind())) {
            clearSeekFromTick(ref, store, commandBuffer, seek);
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        if (plugin == null || world == null) {
            return;
        }
        TownRecord town = PlayerBirthdayGiftService.townForVillager(store, binding.getTownId());
        if (town == null) {
            clearSeekFromTick(ref, store, commandBuffer, seek);
            return;
        }
        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        if (wtr == null) {
            return;
        }
        CalendarDate today = AetherhavenCalendar.from(wtr.getGameDateTime());
        UUID playerUuid = playerWaitingForThisVillager(store, town, npcUuid.getUuid(), today, seek);
        if (playerUuid == null) {
            clearSeekFromTick(ref, store, commandBuffer, seek);
            return;
        }
        if (seek == null || !seek.isActive() || !playerUuid.equals(seek.getPlayerUuid())) {
            PlayerBirthdayGiftSeekState next =
                seek == null ? new PlayerBirthdayGiftSeekState() : (PlayerBirthdayGiftSeekState) seek.clone();
            next.start(playerUuid);
            commandBuffer.putComponent(ref, PlayerBirthdayGiftSeekState.getComponentType(), next);
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
        if (dx * dx + dz * dz > PlayerBirthdayIds.SEEK_ARRIVED_DIST_SQ) {
            VillagerFollowPlayerSystem.startFollowFromTick(ref, store, commandBuffer, playerUuid);
            return;
        }
        PlayerBirthdayGiftSeekState opened = (PlayerBirthdayGiftSeekState) seek.clone();
        opened.markDialogueOpened();
        commandBuffer.putComponent(ref, PlayerBirthdayGiftSeekState.getComponentType(), opened);
        UUID playerId = playerUuid;
        UUID villagerId = npcUuid.getUuid();
        world.execute(
            () -> {
                Store<EntityStore> live =
                    world.getEntityStore() != null ? world.getEntityStore().getStore() : null;
                if (live == null) {
                    return;
                }
                Ref<EntityStore> livePlayer = live.getExternalData().getRefFromUUID(playerId);
                Ref<EntityStore> liveVillager = live.getExternalData().getRefFromUUID(villagerId);
                if (livePlayer == null || !livePlayer.isValid()) {
                    return;
                }
                PlayerBirthdayGiftService.openDialogue(livePlayer, live, liveVillager);
            }
        );
    }

    public static void clearSeek(@Nonnull Ref<EntityStore> npcRef, @Nonnull Store<EntityStore> store) {
        if (!PlayerBirthdayGiftSeekState.isRegistered()) {
            return;
        }
        PlayerBirthdayGiftSeekState seek = store.getComponent(npcRef, PlayerBirthdayGiftSeekState.getComponentType());
        if (seek != null && seek.isActive()) {
            PlayerBirthdayGiftSeekState cleared = (PlayerBirthdayGiftSeekState) seek.clone();
            cleared.clear();
            store.putComponent(npcRef, PlayerBirthdayGiftSeekState.getComponentType(), cleared);
        }
        VillagerFollowPlayerSystem.stopFollow(npcRef, store, true);
    }

    public static boolean shouldSkipAutonomy(@Nullable PlayerBirthdayGiftSeekState seek) {
        return seek != null && seek.isActive();
    }

    @Nullable
    public static Ref<EntityStore> findSeekingVillager(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID playerUuid
    ) {
        if (!PlayerBirthdayGiftSeekState.isRegistered()) {
            return null;
        }
        Ref<EntityStore>[] found = new Ref[1];
        store.forEachChunk(
            Query.and(PlayerBirthdayGiftSeekState.getComponentType(), UUIDComponent.getComponentType()),
            (chunk, commandBuffer) -> {
                if (found[0] != null) {
                    return;
                }
                for (int i = 0; i < chunk.size(); i++) {
                    PlayerBirthdayGiftSeekState seek =
                        chunk.getComponent(i, PlayerBirthdayGiftSeekState.getComponentType());
                    if (seek == null || !seek.isActive() || !playerUuid.equals(seek.getPlayerUuid())) {
                        continue;
                    }
                    Ref<EntityStore> seekRef = chunk.getReferenceTo(i);
                    if (seekRef != null && seekRef.isValid()) {
                        found[0] = seekRef;
                        return;
                    }
                }
            }
        );
        return found[0];
    }

    private static void clearSeekFromTick(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nullable PlayerBirthdayGiftSeekState seek
    ) {
        if (seek == null || !seek.isActive()) {
            return;
        }
        PlayerBirthdayGiftSeekState cleared = (PlayerBirthdayGiftSeekState) seek.clone();
        cleared.clear();
        commandBuffer.putComponent(npcRef, PlayerBirthdayGiftSeekState.getComponentType(), cleared);
        VillagerFollowPlayerSystem.stopFollowFromTick(npcRef, store, commandBuffer, true);
    }

    @Nullable
    private static UUID playerWaitingForThisVillager(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull UUID villagerUuid,
        @Nonnull CalendarDate today,
        @Nullable PlayerBirthdayGiftSeekState seek
    ) {
        UUID preferred = seek != null && seek.isActive() ? seek.getPlayerUuid() : null;
        if (preferred != null && isEligiblePlayer(store, town, preferred, villagerUuid, today)) {
            return preferred;
        }
        UUID chosen = null;
        UUID ownerUuid = town.getOwnerUuid();
        if (isEligiblePlayer(store, town, ownerUuid, villagerUuid, today)) {
            chosen = ownerUuid;
        }
        for (UUID member : town.getMemberPlayerUuids()) {
            if (town.isOwner(member)) {
                continue;
            }
            if (!isEligiblePlayer(store, town, member, villagerUuid, today)) {
                continue;
            }
            if (chosen == null || member.compareTo(chosen) < 0) {
                chosen = member;
            }
        }
        return chosen;
    }

    private static boolean isEligiblePlayer(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nullable UUID playerUuid,
        @Nonnull UUID villagerUuid,
        @Nonnull CalendarDate today
    ) {
        if (playerUuid == null || !town.hasMemberOrOwner(playerUuid)) {
            return false;
        }
        Ref<EntityStore> playerRef = store.getExternalData().getRefFromUUID(playerUuid);
        if (playerRef == null || !playerRef.isValid()) {
            return false;
        }
        if (store.getComponent(playerRef, Player.getComponentType()) == null) {
            return false;
        }
        PlayerTownJournalState journal = store.getComponent(playerRef, PlayerTownJournalState.getComponentType());
        if (!PlayerBirthdayService.isBirthdayToday(journal, today)) {
            return false;
        }
        if (PlayerBirthdayService.alreadyGiftedThisYear(journal, today.year(), villagerUuid)) {
            return false;
        }
        return PlayerBirthdayService.isMaxedFriendship(town, playerUuid, villagerUuid);
    }
}
