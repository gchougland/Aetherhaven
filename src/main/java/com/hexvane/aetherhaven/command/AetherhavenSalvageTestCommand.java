package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.CourierWorkService;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps;
import com.hexvane.aetherhaven.construction.SiteSalvage;
import com.hexvane.aetherhaven.construction.assembly.AssemblyFootprintIndex;
import com.hexvane.aetherhaven.construction.assembly.AssemblyWorldRegistry;
import com.hexvane.aetherhaven.construction.assembly.PlotAssemblyFrontierRuntime;
import com.hexvane.aetherhaven.construction.assembly.PlotAssemblyJob;
import com.hexvane.aetherhaven.construction.assembly.PlotAssemblyPhase;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkProgress;
import com.hexvane.aetherhaven.townsfolk.TownsfolkSkill;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.warehouse.TownWarehouse;
import com.hexvane.aetherhaven.warehouse.WarehouseAccess;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch;
import com.hexvane.aetherhaven.warehouse.WarehouseDeskBlock;
import com.hexvane.aetherhaven.workplace.ApprenticeWorkplace;
import com.hexvane.aetherhaven.workplace.NativeBuildingWork;
import com.hexvane.aetherhaven.workplace.WorkplaceArea;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hexvane.aetherhaven.world.PersistentWorldSupport;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.PropComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PreventItemMerging;
import com.hypixel.hytale.server.core.modules.entity.item.PreventPickup;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import com.hypixel.hytale.server.core.universe.world.storage.provider.EmptyChunkStorageProvider;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.VoidWorldGenProvider;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Builders tidying a building site into the warehouse, checked in a disposable world. Real item entities, real
 * engine block drops and a real ledger desk go through the same {@link SiteSalvage} and warehouse calls the builder
 * tick makes. Every item is counted after each step, wherever it is.
 *
 * <p>What it does not show: a townsfolk walking. No resident is spawned (they only work while a town member is in the
 * world), so the builder here is a name and a position that arrives at each stand the real code chose.
 */
final class AetherhavenSalvageTestCommand extends AbstractAsyncCommand {
    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    AetherhavenSalvageTestCommand() {
        super("salvage", "Check builders tidying loose site items into the warehouse in an isolated world");
        setPermissionGroups("hytale:WorldEditor");
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        Consumer<String> report = text -> {
            ctx.sendMessage(Message.raw(text));
            com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(text);
        };
        if (!RUNNING.compareAndSet(false, true)) {
            report.accept("A salvage check is already running.");
            return CompletableFuture.completedFuture(null);
        }
        String name = "aetherhaven_salvage_check_" + UUID.randomUUID().toString().replace("-", "");
        var universe = Universe.get();
        var path = universe.validateWorldPath(name);
        if (Files.exists(path)) {
            RUNNING.set(false);
            return CompletableFuture.failedFuture(new IllegalStateException("Fixture already exists"));
        }
        var config = new WorldConfig();
        com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig.ensureAndGet(config);
        config.setWorldGenProvider(new VoidWorldGenProvider());
        config.setChunkStorageProvider(EmptyChunkStorageProvider.INSTANCE);
        config.setSavingPlayers(false);
        config.setCanSaveChunks(false);
        config.setSaveNewChunks(false);
        config.setSpawningNPC(false);
        config.setGameTimePaused(true);
        config.setBlockTicking(false);
        config.setDeleteOnRemove(true);
        config.setDeleteOnUniverseStart(true);
        report.accept("Starting isolated salvage check. No town of yours is used.");
        return universe.makeWorld(name, path, config).thenCompose(world -> {
            Fixture fixture = new Fixture(world, report);
            return fixture.run().orTimeout(120, TimeUnit.SECONDS).whenCompleteAsync((done, error) -> {
                if (error == null) {
                    report.accept("Salvage isolated world check: PASS. " + fixture.summary());
                } else {
                    Throwable cause = error;
                    while (cause.getCause() != null) cause = cause.getCause();
                    report.accept("Salvage isolated world check: FAIL at " + fixture.stage + ": " + cause);
                }
                universe.removeWorld(name);
            });
        }).whenComplete((done, error) -> RUNNING.set(false));
    }

