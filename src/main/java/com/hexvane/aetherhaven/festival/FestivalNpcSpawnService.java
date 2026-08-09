package com.hexvane.aetherhaven.festival;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.NpcSpawnOriginUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * Spawns the NPCs a festival brings to town and removes them again when it ends. They are not saved with the town's
 * regular villagers: the uuids live on the town record only for the length of the festival.
 */
public final class FestivalNpcSpawnService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private FestivalNpcSpawnService() {}

    public static void spawnFestivalNpcs(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance square,
        @Nonnull FestivalDefinition festival
    ) {
        despawnFestivalNpcs(world, store, plugin, town);
        List<FestivalDefinition.NpcRow> rows = festival.getNpcs();
        if (rows.isEmpty()) {
            return;
        }
        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) {
            LOGGER.atWarning().log("Festival NPC spawn skipped: NPC support is not available");
            return;
        }
        List<String> spawned = new ArrayList<>(rows.size());
        for (FestivalDefinition.NpcRow row : rows) {
            String roleId = row.getNpcRoleId();
            if (roleId.isEmpty()) {
                continue;
            }
            Vector3d pos =
                FestivalPrefabSwapService.spotWorldPosition(plugin, square, row.getLocalX(), row.getLocalY(), row.getLocalZ());
            Rotation3f rotation = new Rotation3f(0f, (float) Math.toRadians(row.getYawDegrees()), 0f);
            var pair = npcPlugin.spawnNPC(store, roleId, null, pos, rotation);
            if (pair == null) {
                LOGGER.atWarning().log("Festival %s could not spawn NPC role %s", festival.getId(), roleId);
                continue;
            }
            Ref<EntityStore> ref = pair.first();
            store.putComponent(ref, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
            String displayName = row.getDisplayName();
            if (displayName != null) {
                store.putComponent(
                    ref,
                    PersistentDisplayName.getComponentType(),
                    new PersistentDisplayName(Message.raw(displayName))
                );
            }
            NpcSpawnOriginUtil.attach(store, ref, "FESTIVAL_NPC", "festival=" + festival.getId(), world, pos);
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc != null) {
                spawned.add(uc.getUuid().toString());
            }
        }
        town.setActiveFestivalNpcEntityUuids(spawned);
        tm.updateTown(town);
        LOGGER.atInfo().log("Spawned %s festival NPC(s) for %s", spawned.size(), festival.getId());
    }

    public static void despawnFestivalNpcs(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town
    ) {
        List<String> uuids = town.getActiveFestivalNpcEntityUuids();
        if (uuids.isEmpty()) {
            return;
        }
        for (String raw : uuids) {
            UUID id;
            try {
                id = UUID.fromString(raw.trim());
            } catch (IllegalArgumentException e) {
                continue;
            }
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(id);
            if (ref != null && ref.isValid()) {
                store.removeEntity(ref, RemoveReason.REMOVE);
            }
        }
        town.setActiveFestivalNpcEntityUuids(null);
    }
}
