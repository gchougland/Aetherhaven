package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.monument.FounderMonumentSpawnService;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterDefinition;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.NonSerialized;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Spawns non-persistent stone previews of every Aetherhaven villager appearance. */
final class AetherhavenStoneStatueTestCommand extends AbstractPlayerCommand {
    private static final String PLAYER_MODEL = "Characters/Player.blockymodel";
    private static final String AETHERHAVEN_ICON_PREFIX = "Icons/ModelsGenerated/Aetherhaven_";
    private static final Map<UUID, List<UUID>> PREVIEWS_BY_PLAYER = new ConcurrentHashMap<>();

    AetherhavenStoneStatueTestCommand() {
        super("stone-test", "aetherhaven_commands_help.commands.aetherhaven.villager.stone_test.desc");
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null || !AetherhavenDebugUtil.requireDebug(plugin, playerRef)) {
            return;
        }
        TransformComponent playerTransform = store.getComponent(ref, TransformComponent.getComponentType());
        if (playerTransform == null) {
            return;
        }

        clearPrevious(playerRef.getUuid(), store);
        List<ModelAsset> appearances = collectAppearances(plugin);
        if (appearances.isEmpty()) {
            playerRef.sendMessage(Message.raw("No Aetherhaven villager model assets were found."));
            return;
        }

        Vector3d center = new Vector3d(playerTransform.getPosition());
        double radius = Math.max(8.0, appearances.size() * 1.6 / (Math.PI * 2.0));
        List<UUID> spawnedIds = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (int i = 0; i < appearances.size(); i++) {
            ModelAsset appearance = appearances.get(i);
            Model model;
            try {
                model = FounderMonumentSpawnService.buildStonePreviewModel(appearance);
            } catch (RuntimeException e) {
                failed.add(appearance.getId() + " (" + e.getMessage() + ")");
                continue;
            }
            if (model == null) {
                failed.add(appearance.getId());
                continue;
            }
            double angle = Math.PI * 2.0 * i / appearances.size();
            Vector3d position = new Vector3d(
                center.x + Math.cos(angle) * radius,
                center.y,
                center.z + Math.sin(angle) * radius
            );
            Vector3d towardCenter = new Vector3d(center).sub(position);
            float yaw = (float) Math.atan2(-towardCenter.x, -towardCenter.z);
            UUID spawnedId = spawnPreview(store, position, new Rotation3f(0.0f, yaw, 0.0f), model);
            if (spawnedId != null) {
                spawnedIds.add(spawnedId);
            } else {
                failed.add(appearance.getId());
            }
        }
        PREVIEWS_BY_PLAYER.put(playerRef.getUuid(), spawnedIds);
        String failureSuffix = failed.isEmpty() ? "" : " Failed: " + String.join(", ", failed);
        playerRef.sendMessage(
            Message.raw("Spawned " + spawnedIds.size() + " transient stone villager previews." + failureSuffix)
        );
    }

    @Nonnull
    static List<ModelAsset> collectAppearances(@Nonnull AetherhavenPlugin plugin) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (TownsfolkCharacterDefinition definition : plugin.getTownsfolkCharacterCatalog().allById().values()) {
            ids.add(definition.getModelAssetId());
        }
        for (ModelAsset asset : ModelAsset.getAssetMap().getAssetMap().values()) {
            String icon = asset.getIcon();
            if (
                PLAYER_MODEL.equals(asset.getModel())
                    && icon != null
                    && icon.startsWith(AETHERHAVEN_ICON_PREFIX)
            ) {
                ids.add(asset.getId());
            }
        }
        return ids.stream()
            .map(ModelAsset.getAssetMap()::getAsset)
            .filter(asset -> asset != null)
            .sorted(Comparator.comparing(ModelAsset::getId))
            .toList();
    }

    private static void clearPrevious(@Nonnull UUID playerUuid, @Nonnull Store<EntityStore> store) {
        List<UUID> previous = PREVIEWS_BY_PLAYER.remove(playerUuid);
        if (previous == null) {
            return;
        }
        for (UUID entityUuid : previous) {
            Ref<EntityStore> previewRef = store.getExternalData().getRefFromUUID(entityUuid);
            if (previewRef != null && previewRef.isValid()) {
                store.removeEntity(previewRef, RemoveReason.REMOVE);
            }
        }
    }

    private static UUID spawnPreview(
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3d position,
        @Nonnull Rotation3f rotation,
        @Nonnull Model model
    ) {
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        holder.addComponent(EntityStore.REGISTRY.getNonSerializedComponentType(), NonSerialized.get());
        holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(position, rotation));
        holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(rotation));
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(Box.horizontallyCentered(0.8, 2.0, 0.8)));
        holder.addComponent(ActiveAnimationComponent.getComponentType(), new ActiveAnimationComponent());
        holder.addComponent(Velocity.getComponentType(), new Velocity());
        holder.addComponent(Intangible.getComponentType(), Intangible.INSTANCE);
        holder.addComponent(Invulnerable.getComponentType(), Invulnerable.INSTANCE);
        holder.ensureComponent(UUIDComponent.getComponentType());
        Ref<EntityStore> previewRef = store.addEntity(holder, AddReason.SPAWN);
        if (previewRef == null || !previewRef.isValid()) {
            return null;
        }
        UUIDComponent uuid = store.getComponent(previewRef, UUIDComponent.getComponentType());
        return uuid != null ? uuid.getUuid() : null;
    }
}