    /** One item entity lying in the world. */
    private record Lying(String item, int quantity, Vector3d at, boolean display, boolean pickable) {}

    private static final class Fixture {
        private static final String BUILDER = "fixture_builder";
        private static final String DIRT = "Soil_Dirt", STONE = "Rock_Stone", STICK = "Ingredient_Stick", FIBRE = "Ingredient_Fibre",
            TRUNK = "Wood_Beech_Trunk", COPPER = "Ingredient_Bar_Copper", CHARCOAL = "Ingredient_Charcoal", FILLER = "Ingredient_Tree_Sap";
        private static final PlotFootprintRecord SITE = new PlotFootprintRecord(8, 127, 8, 17, 134, 17);
        private static final WarehouseBatch.Point DESK = new WarehouseBatch.Point(25, 128, 7);

        private final World world;
        private final Consumer<String> report;
        private final AetherhavenPlugin plugin = AetherhavenPlugin.get();
        private final Store<EntityStore> store;
        /** Every item the fixture has put anywhere. The world, storage, hands and the bag must always add up to this. */
        private final Map<String, Long> expected = new TreeMap<>();
        private final SimpleItemContainer bag = new SimpleItemContainer((short) 1);
        private final List<String> lines = new ArrayList<>();
        private TownRecord town;
        private TownManager manager;
        private TownWarehouse stock;
        private ApprenticeWorkplace yard;
        private Vector3d position = new Vector3d(7.5, 128.02, 7.5);
        private int limit, pickups, trips, checks, engineDrops;
        private long putBack;
        volatile String stage = "setup";

        Fixture(World world, Consumer<String> report) {
            this.world = world;
            this.report = report;
            this.store = world.getEntityStore().getStore();
        }

        String summary() {
            return String.join(" ", lines) + " Lost 0, duplicated 0: all " + expected.values().stream().mapToLong(Long::longValue).sum()
                + " items were accounted for at each of " + checks + " counts.";
        }

