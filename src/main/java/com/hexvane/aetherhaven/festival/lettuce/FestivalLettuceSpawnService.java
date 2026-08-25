package com.hexvane.aetherhaven.festival.lettuce;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.festival.FestivalPrefabSwapService;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
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
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.PropComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
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

/**
 * Spawns the Springheart Lettuce as a festival NPC with the cabbage model baked in. F prompt / burst Use are owned by
 * {@link FestivalLettuceInteractSync}, not NPC {@code SetInteractable}.
 */
public final class FestivalLettuceSpawnService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final String CENTERPIECE_ITEM_ID = "Plant_Crop_Lettuce_Item";
    public static final String CENTERPIECE_NPC_ROLE_ID = "Aetherhaven_Festival_Lettuce";
    public static final String CENTERPIECE_MODEL_ASSET_ID = "Aetherhaven_Festival_Lettuce";

    private static final int REQUIRED_ESSENCE = FestivalLettuceComponent.DEFAULT_REQUIRED_ESSENCE;
    private static final float MIN_SCALE = 4.0f;
    private static final float MAX_SCALE = 11.2f;
    private static final int SEEDS_PER_BURST = 28;
    /**
     * World-space F-aim box kept fixed while the visual model grows. createScaledModel scales any override box, so we
     * pre-divide by scale. Standing inside a giant aim box makes F miss.
     */
    private static final double INTERACT_HALF_XZ = 1.15;
    private static final double INTERACT_MIN_Y = 0.15;
    private static final double INTERACT_MAX_Y = 2.4;

    private FestivalLettuceSpawnService() {}

    public static void spawnCenterpiece(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance festivalPlot,
        @Nonnull FestivalDefinition festival
    ) {
        var entityStore = world.getEntityStore();
        if (entityStore == null || !FestivalLettuceComponent.isRegistered()) {
            return;
        }
        Store<EntityStore> store = entityStore.getStore();
        despawnCenterpiece(world, festivalPlot, town.getTownId());

        Vector3d pos = resolveSpawnPosition(festivalPlot, festival, store);
        Rotation3f rotation = new Rotation3f();
        Ref<EntityStore> prefabProp = findPrefabLettuce(store, festivalPlot);
        if (prefabProp != null) {
            TransformComponent tc = store.getComponent(prefabProp, TransformComponent.getComponentType());
            if (tc != null) {
                pos = new Vector3d(tc.getPosition());
                rotation = new Rotation3f(tc.getRotation());
            }
            store.removeEntity(prefabProp, RemoveReason.REMOVE);
        }
        if (pos == null) {
            LOGGER.atWarning().log("New Life festival: no lettuce spawn position, skipping centerpiece");
            return;
        }

        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) {
            LOGGER.atWarning().log("New Life festival: NPC support missing, the lettuce will not spawn");
            return;
        }
        int roleIndex = npcPlugin.getIndex(CENTERPIECE_NPC_ROLE_ID);
        if (roleIndex < 0) {
            LOGGER.atWarning().log("New Life festival: could not spawn role %s", CENTERPIECE_NPC_ROLE_ID);
            return;
        }
        Model spawnModel = buildCabbageModel(MIN_SCALE);
        if (spawnModel == null) {
            LOGGER.atWarning().log(
                "New Life festival: cabbage model %s missing; the lettuce will not spawn",
                CENTERPIECE_MODEL_ASSET_ID
            );
            return;
        }
        float spawnScale = spawnModel.getScale();
        var pair = npcPlugin.spawnEntity(
            store,
            roleIndex,
            pos,
            rotation,
            spawnModel,
            (npcEntity, holder, st) -> npcEntity.setInitialModelScale(spawnScale),
            null
        );
        if (pair == null) {
            LOGGER.atWarning().log("New Life festival: could not spawn role %s", CENTERPIECE_NPC_ROLE_ID);
            return;
        }
        Ref<EntityStore> centerpiece = pair.first();
        store.putComponent(centerpiece, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
        // RoleBuilder keeps the spawn model; re-assert cabbage PersistentModel after add.
        store.putComponent(centerpiece, ModelComponent.getComponentType(), new ModelComponent(spawnModel));
        store.putComponent(centerpiece, PersistentModel.getComponentType(), new PersistentModel(spawnModel.toReference()));

        FestivalLettuceComponent lettuce = new FestivalLettuceComponent();
        lettuce.setTownId(town.getTownId());
        lettuce.setRequiredEssence(REQUIRED_ESSENCE);
        lettuce.setMinScale(MIN_SCALE);
        lettuce.setMaxScale(MAX_SCALE);
        lettuce.setSeedsPerBurst(SEEDS_PER_BURST);
        lettuce.setBurstItemIds(festival.getBurstItemIds());
        lettuce.setAppliedModelScale(MIN_SCALE);
        store.putComponent(centerpiece, FestivalLettuceComponent.getComponentType(), lettuce);
        FestivalLettuceInteractSync.sync(centerpiece, lettuce, store);

        NPCEntity npc = store.getComponent(centerpiece, NPCEntity.getComponentType());
        if (npc != null) {
            npc.setLeashPoint(new Vector3d(pos.x, pos.y, pos.z));
            store.putComponent(centerpiece, NPCEntity.getComponentType(), npc);
        }
        NpcSpawnOriginUtil.attach(store, centerpiece, "FESTIVAL_LETTUCE", "festival=new_life", world, pos);
        FestivalLettuceEffects.playFestivalStart(store, new Vector3d(pos));
        LOGGER.atInfo().log(
            "New Life festival: spawned interactable Springheart Lettuce NPC at %.1f %.1f %.1f",
            pos.x,
            pos.y,
            pos.z
        );
    }

    /**
     * Rebuilds the cabbage Model at {@code scale}. Client F-aim and server Use both use this model box; do not rely on
     * {@code EntityScale} for targeting.
     */
    static boolean applyModelScale(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> centerpiece, float scale) {
        Model model = buildCabbageModel(scale);
        if (model == null) {
            return false;
        }
        store.putComponent(centerpiece, ModelComponent.getComponentType(), new ModelComponent(model));
        store.putComponent(centerpiece, PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
        return true;
    }

    static void applyModelScale(
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> centerpiece,
        float scale
    ) {
        Model model = buildCabbageModel(scale);
        if (model == null) {
            return;
        }
        commandBuffer.putComponent(centerpiece, ModelComponent.getComponentType(), new ModelComponent(model));
        commandBuffer.putComponent(centerpiece, PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
    }

    @Nullable
    private static Model buildCabbageModel(float scale) {
        ModelAsset asset = ModelAsset.getAssetMap().getAsset(CENTERPIECE_MODEL_ASSET_ID);
        if (asset == null) {
            return null;
        }
        float safeScale = Math.max(0.01f, scale);
        // Pre-compensate so after createScaledModel.scale() the aim volume stays ~2.3 wide and ~2.25 tall.
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

    /**
     * Only clears this town's lettuce; several towns can run New Life in the same tick and a world wide sweep would
     * delete the centerpiece another town just spawned. Lettuce saved before town ids existed is matched by footprint.
     */
    public static void despawnCenterpiece(
        @Nonnull World world,
        @Nonnull PlotInstance festivalPlot,
        @Nonnull UUID townId
    ) {
        var entityStore = world.getEntityStore();
        if (entityStore == null || !FestivalLettuceComponent.isRegistered()) {
            return;
        }
        Store<EntityStore> store = entityStore.getStore();
        PlotFootprintRecord footprint = festivalPlot.toFootprint();
        List<Ref<EntityStore>> refs = new ArrayList<>();
        store.forEachChunk(
            Query.and(FestivalLettuceComponent.getComponentType(), TransformComponent.getComponentType()),
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    FestivalLettuceComponent lettuce =
                        chunk.getComponent(i, FestivalLettuceComponent.getComponentType());
                    TransformComponent tc = chunk.getComponent(i, TransformComponent.getComponentType());
                    if (lettuce == null || tc == null) {
                        continue;
                    }
                    UUID owner = lettuce.getTownId();
                    boolean mine =
                        owner != null ? townId.equals(owner) : containsBlock(footprint, tc.getPosition());
                    if (!mine) {
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

    @Nullable
    private static Vector3d resolveSpawnPosition(
        @Nonnull PlotInstance festivalPlot,
        @Nonnull FestivalDefinition festival,
        @Nonnull Store<EntityStore> store
    ) {
        Ref<EntityStore> prop = findPrefabLettuce(store, festivalPlot);
        if (prop != null) {
            TransformComponent tc = store.getComponent(prop, TransformComponent.getComponentType());
            if (tc != null) {
                return new Vector3d(tc.getPosition());
            }
        }
        int[] local = festival.getCenterpieceLocal();
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (local != null && plugin != null) {
            return FestivalPrefabSwapService.spotWorldPosition(plugin, festivalPlot, local[0], local[1], local[2]);
        }
        if (plugin != null) {
            return FestivalPrefabSwapService.spotWorldPosition(plugin, festivalPlot, 0, 6, 0);
        }
        return null;
    }

    @Nullable
    private static Ref<EntityStore> findPrefabLettuce(@Nonnull Store<EntityStore> store, @Nonnull PlotInstance plot) {
        PlotFootprintRecord fp = plot.toFootprint();
        List<Ref<EntityStore>> found = new ArrayList<>();
        store.forEachChunk(
            Query.and(
                ItemComponent.getComponentType(),
                TransformComponent.getComponentType(),
                PropComponent.getComponentType()
            ),
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    ItemComponent item = chunk.getComponent(i, ItemComponent.getComponentType());
                    TransformComponent tc = chunk.getComponent(i, TransformComponent.getComponentType());
                    if (item == null || tc == null) {
                        continue;
                    }
                    ItemStack stack = item.getItemStack();
                    if (stack == null || !CENTERPIECE_ITEM_ID.equalsIgnoreCase(stack.getItemId())) {
                        continue;
                    }
                    if (!containsBlock(fp, tc.getPosition())) {
                        continue;
                    }
                    Ref<EntityStore> r = chunk.getReferenceTo(i);
                    if (r != null && r.isValid()) {
                        found.add(r);
                    }
                }
            }
        );
        return found.isEmpty() ? null : found.get(0);
    }

    private static boolean containsBlock(@Nonnull PlotFootprintRecord fp, @Nonnull Vector3d p) {
        int bx = (int) Math.floor(p.x);
        int by = (int) Math.floor(p.y);
        int bz = (int) Math.floor(p.z);
        return bx >= fp.getMinX()
            && bx <= fp.getMaxX()
            && by >= fp.getMinY() - 1
            && by <= fp.getMaxY() + 2
            && bz >= fp.getMinZ()
            && bz <= fp.getMaxZ();
    }
}
