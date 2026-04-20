package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Resolves a villager command target as either an entity UUID or an NPC role id (e.g. {@code Aetherhaven_Blacksmith})
 * within the player's town.
 */
public final class TownVillagerTargetResolver {
    private TownVillagerTargetResolver() {}

    /** Result of resolving {@link #resolve}; either a villager entity UUID or an error message for chat. */
    public static final class Outcome {
        private final @Nullable UUID villagerUuid;
        private final @Nullable String error;

        private Outcome(@Nullable UUID villagerUuid, @Nullable String error) {
            this.villagerUuid = villagerUuid;
            this.error = error;
        }

        public static Outcome ok(@Nonnull UUID villagerUuid) {
            return new Outcome(villagerUuid, null);
        }

        public static Outcome err(@Nonnull String error) {
            return new Outcome(null, error);
        }

        public boolean isOk() {
            return villagerUuid != null && error == null;
        }

        @Nonnull
        public UUID villagerUuidOrThrow() {
            if (villagerUuid == null) {
                throw new IllegalStateException(error != null ? error : "no uuid");
            }
            return villagerUuid;
        }

        @Nullable
        public String error() {
            return error;
        }

        @Nullable
        public UUID villagerUuid() {
            return villagerUuid;
        }
    }

    /** @return true if this UUID is one of the town's registered villager NPCs. */
    public static boolean townReferencesVillager(@Nonnull TownRecord town, @Nonnull UUID npcUuid) {
        String s = npcUuid.toString();
        if (town.getElderEntityUuid() != null && town.getElderEntityUuid().equals(npcUuid)) {
            return true;
        }
        if (town.getInnkeeperEntityUuid() != null && town.getInnkeeperEntityUuid().equals(npcUuid)) {
            return true;
        }
        for (String pool : town.getInnPoolNpcIds()) {
            if (s.equalsIgnoreCase(pool)) {
                return true;
            }
        }
        for (String lock : town.getInnLockedEntityUuids()) {
            if (s.equalsIgnoreCase(lock)) {
                return true;
            }
        }
        for (ResidentNpcRecord r : town.getResidentNpcRecords()) {
            if (npcUuid.equals(r.getLastEntityUuid())) {
                return true;
            }
        }
        for (PlotInstance p : town.getPlotInstances()) {
            UUID h = p.getHomeResidentEntityUuid();
            if (h != null && h.equals(npcUuid)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@link #roleIdForTownVillager(TownRecord, World, Store, UUID)} without requiring the UUID to be pre-listed
     * (for inn visitors we only know role when the entity is loaded).
     */
    @Nonnull
    public static Set<UUID> distinctVillagerEntityUuids(@Nonnull TownRecord town) {
        LinkedHashSet<UUID> out = new LinkedHashSet<>();
        if (town.getElderEntityUuid() != null) {
            out.add(town.getElderEntityUuid());
        }
        if (town.getInnkeeperEntityUuid() != null) {
            out.add(town.getInnkeeperEntityUuid());
        }
        for (String poolId : town.getInnPoolNpcIds()) {
            UUID u = parseUuidString(poolId);
            if (u != null) {
                out.add(u);
            }
        }
        for (String lockedId : town.getInnLockedEntityUuids()) {
            UUID u = parseUuidString(lockedId);
            if (u != null) {
                out.add(u);
            }
        }
        for (ResidentNpcRecord r : town.getResidentNpcRecords()) {
            UUID u = r.getLastEntityUuid();
            if (u.getLeastSignificantBits() != 0L || u.getMostSignificantBits() != 0L) {
                out.add(u);
            }
        }
        for (PlotInstance pi : town.getPlotInstances()) {
            UUID h = pi.getHomeResidentEntityUuid();
            if (h != null) {
                out.add(h);
            }
        }
        return out;
    }

    /**
     * Role id used for reputation / commands: Elder and Innkeeper from constants; residents from save; otherwise
     * {@link NPCEntity#getRoleName()} when the entity is loaded.
     */
    @Nullable
    public static String roleIdForTownVillager(
        @Nonnull TownRecord town,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID villagerEntityUuid
    ) {
        if (town.getElderEntityUuid() != null && town.getElderEntityUuid().equals(villagerEntityUuid)) {
            return AetherhavenConstants.ELDER_NPC_ROLE_ID;
        }
        if (town.getInnkeeperEntityUuid() != null && town.getInnkeeperEntityUuid().equals(villagerEntityUuid)) {
            return AetherhavenConstants.INNKEEPER_NPC_ROLE_ID;
        }
        for (ResidentNpcRecord r : town.getResidentNpcRecords()) {
            if (villagerEntityUuid.equals(r.getLastEntityUuid())) {
                String role = r.getNpcRoleId();
                return role != null && !role.isBlank() ? role.trim() : null;
            }
        }
        var es = world.getEntityStore();
        if (es == null) {
            return null;
        }
        Ref<EntityStore> npcRef = store.getExternalData().getRefFromUUID(villagerEntityUuid);
        if (npcRef == null || !npcRef.isValid()) {
            return null;
        }
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        return npc != null && npc.getRoleName() != null && !npc.getRoleName().isBlank()
            ? npc.getRoleName().trim()
            : null;
    }

    @Nonnull
    public static Outcome resolve(
        @Nonnull TownRecord town,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull String rawInput
    ) {
        String trimmed = rawInput.trim();
        if (trimmed.isEmpty()) {
            return Outcome.err("Missing villager: use an entity UUID or NPC role id (e.g. Aetherhaven_Blacksmith).");
        }
        try {
            UUID asUuid = UUID.fromString(trimmed);
            if (!townReferencesVillager(town, asUuid)) {
                return Outcome.err("That entity is not registered as a villager in your town.");
            }
            return Outcome.ok(asUuid);
        } catch (IllegalArgumentException ignored) {
            // Not a UUID: treat as role id
        }
        String wanted = trimmed;
        List<UUID> matches = new ArrayList<>();
        for (UUID id : distinctVillagerEntityUuids(town)) {
            String roleId = roleIdForTownVillager(town, world, store, id);
            if (roleId != null && roleId.equals(wanted)) {
                matches.add(id);
            }
        }
        if (matches.isEmpty()) {
            return Outcome.err(
                "No villager in your town with role id \""
                    + wanted
                    + "\". Use /aetherhaven villager list, or ensure the NPC is loaded if they are a visitor."
            );
        }
        if (matches.size() > 1) {
            return Outcome.err(
                "Multiple villagers have role \""
                    + wanted
                    + "\". Use a villager entity UUID from /aetherhaven villager list instead."
            );
        }
        return Outcome.ok(matches.get(0));
    }

    @Nullable
    private static UUID parseUuidString(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