        CompletableFuture<Void> run() {
            var sections = new ArrayList<CompletableFuture<?>>();
            for (int y = 3; y <= 4; y++) sections.add(world.getChunkStore().getChunkSectionReferenceAsync(0, y, 0, GetChunkFlags.SET_TICKING));
            long[] before = new long[4];
            return CompletableFuture.allOf(sections.toArray(CompletableFuture[]::new))
                .thenCompose(ignored -> onWorld(this::build))
                .thenCompose(ignored -> later(150))
                .thenCompose(ignored -> onWorld(this::litter))
                .thenCompose(ignored -> await("loose items never became pickable", this::allPickable))
                .thenCompose(ignored -> onWorld(this::withoutWarehouse))
                .thenCompose(ignored -> await("the ledger desk never appeared as a loaded desk", () -> SiteSalvage.storageReady(world, manager, town, plugin)))
                // Phase 1: room for everything.
                .thenCompose(ignored -> onWorld(() -> {
                    stage = "phase 1";
                    before[0] = onSite();
                    before[1] = stored();
                    require(before[0] > 0 && before[1] == 0, "Phase 1 did not start with litter on the site and empty storage");
                }))
                .thenCompose(ignored -> haul(0))
                .thenCompose(ignored -> onWorld(() -> {
                    long left = onSite();
                    require(left == 0, "Items were left on the site although storage had room: " + lying());
                    require(stored() == before[0], "Storage holds " + stored() + ", the site held " + before[0]);
                    require(stock.batches().isEmpty(), "A load was left in the builder's hands");
                    require(trips >= 2, "More kinds lay about than one load holds, yet there was only " + trips + " trip");
                    require(offSite(STONE) == 5, "The stack lying off the site was touched");
                    require(displays() == 1, "The display item was touched");
                    lines.add("Room for everything: on the site " + before[0] + " -> " + left + ", in storage " + before[1] + " -> " + stored()
                        + ", " + pickups + " pickups, " + trips + " trips to the desk (" + limit + " stacks a load, " + engineDrops
                        + " of the items came from blocks broken the way site clearing breaks them). Left alone: 5 off the site and 1 display.");
                }))
                // Phase 2: room for 10 of 25, and none for another kind.
                .thenCompose(ignored -> onWorld(this::crowd))
                .thenCompose(ignored -> await("phase 2 items never became pickable", this::allPickable))
                .thenCompose(ignored -> onWorld(() -> {
                    before[0] = stock.count(TownWarehouse.Page.GENERAL, COPPER);
                    before[2] = trips;
                }))
                .thenCompose(ignored -> haul(0))
                .thenCompose(ignored -> onWorld(() -> {
                    long copper = stock.count(TownWarehouse.Page.GENERAL, COPPER);
                    require(copper == before[0] + 10, "Storage had room for 10 copper and took " + (copper - before[0]));
                    require(onSite(COPPER) == 15, "15 copper should still lie on the site, found " + onSite(COPPER));
                    require(onSite(CHARCOAL) == 4 && stock.count(TownWarehouse.Page.GENERAL, CHARCOAL) == 0, "Charcoal had no room and was taken");
                    require(stock.batches().isEmpty(), "A load was left in the builder's hands");
                    lines.add("Room for 10 of 25: 10 stored, 15 left lying; 4 of a kind with no room left lying.");
                }))
                // Phase 3: the room promised at pickup is gone when the builder reaches the desk.
                .thenCompose(ignored -> onWorld(() -> {
                    stage = "phase 3";
                    short slot = fillerSlot();
                    var taken = stock.withdraw(TownWarehouse.Page.GENERAL, bag, slot, stock.stack(TownWarehouse.Page.GENERAL, slot), TownWarehouse.Amount.STACK);
                    require(taken.moved() > 0, "Could not free a storage slot: " + taken.error());
                    count("a slot freed");
                    require(step(), "Nothing was fetched although a slot was free");
                    before[3] = carried();
                    require(before[3] > 0 && pickups > 0, "The builder carries nothing");
                    // A player fills the slot again while the builder walks.
                    var back = stock.deposit(TownWarehouse.Page.GENERAL, bag, (short) 0, bag.getItemStack((short) 0), TownWarehouse.Amount.STACK);
                    require(back.moved() == taken.moved(), "Could not fill the slot again");
                    before[1] = stored();
                    count("the slot filled again");
                }))
                .thenCompose(ignored -> haul(0))
                .thenCompose(ignored -> onWorld(() -> {
                    require(stock.batches().isEmpty(), "The load that no longer fits is still in the builder's hands");
                    require(stored() == before[1], "Full storage changed");
                    require(putBack == before[3], "Carried " + before[3] + ", put back on the ground " + putBack);
                    lines.add("Storage filled while " + before[3] + " were carried: 0 stored, " + putBack + " put back on the ground at the desk.");
                }));
        }

        // ---- Setting the scene ----

