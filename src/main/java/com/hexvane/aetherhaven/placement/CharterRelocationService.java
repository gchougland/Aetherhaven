package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plot.CharterBlock;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class CharterRelocationService {
    private static final int PLACE_SETTINGS = 10;
    private static final int BREAK_SETTINGS = 10;

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
        if (a.x == ox && a.y == oy && a.z == oz) {
            sendError(store, ref, "Choose a different block than the current charter position.");
            return false;
        }
        if (!tm.allPlotFootprintsFitTerritoryWithCharterAt(town, a.x, a.z)) {
            sendError(
                store,
                ref,
                "Moving the charter here would leave one or more buildings outside your territory. Try a position closer to your buildings."
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
        RotationTuple rt = RotationTuple.of(yaw, Rotation.None, Rotation.None);
        boolean placed = chunk.placeBlock(a.x, a.y, a.z, AetherhavenConstants.CHARTER_ITEM_ID, rt, PLACE_SETTINGS, false);
        if (!placed) {
            sendError(store, ref, "Could not place the charter block (spot blocked?).");
            return false;
        }
        Ref<ChunkStore> newRef = chunk.getBlockComponentEntity(a.x, a.y, a.z);
        if (newRef == null) {
            world.breakBlock(a.x, a.y, a.z, BREAK_SETTINGS);
            sendError(store, ref, "Charter block failed to link (see server log).");
            return false;
        }
        Store<ChunkStore> cs = newRef.getStore();
        cs.putComponent(newRef, CharterBlock.getComponentType(), new CharterBlock(town.getTownId().toString()));

        world.breakBlock(ox, oy, oz, BREAK_SETTINGS);

        town.setCharterPosition(a.x, a.y, a.z);
        tm.updateTown(town);

        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.chartertown.charterMoved"));
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

        String townIdStr = town.getTownId().toString();
        int cx = town.getCharterX();
        int cy = town.getCharterY();
        int cz = town.getCharterZ();

        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(cx, cz));
        if (chunk == null) {
            sendReplaceMsg(feedback, "aetherhaven_town.aetherhaven.town.charterReplace.chunkNotLoaded");
            return false;
        }

        BlockType atType = world.getBlockType(cx, cy, cz);
        boolean blockIsCharter =
            atType != null && AetherhavenConstants.CHARTER_BLOCK_TYPE_ID.equals(atType.getId());

        if (blockIsCharter) {
            Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(cx, cy, cz);
            if (blockRef == null || !blockRef.isValid()) {
                sendReplaceMsg(feedback, "aetherhaven_town.aetherhaven.town.charterReplace.linkFailed");
                return false;
            }
            Store<ChunkStore> cs = blockRef.getStore();
            CharterBlock existing = cs.getComponent(blockRef, CharterBlock.getComponentType());
            if (existing != null && townIdStr.equals(existing.getTownId())) {
                sendReplaceMsg(feedback, "aetherhaven_town.aetherhaven.town.charterReplace.alreadyOk");
                return true;
            }
            cs.putComponent(blockRef, CharterBlock.getComponentType(), new CharterBlock(townIdStr));
            town.setCharterPosition(cx, cy, cz);
            tm.updateTown(town);
            sendReplaceDone(feedback, town);
            return true;
        }

        if (!isReplaceableForCharter(world, cx, cy, cz)) {
            sendReplaceMsg(feedback, "aetherhaven_town.aetherhaven.town.charterReplace.clearFirst");
            return false;
        }
        RotationTuple rt = RotationTuple.of(horizontalYaw, Rotation.None, Rotation.None);
        boolean placed = chunk.placeBlock(cx, cy, cz, AetherhavenConstants.CHARTER_ITEM_ID, rt, PLACE_SETTINGS, false);
        if (!placed) {
            sendReplaceMsg(feedback, "aetherhaven_town.aetherhaven.town.charterReplace.placeFailed");
            return false;
        }
        Ref<ChunkStore> newRef = chunk.getBlockComponentEntity(cx, cy, cz);
        if (newRef == null || !newRef.isValid()) {
            world.breakBlock(cx, cy, cz, BREAK_SETTINGS);
            sendReplaceMsg(feedback, "aetherhaven_town.aetherhaven.town.charterReplace.linkFailed");
            return false;
        }
        Store<ChunkStore> cs = newRef.getStore();
        cs.putComponent(newRef, CharterBlock.getComponentType(), new CharterBlock(townIdStr));
        town.setCharterPosition(cx, cy, cz);
        tm.updateTown(town);
        sendReplaceDone(feedback, town);
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
