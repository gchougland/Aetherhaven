package com.hexvane.aetherhaven.command;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService;
import com.hexvane.aetherhaven.production.*;
import com.hexvane.aetherhaven.production.FarmFieldWorld.Ground;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import com.hexvane.aetherhaven.workplace.FarmerFieldPage;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import com.hypixel.hytale.server.core.universe.world.storage.provider.EmptyChunkStorageProvider;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.VoidWorldGenProvider;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

/**
 * The Scarecrow Fields in a real world on a real server: every crop the Farmer can grow goes through the whole of
 * her work with the game's own soil, seedlings, growth stages and harvest lists, through the very methods her work
 * service calls. The world is disposable, its blocks tick and its clock is the game's own, which starts far below zero.
 */
final class FarmFieldWorldCheck {
    private FarmFieldWorldCheck() {}
    private static final String ESSENCE = NativeFarmCrops.LIFE_ESSENCE;
    private static final int HOME = FarmFieldsState.HOME;
    private static final DoubleSupplier PLAIN = () -> 0.999, LUCKY = () -> 0.0;

    private static WorldConfig config() {
        var config = new WorldConfig();
        com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig.ensureAndGet(config);
        config.setWorldGenProvider(new VoidWorldGenProvider()); config.setChunkStorageProvider(EmptyChunkStorageProvider.INSTANCE);
        config.setSavingPlayers(false); config.setCanSaveChunks(false); config.setSaveNewChunks(false); config.setSpawningNPC(false);
        config.setGameTimePaused(true); config.setBlockTicking(true); config.setDeleteOnRemove(true); config.setDeleteOnUniverseStart(true);
        return config;
    }