        private void build() {
            stage = "setup";
            require(plugin != null, "Plugin missing");
            for (String item : List.of(DIRT, STONE, STICK, FIBRE, TRUNK, COPPER, CHARCOAL, FILLER))
                require(Item.getAssetMap().getAsset(item) != null, "Item missing from the game: " + item);
            require(!PersistentWorldSupport.shouldPersistWorldData(world), "The fixture world must never be saved");
            for (int x = 2; x <= 30; x++) for (int z = 2; z <= 30; z++)
                require(ChunkSectionBlockUtil.setBlockByKey(world, x, 127, z, "Rock_Stone", 0), "Floor placement failed");
            UUID owner = UUID.randomUUID();
            town = new TownRecord(UUID.randomUUID(), owner, world.getName(), 16, 128, 16, 1, 1, 0);
            town.setDisplayName("Salvage Fixture");
            manager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            manager.putTown(town);
            stock = town.getWarehouse();

            // A started project in its placing phase, made the way the building check makes one.
            var definition = plugin.getConstructionCatalog().get(AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE);
            require(definition != null, "House definition missing");
            var prefabPath = PrefabResolveUtil.resolvePrefabPath(definition.getPrefabPath());
            require(prefabPath != null, "House prefab missing");
            var prefab = PrefabBufferUtil.getCached(prefabPath);
            require(prefab != null, "House prefab did not open");
            var sequence = ConstructionPasteOps.buildSequence(prefab, Rotation.None);
            var pending = ConstructionPasteOps.withoutPureAirCells(sequence.pendingBlocks());
            var anchor = new Vector3i(12, 128, 12);
            var plot = new PlotInstance(UUID.randomUUID(), definition.getId(), PlotInstanceState.ASSEMBLING, SITE, 8, 128, 7, 0);
            plot.setPrefabWorldPlacement(anchor.x, anchor.y, anchor.z, Rotation.None);
            plot.resetAssemblyPlacementProgress();
            plot.setAssemblyOwnerUuid(owner);
            town.getPlotInstances().add(plot);
            // A slot of a thousand seconds: nothing is placed by itself while the check runs.
            var job = new PlotAssemblyJob(plot.getPlotId(), owner, anchor, Rotation.None, sequence.pendingBlocks(), AssemblyFootprintIndex.build(sequence.pendingBlocks()),
                pending, sequence.prefabEntitiesInOrder(), prefab, sequence.prefabRotation(), 0, 1_000_000, false, definition.getId());
            AssemblyWorldRegistry.put(world, plot.getPlotId(), town.getTownId(), job, PlotAssemblyPhase.PLACING, PlotAssemblyFrontierRuntime.create(pending, plot, null), null, null);
            yard = new ApprenticeWorkplace("Fixture yard", TownsfolkWorkType.BUILDING, new WorkplaceArea(2, 128, 2, 4, 4));
            require(yard.building().add(plot.getPlotId(), owner) == null, "Project queue failed");
            require(NativeBuildingWork.issue(world, town, yard.building().list().getFirst()) == null, "The project is not being built");
            require(areas().size() == 1, "The project has no site");

            var progress = new TownsfolkProgress();
            progress.setSkill(TownsfolkSkill.BUILDING, 5);
            progress.setSkill(TownsfolkSkill.LOGISTICS, 0);
            limit = SiteSalvage.carryStacks(progress, plugin.getProfessionKits());
            require(limit == 3, "A level 5 builder should carry 3 stacks, not " + limit);

            // Two blocks standing on the site, to be broken the way clearing breaks them.
            require(ChunkSectionBlockUtil.setBlockByKey(world, 10, 128, 13, DIRT, 0) && ChunkSectionBlockUtil.setBlockByKey(world, 11, 128, 13, STONE, 0), "Obstruction placement failed");
        }

        private void litter() {
            stage = "litter";
            // Engine drops: the call and settings AssemblyStaffClearBreak uses for a block with an ordinary breaking drop.
            for (int x = 10; x <= 11; x++) {
                var before = ground();
                var type = ChunkSectionBlockUtil.blockType(world, x, 128, 13);
                var section = ChunkSectionBlockUtil.sectionRefAt(world, x, 128, 13);
                require(type != null && section != null && section.isValid(), "Obstruction block missing");
                var breaking = type.getGathering() == null ? null : type.getGathering().getBreaking();
                BlockHarvestUtils.naturallyRemoveBlock(new Vector3i(x, 128, 13), type, ChunkSectionBlockUtil.filler(world, x, 128, 13),
                    breaking == null ? 1 : Math.max(1, breaking.getQuantity()), breaking == null ? null : breaking.getItemId(),
                    breaking == null ? null : breaking.getDropListId(), 32, section, store, world.getChunkStore().getStore());
                for (var entry : ground().entrySet()) {
                    long more = entry.getValue() - before.getOrDefault(entry.getKey(), 0L);
                    require(more >= 0, "Breaking a block removed items");
                    if (more > 0) {
                        expected.merge(entry.getKey(), more, Long::sum);
                        engineDrops += (int) more;
                    }
                }
            }
            require(engineDrops > 0, "Breaking blocks the way site clearing does dropped nothing");
            // Loose stacks: site litter, a player's dropped sticks, and one stack right at the two block margin.
            drop(DIRT, 40, 12.5, 12.5, false);
            drop(STONE, 25, 15.5, 9.5, false);
            drop(STICK, 7, 9.5, 9.5, false);
            drop(TRUNK, 6, 16.5, 15.5, false);
            drop(FIBRE, 12, 19.5, 19.5, false);
            // Never to be taken: a stack off the site, and a shop style display standing on it.
            drop(STONE, 5, 26.5, 24.5, false);
            drop(COPPER, 1, 9.5, 16.5, true);
            count("litter laid out");
        }

