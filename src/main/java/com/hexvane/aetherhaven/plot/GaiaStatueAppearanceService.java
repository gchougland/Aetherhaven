package com.hexvane.aetherhaven.plot;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorSession;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorSessions;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorGaiaStatueSupport;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownMemberBlockAccess;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.UUID;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** All calls run on the world thread; no polling or asset reloads are needed for appearance changes. */
public final class GaiaStatueAppearanceService {
    private GaiaStatueAppearanceService() {}

    public static boolean isEditorStatue(World world, Vector3i pos, UUID playerId) {
        PlotCreatorSession session = PlotCreatorSessions.get(playerId);
        return session != null && session.getWorld() == world
            && session.getDraft().getCornerFirst() != null && session.getDraft().getCornerSecond() != null
            && session.getDraft().isInsideBounds(pos);
    }

    @Nullable
    public static GaiaStatueBlock componentAt(World world, Vector3i pos) {
        BlockType type = ChunkSectionBlockUtil.blockType(world, pos.x, pos.y, pos.z);
        if (type == null || !GaiaStatueAppearance.isGaiaStatue(type.getId())) return null;
        Ref<ChunkStore> ref = ChunkSectionBlockUtil.blockEntityRefAt(world, pos.x, pos.y, pos.z);
        return ref != null && ref.isValid() ? ref.getStore().getComponent(ref, GaiaStatueBlock.getComponentType()) : null;
    }

    public static boolean canChange(World world, Vector3i pos, UUID playerId) {
        GaiaStatueBlock block = componentAt(world, pos);
        if (block == null) return false;
        // Editor copies can carry the source plot's component; bounds/session ownership grants authoring access.
        if (isEditorStatue(world, pos, playerId)) return true;
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) return false;
        TownRecord town = TownMemberBlockAccess.townFromId(
            AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin), block.getTownId());
        return town != null && town.playerCanManageConstructions(playerId);
    }

    public static boolean change(World world, Vector3i pos, UUID playerId, String targetId) {
        GaiaStatueAppearance appearance = GaiaStatueAppearance.fromBlockTypeId(targetId);
        if (appearance == null || !canChange(world, pos, playerId)) return false;
        BlockType current = ChunkSectionBlockUtil.blockType(world, pos.x, pos.y, pos.z);
        if (current == null) return false;
        if (!appearance.blockTypeId().equals(current.getId())) {
            int rotation = PlotBlockRotationUtil.readBlockRotationIndex(world, pos);
            // All variants share the original hitbox. Rebuild its fillers with the new ID, preserving links.
            int settings = SetBlockSettings.NO_UPDATE_STATE | SetBlockSettings.NO_SEND_PARTICLES
                | SetBlockSettings.NO_UPDATE_NEIGHBOR_CONNECTIONS;
            if (!ChunkSectionBlockUtil.setBlockByKey(world, pos.x, pos.y, pos.z,
                appearance.blockTypeId(), rotation, settings)) return false;
        }
        if (isEditorStatue(world, pos, playerId)) {
            var draft = PlotCreatorSessions.get(playerId).getDraft();
            PlotCreatorGaiaStatueSupport.captureAppearance(world, draft);
            draft.setGaiaAppearancePrefabDirty(true);
        }
        return true;
    }
}
