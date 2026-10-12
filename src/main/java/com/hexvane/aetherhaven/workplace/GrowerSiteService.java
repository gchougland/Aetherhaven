package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.economy.TownEconomyPresence;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import java.util.*;

/**
 * Everything a grower site does in the world: finding its beds, reading whether a living plant is ripe, showing a log
 * bed's stage, and the native harvest of a bush. It only ever reads chunks that are already being simulated.
 */
public final class GrowerSiteService {
    private GrowerSiteService() {}

    /** @param found every bed block inside the area, which can be more than the site uses */
    public record Habitat(boolean simulated, String issue, List<WarehouseBatch.Point> beds, int found, WarehouseBatch.Point bench) {
        public Habitat { beds = List.copyOf(beds); }
        public boolean ready() { return simulated && issue == null && !beds.isEmpty(); }
    }
    private record Cached(WorkplaceArea area, TownsfolkWorkType type, long until, Habitat habitat) {}
    private static final Map<World, Map<UUID, Cached>> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    public static GrowerPlan plan(TownsfolkWorkType type) {
        var plugin = AetherhavenPlugin.get();
        return plugin == null || !GrowerPlan.supports(type) ? null : plugin.getGrowerPlans().find(type);
    }

    /** The id a block had before any of its states were applied. */
    static String baseId(BlockType block) {
        return block == null ? null : block.getDefaultStateKey() == null ? block.getId() : block.getDefaultStateKey();
    }

    /** The state a block is showing. A block in none of its named states is in its default one. */
    private static String shownState(BlockType block) {
        String state = block.getStateForBlock(block);
        return state == null ? "default" : state;
    }

    private static boolean simulated(World world, WorkplaceArea area) {
        for (int x = ChunkUtil.chunkCoordinate(area.minX()); x <= ChunkUtil.chunkCoordinate(area.maxX() - 1); x++)
            for (int z = ChunkUtil.chunkCoordinate(area.minZ()); z <= ChunkUtil.chunkCoordinate(area.maxZ() - 1); z++)
                if (ChunkSectionBlockUtil.worldChunkIfTicking(world, ChunkUtil.indexChunk(x, z)) == null) return false;
        return true;
    }

    public static Habitat inspect(World world, ApprenticeWorkplace site) {
        var area = site.area();
        var plan = plan(site.type());
        if (plan == null || area == null || !area.valid()) return new Habitat(false, "plan", List.of(), 0, null);
        // Read chunk simulation on every use so the short cache cannot keep an unloaded site working.
        if (!simulated(world, area)) return new Habitat(false, "visit", List.of(), 0, null);
        Map<UUID, Cached> cached;
        synchronized (CACHE) { cached = CACHE.computeIfAbsent(world, w -> new HashMap<>()); }
        long now = System.currentTimeMillis();
        var old = cached.get(site.id());
        if (old != null && old.until > now && old.area.equals(area) && old.type == site.type()) return old.habitat;
        var result = scan(world, site, plan);
        cached.values().removeIf(c -> c.until < now - 60_000);
        cached.put(site.id(), new Cached(area, site.type(), now + 1000, result));
        return result;
    }

    private static Habitat scan(World world, ApprenticeWorkplace site, GrowerPlan plan) {
        var area = site.area();
        var beds = new ArrayList<WarehouseBatch.Point>();
        WarehouseBatch.Point bench = null;
        for (int y = area.floorY(); y < area.floorY() + area.height(); y++)
            for (int x = area.minX(); x < area.maxX(); x++) for (int z = area.minZ(); z < area.maxZ(); z++) {
                var section = ChunkSectionBlockUtil.blockSectionAt(world, x, y, z);
                if (section == null || section.getFiller(x, y, z) != 0) continue;
                var block = ChunkSectionBlockUtil.blockType(world, x, y, z);
                if (block == null) continue;
                if (bench == null && plan.bench() != null && block.getBench() != null && plan.bench().equals(block.getBench().getId())
                    && WorkplaceBenchAccess.matches(site.type(), plan.bench(), baseId(block))) bench = new WarehouseBatch.Point(x, y, z);
                if (plan.isBed(baseId(block))) beds.add(new WarehouseBatch.Point(x, y, z));
            }
        String issue = plan.bench() != null && bench == null ? "bench" : beds.isEmpty() ? "beds" : null;
        return new Habitat(true, issue, beds, beds.size(), bench);
    }

    /** Bring the saved beds in line with the world. Returns the habitat it used, or null when the site cannot be read. */
    public static Habitat sync(World world, ApprenticeWorkplace site) {
        var plan = plan(site.type());
        if (plan == null) return null;
        var habitat = inspect(world, site);
        if (!habitat.simulated()) return habitat;
        site.grower().sync(plan, habitat.beds(), habitat.bench());
        return habitat;
    }

    /** A living plant is ripe when the game offers it for harvest. A log bed is ripe when its saved stage says so. */
    public static boolean ripe(World world, GrowerPlan plan, WarehouseBatch.Point p) {
        if (plan.logBeds()) return true;
        if (ChunkSectionBlockUtil.worldChunkIfTicking(world, ChunkUtil.indexChunkFromBlock(p.x(), p.z())) == null) return false;
        var block = ChunkSectionBlockUtil.blockType(world, p.x(), p.y(), p.z());
        return block != null && plan.isBed(baseId(block)) && block.getFarming() != null
            && block.getGathering() != null && block.getGathering().getHarvest() != null;
    }