        private void drop(String item, int quantity, double x, double z, boolean display) {
            Holder<EntityStore> holder = ItemComponent.generateItemDrop(store, new ItemStack(item, quantity), new Vector3d(x, 128.3, z), Rotation3f.ZERO, 0, 0, 0);
            require(holder != null, "Could not make an item entity for " + item);
            if (display) {
                holder.addComponent(PreventPickup.getComponentType(), PreventPickup.INSTANCE);
                holder.addComponent(PropComponent.getComponentType(), PropComponent.get());
                holder.addComponent(PreventItemMerging.getComponentType(), PreventItemMerging.INSTANCE);
            }
            store.addEntity(holder, AddReason.SPAWN);
            expected.merge(item, (long) quantity, Long::sum);
        }

        private void withoutWarehouse() {
            stage = "no warehouse";
            var near = new Vector3d(12.5, 128.02, 11.5);
            require(!SiteSalvage.storageReady(world, manager, town, plugin), "Storage counted as ready with no Warehouse");
            require(collect(near) == 0 && stock.batches().isEmpty(), "Items were picked up with no Warehouse");
            town.getPlotInstances().add(new PlotInstance(UUID.randomUUID(), SiteSalvage.WAREHOUSE, PlotInstanceState.COMPLETE,
                new PlotFootprintRecord(22, 127, 4, 28, 134, 10), 22, 128, 3, 0));
            require(!SiteSalvage.storageReady(world, manager, town, plugin), "A Warehouse with no ledger desk in the world counted as ready");
            require(collect(near) == 0 && stock.batches().isEmpty(), "Items were picked up with no ledger desk");
            count("no warehouse");
            lines.add("No Warehouse, then a Warehouse with no desk: 0 picked up of " + onSite() + " on the site.");
            require(ChunkSectionBlockUtil.setBlockByKey(world, DESK.x(), DESK.y(), DESK.z(), WarehouseAccess.DESK_ITEM, 0), "Ledger desk placement failed");
        }

        private void crowd() {
            stage = "phase 2";
            int copperStack = new ItemStack(COPPER, 1).getItem().getMaxStack(), fillerStack = new ItemStack(FILLER, 1).getItem().getMaxStack();
            require(copperStack > 25, "The copper stack is too small for this check");
            add(COPPER, copperStack - 10);
            // Every other slot is filled to the brim, so only 10 copper fit and no new kind does.
            for (int guard = 0; stock.used(TownWarehouse.Page.GENERAL) < stock.slots(TownWarehouse.Page.GENERAL); guard++) {
                require(guard < 400, "Storage would not fill");
                add(FILLER, fillerStack);
            }
            drop(COPPER, 25, 12.5, 12.5, false);
            drop(CHARCOAL, 4, 14.5, 12.5, false);
            count("storage crowded");
        }

        private void add(String item, int quantity) {
            require(stock.acceptGuardSpoils(new ItemStack(item, quantity)), "Could not stock " + quantity + " " + item);
            expected.merge(item, (long) quantity, Long::sum);
        }

        private short fillerSlot() {
            for (short slot = 0; slot < stock.slots(TownWarehouse.Page.GENERAL); slot++) {
                var held = stock.stack(TownWarehouse.Page.GENERAL, slot);
                if (!ItemStack.isEmpty(held) && FILLER.equals(held.getItemId())) return slot;
            }
            throw new IllegalStateException("No filler slot");
        }

