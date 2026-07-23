package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plot.CharterBlock;
import com.hexvane.aetherhaven.plot.PlotBlockRotationUtil;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownTerritoryClaims;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import org.joml.Vector3i;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.modules.block.BlockEntity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class CharterRelocationService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    /** Bit 2 skips automatic block-entity attachment in {@code placeBlock}; we attach explicitly. */
    private static final int PLACE_SETTINGS = 10;
    private static final int BREAK_SETTINGS = 10;

    public enum LinkRepairResult {
        ALREADY_OK,
        RELINKED,
        PLACED,
        SKIPPED_CHUNK_UNLOADED,
        FAILED_BLOCKED,
        FAILED
    }

    private CharterRelocationService() {}

    public static boolean tryCommit(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CharterRelocationSession session,
        @Nonnull UUID playerUuid
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return false;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(session.getTownId());
        if (town == null) {
            sendError(store, ref, "Town not found.");
            return false;
        }
        if (!world.getName().equals(town.getWorldName())) {
            sendError(store, ref, "Town is not in this world.");
            return false;
        }
        if (!town.getOwnerUuid().equals(playerUuid)) {
            sendError(store, ref, "Only the town owner can move the charter.");
            return false;
        }
        Vector3i a = session.getAnchor();
        int ox = town.getCharterX();
        int oy = town.getCharterY();
        int oz = town.getCharterZ();
        int deltaCx = ChunkUtil.chunkCoordinate(a.x) - ChunkUtil.chunkCoordinate(ox);
        int deltaCz = ChunkUtil.chunkCoordinate(a.z) - ChunkUtil.chunkCoordinate(oz);
        if (a.x == ox && a.y == oy && a.z == oz) {
            sendError(store, ref, "Choose a different block than the current charter position.");
            return false;
        }
        if (!tm.allPlotFootprintsFitAfterClaimShift(town, deltaCx, deltaCz)) {
            sendError(
                store,
                ref,
                "Moving the charter here would leave one or more buildings outside your territory. Try a position closer to your buildings."
            );
            return false;
        }
        TownRecord overlap = tm.findTerritoryOverlapAfterClaimShift(town, deltaCx, deltaCz);
        if (overlap != null) {
            sendError(
                store,
                ref,
                "Too close to "
                    + overlap.getDisplayName()
                    + ". Your town border would overlap theirs."
            );
            return false;
        }
        if (!isReplaceableForCharter(world, a.x, a.y, a.z)) {
            sendError(store, ref, "That spot is blocked. Choose an empty or replaceable block for the charter.");
            return false;
        }
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(a.x, a.z));
        if (chunk == null) {
            sendError(store, ref, "Chunk not loaded for that position.");
            return false;
        }
        Rotation yaw = session.getBlockHorizontalRotation();
        if (!placeAndLinkCharter(world, chunk, a.x, a.y, a.z, yaw, town.getTownId().toString())) {
            sendError(store, ref, "Charter block failed to link (see server log).");
            return false;
        }

        world.breakBlock(ox, oy, oz, BREAK_SETTINGS);

        TownTerritoryClaims.shiftAllClaims(town, deltaCx, deltaCz);
        town.setCharterPosition(a.x, a.y, a.z);
        tm.updateTown(town);

        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.translation("aetherhaven_ui_town.aetherhaven.ui.chartertown.charterMoved"));
        }
        return true;
    }

    /**
     * Restore a missing or unlinked charter block at the {@link TownRecord}'s saved charter coordinates only (owner or
     * {@link com.hexvane.aetherhaven.AetherhavenConstants#PERMISSION_TOWN_ADMIN} / creative). Does not change charter
     * position or territory — use the charter UI relocation flow to move the anchor.
     */
    public static boolean tryReplaceCharter(
        @Nonnull World world,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull Rotation horizontalYaw,
        @Nonnull UUID actorUuid,
        boolean actorMayBypassOwnership,
        @Nullable PlayerRef feedback
    ) {
        if (!town.getOwnerUuid().equals(actorUuid) && !actorMayBypassOwnership) {
            sendReplaceMsg(feedback, "aetherhaven_town.aetherhaven.town.charterReplace.notOwner");
            return false;
        }
        if (!world.getName().equals(town.getWorldName())) {
            sendReplaceMsg(feedback, "aetherhaven_town.aetherhaven.town.charterReplace.wrongWorld");
            return false;
        }

        LinkRepairResult result = repairCharterLink(world, town, horizontalYaw);
        return switch (result) {
            case ALREADY_OK -> {
                sendReplaceMsg(feedback, "aetherhaven_town.aetherhaven.town.charterReplace.alreadyOk");
                yield true;
            }
            case RELINKED, PLACED -> {
                tm.updateTown(town);
                sendReplaceDone(feedback, town);
                yield true;
            }
            case SKIPPED_CHUNK_UNLOADED -> {
                sendReplaceMsg(feedback, "aetherhaven_town.aetherhaven.town.charterReplace.chunkNotLoaded");
                yield false;
            }
            case FAILED_BLOCKED -> {
                sendReplaceMsg(feedback, "aetherhaven_town.aetherhaven.town.charterReplace.clearFirst");
                yield false;
            }
            case FAILED -> {
                sendReplaceMsg(feedback, "aetherhaven_town.aetherhaven.town.charterReplace.linkFailed");
                yield false;
            }
        };
    }

    /**
     * Re-links or re-places the charter at the town's saved coordinates. Used by {@code /ah plots repair} and
     * {@link #tryReplaceCharter}. Does not enforce ownership.
     */
    @Nonnull
    public static LinkRepairResult repairCharterLink(
        @Nonnull World world, @Nonnull TownRecord town, @Nonnull Rotation horizontalYaw
    ) {
        if (!world.getName().equals(town.getWorldName())) {
            return LinkRepairResult.FAILED;
        }
        if (world.isInThread()) {
            return repairCharterLinkOnWorldThread(world, town, horizontalYaw);
        }
        return CompletableFuture.supplyAsync(
                () -> repairCharterLinkOnWorldThread(world, town, horizontalYaw),
                world
            )
            .join();
    }

    @Nonnull
    private static LinkRepairResult repairCharterLinkOnWorldThread(
        @Nonnull World world, @Nonnull TownRecord town, @Nonnull Rotation horizontalYaw
    ) {
        String townIdStr = town.getTownId().toString();
        int cx = town.getCharterX();
        int cy = town.getCharterY();
        int cz = town.getCharterZ();

        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(cx, cz));
        if (chunk == null) {
            return LinkRepairResult.SKIPPED_CHUNK_UNLOADED;
        }

        BlockType atType = world.getBlockType(cx, cy, cz);
        boolean blockIsCharter =
            atType != null && AetherhavenConstants.CHARTER_BLOCK_TYPE_ID.equals(atType.getId());

        if (blockIsCharter) {
            if (isCharterLinked(chunk, cx, cy, cz, townIdStr)) {
                return LinkRepairResult.ALREADY_OK;
            }
            if (!ensureCharterBlockEntity(world, chunk, cx, cy, cz, townIdStr)) {
                LOGGER.atWarning().log(
                    "Charter link repair failed at %s,%s,%s town=%s (block entity attach)",
                    cx,
                    cy,
                    cz,
                    townIdStr
                );
                return LinkRepairResult.FAILED;
            }
            town.setCharterPosition(cx, cy, cz);
            return LinkRepairResult.RELINKED;
        }

        if (!isReplaceableForCharter(world, cx, cy, cz)) {
            return LinkRepairResult.FAILED_BLOCKED;
        }
        if (!placeAndLinkCharter(world, chunk, cx, cy, cz, horizontalYaw, townIdStr)) {
            return LinkRepairResult.FAILED;
        }
        town.setCharterPosition(cx, cy, cz);
        return LinkRepairResult.PLACED;
    }

    private static boolean placeAndLinkCharter(
        @Nonnull World world,
        @Nonnull WorldChunk chunk,
        int x,
        int y,
        int z,
        @Nonnull Rotation horizontalYaw,
        @Nonnull String townIdStr
    ) {
        BlockType blockType = BlockType.getAssetMap().getAsset(AetherhavenConstants.CHARTER_ITEM_ID);
        if (blockType == null) {
            LOGGER.atWarning().log("Charter block type missing: %s", AetherhavenConstants.CHARTER_ITEM_ID);
            return false;
        }
        RotationTuple rt = RotationTuple.of(horizontalYaw, Rotation.None, Rotation.None);
        boolean placed = chunk.placeBlock(x, y, z, AetherhavenConstants.CHARTER_ITEM_ID, rt, PLACE_SETTINGS, false);
        if (!placed) {
            LOGGER.atWarning().log("Charter placeBlock failed at %s,%s,%s", x, y, z);
            return false;
        }
        chunk.setTicking(x, y, z, true);
        if (!ensureCharterBlockEntity(world, chunk, x, y, z, townIdStr)) {
            world.breakBlock(x, y, z, BREAK_SETTINGS);
            LOGGER.atWarning().log(
                "Charter placed at %s,%s,%s but block entity attach failed town=%s",
                x,
                y,
                z,
                townIdStr
            );
            return false;
        }
        return true;
    }

    private static boolean isCharterLinked(
        @Nonnull WorldChunk chunk, int x, int y, int z, @Nonnull String townIdStr
    ) {
        Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(x, y, z);
        if (blockRef == null || !blockRef.isValid()) {
            return false;
        }
        CharterBlock existing = blockRef.getStore().getComponent(blockRef, CharterBlock.getComponentType());
        return existing != null && townIdStr.equals(existing.getTownId());
    }

    /**
     * Attaches {@link CharterBlock} via {@link BlockEntity#setBlockEntity} when missing (PLACE_SETTINGS skips
     * automatic attachment), then writes town id on the live ref or pending holder.
     */
    private static boolean ensureCharterBlockEntity(
        @Nonnull World world,
        @Nonnull WorldChunk chunk,
        int x,
        int y,
        int z,
        @Nonnull String townIdStr
    ) {
        BlockType blockType = world.getBlockType(x, y, z);
        if (blockType == null || !AetherhavenConstants.CHARTER_BLOCK_TYPE_ID.equals(blockType.getId())) {
            return false;
        }
        BlockComponentChunk blockComponentChunk = chunk.getBlockComponentChunk();
        if (blockComponentChunk == null) {
            return false;
        }
        chunk.setTicking(x, y, z, true);
        Ref<ChunkStore> liveRef = chunk.getBlockComponentEntity(x, y, z);
        if (liveRef == null || !liveRef.isValid()) {
            Holder<ChunkStore> template = blockType.getBlockEntity();
            if (template == null) {
                return false;
            }
            Holder<ChunkStore> holder = template.clone();
            holder.putComponent(CharterBlock.getComponentType(), new CharterBlock(townIdStr));
            Ref<ChunkStore> chunkRef = chunk.getReference();
            if (chunkRef == null || world.getChunkStore() == null) {
                return false;
            }
            BlockEntity.setBlockEntity(
                world.getChunkStore().getStore(),
                chunkRef,
                blockComponentChunk,
                x,
                y,
                z,
                blockType,
                PlotBlockRotationUtil.readBlockRotationIndex(world, new Vector3i(x, y, z)),
                holder
            );
        }
        liveRef = chunk.getBlockComponentEntity(x, y, z);
        if (liveRef == null || !liveRef.isValid()) {
            return false;
        }
        liveRef.getStore().putComponent(liveRef, CharterBlock.getComponentType(), new CharterBlock(townIdStr));
        return true;
    }

    private static void sendReplaceDone(@Nullable PlayerRef feedback, @Nonnull TownRecord town) {
        if (feedback != null) {
            feedback.sendMessage(
                Message.translation("aetherhaven_town.aetherhaven.town.charterReplace.done")
                    .param("town", town.getDisplayName())
            );
        }
    }

    private static void sendReplaceMsg(@Nullable PlayerRef feedback, @Nonnull String translationKey) {
        if (feedback != null) {
            feedback.sendMessage(Message.translation(translationKey));
        }
    }

    private static boolean isReplaceableForCharter(@Nonnull World world, int x, int y, int z) {
        BlockType t = world.getBlockType(x, y, z);
        return t == null || t.getMaterial() == BlockMaterial.Empty;
    }

    private static void sendError(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull String text) {
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.raw(text));
        }
    }
}