    /** The next job on this site for a worker of the given Farming skill, or null when there is none right now. */
    public static GrowerSite.Task task(World world, ApprenticeWorkplace site, int skill, org.joml.Vector3d from) {
        var plan = plan(site.type());
        if (plan == null) return null;
        var habitat = sync(world, site);
        if (habitat == null || !habitat.ready()) return null;
        var crop = site.grower().chosen(plan);
        if (crop == null || crop.skill() > skill) return null;
        return site.grower().next(plan, crop, bed -> ripe(world, plan, bed.point()),
            from == null ? null : bed -> from.distanceSquared(bed.point().x() + .5, bed.point().y(), bed.point().z() + .5));
    }

    /** Show a log bed's saved stage on its block. Returns true when the block shows it afterwards. */
    public static boolean show(World world, GrowerPlan plan, GrowerPlan.Crop crop, GrowerSite.Bed bed, int stage) {
        if (!plan.logBeds()) return true;
        var p = bed.point();
        if (ChunkSectionBlockUtil.worldChunkIfTicking(world, ChunkUtil.indexChunkFromBlock(p.x(), p.z())) == null) return false;
        var block = ChunkSectionBlockUtil.blockType(world, p.x(), p.y(), p.z());
        if (block == null || !plan.isBed(baseId(block))) return false;
        String wanted = plan.logState(crop, stage);
        if (wanted.equals(shownState(block))) return true;
        if (block.getBlockKeyForState(wanted) == null) return false;
        var section = world.getChunkStore().getChunkSectionReferenceAtBlock(p.x(), p.y(), p.z());
        if (section == null || !section.isValid()) return false;
        // The game's own state change: it keeps the log's rotation and tells nearby players.
        BlockOperations.setBlockInteractionState(world.getChunkStore(), section, p.x(), p.y(), p.z(), block, wanted, false);
        return true;
    }

    /**
     * Do a finished job in the world and record it. Call outside entity iteration: it changes blocks.
     *
     * @return true when the job was done and saved state moved on
     */
    public static boolean complete(World world, ApprenticeWorkplace site, WarehouseBatch.Point point, GrowerSite.Job job) {
        var plan = plan(site.type());
        if (plan == null || !inspect(world, site).ready()) return false;
        var grower = site.grower();
        var bed = grower.bed(point);
        var crop = grower.chosen(plan);
        if (bed == null || crop == null) return false;
        boolean ripe = !plan.logBeds() && bed.stage() > plan.tends() && ripe(world, plan, point);
        return grower.complete(plan, crop, bed, job, ripe, () -> {
            if (!plan.logBeds()) {
                if (job != GrowerSite.Job.PICK) return ripe(world, plan, point) || growing(world, plan, point);
                // The bush's own harvest: it drops back to its regrowing stage and ripens again by itself.
                var block = ChunkSectionBlockUtil.blockType(world, point.x(), point.y(), point.z());
                return block != null && FoodSiteService.regrowCrop(world, point, baseId(block));
            }
            int after = switch (job) { case SEED -> 1; case TEND -> bed.stage() + 1; case PICK -> 0; };
            return show(world, plan, crop, bed, after);
        });
    }

    private static boolean growing(World world, GrowerPlan plan, WarehouseBatch.Point p) {
        var block = ChunkSectionBlockUtil.blockType(world, p.x(), p.y(), p.z());
        return block != null && plan.isBed(baseId(block));
    }

    /** Put every log bed's block back in step with its saved stage, for example after the crop was changed. */
    public static void refreshBeds(World world, ApprenticeWorkplace site) {
        var plan = plan(site.type());
        if (plan == null || !plan.logBeds() || site.existingGrower() == null) return;
        var crop = site.grower().chosen(plan);
        for (var bed : site.grower().beds()) show(world, plan, crop, bed, bed.stage());
    }

    /** Resting time for every simulated grower site of every town with a member online. */
    public static void advance(World world, AetherhavenPlugin plugin, long minutes) {
        if (minutes <= 0) return;
        var online = TownEconomyPresence.onlinePlayers(world.getEntityStore().getStore());
        if (online.isEmpty()) return;
        var manager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        for (var town : manager.allTowns()) {
            if (!TownEconomyPresence.hasMember(town, online)) continue;
            boolean changed = false;
            for (var site : town.getApprenticeWorkplaces().sites()) {
                var plan = plan(site.type());
                if (plan == null) continue;
                var habitat = inspect(world, site);
                if (!habitat.simulated()) continue;
                long before = site.grower().revision();
                site.grower().sync(plan, habitat.beds(), habitat.bench());
                changed |= site.grower().revision() != before;
                changed |= site.grower().advance(plan, minutes, true, true);
                // A log that was replaced, or whose chunk saved out of step with the town, is repainted here.
                if (plan.logBeds() && mismatched(world, plan, site)) world.execute(() -> refreshBeds(world, site));
            }
            if (changed) manager.updateTown(town);
        }
    }

    private static boolean mismatched(World world, GrowerPlan plan, ApprenticeWorkplace site) {
        var crop = site.grower().chosen(plan);
        for (var bed : site.grower().beds()) {
            var p = bed.point();
            var block = ChunkSectionBlockUtil.blockType(world, p.x(), p.y(), p.z());
            if (block == null || !plan.isBed(baseId(block))) continue;
            if (!plan.logState(crop, bed.stage()).equals(shownState(block))) return true;
        }
        return false;
    }
}
