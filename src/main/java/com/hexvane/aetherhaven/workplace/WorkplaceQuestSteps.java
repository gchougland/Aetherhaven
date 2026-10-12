package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.quest.WorldProof;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.List;

/**
 * The four things a town does to get a working workplace, as facts a quest can check: a type is unlocked, an area is
 * marked, the area has a type, and that type's station stands inside it. The first three are read from the town's
 * save. The last needs the world, and only where it is loaded.
 *
 * <p>A station is the block a workplace is built around: the bench of most types, the trough of a ranch, the beds of
 * a grower site that has no bench of its own.
 */
public final class WorkplaceQuestSteps {
    private WorkplaceQuestSteps() {}

    /** A workplace type by its saved id, or null when a quest names none (any type counts) or names nonsense. */
    public static TownsfolkWorkType type(String id) {
        return id == null || id.isBlank() ? null : TownsfolkWorkType.byId(id.trim());
    }

    private static boolean counts(TownsfolkWorkType type, TownsfolkWorkType only) {
        return type != null && type.requiresWorkplace() && type.availableForNewWork() && (only == null || type == only);
    }

    /** The types a town may mark workplaces for now. */
    public static List<TownsfolkWorkType> unlockedTypes(TownRecord town) {
        var sites = town.getApprenticeWorkplaces();
        var unlocked = new ArrayList<TownsfolkWorkType>();
        for (var type : WorkplaceTypePicker.creatable()) if (sites.unlocked(type)) unlocked.add(type);
        return unlocked;
    }

    public static boolean typeUnlocked(TownRecord town, TownsfolkWorkType only) {
        for (var type : unlockedTypes(town)) if (counts(type, only)) return true;
        return false;
    }

    /**
     * The workplaces a player marked, oldest first. A tunnel planned from a mine's map is part of its mine, not a
     * workplace of its own.
     */
    public static List<ApprenticeWorkplace> markedSites(TownRecord town, TownsfolkWorkType only) {
        var marked = new ArrayList<ApprenticeWorkplace>();
        for (var site : town.getApprenticeWorkplaces().sites())
            if (site.mineBaseId() == null && counts(site.type(), only)) marked.add(site);
        return marked;
    }

    /** A saved workplace always has its area and its type, so one fact answers both steps. */
    public static boolean siteExists(TownRecord town, TownsfolkWorkType only) {
        return !markedSites(town, only).isEmpty();
    }

    /** A block as the search for a station needs it. */
    public record Standing(String benchId, String benchBaseBlock, String baseBlock) {}

    /** The blocks of a world, or of a test. */
    public interface Blocks {
        /** False when the chunk is not loaded and running. Nothing in it can be read. */
        boolean loaded(int chunkX, int chunkZ);

        /** What stands at a position: null for air, for the side block of a larger block, or for nothing readable. */
        Standing at(int x, int y, int z);
    }

    static Standing standing(BlockType block) {
        if (block == null) return null;
        String bench = WorkplaceEquipment.benchId(block);
        return new Standing(bench, bench == null ? null : BenchBlock.getBaseBlockType(block).getId(), GrowerSiteService.baseId(block));
    }

    /** Reads terrain that is already loaded. Never asks for a chunk. */
    public static Blocks of(World world) {
        return new Blocks() {
            @Override public boolean loaded(int chunkX, int chunkZ) {
                return ChunkSectionBlockUtil.worldChunkIfTicking(world, ChunkUtil.indexChunk(chunkX, chunkZ)) != null;
            }
            @Override public Standing at(int x, int y, int z) {
                var section = ChunkSectionBlockUtil.blockSectionAt(world, x, y, z);
                if (section == null || section.getFiller(x, y, z) != 0) return null;
                return standing(ChunkSectionBlockUtil.blockType(world, x, y, z));
            }
        };
    }

    /**
     * Whether a block is the station of a workplace type. The rule is the one the workplaces themselves use: a bench
     * by {@link WorkplaceBenchAccess#matches}, and for a grower site with no bench, one of its beds.
     */
    public static boolean isStation(TownsfolkWorkType type, GrowerPlan plan, Standing block) {
        if (type == null || block == null) return false;
        if (plan != null && plan.bench() == null) return plan.isBed(block.baseBlock());
        return block.benchId() != null && WorkplaceBenchAccess.matches(type, block.benchId(), block.benchBaseBlock());
    }

    /** Looks through one workplace for its station and stops at the first one. */
    public static WorldProof scan(Blocks blocks, WorkplaceArea area, TownsfolkWorkType type, GrowerPlan plan) {
        if (area == null || !area.valid() || type == null) return WorldProof.NO;
        boolean unread = false;
        for (int cx = ChunkUtil.chunkCoordinate(area.minX()); cx <= ChunkUtil.chunkCoordinate(area.maxX() - 1); cx++)
            for (int cz = ChunkUtil.chunkCoordinate(area.minZ()); cz <= ChunkUtil.chunkCoordinate(area.maxZ() - 1); cz++) {
                if (!blocks.loaded(cx, cz)) { unread = true; continue; }
                int x0 = Math.max(area.minX(), cx * 32), x1 = Math.min(area.maxX(), cx * 32 + 32);
                int z0 = Math.max(area.minZ(), cz * 32), z1 = Math.min(area.maxZ(), cz * 32 + 32);
                for (int x = x0; x < x1; x++) for (int z = z0; z < z1; z++)
                    for (int y = area.floorY(); y < area.floorY() + area.height(); y++)
                        if (isStation(type, plan, blocks.at(x, y, z))) return WorldProof.YES;
            }
        return unread ? WorldProof.UNKNOWN : WorldProof.NO;
    }

    /**
     * Whether any of the town's workplaces has its station inside it. One is enough. With none found, the answer is
     * {@link WorldProof#UNKNOWN} while any part of any workplace is out of sight.
     */
    public static WorldProof stationProof(Blocks blocks, TownRecord town, TownsfolkWorkType only, GrowerCatalog growers) {
        boolean unknown = false;
        for (var site : markedSites(town, only)) {
            var proof = scan(blocks, site.area(), site.type(), growers == null ? null : growers.find(site.type()));
            if (proof == WorldProof.YES) return proof;
            unknown |= proof == WorldProof.UNKNOWN;
        }
        return unknown ? WorldProof.UNKNOWN : WorldProof.NO;
    }

    /** The workplace whose station stands at this block, or null. For a block the player has just set down or used. */
    public static ApprenticeWorkplace stationSiteAt(Blocks blocks, TownRecord town, GrowerCatalog growers, int x, int y, int z) {
        var block = blocks.at(x, y, z);
        if (block == null) return null;
        for (var site : markedSites(town, null))
            if (site.area().contains(x, y, z) && isStation(site.type(), growers == null ? null : growers.find(site.type()), block)) return site;
        return null;
    }
}