        // ---- The builder ----

        private List<PlotFootprintRecord> areas() {
            return SiteSalvage.areas(town, yard, order -> NativeBuildingWork.issue(world, town, order) == null);
        }

        private int collect(Vector3d at) {
            return SiteSalvage.collect(store, areas(), stock, SiteSalvage.storageReady(world, manager, town, plugin), BUILDER, limit, at,
                item -> store.removeEntity(item, RemoveReason.REMOVE));
        }

        /**
         * One decision of a builder between pieces, taken as ApprenticeWorkService.tickSalvage and CourierWorkService
         * take it; the walk is replaced by arriving at the stand they chose.
         *
         * @return false when there is nothing to fetch and nothing in hand
         */
        private boolean step() {
            long now = System.currentTimeMillis();
            var room = SiteSalvage.storageReady(world, manager, town, plugin) ? stock.salvageRoom(BUILDER, limit) : null;
            var found = SiteSalvage.nearest(store, world, areas(), BUILDER, room, position, now);
            if (found == null) {
                var load = stock.batches().stream().filter(batch -> BUILDER.equals(batch.courierId())).findFirst().orElse(null);
                if (load == null) return false;
                require(load.salvage() && load.cancelled(), "The load is not one the courier code returns to a desk");
                var from = position;
                var desk = WarehouseDeskBlock.loaded(world, manager, town).stream()
                    .min(Comparator.comparingDouble(p -> from.distanceSquared(p.x() + .5, p.y(), p.z() + .5))).orElse(null);
                require(DESK.equals(desk), "The ledger desk was not found");
                var stand = CourierWorkService.approach(world, desk, position, 0);
                require(stand != null, "No place to stand at the ledger desk");
                position = stand;
                long lyingBefore = total(ground());
                require(SiteSalvage.unload(stock, load.id(), BUILDER, position, store), "The desk refused the load");
                putBack = total(ground()) - lyingBefore;
                trips++;
                count("trip " + trips);
                return true;
            }
            var stand = CourierWorkService.approach(world, found.cell(), position, 0);
            if (stand == null) {
                SiteSalvage.unreachable(world, found.item(), now);
                return true;
            }
            position = stand;
            require(position.distance(found.position()) <= SiteSalvage.SWEEP, "The stand is out of reach of the item");
            if (collect(position) > 0) pickups++;
            else SiteSalvage.unreachable(world, found.item(), now);
            count("pickup " + pickups);
            return true;
        }

        private CompletableFuture<Void> haul(int steps) {
            return CompletableFuture.supplyAsync(this::step, world).thenCompose(more -> {
                if (!more) return CompletableFuture.completedFuture(null);
                if (steps >= 80) return CompletableFuture.failedFuture(new IllegalStateException("The builder never finished"));
                // Let the world tick between decisions, as it would between a builder's.
                return later(60).thenCompose(ignored -> haul(steps + 1));
            });
        }

        // ---- Counting ----

        private List<Lying> lying() {
            List<Lying> found = new ArrayList<>();
            store.forEachChunk(Query.and(ItemComponent.getComponentType(), TransformComponent.getComponentType()), (chunk, buffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    ItemComponent item = chunk.getComponent(i, ItemComponent.getComponentType());
                    ItemStack stack = item.getItemStack();
                    if (ItemStack.isEmpty(stack)) continue;
                    found.add(new Lying(stack.getItemId(), stack.getQuantity(), new Vector3d(chunk.getComponent(i, TransformComponent.getComponentType()).getPosition()),
                        chunk.getArchetype().contains(PreventPickup.getComponentType()), item.canPickUp()));
                }
            });
            return found;
        }

        private Map<String, Long> ground() {
            Map<String, Long> totals = new TreeMap<>();
            for (Lying entry : lying()) totals.merge(entry.item(), (long) entry.quantity(), Long::sum);
            return totals;
        }

