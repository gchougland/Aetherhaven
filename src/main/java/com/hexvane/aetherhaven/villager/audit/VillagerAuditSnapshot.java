package com.hexvane.aetherhaven.villager.audit;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.ResidentLastKnownPosition;
import com.hexvane.aetherhaven.town.ResidentLastKnownPositionService;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownResidentDisplay;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Captured villager context for audit log lines. */
public final class VillagerAuditSnapshot {
    @Nonnull
    private final UUID entityUuid;
    @Nonnull
    private final String displayName;
    @Nonnull
    private final String roleId;
    @Nonnull
    private final String bindingKind;
    @Nonnull
    private final UUID townId;
    @Nonnull
    private final String townName;
    @Nonnull
    private final String worldName;
    @Nullable
    private final Double x;
    @Nullable
    private final Double y;
    @Nullable
    private final Double z;

    public VillagerAuditSnapshot(
        @Nonnull UUID entityUuid,
        @Nonnull String displayName,
        @Nonnull String roleId,
        @Nonnull String bindingKind,
        @Nonnull UUID townId,
        @Nonnull String townName,
        @Nonnull String worldName,
        @Nullable Double x,
        @Nullable Double y,
        @Nullable Double z
    ) {
        this.entityUuid = entityUuid;
        this.displayName = displayName;
        this.roleId = roleId;
        this.bindingKind = bindingKind;
        this.townId = townId;
        this.townName = townName;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Nonnull
    public UUID getEntityUuid() {
        return entityUuid;
    }

    @Nonnull
    public String getDisplayName() {
        return displayName;
    }

    @Nonnull
    public String getRoleId() {
        return roleId;
    }

    @Nonnull
    public String getBindingKind() {
        return bindingKind;
    }

    @Nonnull
    public UUID getTownId() {
        return townId;
    }

    @Nonnull
    public String getTownName() {
        return townName;
    }

    @Nonnull
    public String getWorldName() {
        return worldName;
    }

    @Nullable
    public Double getX() {
        return x;
    }

    @Nullable
    public Double getY() {
        return y;
    }

    @Nullable
    public Double getZ() {
        return z;
    }

    @Nonnull
    public static VillagerAuditSnapshot fromEntity(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull AetherhavenPlugin plugin
    ) {
        World world = store.getExternalData().getWorld();
        TownVillagerBinding binding = store.getComponent(ref, TownVillagerBinding.getComponentType());
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        UUID entityUuid = uc != null ? uc.getUuid() : new UUID(0L, 0L);
        String roleId = npc != null && npc.getRoleName() != null ? npc.getRoleName().trim() : "";
        String kind = binding != null ? binding.getKind() : "";
        UUID townId = binding != null ? binding.getTownId() : new UUID(0L, 0L);
        String displayName = TownResidentDisplay.resolveFromEntity(store, ref, roleId, plugin).displayName();
        var tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        String townName = town != null ? town.getDisplayName() : "";
        Position pos = resolvePosition(store, ref, town);
        return new VillagerAuditSnapshot(
            entityUuid,
            displayName,
            roleId,
            kind,
            townId,
            townName,
            world.getName(),
            pos.x,
            pos.y,
            pos.z
        );
    }

    @Nonnull
    public static VillagerAuditSnapshot fromTrackedUuid(
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID entityUuid,
        @Nonnull UUID townId,
        @Nullable String roleId,
        @Nullable String bindingKind,
        @Nullable String displayNameHint
    ) {
        var tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        String townName = town != null ? town.getDisplayName() : "";
        String role = roleId != null ? roleId : "";
        String kind = bindingKind != null ? bindingKind : "";
        String displayName = displayNameHint != null && !displayNameHint.isBlank() ? displayNameHint : role;
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(entityUuid);
        Position pos;
        if (ref != null && ref.isValid()) {
            pos = resolvePosition(store, ref, town);
        } else if (town != null) {
            pos = lastKnownPosition(town, entityUuid);
        } else {
            pos = Position.unknown();
        }
        return new VillagerAuditSnapshot(
            entityUuid,
            displayName,
            role,
            kind,
            townId,
            townName,
            world.getName(),
            pos.x,
            pos.y,
            pos.z
        );
    }

    @Nonnull
    private static Position resolvePosition(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nullable TownRecord town
    ) {
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc != null) {
            Vector3d p = tc.getPosition();
            return new Position(p.x, p.y, p.z);
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (town != null && uc != null) {
            return lastKnownPosition(town, uc.getUuid());
        }
        return Position.unknown();
    }

    @Nonnull
    private static Position lastKnownPosition(@Nonnull TownRecord town, @Nonnull UUID entityUuid) {
        ResidentLastKnownPosition saved = ResidentLastKnownPositionService.find(town, entityUuid);
        if (saved != null) {
            return new Position(saved.getX(), saved.getY(), saved.getZ());
        }
        return Position.unknown();
    }

    private record Position(@Nullable Double x, @Nullable Double y, @Nullable Double z) {
        @Nonnull
        static Position unknown() {
            return new Position(null, null, null);
        }
    }
}
