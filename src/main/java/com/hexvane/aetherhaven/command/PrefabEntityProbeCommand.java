package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.construction.ConstructionPasteOps;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PropComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.PrefabUtil;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Puts a prefab's entities beside the player and lists what the server holds, to compare with what a viewer is sent. */
final class PrefabEntityProbeCommand extends AbstractCommandCollection {
    private static final double LIST_RADIUS = 96.0;

    PrefabEntityProbeCommand() {
        super("entityprobe", "Paste a prefab's entities and list their scales");
        setPermissionGroups("hytale:WorldEditor");
        addSubCommand(new Paste());
        addSubCommand(new ListNearby());
    }

    private static final class Paste extends AbstractPlayerCommand {
        private final RequiredArg<String> prefab = withRequiredArg("prefab", "Prefab path, such as Festivals/Festival_Square.prefab.json", ArgTypes.STRING);

        Paste() {
            super("paste", "Spawn a prefab's entities at your feet");
            setPermissionGroups("hytale:WorldEditor");
        }

        @Override
        protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world) {
            var buffer = PrefabResolveUtil.resolvePrefabBuffer(ctx.get(prefab));
            TransformComponent at = store.getComponent(ref, TransformComponent.getComponentType());
            if (buffer == null || at == null) {
                report(ctx, "Entity probe: prefab or player position not found.");
                return;
            }
            Vector3d feet = at.getPosition();
            var anchor = new Vector3i((int) Math.floor(feet.x), (int) Math.floor(feet.y), (int) Math.floor(feet.z));
            var sequence = ConstructionPasteOps.buildSequence(buffer, Rotation.None);
            int prefabId = PrefabUtil.getNextPrefabId();
            for (var entity : sequence.prefabEntitiesInOrder()) {
                ConstructionPasteOps.spawnPrefabEntityLikePaste(world, anchor, sequence.prefabRotation(), prefabId, store, entity);
            }
            report(ctx, "Entity probe pasted " + sequence.prefabEntitiesInOrder().size() + " at " + anchor.x + " " + anchor.y + " " + anchor.z);
        }
    }

    private static final class ListNearby extends AbstractPlayerCommand {
        ListNearby() {
            super("list", "List the scale of entities around you");
            setPermissionGroups("hytale:WorldEditor");
        }

        @Override
        protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world) {
            TransformComponent at = store.getComponent(ref, TransformComponent.getComponentType());
            if (at == null) {
                return;
            }
            Vector3d center = new Vector3d(at.getPosition());
            Map<String, Integer> counts = new TreeMap<>();
            store.forEachChunk(Query.and(TransformComponent.getComponentType(), PropComponent.getComponentType()), (chunk, buffer) -> {
                counts.merge("~ awake anywhere in the world", chunk.size(), Integer::sum);
                for (int i = 0; i < chunk.size(); i++) {
                    TransformComponent transform = chunk.getComponent(i, TransformComponent.getComponentType());
                    if (transform == null || transform.getPosition().distance(center) > LIST_RADIUS) {
                        continue;
                    }
                    BlockEntity block = chunk.getComponent(i, BlockEntity.getComponentType());
                    ItemComponent item = chunk.getComponent(i, ItemComponent.getComponentType());
                    EntityScaleComponent scale = chunk.getComponent(i, EntityScaleComponent.getComponentType());
                    String shape = (block != null ? "block=" + block.getBlockTypeKey() + " " : "")
                        + (item != null ? "item " : "")
                        + (chunk.getComponent(i, ModelComponent.getComponentType()) != null ? "model " : "")
                        + "scale=" + (scale != null ? String.format(java.util.Locale.ROOT, "%.2f", scale.getScale()) : "none");
                    counts.merge(shape, 1, Integer::sum);
                }
            });
            StringBuilder text = new StringBuilder("Entity probe list: ").append(counts.size()).append(" kinds");
            counts.forEach((shape, count) -> text.append("\n  ").append(count).append(" x ").append(shape));
            report(ctx, text.toString());
        }
    }

    private static void report(@Nonnull CommandContext ctx, @Nonnull String message) {
        ctx.sendMessage(Message.raw(message));
        HytaleLogger.forEnclosingClass().atInfo().log(message);
    }
}
