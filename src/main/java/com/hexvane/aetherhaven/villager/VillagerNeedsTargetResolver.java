package com.hexvane.aetherhaven.villager;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class VillagerNeedsTargetResolver {
    private VillagerNeedsTargetResolver() {}

    public enum ResolveProblem {
        OK,
        NOT_FOUND,
        AMBIGUOUS,
        NO_TOWN,
        NO_ELDER
    }

    public record Result(@Nullable UUID entityUuid, @Nonnull ResolveProblem problem) {}

    /**
     * @param token {@code Elder} = your town's saved elder entity; full UUID; or {@link AetherhavenVillagerHandle} string (case-insensitive).
     */
    @Nonnull
    public static Result resolve(
        @Nonnull String token,
        @Nonnull Store<EntityStore> playerStore,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> entityStore
    ) {
        String t = token.trim();
        if (t.isEmpty()) {
            return new Result(null, ResolveProblem.NOT_FOUND);
        }
        if (t.equalsIgnoreCase("Elder")) {
            UUIDComponent uc = playerStore.getComponent(playerRef, UUIDComponent.getComponentType());
            if (uc == null) {
                return new Result(null, ResolveProblem.NO_TOWN);
            }
            TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownForOwnerInWorld(uc.getUuid());
            if (town == null) {
                return new Result(null, ResolveProblem.NO_TOWN);
            }
            UUID elder = town.getElderEntityUuid();
            if (elder == null) {
                return new Result(null, ResolveProblem.NO_ELDER);
            }
            return new Result(elder, ResolveProblem.OK);
        }
        try {
            return new Result(UUID.fromString(t), ResolveProblem.OK);
        } catch (IllegalArgumentException ignored) {
            // fall through to handle match
        }
        UUID[] found = new UUID[1];
        int[] count = new int[1];
        entityStore.forEachChunk(
            Query.and(
                VillagerNeeds.getComponentType(),
                AetherhavenVillagerHandle.getComponentType(),
                UUIDComponent.getComponentType()
            ),
            (archetypeChunk, commandBuffer) -> {
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    AetherhavenVillagerHandle h = archetypeChunk.getComponent(i, AetherhavenVillagerHandle.getComponentType());
                    UUIDComponent id = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
                    if (h == null || id == null) {
                        continue;
                    }
                    if (h.getHandle().equalsIgnoreCase(t)) {
                        count[0]++;
                        found[0] = id.getUuid();
                    }
                }
            }
        );
        if (count[0] == 0) {
            return new Result(null, ResolveProblem.NOT_FOUND);
        }
        if (count[0] > 1) {
            return new Result(null, ResolveProblem.AMBIGUOUS);
        }
        return new Result(found[0], ResolveProblem.OK);
    }
}
