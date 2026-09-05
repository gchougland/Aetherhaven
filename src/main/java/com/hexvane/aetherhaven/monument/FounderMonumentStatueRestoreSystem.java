package com.hexvane.aetherhaven.monument;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plot.FounderMonumentBlock;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * After the world is ready, respawns founder statues the same way placing a new monument does.
 */
public final class FounderMonumentStatueRestoreSystem {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int CLUSTER_XZ = 3;
    private static final int CLUSTER_Y = 2;
    private static final double NEARBY_STATUE_RANGE = 3.5;

    private FounderMonumentStatueRestoreSystem() {}

    public static void restorePendingAfterBoot() {
        if (AetherhavenPlugin.get() == null) {
            return;
        }
        for (World world : Universe.get().getWorlds().values()) {
            requestScan(world);
        }
    }

    public static void scanLoadedPedestals(@Nonnull World world) {
        requestScan(world);
    }

    private static void requestScan(@Nonnull World world) {
        boolean booted = HytaleServer.get().isBooted();
        LOGGER.atInfo().log("Founder monument: queue scan for %s booted=%s", world.getName(), booted);
        world.execute(() -> {
            if (!HytaleServer.get().isBooted()) {
                LOGGER.atInfo().log("Founder monument: skip scan, world %s is not booted yet", world.getName());
                return;
            }
            restoreLoadedMonuments(world);
        });
    }

    private static void restoreLoadedMonuments(@Nonnull World world) {
        try {
            List<Pedestal> pedestals = collectPedestals(world);
            if (pedestals.isEmpty()) {
                LOGGER.atInfo().log("Founder monument: no monument blocks loaded in %s yet", world.getName());
                return;
            }
            List<List<Pedestal>> groups = cluster(pedestals);
            LOGGER.atInfo().log(
                "Founder monument: found %s monument block(s) in %s group(s) in %s",
                pedestals.size(),
                groups.size(),
                world.getName()
            );
            for (List<Pedestal> group : groups) {
                spawnGroup(world, group);
            }
        } catch (RuntimeException e) {
            LOGGER.atWarning().withCause(e).log("Founder monument: scan failed in %s", world.getName());
        }
    }

