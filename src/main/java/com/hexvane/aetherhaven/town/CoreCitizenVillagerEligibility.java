package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.command.TownVillagerTargetResolver;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Whether a town villager is a core story citizen (eligible for single-citizen respawn). */
public final class CoreCitizenVillagerEligibility {
    public record CitizenProfile(@Nonnull String roleId, @Nonnull String kind) {}

    public record Outcome(@Nullable CitizenProfile profile, @Nullable String error) {
        public static Outcome ok(@Nonnull CitizenProfile profile) {
            return new Outcome(profile, null);
        }

        public static Outcome err(@Nonnull String error) {
            return new Outcome(null, error);
        }

        public boolean isOk() {
            return profile != null && error == null;
        }
    }

    private CoreCitizenVillagerEligibility() {}

    @Nonnull
    public static Outcome resolveCoreCitizen(
        @Nonnull TownRecord town,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID villagerEntityUuid
    ) {
        if (!TownVillagerTargetResolver.townReferencesVillager(town, villagerEntityUuid)) {
            return Outcome.err("That entity is not registered as a villager in this town.");
        }
        if (isInnPoolOrLocked(town, villagerEntityUuid)) {
            return Outcome.err("Inn visitors cannot be respawned with this command.");
        }
        if (isHiredGuard(town, villagerEntityUuid)) {
            return Outcome.err("Hired guards cannot be respawned with this command.");
        }
        CitizenProfile profile = resolveProfile(town, world, store, villagerEntityUuid);
        if (profile == null) {
            return Outcome.err("Could not resolve villager role for this town citizen.");
        }
        if (!ResidentRegistryService.isGaiaRevivalEligible(profile.kind(), profile.roleId())) {
            return Outcome.err("Only core story citizens can be respawned with this command.");
        }
        return Outcome.ok(profile);
    }

    private static boolean isInnPoolOrLocked(@Nonnull TownRecord town, @Nonnull UUID npcUuid) {
        String s = npcUuid.toString();
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
        return false;
    }

    private static boolean isHiredGuard(@Nonnull TownRecord town, @Nonnull UUID npcUuid) {
        for (HiredGuardRecord rec : town.getHiredGuardRecords()) {
            UUID guardUuid = rec.getEntityUuid();
            if (guardUuid != null && guardUuid.equals(npcUuid)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static CitizenProfile resolveProfile(
        @Nonnull TownRecord town,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID villagerEntityUuid
    ) {
        if (town.getElderEntityUuid() != null && town.getElderEntityUuid().equals(villagerEntityUuid)) {
            return new CitizenProfile(AetherhavenConstants.ELDER_NPC_ROLE_ID, TownVillagerBinding.KIND_ELDER);
        }
        if (town.getInnkeeperEntityUuid() != null && town.getInnkeeperEntityUuid().equals(villagerEntityUuid)) {
            return new CitizenProfile(AetherhavenConstants.INNKEEPER_NPC_ROLE_ID, TownVillagerBinding.KIND_INNKEEPER);
        }
        for (ResidentNpcRecord r : town.getResidentNpcRecords()) {
            if (villagerEntityUuid.equals(r.getLastEntityUuid())) {
                String roleId = r.getNpcRoleId();
                if (roleId == null || roleId.isBlank()) {
                    return null;
                }
                return new CitizenProfile(roleId.trim(), r.getKind());
            }
        }
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(villagerEntityUuid);
        if (ref != null && ref.isValid()) {
            TownVillagerBinding b = store.getComponent(ref, TownVillagerBinding.getComponentType());
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            if (b != null && npc != null && npc.getRoleName() != null && !npc.getRoleName().isBlank()) {
                if (town.getTownId().equals(b.getTownId())) {
                    return new CitizenProfile(npc.getRoleName().trim(), b.getKind());
                }
            }
        }
        String roleId = TownVillagerTargetResolver.roleIdForTownVillager(town, world, store, villagerEntityUuid);
        if (roleId == null || roleId.isBlank()) {
            return null;
        }
        return null;
    }
}
