package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plotcreator.icon.PlotCreatorIconExporter;
import com.hexvane.aetherhaven.shopspot.ShopSpotDisplayService;
import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;
import com.hypixel.hytale.builtin.buildertools.prefabeditor.saving.PrefabSaveContributor;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.VectorBoxUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.blocktype.component.BlockPhysics;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.prefab.PrefabCopyableComponent;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.LocalCachedChunkAccessor;
import com.hypixel.hytale.server.core.universe.world.chunk.EntityChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Exports the plot creator bounds using the same pipeline as the built-in builder tools
 * ({@link BuilderToolsPlugin} {@code saveFromSelection} / {@link com.hypixel.hytale.builtin.buildertools.prefabeditor.saving.PrefabSaver}):
 * world chunk copy via {@link BlockSelection#copyFromAtWorld}, explicit fluid layer copy, optional entities,
 * {@link PrefabSaveContributor}s, then {@link BlockSelection#relativize()} (with a fluid re-apply workaround) and
 * {@link PrefabStore#savePrefab}.
 */
public final class PlotCreatorPrefabExporter {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private PlotCreatorPrefabExporter() {}

    public static boolean export(
        @Nonnull World world,
        @Nonnull PlotCreatorDraft draft,
        @Nonnull Path outputFile,
        boolean overwrite
    ) {
        Vector3i min = draft.boundsMin();
        Vector3i max = draft.boundsMax();
        Vector3i anchor = draft.getPlotAnchor();
        if (anchor == null) {
            return false;
        }
        draft.setPrefabOriginMin(new Vector3i(min));
        PlotCreatorLocalCoords.recomputeAnchorOffset(draft);

        int xMin = min.x;
        int yMin = min.y;
        int zMin = min.z;
        int xMax = max.x;
        int yMax = max.y;
        int zMax = max.z;
        int width = xMax - xMin;
        int height = yMax - yMin;
        int depth = zMax - zMin;
        int halfWidth = width / 2;
        int halfDepth = depth / 2;

        LocalCachedChunkAccessor accessor =
            LocalCachedChunkAccessor.atWorldCoords(world, xMin + halfWidth, zMin + halfDepth, Math.max(width, depth));

        BlockSelection selection = new BlockSelection();
        selection.setPosition(xMin + halfWidth, yMin, zMin + halfDepth);
        selection.setSelectionArea(new Vector3i(xMin, yMin, zMin), new Vector3i(xMax, yMax, zMax));

        int editorBlock = BlockType.getAssetMap().getIndex("Editor_Block");
        boolean skipEditorBlock = editorBlock != Integer.MIN_VALUE;
        int editorBlockPrefabAir = BlockType.getAssetMap().getIndex("Editor_Empty");
        boolean includeEmpty = draft.isSaveEmptySpaces();

        int top = Math.max(yMin, yMax);
        int bottom = Math.min(yMin, yMax);
        int blockCount = 0;

        ChunkStore chunkStoreAccessor = world.getChunkStore();

        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                WorldChunk chunk = accessor.getChunk(ChunkUtil.indexChunkFromBlock(x, z));
                if (chunk == null) {
                    continue;
                }
                Store<ChunkStore> chunkStore = chunk.getReference().getStore();
                int lastSection = -1;
                Ref<ChunkStore> sectionRef = null;
                BlockPhysics blockPhysics = null;

                for (int y = top; y >= bottom; y--) {
                    int block = chunk.getBlock(x, y, z);
                    if (lastSection != ChunkUtil.indexSection(y)) {
                        lastSection = ChunkUtil.indexSection(y);
                        sectionRef = chunkStoreAccessor.getChunkSectionReferenceAtBlock(x, y, z);
                        blockPhysics = sectionRef != null && sectionRef.isValid()
                            ? chunkStore.getComponent(sectionRef, BlockPhysics.getComponentType())
                            : null;
                    }

                    int fluid = 0;
                    byte fluidLevel = 0;
                    if (sectionRef != null && sectionRef.isValid()) {
                        FluidSection fluidSection = chunkStore.getComponent(sectionRef, FluidSection.getComponentType());
                        if (fluidSection != null) {
                            fluid = fluidSection.getFluidId(x, y, z);
                            fluidLevel = fluidSection.getFluidLevel(x, y, z);
                        }
                    }

                    if ((block != 0 || fluid != 0 || includeEmpty) && (!skipEditorBlock || block != editorBlock)) {
                        if (block == editorBlockPrefabAir || (block == 0 && fluid == 0)) {
                            selection.addBlockAtWorldPos(x, y, z, 0, 0, 0, 0);
                        } else {
                            selection.copyFromAtWorld(x, y, z, chunk, blockPhysics);
                        }
                        // Match BuilderTools PrefabSaver: always write the fluid layer explicitly so fluid-only
                        // cells and editor-air cells keep their fluids (copyFromAtWorld alone is not enough for
                        // the editor-air branch).
                        if (fluid != 0 || includeEmpty) {
                            selection.addFluidAtWorldPos(x, y, z, fluid, fluidLevel);
                        }
                        blockCount++;
                    }
                }
            }
        }

        selection.setAnchorAtWorldPos(anchor.x, anchor.y, anchor.z);

        Store<EntityStore> entityStore = world.getEntityStore() != null ? world.getEntityStore().getStore() : null;
        if (entityStore != null) {
            copyEntitiesInBounds(world, entityStore, selection, xMin, yMin, zMin, width, height, depth);
        }

        BuilderToolsPlugin builderTools = BuilderToolsPlugin.get();
        if (builderTools != null) {
            List<PrefabSaveContributor> contributors = builderTools.getPrefabSaveContributors();
            Vector3i minCorner = new Vector3i(xMin, yMin, zMin);
            Vector3i maxCorner = new Vector3i(xMax, yMax, zMax);
            for (PrefabSaveContributor contributor : contributors) {
                contributor.contribute(selection, world, minCorner, maxCorner);
            }
        }

        if (blockCount == 0) {
            LOGGER.atWarning().log("Plot creator prefab export: no blocks in bounds");
            return false;
        }

        try {
            Files.createDirectories(outputFile.getParent());
            // BlockSelection.relativize() copies blocks/entities but drops fluids when the anchor is not (0,0,0).
            // Plot creator anchors at the plot sign, so re-apply fluids with the same origin offset.
            int originX = selection.getAnchorX();
            int originY = selection.getAnchorY();
            int originZ = selection.getAnchorZ();
            BlockSelection prefab = selection.relativize();
            if (originX != 0 || originY != 0 || originZ != 0) {
                selection.forEachFluid((fx, fy, fz, fluidId, level) ->
                    prefab.addFluidAtLocalPos(fx - originX, fy - originY, fz - originZ, fluidId, level));
            }
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin != null) {
                PlotCreatorIconExporter.tryExportIcon(prefab, draft.getConstructionId(), plugin.getDataDirectory());
            }
            PrefabStore.get().savePrefab(outputFile, prefab, overwrite);
            return true;
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to save prefab to %s", outputFile);
            return false;
        }
    }

    private static void copyEntitiesInBounds(
        @Nonnull World world,
        @Nonnull Store<EntityStore> entityStore,
        @Nonnull BlockSelection selection,
        int xMin,
        int yMin,
        int zMin,
        int width,
        int height,
        int depth
    ) {
        Set<UUID> addedEntityUuids = new HashSet<>();
        ComponentRegistry.Data<EntityStore> registryData = EntityStore.REGISTRY.getData();
        ComponentType<EntityStore, PrefabCopyableComponent> prefabCopyableType = PrefabCopyableComponent.getComponentType();
        ComponentType<EntityStore, TransformComponent> transformType = TransformComponent.getComponentType();
        ComponentType<EntityStore, BlockEntity> blockEntityType = BlockEntity.getComponentType();

        BuilderToolsPlugin.forEachCopyableInSelection(world, xMin, yMin, zMin, width, height, depth, e -> {
            if (ShopSpotDisplayService.isRuntimeShopDisplayProp(entityStore, e)) {
                return;
            }
            if (isEditorBlockEntity(entityStore.getComponent(e, blockEntityType))) {
                return;
            }
            Holder<EntityStore> holder = entityStore.copyEntity(e);
            trackUuid(addedEntityUuids, holder);
            selection.addEntityFromWorld(holder);
        });

        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> chunkComponentStore = chunkStore.getStore();
        int minChunkX = xMin >> 5;
        int maxChunkX = (xMin + width) >> 5;
        int minChunkZ = zMin >> 5;
        int maxChunkZ = (zMin + depth) >> 5;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(ChunkUtil.indexChunk(cx, cz));
                if (chunkRef == null || !chunkRef.isValid()) {
                    continue;
                }
                EntityChunk entityChunk = chunkComponentStore.getComponent(chunkRef, EntityChunk.getComponentType());
                if (entityChunk == null) {
                    continue;
                }
                for (Holder<EntityStore> holder : entityChunk.getEntityHolders()) {
                    if (!holder.getArchetype().contains(prefabCopyableType) || !holder.hasSerializableComponents(registryData)) {
                        continue;
                    }
                    if (ShopSpotDisplayService.isRuntimeShopDisplayProp(holder)) {
                        continue;
                    }
                    if (isEditorBlockEntity(holder.getComponent(blockEntityType))) {
                        continue;
                    }
                    TransformComponent transform = holder.getComponent(transformType);
                    Vector3d position = transform != null ? transform.getPosition() : null;
                    if (transform == null
                        || position == null
                        || !VectorBoxUtil.isInside(xMin, yMin, zMin, 0.0, 0.0, 0.0, width + 1, height + 1, depth + 1, position)) {
                        continue;
                    }
                    UUIDComponent uuidComp = holder.getComponent(UUIDComponent.getComponentType());
                    if (uuidComp != null && addedEntityUuids.contains(uuidComp.getUuid())) {
                        continue;
                    }
                    trackUuid(addedEntityUuids, holder);
                    Holder<EntityStore> clonedHolder = holder.clone();
                    TransformComponent clonedTransform = clonedHolder.getComponent(transformType);
                    if (clonedTransform != null && clonedTransform.getPosition() != null) {
                        clonedTransform.getPosition().sub(selection.getX(), selection.getY(), selection.getZ());
                    }
                    selection.addEntityHolderRaw(clonedHolder);
                }
            }
        }

        selection.sortEntitiesByPosition();
    }

    private static void trackUuid(@Nonnull Set<UUID> addedEntityUuids, @Nonnull Holder<EntityStore> holder) {
        UUIDComponent uuidComp = holder.getComponent(UUIDComponent.getComponentType());
        if (uuidComp != null) {
            addedEntityUuids.add(uuidComp.getUuid());
        }
    }

    private static boolean isEditorBlockEntity(@Nullable BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }
        String key = blockEntity.getBlockTypeKey();
        return key != null && (key.equals("Editor_Block") || key.equals("Editor_Empty") || key.equals("Editor_Anchor"));
    }

    /**
     * Building JSON {@code prefabPath} and on-disk export name, e.g. {@code plot_my_shop.prefab.json}.
     * Derived from the construction id only (never re-sanitize an already suffixed name).
     */
    @Nullable
    public static String prefabPathKeyFromConstructionId(@Nullable String constructionId) {
        if (constructionId == null || constructionId.isBlank()) {
            return null;
        }
        String id = constructionId.trim().toLowerCase(java.util.Locale.ROOT);
        if (id.endsWith(".prefab.json")) {
            return id;
        }
        if (!id.matches("plot_[a-z0-9_]+")) {
            return null;
        }
        return id + ".prefab.json";
    }

    /** @deprecated use {@link #prefabPathKeyFromConstructionId} */
    @Deprecated
    @Nullable
    public static String sanitizePrefabFileName(@Nullable String raw) {
        return prefabPathKeyFromConstructionId(raw);
    }
}
