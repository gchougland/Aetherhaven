package com.hexvane.aetherhaven.festival.hallowseve;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.festival.FestivalPrefabSwapService;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.NpcSpawnOriginUtil;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Spawns the maze jack o lantern centerpiece. */
public final class HallowsEvePumpkinSpawnService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final double INTERACT_HALF_XZ = 0.9;
    private static final double INTERACT_MIN_Y = 0.1;
    private static final double INTERACT_MAX_Y = 2.0;

    private HallowsEvePumpkinSpawnService() {}

    public static void spawnCenterpiece(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance festivalPlot,
        @Nonnull FestivalDefinition festival
    ) {
        var entityStore = world.getEntityStore();
        if (entityStore == null || !HallowsEvePumpkinComponent.isRegistered()) {
            return;
        }
        Store<EntityStore> store = entityStore.getStore();
        despawnCenterpiece(world, town.getTownId());

        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        double[] local = festival.getCenterpieceLocalExact();
        Vector3d pos =
            local != null
                ? FestivalPrefabSwapService.spotWorldPositionExact(
                    plugin, festivalPlot, local[0], local[1], local[2]
                )
                : FestivalPrefabSwapService.spotWorldPositionExact(plugin, festivalPlot, 1.0, 6.0, 1.0);
        pos.y += HallowsEveIds.PUMPKIN_SPAWN_Y_OFFSET;
        Rotation3f facing = facingAwayFromEntrance(plugin, festivalPlot, festival, pos);

        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) {
            LOGGER.atWarning().log("Hallow's Eve: NPC support missing, the pumpkin will not spawn");
            return;
        }
        int roleIndex = npcPlugin.getIndex(HallowsEveIds.PUMPKIN_NPC_ROLE);
        if (roleIndex < 0) {
            LOGGER.atWarning().log("Hallow's Eve: could not spawn role %s", HallowsEveIds.PUMPKIN_NPC_ROLE);
            return;
        }
        Model spawnModel = buildPumpkinModel(HallowsEveIds.PUMPKIN_MIN_SCALE);
        if (spawnModel == null) {
            LOGGER.atWarning().log("Hallow's Eve: pumpkin model missing");
            return;
        }
        float spawnScale = spawnModel.getScale();
        var pair = npcPlugin.spawnEntity(
            store,
            roleIndex,
            pos,
            facing,
            spawnModel,
            (npcEntity, holder, st) -> npcEntity.setInitialModelScale(spawnScale),
            null
        );
        if (pair == null) {
            LOGGER.atWarning().log("Hallow's Eve: could not spawn pumpkin");
            return;
        }
        Ref<EntityStore> centerpiece = pair.first();
        store.putComponent(centerpiece, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
        store.putComponent(centerpiece, ModelComponent.getComponentType(), new ModelComponent(spawnModel));
        store.putComponent(centerpiece, PersistentModel.getComponentType(), new PersistentModel(spawnModel.toReference()));

        HallowsEvePumpkinComponent pumpkin = new HallowsEvePumpkinComponent();
        pumpkin.setTownId(town.getTownId());
        pumpkin.setMinScale(HallowsEveIds.PUMPKIN_MIN_SCALE);
        pumpkin.setMaxScale(HallowsEveIds.PUMPKIN_MAX_SCALE);
        pumpkin.setAppliedModelScale(HallowsEveIds.PUMPKIN_MIN_SCALE);
        store.putComponent(centerpiece, HallowsEvePumpkinComponent.getComponentType(), pumpkin);
        HallowsEvePumpkinInteractSync.sync(centerpiece, pumpkin, store);

        NPCEntity npc = store.getComponent(centerpiece, NPCEntity.getComponentType());
        if (npc != null) {
            npc.setLeashPoint(new Vector3d(pos.x, pos.y, pos.z));
            store.putComponent(centerpiece, NPCEntity.getComponentType(), npc);
        }
        NpcSpawnOriginUtil.attach(store, centerpiece, "FESTIVAL_JACK_LANTERN", "festival=hallows_eve", world, pos);
    }

    /**
     * Faces the lantern away from the maze entrance in world space, so a rotated festival square still points
     * the face toward the back of the maze.
     */
    @Nonnull
    static Rotation3f facingAwayFromEntrance(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PlotInstance festivalPlot,
        @Nonnull FestivalDefinition festival,
        @Nonnull Vector3d pumpkinPos
    ) {
        FestivalDefinition.MazeStartLocalRow start = festival.getMazeStartLocal();
        if (start == null) {
            return new Rotation3f();
        }
        Vector3d entrance =
            FestivalPrefabSwapService.spotWorldPosition(
                plugin,
                festivalPlot,
                start.getLocalX(),
                start.getLocalY(),
                start.getLocalZ()
            );
        float yawRad = (float) Math.toRadians(HallowsEveTeleport.yawDegreesAwayFrom(pumpkinPos, entrance));
        return new Rotation3f(0f, yawRad, 0f);
    }

    static void applyModelScale(
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> centerpiece,
        float scale
    ) {
        Model model = buildPumpkinModel(scale);
        if (model == null) {
            return;
        }
        commandBuffer.putComponent(centerpiece, ModelComponent.getComponentType(), new ModelComponent(model));
        commandBuffer.putComponent(
            centerpiece,
            PersistentModel.getComponentType(),
            new PersistentModel(model.toReference())
        );
    }

    @Nullable
    private static Model buildPumpkinModel(float scale) {
        ModelAsset asset = ModelAsset.getAssetMap().getAsset(HallowsEveIds.PUMPKIN_MODEL_ASSET_ID);
        if (asset == null) {
            return null;
        }
        float safeScale = Math.max(0.01f, scale);
        Box interactBox =
            new Box(
                -INTERACT_HALF_XZ / safeScale,
                INTERACT_MIN_Y / safeScale,
                -INTERACT_HALF_XZ / safeScale,
                INTERACT_HALF_XZ / safeScale,
                INTERACT_MAX_Y / safeScale,
                INTERACT_HALF_XZ / safeScale
            );
        return Model.createScaledModel(asset, safeScale, null, interactBox, false);
    }

    public static void despawnCenterpiece(@Nonnull World world, @Nonnull UUID townId) {
        var entityStore = world.getEntityStore();
        if (entityStore == null || !HallowsEvePumpkinComponent.isRegistered()) {
            return;
        }
        Store<EntityStore> store = entityStore.getStore();
        List<Ref<EntityStore>> refs = new ArrayList<>();
        store.forEachChunk(
            Query.and(HallowsEvePumpkinComponent.getComponentType()),
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    HallowsEvePumpkinComponent pumpkin =
                        chunk.getComponent(i, HallowsEvePumpkinComponent.getComponentType());
                    if (pumpkin == null || !townId.equals(pumpkin.getTownId())) {
                        continue;
                    }
                    Ref<EntityStore> r = chunk.getReferenceTo(i);
                    if (r != null && r.isValid()) {
                        refs.add(r);
                    }
                }
            }
        );
        for (Ref<EntityStore> r : refs) {
            if (r.isValid()) {
                store.removeEntity(r, RemoveReason.REMOVE);
            }
        }
    }
}