        private boolean allPickable() {
            return lying().stream().allMatch(entry -> entry.display() || entry.pickable());
        }

        private boolean onTheSite(Lying entry) {
            return !entry.display() && SiteSalvage.lies(SiteSalvage.area(SITE), entry.at().x, entry.at().y, entry.at().z);
        }

        private long onSite() {
            return lying().stream().filter(this::onTheSite).mapToLong(Lying::quantity).sum();
        }

        private long onSite(String item) {
            return lying().stream().filter(entry -> onTheSite(entry) && item.equals(entry.item())).mapToLong(Lying::quantity).sum();
        }

        private long offSite(String item) {
            return lying().stream().filter(entry -> !entry.display() && !onTheSite(entry) && item.equals(entry.item())).mapToLong(Lying::quantity).sum();
        }

        private long displays() {
            return lying().stream().filter(Lying::display).count();
        }

        private long stored() {
            long sum = 0;
            for (var page : TownWarehouse.Page.values()) for (short slot = 0; slot < stock.slots(page); slot++) {
                var held = stock.stack(page, slot);
                if (!ItemStack.isEmpty(held)) sum += held.getQuantity();
            }
            return sum;
        }

        private long carried() {
            return stock.batches().stream().flatMap(batch -> batch.cargoItems().stream()).mapToLong(ItemStack::getQuantity).sum();
        }

        private static long total(Map<String, Long> counts) {
            return counts.values().stream().mapToLong(Long::longValue).sum();
        }

        /** Counts every item wherever it is. Anything missing was lost, anything extra was copied. */
        private void count(String when) {
            Map<String, Long> actual = ground();
            for (var page : TownWarehouse.Page.values()) for (short slot = 0; slot < stock.slots(page); slot++) {
                var held = stock.stack(page, slot);
                if (!ItemStack.isEmpty(held)) actual.merge(held.getItemId(), (long) held.getQuantity(), Long::sum);
            }
            for (var batch : stock.batches()) for (var held : batch.cargoItems()) actual.merge(held.getItemId(), (long) held.getQuantity(), Long::sum);
            var inBag = bag.getItemStack((short) 0);
            if (!ItemStack.isEmpty(inBag)) actual.merge(inBag.getItemId(), (long) inBag.getQuantity(), Long::sum);
            checks++;
            if (actual.equals(expected)) return;
            Map<String, Long> lost = new TreeMap<>(), copied = new TreeMap<>();
            for (var entry : expected.entrySet()) {
                long difference = entry.getValue() - actual.getOrDefault(entry.getKey(), 0L);
                if (difference > 0) lost.put(entry.getKey(), difference);
                if (difference < 0) copied.put(entry.getKey(), -difference);
            }
            for (var entry : actual.entrySet()) if (!expected.containsKey(entry.getKey())) copied.put(entry.getKey(), entry.getValue());
            throw new IllegalStateException("Count after " + when + ": lost " + lost + ", duplicated " + copied);
        }

        // ---- Plumbing ----

        private CompletableFuture<Void> onWorld(Runnable action) {
            return CompletableFuture.runAsync(action, world);
        }

        private static CompletableFuture<Void> later(long millis) {
            return CompletableFuture.runAsync(() -> {}, CompletableFuture.delayedExecutor(millis, TimeUnit.MILLISECONDS));
        }

        private CompletableFuture<Void> await(String failure, BooleanSupplier ready) {
            return await(failure, ready, 0);
        }

        private CompletableFuture<Void> await(String failure, BooleanSupplier ready, int attempt) {
            return CompletableFuture.supplyAsync(ready::getAsBoolean, world).thenCompose(done -> {
                if (done) return CompletableFuture.completedFuture(null);
                if (attempt >= 50) return CompletableFuture.failedFuture(new IllegalStateException(failure));
                return later(200).thenCompose(ignored -> await(failure, ready, attempt + 1));
            });
        }

        private static void require(boolean okay, String reason) {
            if (!okay) throw new IllegalStateException(reason);
        }
    }
}
