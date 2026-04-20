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
            sendError(store, ref, "That spot is blocked — choose an empty or replaceable block for the charter.");
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
            pr.sendMessage(Message.translation("server.aetherhaven.ui.chartertown.charterMoved"));
        }
        return true;
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
