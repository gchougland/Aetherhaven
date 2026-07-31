package com.hexvane.aetherhaven.territory;

import com.hexvane.aetherhaven.plotcreator.PlotCreatorSession;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorSessions;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownTerritoryBypassAccess;
import com.hexvane.aetherhaven.town.TownTerritoryClaims;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class TownTerritoryGuard {
    public enum UseKind {
        CONTAINER,
        DOOR,
        OTHER
    }

    private TownTerritoryGuard() {}

    @Nullable
    public static TownRecord findClaimTown(@Nonnull TownManager tm, @Nonnull String worldName, @Nonnull Vector3i blockPos) {
        return tm.findTownContainingBlock(worldName, blockPos.x, blockPos.z);
    }

    public static boolean shouldBypassPlayer(
        @Nonnull Player player,
        @Nonnull UUID playerUuid,
        @Nonnull Vector3i blockPos,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref
    ) {
        if (player.getGameMode() == GameMode.Creative) {
            return true;
        }
        if (TownTerritoryBypassAccess.canModifyAnyTownClaim(store, ref)) {
            return true;
        }
        PlotCreatorSession session = PlotCreatorSessions.get(playerUuid);
        if (session != null && session.getDraft().isInsideBounds(blockPos)) {
            return true;
        }
        return false;
    }

    public static boolean isHarvestStyleBreak(@Nonnull BlockType blockType) {
        BlockGathering gathering = blockType.getGathering();
        return gathering != null && gathering.getHarvest() != null;
    }

    @Nonnull
    public static UseKind classifyUseBlock(@Nonnull BlockType blockType) {
        if (blockType.getInteractions() != null) {
            for (String interactionId : blockType.getInteractions().values()) {
                if (interactionId == null) {
                    continue;
                }
                if (interactionId.contains("OpenContainer") || interactionId.contains("Container")) {
                    return UseKind.CONTAINER;
                }
                if (interactionId.contains("Door")) {
                    return UseKind.DOOR;
                }
            }
        }
        String id = blockType.getId();
        if (id != null) {
            String lower = id.toLowerCase();
            if (lower.contains("chest") || lower.contains("container") || lower.contains("barrel")) {
                return UseKind.CONTAINER;
            }
            if (lower.contains("door") || lower.contains("gate")) {
                return UseKind.DOOR;
            }
        }
        return UseKind.OTHER;
    }

    public static boolean playerMayBreak(@Nonnull TownRecord town, @Nonnull UUID playerUuid, @Nonnull BlockType blockType) {
        if (town.getOwnerUuid().equals(playerUuid)) {
            return true;
        }
        if (!town.hasMemberOrOwner(playerUuid)) {
            return false;
        }
        if (isHarvestStyleBreak(blockType)) {
            return town.playerCanHarvestBlocks(playerUuid);
        }
        return town.playerCanBreakBlocks(playerUuid);
    }

    public static boolean playerMayPlace(@Nonnull TownRecord town, @Nonnull UUID playerUuid) {
        if (town.getOwnerUuid().equals(playerUuid)) {
            return true;
        }
        if (!town.hasMemberOrOwner(playerUuid)) {
            return false;
        }
        return town.playerCanPlaceBlocks(playerUuid);
    }

    public static boolean playerMayUse(@Nonnull TownRecord town, @Nonnull UUID playerUuid, @Nonnull UseKind kind) {
        if (town.getOwnerUuid().equals(playerUuid)) {
            return true;
        }
        if (!town.hasMemberOrOwner(playerUuid)) {
            return false;
        }
        return switch (kind) {
            case CONTAINER -> town.playerCanOpenContainers(playerUuid);
            case DOOR -> town.playerCanUseDoors(playerUuid);
            case OTHER -> town.playerCanBreakBlocks(playerUuid) || town.playerCanPlaceBlocks(playerUuid);
        };
    }

    @Nullable
    public static UUID resolvePlayerUuid(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        return uc != null ? uc.getUuid() : null;
    }

    public static boolean blockInTownClaims(
        @Nonnull TownManager tm,
        @Nonnull World world,
        @Nonnull Vector3i pos
    ) {
        TownRecord town = findClaimTown(tm, world.getName(), pos);
        return town != null && TownTerritoryClaims.containsBlock(town, pos.x, pos.z);
    }
}
