package com.hexvane.aetherhaven.townsfolk;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.guild.GuildHallDisplayAnchor;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterCatalog;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterDefinition;
import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hexvane.aetherhaven.villager.AetherhavenVillagerHandle;
import com.hexvane.aetherhaven.villager.NpcModelSpawnUtil;
import com.hexvane.aetherhaven.villager.NpcSpawnOriginUtil;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

public final class TownsfolkSpawnService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private TownsfolkSpawnService() {}

    public record SpawnedTownsfolk(
        @Nonnull String characterId,
        @Nonnull UUID entityUuid,
        @Nonnull List<String> personalityIds,
        @Nonnull String assignmentKind
    ) {}

    @Nonnull
    public static List<String> availableCharacterIds(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID townId,
        @Nonnull String assignmentKind
    ) {
        TownsfolkPoolState pool = TownsfolkPoolPersistence.getOrLoad(world, plugin);
        return pool.availableCharacterIds(townId, plugin.getTownsfolkCharacterCatalog(), assignmentKind);
    }

    @Nonnull
    public static Optional<SpawnedTownsfolk> trySpawn(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3d position,
        @Nonnull String assignmentKind,
        @Nullable String preferredCharacterId,
        @Nonnull Random random
    ) {
        return trySpawn(
            world,
            plugin,
            town,
            store,
            position,
            assignmentKind,
            preferredCharacterId,
            random,
            new Rotation3f(0.0F, 0.0F, 0.0F),
            null,
            null
        );
    }

    @Nonnull
    public static Optional<SpawnedTownsfolk> trySpawn(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3d position,
        @Nonnull String assignmentKind,
        @Nullable String preferredCharacterId,
        @Nonnull Random random,
        @Nonnull Rotation3f rotation,
        @Nullable Float displayAnchorYawRadians,
        @Nullable Vector3d guildHallSpawnMarkerPosition
    ) {
        String kind = assignmentKind.trim().toLowerCase();
        TownsfolkCharacterCatalog catalog = plugin.getTownsfolkCharacterCatalog();
        TownsfolkPoolState pool = TownsfolkPoolPersistence.getOrLoad(world, plugin);
        UUID townId = town.getTownId();

        String characterId;
        if (preferredCharacterId != null && !preferredCharacterId.isBlank()) {
            characterId = preferredCharacterId.trim();
            TownsfolkCharacterDefinition def = catalog.byId(characterId);
            if (def == null) {
                LOGGER.atWarning().log("Unknown townsfolk character id %s", characterId);
                return Optional.empty();
            }
            if (!TownsfolkCharacterAvailability.isEligibleForPoolDraw(def)) {
                LOGGER.atWarning().log(
                    "Townsfolk %s is not eligible (optional plugin requirement not satisfied)",
                    characterId
                );
                return Optional.empty();
            }
            if (pool.isCheckedOut(townId, characterId)) {
                LOGGER.atWarning().log("Townsfolk %s already checked out in town %s", characterId, townId);
                return Optional.empty();
            }
            if (TownsfolkAssignmentKinds.isGuildHallAdventurer(kind)) {
                if (!def.supportsAssignment(TownsfolkAssignmentKinds.GUARD)) {
                    LOGGER.atWarning().log("Townsfolk %s is not guard eligible", characterId);
                    return Optional.empty();
                }
            } else if (!def.supportsAssignment(kind)) {
                LOGGER.atWarning().log("Townsfolk %s does not support assignment %s", characterId, kind);
                return Optional.empty();
            }
        } else if (TownsfolkAssignmentKinds.isGuildHallAdventurer(kind)) {
            characterId = pool.pickRandomGuardEligibleCharacterId(townId, catalog, random);
            if (characterId == null) {
                LOGGER.atFine().log("No guard eligible townsfolk for guild hall in town %s", townId);
                return Optional.empty();
            }
        } else {
            characterId = pool.pickRandomAvailableCharacterId(townId, catalog, kind, random);
            if (characterId == null) {
                LOGGER.atFine().log("No available townsfolk for assignment %s in town %s", kind, townId);
                return Optional.empty();
            }
        }

        TownsfolkCharacterDefinition character = catalog.byId(characterId);
        if (character == null) {
            return Optional.empty();
        }
        return spawnTownsfolkEntity(
            world,
            plugin,
            town,
            store,
            position,
            kind,
            characterId,
            character,
            random,
            rotation,
            displayAnchorYawRadians,
            guildHallSpawnMarkerPosition
        );
    }

    @Nonnull
    private static Optional<SpawnedTownsfolk> spawnTownsfolkEntity(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3d position,
        @Nonnull String kind,
        @Nonnull String characterId,
        @Nonnull TownsfolkCharacterDefinition character,
        @Nonnull Random random,
        @Nonnull Rotation3f rotation,
        @Nullable Float displayAnchorYawRadians,
        @Nullable Vector3d guildHallSpawnMarkerPosition
    ) {
        List<String> personalities = character.getPersonalityIds();

        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) {
            return Optional.empty();
        }
        int roleIndex = npcPlugin.getIndex(AetherhavenConstants.NPC_TOWNSFOLK);
        if (roleIndex < 0) {
            LOGGER.atWarning().log("Townsfolk NPC role not registered: %s", AetherhavenConstants.NPC_TOWNSFOLK);
            return Optional.empty();
        }
        Builder<Role> roleBuilder = npcPlugin.tryGetCachedValidRole(roleIndex);
        if (roleBuilder == null) {
            LOGGER.atWarning().log(
                "Townsfolk role %s (index %s) is missing or failed NPC validation; cannot spawn %s",
                AetherhavenConstants.NPC_TOWNSFOLK,
                roleIndex,
                characterId
            );
            return Optional.empty();
        }
        Model spawnModel = NpcModelSpawnUtil.buildScaledModel(character.getModelAssetId(), character.getModelScale());
        if (spawnModel == null) {
            LOGGER.atWarning().log("Unknown townsfolk model asset %s for character %s", character.getModelAssetId(), characterId);
            return Optional.empty();
        }
        float spawnScale = spawnModel.getScale();
        Vector3d spawnPos = VillagerBlockUtil.snapNpcFeetToStand(world, position);
        var pair = npcPlugin.spawnEntity(
            store,
            roleIndex,
            spawnPos,
            rotation,
            spawnModel,
            (npcEntity, holder, st) -> npcEntity.setInitialModelScale(spawnScale),
            null
        );
        if (pair == null) {
            LOGGER.atWarning().log("Failed to spawn townsfolk NPC %s for town %s", characterId, town.getTownId());
            return Optional.empty();
        }
        Ref<EntityStore> ref = pair.first();

        String displayName = character.getDisplayName();
        if (displayName != null) {
            store.putComponent(ref, PersistentDisplayName.getComponentType(), new PersistentDisplayName(Message.raw(displayName)));
        }

        String handle = "Townsfolk_" + characterId + "_" + shortHex(town.getTownId());
        store.putComponent(ref, AetherhavenVillagerHandle.getComponentType(), new AetherhavenVillagerHandle(handle));
        store.putComponent(
            ref,
            TownVillagerBinding.getComponentType(),
            new TownVillagerBinding(town.getTownId(), TownVillagerBinding.KIND_TOWNSFOLK, null)
        );
        store.putComponent(
            ref,
            TownsfolkCharacterBinding.getComponentType(),
            new TownsfolkCharacterBinding(
                characterId,
                "",
                kind,
                character.getModelAssetId(),
                personalities
            )
        );
        com.hexvane.aetherhaven.villagercosmetic.VillagerCosmeticAppearanceService.applySavedCosmetics(ref, store, town);
        NpcSpawnOriginUtil.attach(
            store,
            ref,
            "TOWNSFOLK_POOL",
            "assignmentKind=" + kind + ",characterId=" + characterId,
            world,
            spawnPos
        );

        if (TownsfolkAssignmentKinds.isGuildHallAdventurer(kind)) {
            float anchorYaw = displayAnchorYawRadians != null ? displayAnchorYawRadians : rotation.yaw();
            Vector3d markerPos = guildHallSpawnMarkerPosition != null ? guildHallSpawnMarkerPosition : position;
            GuildHallDisplayAnchor displayAnchor = new GuildHallDisplayAnchor(position, anchorYaw);
            displayAnchor.setSpawnMarkerPosition(markerPos);
            store.putComponent(ref, GuildHallDisplayAnchor.getComponentType(), displayAnchor);
        }

        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComp == null) {
            return Optional.empty();
        }
        UUID entityUuid = uuidComp.getUuid();
        TownsfolkExistenceService.registerSpawn(
            world,
            plugin,
            new TownsfolkPoolCheckoutRecord(
                characterId,
                town.getTownId().toString(),
                entityUuid.toString(),
                kind,
                ""
            )
        );
        return Optional.of(new SpawnedTownsfolk(characterId, entityUuid, personalities, kind));
    }

    /**
     * Respawns a checked-out townsfolk pool character (tourist citizen, etc.) while preserving character identity and
     * pool ledger ownership.
     *
     * @return new entity uuid, or null on failure
     */
    @Nullable
    public static UUID respawnPoolCharacterAtPosition(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull String characterId,
        @Nonnull String assignmentKind,
        @Nonnull TownsfolkCharacterBinding characterBinding,
        @Nonnull Vector3d spawnPos,
        @Nonnull String spawnSource,
        @Nonnull String spawnDetail
    ) {
        String cid = characterId.trim();
        if (cid.isEmpty()) {
            return null;
        }
        TownsfolkCharacterDefinition character = plugin.getTownsfolkCharacterCatalog().byId(cid);
        if (character == null) {
            LOGGER.atWarning().log("Cannot respawn townsfolk %s: unknown character id", cid);
            return null;
        }
        String kind = assignmentKind.trim().toLowerCase();
        Optional<SpawnedTownsfolk> spawned =
            spawnTownsfolkEntity(
                world,
                plugin,
                town,
                store,
                spawnPos,
                kind,
                cid,
                character,
                new Random(),
                new Rotation3f(),
                null,
                null
            );
        if (spawned.isEmpty()) {
            return null;
        }
        UUID newUuid = spawned.get().entityUuid();
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(newUuid);
        if (ref != null && ref.isValid()) {
            NpcSpawnOriginUtil.attach(store, ref, spawnSource, spawnDetail, world, spawnPos);
            String activePersonality = characterBinding.getActivePersonalityId();
            String modelAssetId = characterBinding.getModelAssetId();
            if (modelAssetId == null || modelAssetId.isBlank()) {
                modelAssetId = character.getModelAssetId();
            }
            List<String> personalities =
                characterBinding.getPersonalityIds().isEmpty()
                    ? character.getPersonalityIds()
                    : characterBinding.getPersonalityIds();
            store.putComponent(
                ref,
                TownsfolkCharacterBinding.getComponentType(),
                new TownsfolkCharacterBinding(cid, activePersonality != null ? activePersonality : "", kind, modelAssetId, personalities)
            );
        }
        if (!TownsfolkExistenceService.transferInstanceOnHire(world, plugin, cid, newUuid, town.getTownId())) {
            LOGGER.atFine().log("Respawned townsfolk %s without townsfolk ledger checkout update", cid);
        }
        TownsfolkExistenceService.purgeDuplicateEntities(world, store, town.getTownId(), cid, newUuid);
        return newUuid;
    }

    public static void release(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID townId,
        @Nonnull String characterId
    ) {
        TownsfolkExistenceService.releaseCharacter(
            world,
            plugin,
            townId,
            characterId,
            TownsfolkExistenceService.ReleaseReason.DESPAWN
        );
    }

    public static void releaseByEntity(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull UUID entityUuid) {
        TownsfolkExistenceService.releaseByEntity(world, plugin, entityUuid);
    }

    /**
     * Despawns every townsfolk NPC in this world and clears all pool checkouts.
     *
     * @return number of entities removed from the world
     */
    public static int clearPoolAndDespawnAll(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        List<Ref<EntityStore>> refs = new ArrayList<>();
        store.forEachChunk(
            Query.and(TownsfolkCharacterBinding.getComponentType(), NPCEntity.getComponentType()),
            (archetypeChunk, commandBuffer) -> {
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    Ref<EntityStore> ref = archetypeChunk.getReferenceTo(i);
                    if (ref != null && ref.isValid()) {
                        refs.add(ref);
                    }
                }
            }
        );
        int despawned = 0;
        for (Ref<EntityStore> ref : refs) {
            if (ref.isValid()) {
                store.removeEntity(ref, RemoveReason.REMOVE);
                despawned++;
            }
        }
        TownsfolkPoolState pool = TownsfolkPoolPersistence.getOrLoad(world, plugin);
        int checkouts = pool.clearAllCheckouts();
        TownsfolkPoolPersistence.save(world, plugin, pool);
        LOGGER.atInfo().log(
            "Cleared townsfolk pool in world %s: despawned %s entities, released %s checkouts",
            world.getName(),
            despawned,
            checkouts
        );
        return despawned;
    }

    public static void reconcileAfterWorldLoad(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        TownsfolkExistenceService.reconcileAfterWorldLoad(world, plugin);
    }

    @Nonnull
    private static String shortHex(@Nonnull UUID townId) {
        String hex = townId.toString().replace("-", "");
        return hex.length() >= 8 ? hex.substring(0, 8) : hex;
    }

    public static void applyCharacterAppearance(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull String modelAssetId,
        @Nullable Float modelScale
    ) {
        if (modelScale != null) {
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            if (npc != null) {
                npc.setInitialModelScale(modelScale);
            }
        }
        if (!NPCEntity.setAppearance(ref, modelAssetId, store)) {
            return;
        }
        var world = store.getExternalData().getWorld();
        TownVillagerBinding binding = store.getComponent(ref, TownVillagerBinding.getComponentType());
        if (world == null) {
            return;
        }
        UUID townId = binding != null ? binding.getTownId() : null;
        world.execute(
            () -> {
                if (!ref.isValid()) {
                    return;
                }
                NpcModelSpawnUtil.resyncFromPersistentModel(ref, store);
                if (townId == null) {
                    return;
                }
                AetherhavenPlugin plugin = AetherhavenPlugin.get();
                if (plugin == null) {
                    return;
                }
                TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
                TownRecord town = tm.getTown(townId);
                if (town != null) {
                    com.hexvane.aetherhaven.villagercosmetic.VillagerCosmeticAppearanceService.applySavedCosmetics(
                        ref,
                        store,
                        town
                    );
                }
            }
        );
    }
}

