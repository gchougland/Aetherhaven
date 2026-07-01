package com.hexvane.aetherhaven.inn;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
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
import org.joml.Vector3i;

/** Inn bell: remove pool visitors and respawn them at guest spawn points. */
public final class InnBellService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public enum RingOutcome {
        VISITORS_RETURNED,
        NO_VISITORS,
        NO_MORE_GUESTS;

        @Nonnull
        public String messageKeySuffix() {
            return switch (this) {
                case VISITORS_RETURNED -> "returned";
                case NO_MORE_GUESTS -> "noMoreGuests";
                case NO_VISITORS -> "noVisitors";
            };
        }
    }

    private record PoolSlotSnapshot(
        int slotIndex,
        @Nullable UUID oldUuid,
        @Nullable String roleId,
        @Nullable String villagerKind,
        boolean questLocked
    ) {}

    private InnBellService() {}

    /**
     * @return outcome for player feedback; assumes town, inn plot, and inn active were validated by caller
     */
    @Nonnull
    public static RingOutcome ring(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotInstance innPlot
    ) {
        ConstructionDefinition innDef = InnPlotResolver.resolveInnDefinition(plugin, innPlot);
        if (innDef == null) {
            return RingOutcome.NO_VISITORS;
        }

        InnPoolService.reconcileInnVisitorEntities(world, town, tm, store, true);
        InnPoolService.repairInnPoolForTown(world, plugin, town, tm, store, false);

        int actions = respawnAllPoolVisitorsAtSpawns(world, plugin, town, tm, store, innPlot, innDef);

        InnPoolService.repairInnPoolForTown(world, plugin, town, tm, store, false);
        tm.updateTown(town);

        if (actions > 0) {
            return RingOutcome.VISITORS_RETURNED;
        }
        if (town.getInnPoolNpcIds().size() < InnPoolService.MAX_VISITORS
            && !InnPoolService.hasEligibleInnPoolRoleForFill(town, plugin, store)) {
            return RingOutcome.NO_MORE_GUESTS;
        }
        return RingOutcome.NO_VISITORS;
    }

    public static void playRingSound(@Nonnull Store<EntityStore> store, @Nonnull Vector3i bellBlock) {
        InnBellSounds.playAt(store, bellBlock);
    }

    /**
     * Snapshots listed pool slots, removes every inn visitor for this town, then spawns fresh NPCs at guest spawn locals.
     * Preserves role and quest lock per slot when the entity was loaded; inferred when missing.
     */
    private static int respawnAllPoolVisitorsAtSpawns(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotInstance innPlot,
        @Nonnull ConstructionDefinition innDef
    ) {
        List<String> poolIds = new ArrayList<>(town.getInnPoolNpcIds());
        List<PoolSlotSnapshot> snapshots = new ArrayList<>();

        for (int slotIndex = 0; slotIndex < poolIds.size(); slotIndex++) {
            String sid = poolIds.get(slotIndex);
            UUID oldUuid = parseUuid(sid);
            if (oldUuid == null) {
                continue;
            }
            boolean questLocked = town.isInnVisitorLocked(oldUuid);
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(oldUuid);

            String roleId = null;
            String kind = null;
            if (ref != null && ref.isValid()) {
                TownVillagerBinding binding = store.getComponent(ref, TownVillagerBinding.getComponentType());
                NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                if (binding == null
                    || npc == null
                    || npc.getRoleName() == null
                    || npc.getRoleName().isBlank()
                    || !town.getTownId().equals(binding.getTownId())
                    || !TownVillagerBinding.isVisitorKind(binding.getKind())) {
                    town.removeInnLockedEntity(oldUuid);
                    continue;
                }
                roleId = npc.getRoleName().trim();
                kind = binding.getKind();
            }

            snapshots.add(new PoolSlotSnapshot(slotIndex, oldUuid, roleId, kind, questLocked));
            town.removeInnLockedEntity(oldUuid);
        }

        town.getInnPoolNpcIds().clear();
        tm.updateTown(town);
        InnPoolService.despawnAllTownInnVisitors(town, store);

        int respawned = 0;
        LinkedHashSet<String> rolesTaken = new LinkedHashSet<>();
        for (PoolSlotSnapshot snap : snapshots) {
            String roleId = snap.roleId();
            if (roleId != null && !InnPoolService.isVisitorRoleEligible(plugin, town, store, roleId)) {
                roleId = null;
            }
            if (roleId == null) {
                roleId = inferRoleForMissingVisitor(town, plugin, store, rolesTaken);
            }
            if (roleId == null) {
                LOGGER.atInfo().log(
                    "Inn bell: no role to respawn for pool slot %s in town %s",
                    snap.slotIndex(),
                    town.getTownId()
                );
                continue;
            }
            String kind =
                snap.villagerKind() != null
                    ? snap.villagerKind()
                    : InnPoolService.visitorBindingKindForRole(plugin, roleId);
            UUID spawned =
                InnPoolService.spawnInnVisitorAtSlot(
                    world, plugin, town, store, innPlot, innDef, roleId, kind, snap.slotIndex()
                );
            if (spawned == null) {
                continue;
            }
            insertPoolIdAtSlot(town, snap.slotIndex(), spawned.toString());
            if (snap.questLocked() || InnPoolService.innQuestLocksVisitorRole(town, roleId)) {
                town.addInnLockedEntity(spawned);
            }
            rolesTaken.add(roleId);
            respawned++;
            tm.updateTown(town);
        }
        return respawned;
    }

    private static void insertPoolIdAtSlot(@Nonnull TownRecord town, int slotIndex, @Nonnull String npcId) {
        List<String> ids = town.getInnPoolNpcIds();
        if (slotIndex <= ids.size()) {
            if (slotIndex == ids.size()) {
                ids.add(npcId);
            } else {
                ids.add(slotIndex, npcId);
            }
        }
    }

    @Nullable
    private static String inferRoleForMissingVisitor(
        @Nonnull TownRecord town,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull Set<String> rolesTaken
    ) {
        List<String> merged = InnPoolService.mergedVisitorRoleOrder(town, plugin, store);
        for (String candidate : merged) {
            if (rolesTaken.contains(candidate)) {
                continue;
            }
            if (!InnPoolService.isVisitorRoleEligible(plugin, town, store, candidate)) {
                continue;
            }
            rolesTaken.add(candidate);
            return candidate;
        }
        return null;
    }

    @Nullable
    private static UUID parseUuid(@Nullable String sid) {
        if (sid == null || sid.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(sid.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