    @Nonnull
    private static List<Pedestal> collectPedestals(@Nonnull World world) {
        List<Pedestal> pedestals = new ArrayList<>();
        Store<ChunkStore> chunks = world.getChunkStore().getStore();
        Query<ChunkStore> query =
            Query.and(FounderMonumentBlock.getComponentType(), BlockModule.BlockStateInfo.getComponentType());
        chunks.forEachChunk(
            query,
            (archetypeChunk, commandBuffer) -> {
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    Ref<ChunkStore> ref = archetypeChunk.getReferenceTo(i);
                    if (ref == null || !ref.isValid()) {
                        continue;
                    }
                    FounderMonumentBlock block = commandBuffer.getComponent(ref, FounderMonumentBlock.getComponentType());
                    BlockModule.BlockStateInfo state =
                        commandBuffer.getComponent(ref, BlockModule.BlockStateInfo.getComponentType());
                    if (block == null || state == null) {
                        continue;
                    }
                    Vector3i pos = new Vector3i();
                    if (!state.fillWorldPos(chunks, pos)) {
                        continue;
                    }
                    pedestals.add(Pedestal.from(pos, block));
                }
            }
        );
        return pedestals;
    }

    @Nonnull
    private static List<List<Pedestal>> cluster(@Nonnull List<Pedestal> pedestals) {
        List<List<Pedestal>> groups = new ArrayList<>();
        boolean[] used = new boolean[pedestals.size()];
        for (int i = 0; i < pedestals.size(); i++) {
            if (used[i]) {
                continue;
            }
            List<Pedestal> group = new ArrayList<>();
            group.add(pedestals.get(i));
            used[i] = true;
            boolean grew = true;
            while (grew) {
                grew = false;
                for (int j = 0; j < pedestals.size(); j++) {
                    if (used[j]) {
                        continue;
                    }
                    Pedestal candidate = pedestals.get(j);
                    for (Pedestal member : group) {
                        if (member.inCluster(candidate)) {
                            group.add(candidate);
                            used[j] = true;
                            grew = true;
                            break;
                        }
                    }
                }
            }
            groups.add(group);
        }
        return groups;
    }

    private static void spawnGroup(@Nonnull World world, @Nonnull List<Pedestal> group) {
        Pedestal best = richest(group);
        Store<EntityStore> store = world.getEntityStore().getStore();
        List<LivingStatue> nearby = findNearbyStatues(store, best);
        LOGGER.atInfo().log(
            "Founder monument: group at %s %s %s town=%s uuid=%s skinChars=%s label=%s yaw=%s nearbyTagged=%s",
            best.x(),
            best.y(),
            best.z(),
            blankOr(best.townId()),
            blankOr(best.statueUuid()),
            best.skinJson().length(),
            blankOr(best.label()),
            best.yaw(),
            nearby.size()
        );
        for (Pedestal member : group) {
            LOGGER.atInfo().log(
                "Founder monument:   pillar %s %s %s town=%s uuid=%s skinChars=%s yaw=%s score=%s",
                member.x(),
                member.y(),
                member.z(),
                blankOr(member.townId()),
                blankOr(member.statueUuid()),
                member.skinJson().length(),
                member.yaw(),
                member.score()
            );
        }
        LivingStatue richestNearby = null;
        for (LivingStatue candidate : nearby) {
            LOGGER.atInfo().log(
                "Founder monument:   tagged leftover %s at %s %s %s network=%s model=%s persistent=%s skinChars=%s cosmetics=%s visible=%s",
                candidate.id(),
                format(candidate.x()),
                format(candidate.y()),
                format(candidate.z()),
                candidate.hasNetwork(),
                candidate.modelName(),
                candidate.persistentModel(),
                candidate.skinJson().length(),
                candidate.hasCosmetics(),
                candidate.visible()
            );
            if (richestNearby == null || candidate.skinJson().length() > richestNearby.skinJson().length()) {
                richestNearby = candidate;
            }
        }
        String skinJson = firstNonBlank(best.skinJson(), richestNearby != null ? richestNearby.skinJson() : "");
        String label = firstNonBlank(best.label(), richestNearby != null ? richestNearby.label() : "");
        Rotation3f rotation =
            mergeRotation(
                best.rotation(),
                richestNearby != null ? richestNearby.rotation() : new Rotation3f(0f, (float) Math.PI, 0f)
            );
        if (isIdentityRotation(rotation)) {
            rotation = new Rotation3f(0f, (float) Math.PI, 0f);
        }
        PlayerSkin skin = FounderMonumentStatueSkin.tryParseSkinJson(skinJson);
        int removed = removeNearbyStatues(store, best, null);
        LOGGER.atInfo().log("Founder monument: removed %s leftover tagged entit%s before spawn", removed, removed == 1 ? "y" : "ies");
        UUID spawned;
        try {
            spawned =
                FounderMonumentSpawnService.spawnStatueLikePlace(
                    world,
                    store,
                    best.x(),
                    best.y(),
                    best.z(),
                    skin,
                    label,
                    rotation
                );
        } catch (RuntimeException e) {
            LOGGER.atWarning().withCause(e).log(
                "Founder monument: place-style spawn crashed at %s %s %s",
                best.x(),
                best.y(),
                best.z()
            );
            return;
        }
        if (spawned == null) {
            LOGGER.atWarning().log(
                "Founder monument: place-style spawn failed at %s %s %s skinChars=%s",
                best.x(),
                best.y(),
                best.z(),
                skinJson.length()
            );
            return;
        }
        persist(world, best, spawned, skinJson, label, rotation);
        LOGGER.atInfo().log(
            "Founder monument: spawned statue %s at %s %s %s skinChars=%s yaw=%s",
            spawned,
            best.x(),
            best.y(),
            best.z(),
            skinJson.length(),
            rotation.yaw()
        );
    }

    @Nonnull
    private static Pedestal richest(@Nonnull List<Pedestal> group) {
        Pedestal best = group.get(0);
        int bestScore = best.score();
        for (int i = 1; i < group.size(); i++) {
            Pedestal candidate = group.get(i);
            int score = candidate.score();
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    @Nonnull
    private static List<LivingStatue> findNearbyStatues(@Nonnull Store<EntityStore> store, @Nonnull Pedestal pedestal) {
        List<LivingStatue> found = new ArrayList<>();
        Query<EntityStore> query =
            Query.and(
                FounderMonumentStatueSkin.getComponentType(),
                TransformComponent.getComponentType(),
                UUIDComponent.getComponentType()
            );
        store.forEachChunk(
            query,
            (archetypeChunk, commandBuffer) -> {
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    Ref<EntityStore> ref = archetypeChunk.getReferenceTo(i);
                    if (ref == null || !ref.isValid()) {
                        continue;
                    }
                    TransformComponent transform =
                        commandBuffer.getComponent(ref, TransformComponent.getComponentType());
                    UUIDComponent uuid = commandBuffer.getComponent(ref, UUIDComponent.getComponentType());
                    if (transform == null
                        || uuid == null
                        || !pedestal.nearStatue(
                            transform.getPosition().x,
                            transform.getPosition().y,
                            transform.getPosition().z
                        )) {
                        continue;
                    }
                    found.add(describeStatue(store, ref, uuid, transform));
                }
            }
        );
        return found;
    }

    @Nonnull
    private static LivingStatue describeStatue(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UUIDComponent uuid,
        @Nonnull TransformComponent transform
    ) {
        FounderMonumentStatueSkin skin = store.getComponent(ref, FounderMonumentStatueSkin.getComponentType());
        HeadRotation head = store.getComponent(ref, HeadRotation.getComponentType());
        PersistentDisplayName displayName = store.getComponent(ref, PersistentDisplayName.getComponentType());
        ModelComponent modelComponent = store.getComponent(ref, ModelComponent.getComponentType());
        PersistentModel persistentModel = store.getComponent(ref, PersistentModel.getComponentType());
        NetworkId networkId = store.getComponent(ref, NetworkId.getComponentType());
        Rotation3f rotation = new Rotation3f(transform.getRotation());
        if (isIdentityRotation(rotation) && head != null) {
            rotation = new Rotation3f(head.getRotation());
        }
        String label = "";
        if (displayName != null && displayName.getDisplayName() != null) {
            String raw = displayName.getDisplayName().getRawText();
            label = raw != null ? raw.trim() : "";
        }
        Model model = modelComponent != null ? modelComponent.getModel() : null;
        return new LivingStatue(
            ref,
            uuid.getUuid(),
            transform.getPosition().x,
            transform.getPosition().y,
            transform.getPosition().z,
            skin != null ? skin.getSkinJson() : "",
            label,
            rotation,
            networkId != null,
            modelName(model),
            persistentModel != null ? persistentModel.getModelReference().getModelAssetId() : "none",
            hasAppliedStatueCosmetics(modelComponent),
            isClientVisibleStatue(modelComponent, networkId)
        );
    }

    private static int removeNearbyStatues(
        @Nonnull Store<EntityStore> store,
        @Nonnull Pedestal pedestal,
        @Nullable UUID keep
    ) {
        List<Ref<EntityStore>> remove = new ArrayList<>();
        Query<EntityStore> query =
            Query.and(FounderMonumentStatueSkin.getComponentType(), TransformComponent.getComponentType());
        store.forEachChunk(
            query,
            (archetypeChunk, commandBuffer) -> {
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    Ref<EntityStore> ref = archetypeChunk.getReferenceTo(i);
                    if (ref == null || !ref.isValid()) {
                        continue;
                    }
                    TransformComponent transform =
                        commandBuffer.getComponent(ref, TransformComponent.getComponentType());
                    if (transform == null
                        || !pedestal.nearStatue(
                            transform.getPosition().x,
                            transform.getPosition().y,
                            transform.getPosition().z
                        )) {
                        continue;
                    }
                    UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
                    if (keep != null && uuid != null && keep.equals(uuid.getUuid())) {
                        continue;
                    }
                    remove.add(ref);
                }
            }
        );
        UUID old = parseUuid(pedestal.statueUuid());
        if (old != null && (keep == null || !keep.equals(old))) {
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(old);
            if (ref != null && ref.isValid() && !remove.contains(ref)) {
                remove.add(ref);
            }
        }
        int removed = 0;
        for (Ref<EntityStore> ref : remove) {
            if (ref.isValid()) {
                store.removeEntity(ref, RemoveReason.REMOVE);
                removed++;
            }
        }
        return removed;
    }

    private static void persist(
        @Nonnull World world,
        @Nonnull Pedestal pedestal,
        @Nonnull UUID statueId,
        @Nonnull String skinJson,
        @Nonnull String label,
        @Nonnull Rotation3f rotation
    ) {
        FounderMonumentSpawnService.persistStatueOnBlock(
            world,
            pedestal.x() + 0.5,
            pedestal.y() + 1.05,
            pedestal.z() + 0.5,
            statueId,
            skinJson,
            label,
            rotation
        );
    }

    static boolean isPlacedPedestal(@Nullable String townId, @Nullable String skinJson, @Nullable String label) {
        return notBlank(townId) || notBlank(skinJson) || notBlank(label);
    }

    static boolean needsBlockRecovery(
        @Nullable String townId,
        @Nullable String skinJson,
        @Nullable String label,
        boolean livingFinished
    ) {
        return !livingFinished;
    }

    static boolean needsBlockRecovery(@Nullable String skinJson, @Nullable String statueEntityUuid, boolean livingVisible) {
        return !livingVisible;
    }

    static boolean isIdentityRotation(@Nonnull Rotation3f rotation) {
        return FounderMonumentSpawnService.isIdentityRotation(rotation);
    }

    @Nonnull
    static String firstNonBlank(@Nullable String preferred, @Nullable String fallback) {
        return FounderMonumentSpawnService.firstNonBlank(preferred, fallback);
    }

    @Nonnull
    static Rotation3f mergeRotation(@Nonnull Rotation3f preferred, @Nonnull Rotation3f fallback) {
        return isIdentityRotation(preferred) ? fallback : preferred;
    }

    static boolean isStoneStatueMesh(@Nullable ModelComponent modelComponent) {
        if (modelComponent == null) {
            return false;
        }
        Model model = modelComponent.getModel();
        if (model == null) {
            return false;
        }
        return AetherhavenConstants.FOUNDER_MONUMENT_STATUE_TEXTURE.equals(model.getTexture())
            || AetherhavenConstants.FOUNDER_MONUMENT_STATUE_BODY_MODEL.equals(model.getModel())
            || AetherhavenConstants.FOUNDER_MONUMENT_STATUE_MODEL_ID.equals(model.getModelAssetId());
    }

    static boolean hasAppliedStatueCosmetics(@Nullable ModelComponent modelComponent) {
        if (!isStoneStatueMesh(modelComponent)) {
            return false;
        }
        ModelAttachment[] attachments = modelComponent.getModel().getAttachments();
        return attachments != null && attachments.length > 0;
    }

    static boolean isClientVisibleStatue(@Nullable ModelComponent modelComponent, @Nullable NetworkId networkId) {
        return networkId != null && modelComponent != null && modelComponent.getModel() != null;
    }

    @Nonnull
    private static String modelName(@Nullable Model model) {
        if (model == null) {
            return "none";
        }
        String texture = model.getTexture() != null ? model.getTexture() : "none";
        String path = model.getModel() != null ? model.getModel() : "none";
        int attachments = model.getAttachments() != null ? model.getAttachments().length : 0;
        return path + "/" + texture + "/att=" + attachments;
    }

    @Nonnull
    private static String blankOr(@Nullable String value) {
        return notBlank(value) ? value : "-";
    }

    @Nonnull
    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    @Nullable
    private static UUID parseUuid(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean notBlank(@Nullable String value) {
        return value != null && !value.isBlank();
    }

    public static final class BlockLoad extends RefSystem<ChunkStore> {
        private final ComponentType<ChunkStore, FounderMonumentBlock> blockType = FounderMonumentBlock.getComponentType();
        private final ComponentType<ChunkStore, BlockModule.BlockStateInfo> stateType =
            BlockModule.BlockStateInfo.getComponentType();

        @Nonnull
        @Override
        public Query<ChunkStore> getQuery() {
            return Query.and(this.blockType, this.stateType);
        }

        @Override
        public void onEntityAdded(
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull AddReason reason,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer
        ) {
            if (reason != AddReason.LOAD) {
                return;
            }
            World world = store.getExternalData().getWorld();
            if (world == null) {
                LOGGER.atInfo().log("Founder monument: block loaded but world is missing");
                return;
            }
            FounderMonumentBlock block = store.getComponent(ref, this.blockType);
            BlockModule.BlockStateInfo state = store.getComponent(ref, this.stateType);
            Vector3i pos = new Vector3i();
            if (state != null && state.fillWorldPos(store, pos)) {
                LOGGER.atInfo().log(
                    "Founder monument: block loaded at %s %s %s town=%s uuid=%s skinChars=%s",
                    pos.x,
                    pos.y,
                    pos.z,
                    block != null ? blankOr(block.getTownId()) : "-",
                    block != null ? blankOr(block.getStatueEntityUuid()) : "-",
                    block != null ? block.getSkinJson().length() : 0
                );
            } else {
                LOGGER.atInfo().log("Founder monument: block loaded with no world position");
            }
            requestScan(world);
        }

        @Override
        public void onEntityRemove(
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull RemoveReason reason,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer
        ) {}
    }

    private record Pedestal(
        int x,
        int y,
        int z,
        @Nonnull String townId,
        @Nonnull String statueUuid,
        @Nonnull String skinJson,
        @Nonnull String label,
        float pitch,
        float yaw,
        float roll
    ) {
        @Nonnull
        static Pedestal from(@Nonnull Vector3i pos, @Nonnull FounderMonumentBlock block) {
            return new Pedestal(
                pos.x,
                pos.y,
                pos.z,
                block.getTownId(),
                block.getStatueEntityUuid() != null ? block.getStatueEntityUuid() : "",
                block.getSkinJson(),
                block.getLabel(),
                block.getPitch(),
                block.getYaw(),
                block.getRoll()
            );
        }

        boolean inCluster(@Nonnull Pedestal other) {
            return Math.abs(x - other.x) <= CLUSTER_XZ
                && Math.abs(z - other.z) <= CLUSTER_XZ
                && Math.abs(y - other.y) <= CLUSTER_Y;
        }

        boolean nearStatue(double statueX, double statueY, double statueZ) {
            double dx = statueX - (x + 0.5);
            double dy = statueY - (y + 1.05);
            double dz = statueZ - (z + 0.5);
            return Math.sqrt(dx * dx + dy * dy + dz * dz) <= NEARBY_STATUE_RANGE;
        }

        int score() {
            int score = skinJson.length();
            if (notBlank(townId)) {
                score += 1000;
            }
            if (notBlank(label)) {
                score += 100;
            }
            if (notBlank(statueUuid)) {
                score += 10;
            }
            if (!isIdentityRotation(rotation())) {
                score += 5;
            }
            return score;
        }

        @Nonnull
        Rotation3f rotation() {
            return new Rotation3f(pitch, yaw, roll);
        }
    }

    private record LivingStatue(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UUID id,
        double x,
        double y,
        double z,
        @Nonnull String skinJson,
        @Nonnull String label,
        @Nonnull Rotation3f rotation,
        boolean hasNetwork,
        @Nonnull String modelName,
        @Nonnull String persistentModel,
        boolean hasCosmetics,
        boolean visible
    ) {}
}