    /** Runs every check and adds to the tally: results[0] passed, results[1] failed. */
    static CompletableFuture<Void> run(Universe universe, int[] results, Consumer<String> report) {
        String name = "aetherhaven_farm_check_" + UUID.randomUUID().toString().replace("-", "");
        return universe.makeWorld(name, universe.validateWorldPath(name), config()).thenCompose(world -> {
            var order = FarmFields.cropOrder();
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null); int offset = 0;
            for (String item : order) {
                int at = offset++; var crop = NativeFarmCrops.byOutput(item);
                chain = chain.thenCompose(ignored -> tally(cycle(world, crop, order, at), results, report, "Scarecrow Fields " + item,
                    "plan, till, seed, sow, water, growth, harvest, replant, wear, compost, rotation, favoured sprout, Golden Harvest, stores and save passed."));
            }
            int scarecrow = offset++, legacy = offset++, automatic = offset++, almanac = offset;
            chain = chain.thenCompose(ignored -> tally(secondScarecrow(world, scarecrow), results, report, "Scarecrow Fields second Scarecrow",
                "block, claim, limit, reach, farmyard, own plan, wider reach and release passed."));
            chain = chain.thenCompose(ignored -> tally(oldFieldWork(world, legacy), results, report, "Scarecrow Fields old save with field work",
                "beds became the plan, the carried harvest moved to the basket, upgrades kept, and she harvests and resows them."));
            chain = chain.thenCompose(ignored -> tally(oldAutomatic(world, automatic), results, report, "Scarecrow Fields old save on automatic production",
                "stays automatic until a plan is painted, keeps its stock and gets its first seeds once."));
            chain = chain.thenCompose(ignored -> tally(almanacAndItems(world, almanac), results, report, "Scarecrow Fields Almanac and Golden Harvest",
                "weeks on the game's own clock, a week boundary, favoured crops, the item, its shop price and every villager's love passed."));
            return chain.whenComplete((done, error) -> universe.removeWorld(name));
        });
    }

    private static CompletableFuture<Void> tally(CompletableFuture<Void> check, int[] results, Consumer<String> report, String name, String passed) {
        return check.handle((done, error) -> {
            if (error == null) { results[0]++; report.accept(name + ": " + passed); }
            else { results[1]++; Throwable cause = error; while (cause.getCause() != null) cause = cause.getCause(); report.accept(name + ": FAILED: " + cause.getMessage()); }
            return null;
        });
    }

    // Fixture ---------------------------------------------------------------------------------------------------

    /** A town with one finished farm whose Farmer is fully trained and whose every crop is unlocked, at its own spot in the test world. */
    private record Farm(World world, TownRecord town, PlotInstance plot, PlotProductionState production, int x, int y, int z) {
        FarmFieldsState fields() { return production.farm(); }
        ProductionCatalog.Entry entry() {
            var plugin = AetherhavenPlugin.get();
            return ProductionEffectiveCatalog.effective(plugin.getProductionCatalog(), plugin.getWorkplaceUnlockCatalog(), FarmFields.CONSTRUCTION, production);
        }
        FarmFields.Bounds home() { return FarmFields.homeBounds(plot); }
        FarmFieldWorld.Spot look(int cx, int cz) { return FarmFieldWorld.inspect(world, town, plot, true, null, home(), cx, cz); }
        boolean plant(int slot, Point cell, List<String> favoured) {
            return FarmFieldJobs.plant(world, town, plot, production, entry(), slot, cell.x(), cell.z(), favoured);
        }
        FarmFieldsState.Harvest harvest(int slot, Point cell, List<String> favoured, DoubleSupplier dice) {
            return FarmFieldJobs.harvest(world, town, plot, production, entry(), slot, cell.x(), cell.z(), favoured, dice);
        }
        void ground(int cx, int cz, String block) { require(ChunkSectionBlockUtil.setBlockByKey(world, cx, y - 1, cz, block, 0), "Test ground could not be placed"); }
        long essence() { return production.getAmount(ESSENCE); }
        void train() { var mastery = CoreVillagerMasteryService.progress(town, FarmFields.KIND); while (mastery.train()) { } }
        void unlockAll() { for (String crop : FarmFields.cropOrder()) production.addWorkplaceOutputUnlock(crop); }
    }
    private static Farm farm(World world, int offset, PlotProductionState production) {
        int x = 16 + 64 * offset, y = 128, z = 16;
        var town = new TownRecord(UUID.randomUUID(), UUID.randomUUID(), world.getName(), x, y, z, 1, 1, 0);
        var plot = new PlotInstance(UUID.randomUUID(), FarmFields.CONSTRUCTION, PlotInstanceState.COMPLETE,
            new PlotFootprintRecord(x - 3, y - 1, z - 3, x + 3, y + 5, z + 3), x, y, z, 0);
        town.getPlotInstances().add(plot);
        return new Farm(world, town, plot, production != null ? production : FarmFields.production(town, plot), x, y, z);
    }
    private static CompletableFuture<Void> load(World world, int x) {
        var loads = new ArrayList<CompletableFuture<?>>();
        for (int section = 3; section <= 4; section++)
            loads.add(world.getChunkStore().getChunkSectionReferenceAsync(ChunkUtil.chunkCoordinate(x), section, 0, GetChunkFlags.SET_TICKING));
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new));
    }
    /** Takes a crop to its last stage the way the game does, by the stage change the game itself applies. */
    private static void ripen(World world, Point p, NativeFarmCrops.Crop crop) {
        var base = BlockType.getAssetMap().getAsset(crop.block()); var farming = base.getFarming(); var stages = farming.getStages().get(farming.getStartingStageSet());
        var ref = ChunkSectionBlockUtil.blockEntityRefAt(world, p.x(), p.y(), p.z());
        require(ref != null && ref.isValid(), "The crop has no growth record");
        var store = world.getChunkStore().getStore(); var growth = store.getComponent(ref, FarmingBlock.getComponentType());
        require(growth != null, "The crop is not growing");
        growth.setCurrentStageSet(farming.getStartingStageSet()); growth.setPreviousBlockType(crop.block());
        growth.setGeneration(stages.length - 1); growth.setGrowthProgress(stages.length - 1);
        stages[stages.length - 1].apply(store, ChunkSectionBlockUtil.sectionRefAt(world, p.x(), p.y(), p.z()), ref,
            ChunkUtil.localCoordinate(p.x()), ChunkUtil.localCoordinate(p.y()), ChunkUtil.localCoordinate(p.z()), stages[0]);
    }
    private static String soilBlock(World world, Point cell) {
        var type = ChunkSectionBlockUtil.blockType(world, cell.x(), cell.y() - 1, cell.z());
        return type == null ? null : type.getId();
    }
    private static String cropBlock(World world, Point cell) {
        var type = ChunkSectionBlockUtil.blockType(world, cell.x(), cell.y(), cell.z());
        return type == null ? null : type.getId();
    }

    // Every crop -------------------------------------------------------------------------------------------------

    private static CompletableFuture<Void> cycle(World world, NativeFarmCrops.Crop crop, List<String> order, int offset) {
        var f = farm(world, offset, null); var fields = f.fields(); var rules = FarmFieldRules.get();
        int x = f.x(), y = f.y(), z = f.z(), wear = rules.soil().wearEvery();
        String item = crop.output(), seed = NativeFarmCrops.seedFor(item), other = order.get((order.indexOf(item) + 1) % order.size());
        var otherCrop = NativeFarmCrops.byOutput(other);
        var a = new Point(x, y, z); var b = new Point(x + 1, y, z); var c = new Point(x + 2, y, z);
        var chain = load(world, x).thenCompose(ignored -> onWorld(world, () -> {
            f.ground(x, z, "Soil_Dirt"); f.ground(x + 1, z, "Soil_Grass"); f.ground(x + 2, z, "Soil_Mud_Dry");
            f.ground(x - 1, z, "Rock_Stone"); f.ground(x - 2, z, "Soil_Dirt");
            require(ChunkSectionBlockUtil.setBlockByKey(world, x - 2, y, z, "Rock_Stone", 0), "Test block could not be placed");
        }));
        chain = chain.thenCompose(ignored -> after(world, () -> {
            require(FarmFieldWorld.instant(world).getEpochSecond() < 0, "The game's clock was expected to count from year 1, far below zero");
            var spot = f.look(x, z);
            require(spot.ok() && !spot.tilled() && spot.crop() == null && spot.y() == y, "Plain dirt was not offered as a cell: " + spot);
            require(f.look(x + 1, z).ok() && f.look(x + 2, z).ok(), "Grass and dry mud were not offered as cells");
            require(f.look(x - 1, z).ground() == Ground.NOT_SOIL, "Stone was offered as soil");
            require(f.look(x - 2, z).ground() == Ground.BLOCKED, "A cell with a block on it was offered");
            require(!f.production().usesFarmFields(), "A farm with no plan was already doing field work");
            f.unlockAll();
            for (var cell : List.of(a, b, c)) require(fields.paint(HOME, cell.x(), cell.y(), cell.z(), item), "The plan could not be painted");
            require(f.production().usesFarmFields() && fields.plannedCount() == 3, "Painting did not start field work");
            if (FarmFields.mastery(item) > 1) {
                require("mastery".equals(FarmFields.cropIssue(f.town(), f.entry(), item)), "An untrained Farmer may grow " + item);
                f.production().addAmount(ESSENCE, 50, 100);
                require(!f.plant(HOME, a, List.of()) && FarmFieldWorld.free(world, a), "An untrained Farmer planted a crop she has not learnt");
                f.production().removeAmountUpTo(ESSENCE, 50);
            }
            f.train();
            require(FarmFields.cropIssue(f.town(), f.entry(), item) == null, "A trained Farmer with the crop unlocked cannot grow " + item);
            // Seeds. Wheat and lettuce come with the farm. Every other crop needs a seed made from Life Essence.
            int cost = FarmFields.seedCost(seed), stored = fields.seeds(seed);
            require(cost >= 1 && cost <= 20, "The game's recipe for " + seed + " was not read: " + cost);
            if (stored == 0) {
                require(!FarmFieldJobs.seedReady(f.town(), f.plot(), f.production(), item), "A seed was ready with no seeds and no Life Essence");
                require(!f.plant(HOME, a, List.of()) && FarmFieldWorld.free(world, a) && !f.look(x, z).tilled(), "A crop was planted or ground tilled with no seed to sow");
                var missing = FarmFieldWork.missingSeeds(fields, (slot, cell) -> FarmFieldWorld.view(world, f.town(), cell), any -> true, NativeFarmCrops::seedFor,
                    each -> FarmFields.seedCost(NativeFarmCrops.seedFor(each)), FarmFieldJobs.spareEssence(f.town(), f.plot(), f.production()));
                require(Integer.valueOf(3).equals(missing.get(item)), "The status would not say three seeds are needed: " + missing);
                f.production().addAmount(ESSENCE, 100, 100);
            }
            long essence = f.essence();
            require(f.plant(HOME, a, List.of()), "She could not till and sow " + item);
            require(f.look(x, z).tilled() && rules.soil().tilled().equals(soilBlock(world, a)), "The dirt was not tilled into the game's farmland");
            require(crop.block().equals(cropBlock(world, a)), "The game's own seedling block was not set: " + cropBlock(world, a));
            require(crop.equals(FarmFieldWorld.cropAt(world, a)) && !FarmFieldWorld.ripe(world, a, crop), "The seedling is not a growing " + item);
            if (stored > 0) require(fields.seeds(seed) == stored - 1 && f.essence() == essence, "A stored seed was not the one used");
            else require(f.essence() == essence - cost, "Making a seed did not cost the Farming Bench's " + cost + " Life Essence");
            require(FarmFieldWorld.wet(world, a) && !FarmFieldWorld.needsWater(world, a, rules.work().rewaterHours()), "The new seedling was not watered");
            require(!f.plant(HOME, a, List.of()), "A second seed was sown in a cell that already holds a crop");
            ChunkSectionBlockUtil.performBlockUpdate(world, x, y, z, false);
        }));
        // The game's own farmland shows the watering, and the seedling keeps standing on it while blocks tick.
        chain = chain.thenCompose(ignored -> eventually(world, () -> !rules.soil().tilled().equals(soilBlock(world, a)) && FarmFieldWorld.wet(world, a)
            && crop.equals(FarmFieldWorld.cropAt(world, a)), "The farmland never showed the game's watered state, or the seedling did not stay on it", 40));
        // Harvest after harvest of one crop from one cell, each ripened by the game's own stage change.
        for (int round = 1; round <= wear; round++) {
            int count = round;
            chain = chain.thenCompose(ignored -> after(world, () -> ripen(world, a, crop)));
            chain = chain.thenCompose(ignored -> eventually(world, () -> FarmFieldWorld.ripe(world, a, crop), "The crop did not reach its last stage", 40));
            chain = chain.thenCompose(ignored -> onWorld(world, () -> {
                int basket = fields.basket().getOrDefault(item, 0), seeds = fields.seeds(seed); long essence = f.essence();
                var cut = f.harvest(HOME, a, List.of(), PLAIN);
                require(cut != null && cut.crops() >= 1 && cut.essence() >= 1 && !cut.golden() && !cut.seedReturned(), "Harvest " + count + " gave " + cut);
                require(fields.basket().getOrDefault(item, 0) == basket + cut.crops(), "The harvest is not in her basket");
                require(f.production().getAmount(item) == 0, "A harvest reached the stores before she carried it home");
                require(crop.block().equals(cropBlock(world, a)) && !FarmFieldWorld.ripe(world, a, crop), "The cell was not sown again after the harvest");
                require(fields.seeds(seed) == seeds - 1 || f.essence() < essence, "Sowing again used no seed");
                require(cut.soilBefore() == FarmFieldsState.GOOD && cut.soilAfter() == (count == wear ? FarmFieldsState.WORN : FarmFieldsState.GOOD),
                    "Harvest " + count + " of " + wear + " left the soil at " + cut.soilAfter());
                require(f.harvest(HOME, a, List.of(), PLAIN) == null, "An unripe seedling was harvested");
            }));
        }
        // Worn soil holds no water. Spare crops become compost, and compost mends it.
        chain = chain.thenCompose(ignored -> after(world, () -> {
            require(fields.soil(HOME, x, z) == FarmFieldsState.WORN, "The soil is not Worn");
            require(!FarmFieldWorld.wet(world, a), "Worn soil still holds the Farmer's water");
            require(FarmFieldJobs.water(world, f.town(), f.production(), x, y, z) == 0 && !FarmFieldWorld.wet(world, a), "She watered Worn soil");
            long keep = FarmUpgrades.capacity(f.production(), f.entry(), item);
            f.production().addAmount(item, keep, keep);
            int carried = fields.basket().getOrDefault(item, 0);
            var delivery = FarmFieldJobs.deliver(f.town(), f.production(), f.entry());
            require(delivery.composted() == carried && delivery.compostMade() == carried / FarmUpgrades.cropsPerCompost(f.production()) && fields.compost() >= 1,
                "Spare crops did not become compost: " + delivery);
            require(f.production().getAmount(item) == keep && !fields.carrying(), "The stores took more than they keep");
            var target = fields.compostTarget(false);
            require(target != null && target.cell().x() == x && target.cell().z() == z, "The Worn cell is not the one to compost");
            int heap = fields.compost();
            require(FarmFieldJobs.compost(world, f.town(), f.production(), HOME, x, z) && fields.soil(HOME, x, z) == FarmFieldsState.GOOD && fields.compost() == heap - 1,
                "Compost did not mend the Worn cell");
            require(!FarmFieldJobs.compost(world, f.town(), f.production(), HOME, x, z), "Plain compost made Good soil Rich");
            require(FarmFieldJobs.water(world, f.town(), f.production(), x, y, z) == 1 && FarmFieldWorld.wet(world, a), "Mended soil could not be watered");
            f.production().removeAmountUpTo(item, keep);
            // A favoured crop goes in already sprouted.
            require(f.plant(HOME, b, List.of(item)), "She could not sow the second cell");
        }));
        chain = chain.thenCompose(ignored -> eventually(world, () -> {
            var ref = ChunkSectionBlockUtil.blockEntityRefAt(world, b.x(), b.y(), b.z());
            var growing = ref == null || !ref.isValid() ? null : world.getChunkStore().getStore().getComponent(ref, FarmingBlock.getComponentType());
            return crop.equals(FarmFieldWorld.cropAt(world, b)) && !crop.block().equals(cropBlock(world, b)) && growing != null && growing.getGrowthProgress() >= 1
                && !FarmFieldWorld.ripe(world, b, crop);
        }, "A favoured crop was not planted already sprouted", 40));
        // A change of crop: the old crop is gathered when ripe, the new one sown, and the soil becomes Rich.
        chain = chain.thenCompose(ignored -> after(world, () -> ripen(world, b, crop)));
        chain = chain.thenCompose(ignored -> eventually(world, () -> FarmFieldWorld.ripe(world, b, crop), "The second crop did not ripen", 40));
        chain = chain.thenCompose(ignored -> onWorld(world, () -> {
            require(f.harvest(HOME, b, List.of(), PLAIN) != null && crop.block().equals(cropBlock(world, b)), "The second cell was not harvested and sown again");
            require(fields.paint(HOME, b.x(), b.y(), b.z(), other), "The plan could not be changed");
            require(f.harvest(HOME, b, List.of(), PLAIN) == null && crop.equals(FarmFieldWorld.cropAt(world, b)), "A crop from before the plan changed was torn out unripe");
        }));
        chain = chain.thenCompose(ignored -> after(world, () -> ripen(world, b, crop)));
        chain = chain.thenCompose(ignored -> eventually(world, () -> FarmFieldWorld.ripe(world, b, crop), "The crop from before the change did not ripen", 40));
        chain = chain.thenCompose(ignored -> onWorld(world, () -> {
            f.production().addAmount(ESSENCE, 100, 100);
            int before = fields.basket().getOrDefault(item, 0);
            var cut = f.harvest(HOME, b, List.of(), PLAIN);
            require(cut != null && fields.basket().getOrDefault(item, 0) == before + cut.crops(), "The crop from before the change was not gathered as itself");
            require(otherCrop.equals(FarmFieldWorld.cropAt(world, b)), "The newly planned crop was not sown: " + cropBlock(world, b));
            require(fields.soil(HOME, b.x(), b.z()) == FarmFieldsState.RICH, "A change of crop did not make Good soil Rich");
        }));
        chain = chain.thenCompose(ignored -> after(world, () -> ripen(world, b, otherCrop)));
        chain = chain.thenCompose(ignored -> eventually(world, () -> FarmFieldWorld.ripe(world, b, otherCrop), "The rotated crop did not ripen", 40));
        // Rich soil, a favoured crop and a lucky roll: a doubled harvest, one extra, a Golden Harvest and the seed back.
        chain = chain.thenCompose(ignored -> onWorld(world, () -> {
            String golden = rules.golden().item();
            var cut = f.harvest(HOME, b, List.of(other), LUCKY);
            require(cut != null && cut.soilBefore() == FarmFieldsState.RICH, "The Rich cell was not harvested");
            require(cut.crops() >= 2 * 1 + 1 && (cut.crops() - rules.almanac().extraYield()) % rules.soil().richYield() == 0,
                "Rich soil and the Almanac gave " + cut.crops() + ", which is not a doubled harvest and one extra");
            require(cut.golden() && fields.basket().getOrDefault(golden, 0) == 1, "A forced roll on Rich soil gave no Golden Harvest");
            require(cut.seedReturned(), "A forced roll gave no seed back");
            // Home with the basket, then a save and a load.
            var delivery = FarmFieldJobs.deliver(f.town(), f.production(), f.entry());
            require(Integer.valueOf(1).equals(delivery.stored().get(golden)) && f.production().getAmount(golden) == 1, "The Golden Harvest did not reach the stores");
            require(f.production().getAmount(other) == cut.crops() && f.production().getAmount(item) > 0, "The crops did not reach the stores");
            require(CoreVillagerMasteryService.progress(f.town(), FarmFields.KIND).level() >= 1, "Mastery was lost");
            var saved = new Gson().fromJson(new Gson().toJson(f.production()), PlotProductionState.class);
            require(saved.usesFarmFields() && saved.farm().plannedCount() == 3 && other.equals(saved.farm().cell(HOME, b.x(), b.z()).crop())
                && item.equals(saved.farm().cell(HOME, x, z).crop()) && saved.farm().compost() == fields.compost() && saved.farm().seeds().equals(fields.seeds())
                && saved.farm().cell(HOME, b.x(), b.z()).equals(fields.cell(HOME, b.x(), b.z())) && saved.getAmount(golden) == 1, "The plan, soil, seeds, compost or stores did not survive a save");
            // Crops inside a townsfolk workplace are not the Farmer's.
            var resident = new ResidentNpcRecord(); resident.setKind(FarmFields.KIND); f.town().getResidentNpcRecords().add(resident);
            var work = com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.FARMING;
            require(f.town().getApprenticeWorkplaces().unlock(f.town(), work) == null, "Test food workplace unlock failed");
            require(f.town().getApprenticeWorkplaces().save(f.town(), null, work, new com.hexvane.aetherhaven.workplace.WorkplaceArea(x, y, z, x + 3, z + 2), "Test food beds") == null,
                "Test food workplace placement failed");
            require(f.look(x, z).ground() == Ground.WORKPLACE && !FarmFieldWorld.workable(world, f.town(), a), "A townsfolk workplace is still offered to the Farmer");
            // The workplace covers all three cells. The third was never sown, so only the workplace can stop her there.
            require(!FarmFieldWorld.view(world, f.town(), fields.cell(HOME, x, z)).workable(), "A cell inside a townsfolk workplace is still on her round");
            require(f.look(c.x(), c.z()).ground() == Ground.WORKPLACE && !f.plant(HOME, c, List.of()) && FarmFieldWorld.free(world, c), "The Farmer sowed inside a townsfolk workplace");
        }));
        return chain;
    }

    // A second Scarecrow -----------------------------------------------------------------------------------------

    private static CompletableFuture<Void> secondScarecrow(World world, int offset) {
        var f = farm(world, offset, null); var fields = f.fields();
        int x = f.x(), y = f.y(), z = f.z();
        var anchor = new Point(x + 5, y, z); var cell = new Point(x + 6, y, z);
        var chain = load(world, x).thenCompose(ignored -> onWorld(world, () -> {
            var block = BlockType.getAssetMap().getAsset(FarmFields.BLOCK);
            require(block != null && Item.getAssetMap().getAsset(FarmFields.BLOCK) != null, "The Scarecrow is not loaded");
            require("aetherhaven_fields.items.scarecrow.hint".equals(block.getInteractionHint()), "The Scarecrow has no key prompt: " + block.getInteractionHint());
            for (int dx = 3; dx <= 9; dx++) for (int dz = -4; dz <= 4; dz++) f.ground(x + dx, z + dz, "Soil_Dirt");
            require(ChunkSectionBlockUtil.setBlockByKey(world, anchor.x(), anchor.y(), anchor.z(), FarmFields.BLOCK, 0), "The Scarecrow could not be placed");
            ChunkSectionBlockUtil.performBlockUpdate(world, anchor.x(), anchor.y(), anchor.z(), false);
        }));
        chain = chain.thenCompose(ignored -> after(world, () -> {
            var plugin = AetherhavenPlugin.get();
            require(FarmFields.is(FarmFieldWorldCheck.baseId(world, anchor)), "The Scarecrow did not stay standing: " + cropBlock(world, anchor));
            require(FarmerFieldPage.farmFor(plugin, f.town(), anchor) == f.plot(), "The Scarecrow found no farm to belong to");
            f.train(); f.unlockAll(); f.production().addAmount(ESSENCE, 100, 100);
            int allowed = FarmUpgrades.scarecrows(f.production());
            require(allowed == 1 && fields.freeSlot(allowed) == 1, "A new farm should tend one Scarecrow field");
            require(fields.claim(1, allowed, anchor) == null && fields.fieldAt(anchor) == 1, "The Scarecrow could not claim its field");
            require("full".equals(fields.claim(2, allowed, new Point(x + 9, y, z + 4))) && fields.freeSlot(allowed) < 0, "A Scarecrow beyond the limit claimed a field");
            var bounds = FarmFields.bounds(f.plot(), f.production(), 1);
            require(bounds.width() == 5 && bounds.depth() == 5, "The first reach is not two blocks: " + bounds);
            require(FarmFieldWorld.inspect(world, f.town(), f.plot(), false, anchor, bounds, anchor.x(), anchor.z()).ground() == Ground.SCARECROW, "The Scarecrow's own cell can be planted");
            require(FarmFieldWorld.inspect(world, f.town(), f.plot(), false, anchor, bounds, x + 3, z).ground() == Ground.FARMYARD, "The farm's own yard is offered to a Scarecrow field");
            var spot = FarmFieldWorld.inspect(world, f.town(), f.plot(), false, anchor, bounds, cell.x(), cell.z());
            require(spot.ok() && spot.y() == y, "Open dirt beside the Scarecrow is not offered: " + spot);
            String wheat = "Plant_Crop_Wheat_Item";
            require(fields.paint(1, cell.x(), spot.y(), cell.z(), wheat) && fields.owner(cell.x(), cell.z()) == 1, "The Scarecrow field could not be planned");
            require(fields.paint(HOME, x, y, z, wheat) && fields.owner(x, z) == HOME, "The farm's own field lost its plan");
            require(f.plant(1, cell, List.of()) && NativeFarmCrops.byOutput(wheat).equals(FarmFieldWorld.cropAt(world, cell)), "She could not sow in the Scarecrow field");
            fields.setLevel(FarmUpgrades.Track.REACH.name(), FarmUpgrades.maxLevel(FarmUpgrades.Track.REACH));
            require(FarmFields.bounds(f.plot(), f.production(), 1).width() == 9, "Wider reach did not widen the field");
            fields.setLevel(FarmUpgrades.Track.SCARECROWS.name(), 1);
            require(fields.claim(2, FarmUpgrades.scarecrows(f.production()), new Point(x + 9, y, z + 4)) == null, "More Scarecrows did not allow a second Scarecrow field");
            // Breaking the Scarecrow frees its field and leaves its crops standing.
            require(f.production().releaseScarecrowAt(anchor) && fields.fieldAt(anchor) < 0 && fields.owner(cell.x(), cell.z()) < 0, "A broken Scarecrow kept its field");
            require(FarmFieldWorld.cropAt(world, cell) != null && fields.owner(x, z) == HOME && fields.field(2) != null, "Breaking one Scarecrow harmed another field or its crops");
        }));
        return chain;
    }
    private static String baseId(World world, Point p) {
        var type = ChunkSectionBlockUtil.blockType(world, p.x(), p.y(), p.z());
        return type == null ? null : type.getDefaultStateKey() != null ? type.getDefaultStateKey() : type.getId();
    }

    // Old saves --------------------------------------------------------------------------------------------------

    private static CompletableFuture<Void> oldFieldWork(World world, int offset) {
        var old = new PlotProductionState(); var f = farm(world, offset, old);
        int x = f.x(), y = f.y(), z = f.z(); var wheat = NativeFarmCrops.byOutput("Plant_Crop_Wheat_Item"); var lettuce = NativeFarmCrops.byOutput("Plant_Crop_Lettuce_Item");
        var a = new Point(x, y, z); var b = new Point(x + 1, y, z);
        var loaded = new PlotProductionState[1];
        var chain = load(world, x).thenCompose(ignored -> onWorld(world, () -> {
            f.ground(x, z, "Soil_Dirt_Tilled"); f.ground(x + 1, z, "Soil_Dirt_Tilled");
            require(ChunkSectionBlockUtil.setBlockByKey(world, x, y, z, wheat.block(), 0) && ChunkSectionBlockUtil.setBlockByKey(world, x + 1, y, z, lettuce.block(), 0), "Old crops could not be placed");
        }));
        chain = chain.thenCompose(ignored -> after(world, () -> {
            // The farm as the version before the Scarecrow Fields left it: beds reviewed and adopted, a harvest in hand, ingot upgrades bought.
            var review = NativeFarmCrops.review(world, f.town(), f.plot());
            require(review.ready() && review.beds().size() == 2, "The old bed review failed: " + review.issue());
            require(old.field().adopt(0, review.beds()) == null && old.field().harvest(review.beds().getFirst(), 2, () -> true), "The old farm could not be set up");
            old.addAmount(wheat.output(), 20, 25); old.setIronUpgrade(1); old.setCobaltLevel(1); old.setThoriumLevel(2); old.setAdamantineLevel(1);
            String json = new Gson().toJson(old);
            require(!json.contains("\"farm\""), "The old save already knows the Scarecrow Fields");
            var production = new Gson().fromJson(json, PlotProductionState.class); loaded[0] = production;
            require(production.usesPhysicalField() && !production.usesFarmFields(), "The old save did not load as an old field farm");
            // Exactly what the game does the first time it looks at a farm.
            FarmUpgrades.mapLegacy(production); production.farm().adoptLegacy(production.peekField());
            var fields = production.farm();
            require(production.usesFarmFields() && fields.plannedCount() == 2, "The old beds did not become the plan");
            require(wheat.output().equals(fields.cell(HOME, x, z).crop()) && lettuce.output().equals(fields.cell(HOME, x + 1, z).crop()) && fields.cell(HOME, x, z).y() == y,
                "The plan does not hold the crops that were in the beds");
            require(Integer.valueOf(2).equals(fields.basket().get(review.beds().getFirst().output())) && production.peekField().cargo() == null, "The carried harvest was lost or doubled");
            require(production.getAmount(wheat.output()) == 20 && production.getThoriumLevel() == 2 && production.getAdamantineLevel() == 1, "Stock or ingot upgrades were lost");
            require(FarmUpgrades.level(production, FarmUpgrades.Track.SCARECROWS) == 2 && FarmUpgrades.capacity(production, f.entry(), wheat.output()) == 50, "Old upgrades were not carried over");
            require(production.farm().adoptLegacy(production.peekField()) == 0 && !FarmUpgrades.mapLegacy(production) && fields.plannedCount() == 2, "The carry over ran twice");
            CoreVillagerMasteryService.progress(f.town(), FarmFields.KIND);
            ripen(world, a, wheat);
        }));
        chain = chain.thenCompose(ignored -> eventually(world, () -> FarmFieldWorld.ripe(world, a, wheat), "The old wheat bed did not ripen", 40));
        chain = chain.thenCompose(ignored -> onWorld(world, () -> {
            var production = loaded[0]; var entry = ProductionEffectiveCatalog.effective(AetherhavenPlugin.get().getProductionCatalog(),
                AetherhavenPlugin.get().getWorkplaceUnlockCatalog(), FarmFields.CONSTRUCTION, production);
            int seeds = production.farm().seeds("Plant_Seeds_Wheat");
            var cut = FarmFieldJobs.harvest(world, f.town(), f.plot(), production, entry, HOME, x, z, List.of(), PLAIN);
            require(cut != null && cut.crops() >= 1, "She could not harvest a bed from the old save");
            require(wheat.block().equals(cropBlock(world, a)) && production.farm().seeds("Plant_Seeds_Wheat") == seeds - 1, "The old bed was not sown again from the first seeds");
            require(lettuce.equals(FarmFieldWorld.cropAt(world, b)), "The other old bed was disturbed");
            var delivery = FarmFieldJobs.deliver(f.town(), production, entry);
            require(production.getAmount(wheat.output()) == 20 + 2 + cut.crops() && delivery.composted() == 0, "The old harvest and the new one did not both reach the stores");
        }));
        return chain;
    }

    private static CompletableFuture<Void> oldAutomatic(World world, int offset) {
        var f = farm(world, offset, new PlotProductionState());
        return load(world, f.x()).thenCompose(ignored -> onWorld(world, () -> {
            var old = f.production(); old.addAmount("Plant_Crop_Wheat_Item", 25, 25); old.setSlotCursor(0, 0); old.setSlotTickAccum(0, 700); old.setThoriumLevel(1);
            var production = new Gson().fromJson(new Gson().toJson(old), PlotProductionState.class);
            FarmUpgrades.mapLegacy(production); production.farm().adoptLegacy(production.peekField());
            require(!production.usesFarmFields() && !production.usesPhysicalField() && production.peekField() == null, "A farm on automatic production was switched to field work by being looked at");
            require(production.getAmount("Plant_Crop_Wheat_Item") == 25 && production.getSlotTickAccum(0) == 700 && production.getThoriumLevel() == 1, "Stock, progress or upgrades were lost");
            require(production.farm().seeds("Plant_Seeds_Wheat") == 16 && production.farm().seeds("Plant_Seeds_Lettuce") == 16, "The farm did not get its first seeds");
            production = new Gson().fromJson(new Gson().toJson(production), PlotProductionState.class);
            require(!FarmUpgrades.mapLegacy(production) && production.farm().seeds("Plant_Seeds_Wheat") == 16 && !production.usesFarmFields(), "The first seeds were given twice or field work started on its own");
            require(production.farm().paint(HOME, f.x(), f.y(), f.z(), "Plant_Crop_Wheat_Item") && production.usesFarmFields() && production.usesPhysicalField(),
                "Painting a plan did not hand the farm to the Farmer");
            require(production.getAmount("Plant_Crop_Wheat_Item") == 25, "Painting a plan lost stock");
        }));
    }

    // The Almanac and the Golden Harvest -------------------------------------------------------------------------

    private static CompletableFuture<Void> almanacAndItems(World world, int offset) {
        var f = farm(world, offset, null);
        return load(world, f.x()).thenCompose(ignored -> onWorld(world, () -> {
            var plugin = AetherhavenPlugin.get(); var rules = FarmFieldRules.get();
            var store = world.getEntityStore().getStore(); var time = store.getResource(WorldTimeResource.getResourceType());
            var start = time.getGameTime();
            require(start.getEpochSecond() < 0, "The game's clock was expected to be far below zero: " + start);
            long week = FarmAlmanac.week(FarmFieldWorld.dateTime(world)); int left = FarmAlmanac.daysLeft(FarmFieldWorld.dateTime(world));
            require(week >= 0 && left >= 1 && left <= 7, "The Almanac could not place the game's clock in a week: " + week + ", " + left);
            var named = FarmAlmanac.describe(week);
            var calendar = com.hexvane.aetherhaven.hud.AetherhavenCalendar.from(FarmFieldWorld.dateTime(world));
            require(named.season() == calendar.season() && named.weekOfSeason() == (calendar.dayOfSeason() - 1) / 7 + 1 && named.year() == calendar.year(),
                "The Almanac and the town calendar disagree: " + named + " against " + calendar);
            f.unlockAll();
            var untrained = FarmFields.growable(f.town(), f.entry());
            require(untrained.equals(List.of("Plant_Crop_Wheat_Item", "Plant_Crop_Lettuce_Item")), "A new Farmer should grow wheat and lettuce: " + untrained);
            var first = FarmFields.favoured(f.town(), f.entry(), f.production(), week);
            require(first.size() == 1 && untrained.contains(first.getFirst()), "With two crops one is favoured: " + first);
            f.train();
            var growable = FarmFields.growable(f.town(), f.entry());
            require(growable.size() == 14, "A fully trained Farmer with every crop unlocked grows fourteen crops: " + growable);
            var now = FarmFields.favoured(f.town(), f.entry(), f.production(), week);
            require(now.size() == 2 && new HashSet<>(now).size() == 2 && growable.containsAll(now), "Two crops are favoured each week: " + now);
            require(now.equals(FarmFields.favoured(f.town(), f.entry(), f.production(), week)), "The same week named different crops");
            var next = FarmFields.favoured(f.town(), f.entry(), f.production(), week + 1);
            require(Collections.disjoint(now, next), "Next week favours the same crops again: " + now + " then " + next);
            // The last second of this week and the first of the next, on the server's own clock.
            var boundary = start.truncatedTo(ChronoUnit.DAYS).plus(left, ChronoUnit.DAYS);
            time.setGameTime(boundary.minusSeconds(1), world, store);
            require(FarmAlmanac.week(FarmFieldWorld.dateTime(world)) == week && FarmAlmanac.daysLeft(FarmFieldWorld.dateTime(world)) == 1, "The week ended early");
            time.setGameTime(boundary, world, store);
            require(FarmAlmanac.week(FarmFieldWorld.dateTime(world)) == week + 1 && FarmAlmanac.daysLeft(FarmFieldWorld.dateTime(world)) == 7, "Midnight did not start the next week");
            require(next.equals(FarmFields.favoured(f.town(), f.entry(), f.production(), FarmAlmanac.week(FarmFieldWorld.dateTime(world)))), "The new week does not favour what the Almanac foretold");
            time.setGameTime(start, world, store);
            // The Golden Harvest: an item, a shop price and a gift every villager loves.
            String golden = rules.golden().item();
            var item = Item.getAssetMap().getAsset(golden);
            require(item != null && item.getTranslationKey() != null && item.getTranslationKey().startsWith("aetherhaven_fields."), "The Golden Harvest is not loaded");
            long price = plugin.getShopPriceCatalog().getGoldPrice(golden);
            require(price == 50, "The Golden Harvest should sell for 50 gold and sells for " + price);
            int lovers = 0;
            for (String role : List.of("Aetherhaven_Farmer", "Aetherhaven_Chef", "Aetherhaven_Druid", "Aetherhaven_Merchant", "Aetherhaven_Elder_Lyren")) {
                var villager = plugin.getVillagerDefinitionCatalog().byNpcRoleId(role);
                if (villager != null && villager.getGiftLoves().contains(golden)) lovers++;
            }
            require(lovers == 5, "Only " + lovers + " of five villagers asked love a Golden Harvest");
        }));
    }

    // Plumbing ---------------------------------------------------------------------------------------------------

    private static CompletableFuture<Void> after(World world, Runnable action) {
        return CompletableFuture.runAsync(() -> { }, CompletableFuture.delayedExecutor(150, TimeUnit.MILLISECONDS)).thenCompose(ignored -> onWorld(world, action));
    }
    private static CompletableFuture<Void> onWorld(World world, Runnable action) { return CompletableFuture.runAsync(action, world); }
    /** Waits for block ticks to do something, looking every 150 milliseconds. */
    private static CompletableFuture<Void> eventually(World world, java.util.function.BooleanSupplier done, String issue, int tries) {
        var seen = new boolean[1];
        return after(world, () -> seen[0] = done.getAsBoolean()).thenCompose(ignored -> seen[0] ? CompletableFuture.completedFuture(null)
            : tries <= 1 ? CompletableFuture.failedFuture(new IllegalStateException(issue)) : eventually(world, done, issue, tries - 1));
    }
    private static void require(boolean yes, String issue) { if (!yes) throw new IllegalStateException(issue); }
}
