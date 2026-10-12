package com.hexvane.aetherhaven.villager;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Town identity must exist before the engine can unload a newly added NPC into chunk storage. */
public final class TownVillagerSpawner {
    private TownVillagerSpawner() {}

    @Nullable
    public static Ref<EntityStore> spawn(
        @Nonnull Store<EntityStore> store, @Nonnull String roleId, @Nonnull Vector3d position,
        @Nonnull TownVillagerBinding binding, @Nonnull String source, @Nonnull String detail
    ) {
        var world = store.getExternalData().getWorld();
        // Do not create untracked holders or repeatedly refill slots outside the active simulation.
        if (!canSpawnAt(store, position)) {
            return null;
        }
        NPCPlugin npcs = NPCPlugin.get();
        if (npcs == null || !npcs.hasRoleName(roleId)) {
            return null;
        }
        var pair = npcs.spawnEntity(
            store, npcs.getIndex(roleId), position, Rotation3f.ZERO, null,
            (npc, holder, accessor) -> prepare(holder, binding, new AetherhavenNpcSpawnOrigin(
                source, detail, world.getName(), position.x, position.y, position.z, System.currentTimeMillis(), 0L
            )),
            null
        );
        return pair != null ? pair.first() : null;
    }

    /**
     * True when {@link #spawn} would be allowed to place a villager here: the chunk section is loaded and simulating.
     * Callers that remove a villager in order to replace them should ask first. Reads loaded sections only.
     */
    public static boolean canSpawnAt(@Nonnull Store<EntityStore> store, @Nonnull Vector3d position) {
        var chunks = store.getExternalData().getWorld().getChunkStore();
        var section = chunks.getChunkSectionReference(
            ChunkUtil.chunkCoordinate(position.x), ChunkUtil.chunkCoordinate(position.y), ChunkUtil.chunkCoordinate(position.z)
        );
        return section != null && section.isValid()
            && !chunks.getStore().getArchetype(section).contains(ChunkStore.REGISTRY.getNonTickingComponentType());
    }

    public static void prepare(
        @Nonnull Holder<EntityStore> holder, @Nonnull TownVillagerBinding binding,
        @Nonnull AetherhavenNpcSpawnOrigin origin
    ) {
        holder.putComponent(VillagerNeeds.getComponentType(), VillagerNeeds.full());
        holder.putComponent(AetherhavenVillagerHandle.getComponentType(), new AetherhavenVillagerHandle(handle(binding)));
        holder.putComponent(TownVillagerBinding.getComponentType(), binding);
        holder.putComponent(AetherhavenNpcSpawnOrigin.getComponentType(), origin);
    }

    @Nonnull
    public static String handle(@Nonnull TownVillagerBinding binding) {
        return "Villager_" + binding.getKind() + "_" + binding.getTownId().toString().replace("-", "").substring(0, 8);
    }
}
