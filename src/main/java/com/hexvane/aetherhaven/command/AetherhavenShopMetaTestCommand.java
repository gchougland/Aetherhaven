package com.hexvane.aetherhaven.command;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.alchemy.AlchemyBrew;
import com.hexvane.aetherhaven.alchemy.AlchemyFlask;
import com.hexvane.aetherhaven.alchemy.AlchemyIngredients;
import com.hexvane.aetherhaven.festival.FestivalHeldItemsService;
import com.hexvane.aetherhaven.festival.market.MarketSession;
import com.hexvane.aetherhaven.festival.market.MarketSessionIndex;
import com.hexvane.aetherhaven.festival.market.MarketStallService;
import com.hexvane.aetherhaven.festival.wintertide.WintertideGiftService;
import com.hexvane.aetherhaven.festival.wintertide.WintertideSession;
import com.hexvane.aetherhaven.festival.wintertide.WintertideSessionIndex;
import com.hexvane.aetherhaven.inventory.ItemStackData;
import com.hexvane.aetherhaven.jewelry.JewelryItemIds;
import com.hexvane.aetherhaven.jewelry.JewelryMetadata;
import com.hexvane.aetherhaven.jewelry.JewelryRarity;
import com.hexvane.aetherhaven.questboard.FetchQuestPayment;
import com.hexvane.aetherhaven.questboard.QuestBoardItemRequirement;
import com.hexvane.aetherhaven.questboard.data.QuestBoardFetchEntryJson;
import com.hexvane.aetherhaven.shopspot.ShopListing;
import com.hexvane.aetherhaven.shopspot.ShopListingText;
import com.hexvane.aetherhaven.shopspot.ShopPriceAdmin;
import com.hexvane.aetherhaven.shopspot.ShopPriceCatalog.Source;
import com.hexvane.aetherhaven.shopspot.ShopPriceEntry;
import com.hexvane.aetherhaven.shopspot.ShopSpotDisplayService;
import com.hexvane.aetherhaven.shopspot.ShopSpotJewelrySupport;
import com.hexvane.aetherhaven.shopspot.ShopSpotPersistence;
import com.hexvane.aetherhaven.shopspot.ShopSpotPricing;
import com.hexvane.aetherhaven.shopspot.ShopSpotPurchaseService;
import com.hexvane.aetherhaven.shopspot.ShopSpotRecord;
import com.hexvane.aetherhaven.shopspot.ShopSpotRegistry;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownWorldFile;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hexvane.aetherhaven.world.PersistentWorldSupport;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PreventPickup;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import com.hypixel.hytale.server.core.universe.world.storage.provider.EmptyChunkStorageProvider;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.VoidWorldGenProvider;
import com.hypixel.hytale.server.core.util.MessageUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bson.BsonString;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * {@code /ah test shopmeta}: player shop listings keep whole stacks, and {@code /ah price} changes prices, on the
 * running server.
 *
 * <p>A player shop is set up in a disposable world with real stall blocks. A filled Alchemy Flask, a worn tool, a
 * rolled ring and a stack of bars are listed with the stall interaction's own listing step, shown on the stalls,
 * written to disk with the town, read back, bought into a real container and compared with what was listed, field by
 * field. The price commands are typed the way the console types them, through the engine's command dispatcher.
 *
 * <p>What it does not show: a player pressing the key on a stall, the look of the page and HUD, or item tooltips in
 * the client. No player exists here, so the seller and the buyer are an inventory each.
 *
 * <p>It sets two prices on the server it runs on and takes them off again. A price either item already had on this
 * server is put back as it was.
 */
final class AetherhavenShopMetaTestCommand extends AbstractAsyncCommand {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final Gson GSON = new Gson();
    private static final String FLASK = AlchemyFlask.ITEM, TOOL = "Tool_Pickaxe_Iron", BARS = "Ingredient_Bar_Copper", STONE = "Rock_Stone";
    private static final List<String> MIXTURE = List.of("Plant_Crop_Health2", "Plant_Crop_Mana3");
    private static final List<String> OTHER_MIXTURE = List.of("Plant_Crop_Mana3");

    AetherhavenShopMetaTestCommand() {
        super("shopmeta", "Check that shop listings keep whole item stacks and that /ah price works, in an isolated world");
        setPermissionGroups("hytale:WorldEditor");
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        Consumer<String> report = text -> {
            ctx.sendMessage(Message.raw(text));
            LOGGER.atInfo().log(text);
        };
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            report.accept("Shop listing world check: FAIL at setup: Aetherhaven is not running.");
            report.accept("Festival held items world check: FAIL at setup: Aetherhaven is not running.");
            report.accept("Price command check: FAIL at setup: Aetherhaven is not running.");
            return CompletableFuture.completedFuture(null);
        }
        if (!RUNNING.compareAndSet(false, true)) {
            report.accept("A shop metadata check is already running.");
            return CompletableFuture.completedFuture(null);
        }
        String name = "aetherhaven_shopmeta_check_" + UUID.randomUUID().toString().replace("-", "");
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
        report.accept("Starting isolated shop metadata check. No town of yours is used. Two item prices are set and taken off again.");
        Prices prices = new Prices(plugin);
        Path scratch = plugin.getDataDirectory().resolve("test").resolve("shopmeta-" + UUID.randomUUID());
        return universe.makeWorld(name, path, config).thenCompose(world -> {
            Shop shop = new Shop(world, plugin, scratch);
            // Prices first: the flask has no shipped price, and an item without one cannot be listed.
            return prices.setAndCheck()
                .handle((done, error) -> error)
                .thenCompose(priceError -> shop.run().orTimeout(120, TimeUnit.SECONDS).handle((done, error) -> {
                    report.accept(error == null
                        ? "Shop listing world check: PASS. " + shop.summary()
                        : "Shop listing world check: FAIL at " + shop.stage + ": " + rootCause(error)
                            + (priceError == null ? "" : " (the price commands failed first, see the next line)"));
                    return priceError;
                }))
                .thenCompose(priceError -> {
                    Festivals festivals = new Festivals(world, plugin, scratch);
                    return festivals.run().orTimeout(60, TimeUnit.SECONDS).handle((done, error) -> {
                        report.accept(error == null
                            ? "Festival held items world check: PASS. " + festivals.summary()
                            : "Festival held items world check: FAIL at " + festivals.stage + ": " + rootCause(error));
                        return priceError;
                    });
                })
                .thenCompose(priceError -> priceError != null
                    ? CompletableFuture.<Void>failedFuture(priceError)
                    : prices.removeAndCheck(shop.posted))
                .handle((done, error) -> {
                    report.accept(error == null
                        ? "Price command check: PASS. " + prices.summary()
                        : "Price command check: FAIL at " + prices.stage + ": " + rootCause(error));
                    return (Void) null;
                })
                .whenComplete((done, error) -> {
                    prices.restore();
                    deleteQuietly(scratch);
                    universe.removeWorld(name);
                });
        }).handle((done, error) -> {
            if (error != null) {
                report.accept("Shop listing world check: FAIL at world creation: " + rootCause(error));
                report.accept("Festival held items world check: FAIL at world creation: " + rootCause(error));
                report.accept("Price command check: FAIL at world creation: " + rootCause(error));
                prices.restore();
            }
            RUNNING.set(false);
            return null;
        });
    }

    private static Throwable rootCause(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause;
    }

    private static void deleteQuietly(Path dir) {
        try (var files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(file -> {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException ignored) {
                    // A leftover test file is harmless.
                }
            });
        } catch (IOException ignored) {
            // Nothing was written.
        }
    }

    private static void require(boolean okay, String reason) {
        if (!okay) throw new IllegalStateException(reason);
    }

    // ------------------------------------------------------------------------------------------------ listings

    /** One thing put up for sale: the stack as the seller held it, and the stall it went on. */
    private record Sale(String what, ItemStack held, ShopSpotRecord stall) {}

    private static final class Shop {
        private static final PlotFootprintRecord SITE = new PlotFootprintRecord(4, 127, 4, 20, 134, 12);
        private final World world;
        private final AetherhavenPlugin plugin;
        private final Path scratch;
        private final Store<EntityStore> store;
        private final UUID seller = UUID.randomUUID();
        private final SimpleItemContainer sellerHotbar = new SimpleItemContainer((short) 9);
        private final SimpleItemContainer buyerBag = new SimpleItemContainer((short) 9);
        private final List<String> lines = new ArrayList<>();
        private final List<Sale> sales = new ArrayList<>();
        private ShopSpotRegistry registry;
        private TownManager manager;
        private TownRecord town;
        private UUID plotId;
        private int stalls;
        private long owed;
        /** A listing left on its stall, for the price check to look at after prices change. */
        volatile ShopSpotRecord posted;
        volatile String stage = "setup";

        Shop(World world, AetherhavenPlugin plugin, Path scratch) {
            this.world = world;
            this.plugin = plugin;
            this.scratch = scratch;
            this.store = world.getEntityStore().getStore();
        }

        String summary() {
            return String.join(" ", lines);
        }

        CompletableFuture<Void> run() {
            var sections = new ArrayList<CompletableFuture<?>>();
            for (int y = 3; y <= 4; y++) sections.add(world.getChunkStore().getChunkSectionReferenceAsync(0, y, 0, GetChunkFlags.SET_TICKING));
            return CompletableFuture.allOf(sections.toArray(CompletableFuture[]::new))
                .thenCompose(ignored -> onWorld(this::build))
                .thenCompose(ignored -> onWorld(this::list))
                .thenCompose(ignored -> await("the stalls never showed the listed stacks", this::everyStallShowsItsStack))
                .thenCompose(ignored -> onWorld(this::saveAndReload))
                .thenCompose(ignored -> onWorld(this::buy))
                .thenCompose(ignored -> await("sold out stalls kept their displays", this::soldOutStallsAreBare))
                .thenCompose(ignored -> onWorld(this::oldSaves));
        }

        // ---- setting the scene

        private void build() {
            stage = "setup";
            for (String item : List.of(FLASK, TOOL, BARS, STONE))
                require(Item.getAssetMap().getAsset(item) != null, "Item missing from the game: " + item);
            require(!PersistentWorldSupport.shouldPersistWorldData(world), "The fixture world must never be saved");
            for (int x = 2; x <= 22; x++) for (int z = 2; z <= 14; z++)
                require(ChunkSectionBlockUtil.setBlockByKey(world, x, 127, z, "Rock_Stone", 0), "Floor placement failed");
            town = new TownRecord(UUID.randomUUID(), seller, world.getName(), 12, 128, 8, 1, 1, 0);
            town.setDisplayName("Shop Fixture");
            plotId = UUID.randomUUID();
            town.getPlotInstances().add(new PlotInstance(plotId, AetherhavenConstants.CONSTRUCTION_PLOT_PLAYER_SHOP, PlotInstanceState.COMPLETE, SITE, 4, 128, 4, 0));
            manager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            manager.putTown(town);
            require(ShopSpotPurchaseService.isPlayerShopPlot(plugin, town, plotId), "The fixture plot is not a player shop");
            registry = AetherhavenWorldRegistries.getOrCreateShopSpotRegistry(world, plugin);
            require(registry.allRecords().isEmpty(), "The fixture world already has stalls");
        }

        /** A real stall block and its saved row, as placing a Shop Spot in a finished shop makes them. */
        private ShopSpotRecord stall() {
            int x = 6 + 2 * stalls++;
            require(ChunkSectionBlockUtil.setBlockByKey(world, x, 128, 8, AetherhavenConstants.SHOP_SPOT_BLOCK_TYPE_ID, 0), "Stall block placement failed");
            var record = new ShopSpotRecord();
            record.setSpotId(UUID.randomUUID());
            record.setWorldName(world.getName());
            record.setBlockPosition(new Vector3i(x, 128, 8));
            record.setTownId(town.getTownId());
            record.setPlotId(plotId);
            record.setPlayerControlled(true);
            record.setDisplayYawRadians(0f);
            registry.put(record);
            return record;
        }

        private ItemStack flask(List<String> mixture) {
            for (String ingredient : mixture) require(AlchemyIngredients.find(ingredient) != null, "Not an alchemy ingredient: " + ingredient);
            var flask = AlchemyFlask.upgrade(new ItemStack(FLASK, 1));
            flask = AlchemyFlask.consume(AlchemyFlask.fill(flask, mixture, AlchemyBrew.Quality.GREAT));
            require(AlchemyFlask.ingredients(flask).equals(mixture) && AlchemyFlask.charges(flask) == 1 && AlchemyFlask.capacity(flask) == 2, "The flask was not filled");
            return flask;
        }

        // ---- listing

        private void list() {
            stage = "listing";
            var prices = plugin.getShopPriceCatalog();
            var full = new ItemStack(TOOL, 1);
            require(full.getMaxDurability() > 1, TOOL + " has no durability in this game");
            // About a third of its life left, of a quality of its own, with a tag another mod might write.
            var worn = full.withDurability(Math.floor(full.getMaxDurability() * 0.37)).withQuality(2).withMetadata("ShopMetaCheck", new BsonString("kept"));
            require(ShopListingText.isWorn(worn), "The tool is not worn");

            put("filled Alchemy Flask", flask(MIXTURE), 1);
            put("worn " + TOOL, worn, 1);
            put("flask with another mixture", flask(OTHER_MIXTURE), 1);
            ShopPriceEntry bars = prices.getEntry(BARS);
            put(bars.getBatchSize() * 4 + " " + BARS, new ItemStack(BARS, bars.getBatchSize() * 4), bars.getBatchSize() * 4);
            String ring = Item.getAssetMap().getAssetMap().keySet().stream().filter(JewelryItemIds::isRing).filter(prices::hasExplicitPrice).sorted().findFirst().orElse(null);
            if (ring != null) {
                var rolled = JewelryMetadata.rollCraftedAppraised(ring, JewelryRarity.RARE, ThreadLocalRandom.current());
                require(JewelryMetadata.hasJewelryMeta(rolled) && JewelryMetadata.isAppraised(rolled), "The ring was not rolled");
                put("appraised " + ring, rolled, 1);
            } else {
                lines.add("(No priced ring is loaded on this server, so no ring was listed.)");
            }
            // One more worn tool that stays on its stall, for the price check.
            posted = put("second worn " + TOOL, worn.withMetadata("ShopMetaCheck", new BsonString("posted")), 1).stall();
            sales.removeLast();
        }

        /** The seller holds the stack in their hotbar and uses an empty stall: the interaction's own listing step. */
        private Sale put(String what, ItemStack held, int expectListed) {
            short slot = (short) Math.min(sales.size(), 8);
            sellerHotbar.setItemStackForSlot(slot, held, false);
            var stall = stall();
            var listed = ShopSpotPurchaseService.listFromSlot(plugin.getShopPriceCatalog(), stall, sellerHotbar, slot, seller, "Fixture Seller");
            require(listed.done(), what + " was refused: " + listed.refusal());
            require(listed.listed() == expectListed && listed.kept() == 0, what + ": listed " + listed.listed() + ", kept " + listed.kept());
            require(ItemStack.isEmpty(sellerHotbar.getItemStack(slot)), what + " is still in the seller's hotbar");
            require(ShopListing.keepsWholeStack(stall), what + ": the stall kept only an item id");
            require(stall.getQuote() != null, what + ": no price was recorded on the listing");
            registry.put(stall);
            ShopSpotDisplayService.syncDisplay(world, store, plugin, registry, stall, town);
            var sale = new Sale(what, held, stall);
            sales.add(sale);
            return sale;
        }

        /** The floating item above each stall is the listed stack itself, one of it. */
        private boolean everyStallShowsItsStack() {
            stage = "display";
            var all = new ArrayList<>(sales);
            all.add(new Sale("posted tool", ShopListing.stack(posted, 1), posted));
            for (Sale sale : all) {
                UUID display = sale.stall().getDisplayEntityUuid();
                Ref<EntityStore> ref = display == null ? null : store.getExternalData().getRefFromUUID(display);
                if (ref == null || !ref.isValid()) return false;
                ItemComponent item = store.getComponent(ref, ItemComponent.getComponentType());
                if (item == null || ItemStack.isEmpty(item.getItemStack())) return false;
                // The display is a real item entity holding the whole stack, so nothing may ever pick it up: not a
                // player, and not the backpack magnet, which takes what ItemComponent.canPickUp allows.
                require(ShopSpotDisplayService.isRuntimeShopDisplayProp(store, ref), sale.what() + ": its display is not marked as a shop display");
                require(!item.canPickUp(), sale.what() + ": its display can be picked up");
                String difference = difference(sale.held().withQuantity(1), item.getItemStack());
                require(difference == null, sale.what() + " is shown on its stall as something else: " + difference);
            }
            return true;
        }

        // ---- a restart

        private void saveAndReload() {
            stage = "save and reload";
            try {
                Files.createDirectories(scratch);
                Path stallsFile = scratch.resolve(ShopSpotPersistence.SHOP_SPOTS_FILE_NAME);
                Path townFile = scratch.resolve("towns.json");
                ShopSpotPersistence.saveTo(stallsFile, registry);
                TownWorldFile.writeBytesAtomic(townFile, TownWorldFile.toJsonBytes(List.of(town)));
                String saved = Files.readString(stallsFile);
                require(saved.contains("\"stack\""), "The saved stalls hold no stacks");
                int before = registry.allRecords().size();
                // Everything in memory is thrown away and read back, as a restart does.
                ShopSpotPersistence.loadFrom(stallsFile, registry);
                require(registry.allRecords().size() == before, "Stalls were lost on reload: " + registry.allRecords().size() + " of " + before);
                var towns = TownWorldFile.readOrEmpty(townFile).getTowns();
                require(towns.size() == 1 && towns.getFirst().getTownId().equals(town.getTownId()), "The town did not come back");
                town = towns.getFirst();
                manager.putTown(town);
                require(ShopSpotPurchaseService.isPlayerShopPlot(plugin, town, plotId), "The reloaded plot is not a player shop");
                for (int i = 0; i < sales.size(); i++) {
                    Sale sale = sales.get(i);
                    var reloaded = registry.get(sale.stall().getSpotId());
                    require(reloaded != null && reloaded != sale.stall(), sale.what() + ": its stall did not come back from disk");
                    require(seller.equals(reloaded.getSellerUuid()), sale.what() + ": the seller was lost");
                    sales.set(i, new Sale(sale.what(), sale.held(), reloaded));
                }
                posted = Objects.requireNonNull(registry.get(posted.getSpotId()), "The posted listing did not come back");
                lines.add(sales.size() + 1 + " listings were shown on their stalls as the listed stacks (none can be picked up), then written to disk with their town and read back.");
            } catch (IOException e) {
                throw new IllegalStateException("Could not write the fixture save: " + e, e);
            }
        }

        // ---- buying

        private void buy() {
            stage = "buying";
            for (Sale sale : sales) {
                var stall = sale.stall();
                String itemId = Objects.requireNonNull(stall.getItemId(), sale.what() + ": the stall is empty");
                ShopPriceEntry entry = ShopSpotPricing.entry(plugin, stall);
                int lots = entry.batchCountFromItemStock(stall.getStock());
                int quantity = entry.itemsForBatchCount(lots);
                require(quantity == sale.held().getQuantity(), sale.what() + ": " + quantity + " can be bought of " + sale.held().getQuantity() + " listed");
                long cost = ShopSpotPricing.totalCost(ShopSpotPricing.goldPerBatch(plugin, stall, itemId), lots);
                require(cost > 0, sale.what() + " is free");
                // What ShopSpotPurchaseService.executeBuy does once the buyer has paid.
                ItemStack grant = ShopSpotJewelrySupport.buildListingStack(itemId, quantity, stall);
                short slot = (short) sales.indexOf(sale);
                buyerBag.setItemStackForSlot(slot, ItemStack.EMPTY, false);
                require(buyerBag.addItemStackToSlot(slot, grant).succeeded(), sale.what() + " did not fit the buyer's bag");
                ShopSpotPurchaseService.creditSellerPayout(plugin, town, stall, cost);
                owed += cost;
                ShopListing.sold(stall, quantity);
                registry.put(stall);
                ShopSpotDisplayService.syncDisplay(world, store, plugin, registry, stall, town);

                ItemStack bought = buyerBag.getItemStack(slot);
                String difference = difference(sale.held(), bought);
                require(difference == null, sale.what() + " came out of the shop changed: " + difference);
                require(!stall.hasStock() && stall.getStackData() == null && stall.getSellerUuid() == null, sale.what() + ": the sold out stall still holds something");
                lines.add(sale.what() + ": bought for " + cost + " gold, equal to what was listed in " + describe(bought) + ".");
            }
            // The item itself, not a look-alike.
            ItemStack flask = buyerBag.getItemStack((short) 0);
            require(AlchemyFlask.ingredients(flask).equals(MIXTURE) && AlchemyFlask.charges(flask) == 1 && AlchemyFlask.capacity(flask) == 2
                && AlchemyFlask.quality(flask) == AlchemyBrew.Quality.GREAT, "The bought flask lost its mixture, charges or quality");
            // Two flasks with different mixtures are two listings and two stacks: the engine will not stack them.
            ItemStack otherFlask = buyerBag.getItemStack((short) 2);
            require(!flask.isStackableWith(otherFlask) && AlchemyFlask.ingredients(otherFlask).equals(OTHER_MIXTURE), "The two flasks were mixed up");
            require(town.getPlayerShopSafeGold(seller) == owed, "The seller's safe holds " + town.getPlayerShopSafeGold(seller) + " gold, sales came to " + owed);
            lines.add("The seller's safe holds the " + owed + " gold the sales came to.");
        }

        private boolean soldOutStallsAreBare() {
            stage = "display after sale";
            for (Sale sale : sales) if (sale.stall().getDisplayEntityUuid() != null) return false;
            return true;
        }

        // ---- listings from before stacks were kept

        private void oldSaves() {
            stage = "old saves";
            // A row as older versions wrote it: an item id, a count, and for jewelry its stats as text.
            var planks = stall();
            planks.setItemId(STONE);
            planks.setStock(12);
            planks.setSellerUuid(seller);
            require(!ShopListing.keepsWholeStack(planks) && planks.getQuote() == null, "An old row was given a stack or a price");
            ItemStack plain = ShopSpotJewelrySupport.buildListingStack(STONE, 12, planks);
            String difference = difference(new ItemStack(STONE, 12), plain);
            require(difference == null, "An old plain listing came out changed: " + difference);
            String text = "An old plain listing of 12 " + STONE + " still sells as 12 plain " + STONE + ".";
            Sale ringSale = sales.stream().filter(s -> JewelryItemIds.isJewelry(s.held().getItemId())).findFirst().orElse(null);
            if (ringSale != null) {
                var ring = stall();
                ring.setItemId(ringSale.held().getItemId());
                ring.setStock(1);
                ring.setSellerUuid(seller);
                ring.setJewelryMetaJson(JewelryMetadata.exportJewelryRootJson(ringSale.held()));
                ItemStack old = ShopSpotJewelrySupport.buildListingStack(ring.getItemId(), 1, ring);
                require(JewelryMetadata.hasJewelryMeta(old) && JewelryMetadata.isAppraised(old)
                    && JewelryMetadata.readRarity(old) == JewelryRarity.RARE
                    && JewelryMetadata.readTraits(old).equals(JewelryMetadata.readTraits(ringSale.held())), "An old ring listing lost its stats");
                text += " An old ring listing still sells with its rarity and " + JewelryMetadata.readTraits(old).size() + " stats.";
            }
            lines.add(text);
        }

        // ---- plumbing

        private CompletableFuture<Void> onWorld(Runnable action) {
            return CompletableFuture.runAsync(action, world);
        }

        private CompletableFuture<Void> await(String failure, BooleanSupplier ready) {
            return await(failure, ready, 0);
        }

        private CompletableFuture<Void> await(String failure, BooleanSupplier ready, int attempt) {
            return CompletableFuture.supplyAsync(ready::getAsBoolean, world).thenCompose(done -> {
                if (done) return CompletableFuture.completedFuture(null);
                if (attempt >= 50) return CompletableFuture.failedFuture(new IllegalStateException(failure));
                return CompletableFuture.runAsync(() -> {}, CompletableFuture.delayedExecutor(200, TimeUnit.MILLISECONDS))
                    .thenCompose(ignored -> await(failure, ready, attempt + 1));
            });
        }
    }

    // ------------------------------------------------------------------------------------------------ festivals

    /**
     * Items a festival holds for players: goods in the Autumn Market stall and a Wintertide gift between two players.
     * The stall is the real container with the real change listener; restarts go through the real town file; the
     * leftovers are real item entities on the ground. No player exists here, so handing a stack into a player's
     * inventory is the one step this cannot show.
     */
    private static final class Festivals {
        /** On the fixture floor the shop part laid, clear of its stalls and of the floor's edge. */
        private static final Vector3d CHARTER = new Vector3d(12.5, 129.0, 11.5);
        private final World world;
        private final AetherhavenPlugin plugin;
        private final Path scratch;
        private final Store<EntityStore> store;
        private final UUID giver = UUID.randomUUID(), receiver = UUID.randomUUID();
        private final List<String> lines = new ArrayList<>();
        private TownManager manager;
        private TownRecord town;
        private ItemStack flask, tool, otherFlask;
        volatile String stage = "setup";

        Festivals(World world, AetherhavenPlugin plugin, Path scratch) {
            this.world = world;
            this.plugin = plugin;
            this.scratch = scratch;
            this.store = world.getEntityStore().getStore();
        }

        String summary() {
            return String.join(" ", lines);
        }

        CompletableFuture<Void> run() {
            return CompletableFuture.runAsync(this::market, world)
                // The market's own queued hand-over has run by now. The keeper is not in the world, so the goods wait.
                .thenCompose(ignored -> CompletableFuture.runAsync(this::marketLeftovers, world))
                .thenCompose(ignored -> CompletableFuture.runAsync(this::wintertide, world))
                .thenCompose(ignored -> CompletableFuture.runAsync(this::townRemoved, world))
                .thenCompose(ignored -> await("what a removed town held never lay at its charter as the stacks it held", this::releasedStacksLie));
        }

        private ItemStack flask(List<String> mixture) {
            var filled = AlchemyFlask.upgrade(new ItemStack(FLASK, 1));
            filled = AlchemyFlask.consume(AlchemyFlask.fill(filled, mixture, AlchemyBrew.Quality.GREAT));
            require(AlchemyFlask.ingredients(filled).equals(mixture) && AlchemyFlask.charges(filled) == 1, "The flask was not filled");
            return filled;
        }

        /** A server restart for the town: written to its file form, read back, and put in place of the old record. */
        private void restartTown() {
            try {
                Files.createDirectories(scratch);
                Path file = scratch.resolve("festival-towns.json");
                TownWorldFile.writeBytesAtomic(file, TownWorldFile.toJsonBytes(List.of(town)));
                var towns = TownWorldFile.readOrEmpty(file).getTowns();
                require(towns.size() == 1 && towns.getFirst().getTownId().equals(town.getTownId()), "The town did not come back");
                town = towns.getFirst();
                manager.putTown(town);
            } catch (IOException e) {
                throw new IllegalStateException("Could not write the fixture town: " + e, e);
            }
        }

        private void same(ItemStack held, ItemStack got, String what) {
            String difference = difference(held, got);
            require(difference == null, what + ": " + difference);
        }

        private void market() {
            stage = "market stall";
            var full = new ItemStack(TOOL, 1);
            flask = flask(MIXTURE);
            otherFlask = flask(OTHER_MIXTURE);
            tool = full.withDurability(Math.floor(full.getMaxDurability() * 0.37)).withQuality(2).withMetadata("ShopMetaCheck", new BsonString("market"));
            manager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            town = new TownRecord(UUID.randomUUID(), giver, world.getName(), 12, 128, 11, 1, 1, 0);
            town.setDisplayName("Festival Fixture");
            manager.putTown(town);
            UUID townId = town.getTownId();

            // Players set goods out: the real stall container, written through the engine's own slot calls.
            MarketSession session = MarketSessionIndex.getOrCreate(townId);
            SimpleItemContainer stall = MarketStallService.stall(world, townId, session);
            require(stall.addItemStackToSlot((short) 0, flask).succeeded() && stall.addItemStackToSlot((short) 1, tool).succeeded()
                && stall.addItemStackToSlot((short) 2, otherFlask).succeeded(), "The stall refused the goods");
            require(town.hasFestivalHeldItems() && town.festivalHeldItems().hasMarketStall(), "Putting goods in the stall did not save them with the town");

            // A restart in the middle of the market: the session is gone, the town comes back from its file.
            stage = "market restart";
            restartTown();
            MarketSessionIndex.remove(townId);
            session = MarketSessionIndex.getOrCreate(townId);
            require(session.getLiveContainer() == null, "The session survived the restart");
            SimpleItemContainer restored = MarketStallService.stall(world, townId, session);
            require(restored != stall, "The stall was not read back from the town");
            same(flask, restored.getItemStack((short) 0), "The flask in the stall after a restart");
            same(tool, restored.getItemStack((short) 1), "The worn tool in the stall after a restart");
            same(otherFlask, restored.getItemStack((short) 2), "The second flask in the stall after a restart");
            require(AlchemyFlask.ingredients(restored.getItemStack((short) 0)).equals(MIXTURE)
                && !restored.getItemStack((short) 0).isStackableWith(restored.getItemStack((short) 2)), "The two flasks were mixed up");
            lines.add("Market stall: a filled flask, a worn tool and a second flask were saved with the town as they went in, and after a restart the stall held the same three stacks (" + describe(restored.getItemStack((short) 1)) + ").");

            // A player takes one flask back out. The town's copy follows, so it cannot come back a second time.
            require(!ItemStack.isEmpty(restored.removeItemStackFromSlot((short) 2).getOutput()), "The flask could not be taken out");
            restartTown();
            MarketSessionIndex.remove(townId);
            session = MarketSessionIndex.getOrCreate(townId);
            restored = MarketStallService.stall(world, townId, session);
            require(ItemStack.isEmpty(restored.getItemStack((short) 2)), "A flask taken out of the stall was still saved in it");

            // The festival square is set up again mid market (a look change): the session starts over. A window still
            // open on the old container must not hold a second copy of what the next stall reads back.
            stage = "market set up again";
            SimpleItemContainer stale = restored;
            session.clearAll();
            require(stale.isEmpty(), "A container that is no longer the stall still holds its goods");
            restored = MarketStallService.stall(world, townId, session);
            require(restored != stale, "The stall was not read back after the square was set up again");
            same(flask, restored.getItemStack((short) 0), "The flask after the square was set up again");
            same(tool, restored.getItemStack((short) 1), "The worn tool after the square was set up again");
            // Something put into the stale window is set aside for the keeper, not lost when the window closes.
            session.setStallKeeper(giver);
            require(restored.addItemStackToSlot((short) 3, new ItemStack(BARS, 1)).succeeded() && restored.removeItemStackFromSlot((short) 3).succeeded(), "The stall could not be used");
            require(stale.addItemStackToSlot((short) 0, otherFlask).succeeded(), "The stale container refused an item");
            require(stale.isEmpty() && town.festivalHeldItems().owedPlayers().equals(java.util.Set.of(giver)), "An item put into a stall that is gone was not set aside");
            same(otherFlask, town.festivalHeldItems().takeOwed(giver).getFirst(), "The flask put into a stall that is gone");

            // The market ends with the flask and the tool still in the stall.
            stage = "market end";
            MarketStallService.returnLeftovers(world, town);
            require(restored.isEmpty() && session.getLiveContainer() == null, "The stall still holds the goods it has let go of");
            require(!town.festivalHeldItems().hasMarketStall() && town.festivalHeldItems().owedPlayers().equals(java.util.Set.of(giver)), "The leftovers were not set aside for the stall's keeper");
            MarketSessionIndex.remove(townId);
        }

        private void marketLeftovers() {
            stage = "market leftovers";
            // The keeper is not in the world: the goods are still set aside, also after another restart.
            restartTown();
            var back = town.festivalHeldItems().takeOwed(giver);
            require(back.size() == 2 && town.festivalHeldItems().takeOwed(giver).isEmpty(), "The keeper is owed " + back.size() + " stacks, 2 were left in the stall");
            same(flask, back.get(0), "The leftover flask");
            same(tool, back.get(1), "The leftover tool");
            require(lying().isEmpty(), "Leftovers were put on the ground");
            lines.add("Market end: the flask and the tool left in the stall were set aside for the member who kept it, survived another restart, and were owed once, as the same two stacks. A container that stopped being the stall was emptied, and an item put into it was set aside too.");
        }

        /** Loose item stacks lying around the town's charter. Shop displays are not loose. */
        private List<ItemStack> lying() {
            List<ItemStack> out = new ArrayList<>();
            store.forEachChunk(
                Query.and(ItemComponent.getComponentType(), TransformComponent.getComponentType()),
                (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> ignored) -> {
                    for (int i = 0; i < chunk.size(); i++) {
                        Vector3d at = chunk.getComponent(i, TransformComponent.getComponentType()).getPosition();
                        ItemStack stack = chunk.getComponent(i, ItemComponent.getComponentType()).getItemStack();
                        if (at.distance(CHARTER) < 4 && !ItemStack.isEmpty(stack) && !chunk.getArchetype().contains(PreventPickup.getComponentType())) {
                            out.add(stack);
                        }
                    }
                }
            );
            return out;
        }

        /** The town is removed while it holds stall goods and an unanswered gift, and nobody they belong to is here. */
        private void townRemoved() {
            stage = "town removed";
            UUID townId = town.getTownId();
            MarketSession session = MarketSessionIndex.getOrCreate(townId);
            session.setStallKeeper(giver);
            require(MarketStallService.stall(world, townId, session).addItemStackToSlot((short) 0, tool).succeeded(), "The stall refused the tool");
            WintertideGiftService.holdGift(world, town, WintertideSessionIndex.getOrCreate(townId), giver, receiver, flask);
            FestivalHeldItemsService.releaseAll(world, store, town);
            require(!town.hasFestivalHeldItems() && MarketSessionIndex.get(townId) == null && WintertideSessionIndex.get(townId) == null,
                "A removed town still holds players' items");
            manager.removeTown(townId);
        }

        private boolean releasedStacksLie() {
            stage = "town removed, on the ground";
            var lying = lying();
            if (lying.size() < 2) {
                return false;
            }
            require(lying.size() == 2, lying.size() + " stacks lie at the charter, the town held 2");
            same(flask, lying.stream().filter(s -> FLASK.equals(s.getItemId())).findFirst().orElse(null), "The gift a removed town held");
            same(tool, lying.stream().filter(s -> TOOL.equals(s.getItemId())).findFirst().orElse(null), "The stall goods a removed town held");
            lines.add("Town removed while holding a gift and stall goods, their players away: both were set down at the charter as real item entities, equal to what was held.");
            return true;
        }

        private void wintertide() {
            stage = "wintertide gift";
            UUID townId = town.getTownId();
            // One item leaves the giver's hand and waits for the receiver's answer.
            WintertideSession session = WintertideSessionIndex.getOrCreate(townId);
            WintertideGiftService.holdGift(world, town, session, giver, receiver, flask);
            require(session.getPendingPlayerGift() != null && town.festivalHeldItems().wintertideGift() != null, "The gift was not held");
            same(flask, session.getPendingPlayerGift().stack(), "The gift waiting for an answer");

            // The server restarts before the answer. The gift comes back from the town's file and, with its giver not
            // in the world, is set aside for them.
            stage = "wintertide restart";
            restartTown();
            WintertideSessionIndex.remove(townId);
            same(flask, town.festivalHeldItems().wintertideGift().stack(), "The gift after a restart");
            FestivalHeldItemsService.settle(world, plugin, townId);
            town = manager.getTown(townId);
            require(town.festivalHeldItems().wintertideGift() == null && town.festivalHeldItems().owedPlayers().equals(java.util.Set.of(giver)),
                "An unanswered gift was not set aside for its giver");
            restartTown();
            var back = town.festivalHeldItems().takeOwed(giver);
            require(back.size() == 1 && town.festivalHeldItems().takeOwed(giver).isEmpty(), "The giver is owed " + back.size() + " stacks, not 1");
            same(flask, back.getFirst(), "The gift set aside for its giver");

            // The festival ends while a gift is unanswered: the session is thrown away, as the festival's end does.
            stage = "wintertide end";
            session = WintertideSessionIndex.getOrCreate(townId);
            WintertideGiftService.holdGift(world, town, session, giver, receiver, tool);
            WintertideSessionIndex.remove(townId);
            FestivalHeldItemsService.settle(world, plugin, townId);
            town = manager.getTown(townId);
            back = town.festivalHeldItems().takeOwed(giver);
            require(back.size() == 1 && town.festivalHeldItems().wintertideGift() == null, "A gift unanswered at the festival's end was not set aside for its giver");
            same(tool, back.getFirst(), "The gift held at the festival's end");

            // The receiver leaves without answering while the festival runs on: the same, and the session lets go.
            session = WintertideSessionIndex.getOrCreate(townId);
            WintertideGiftService.holdGift(world, town, session, giver, receiver, otherFlask);
            FestivalHeldItemsService.settle(world, plugin, townId);
            town = manager.getTown(townId);
            require(session.getPendingPlayerGift() == null, "A gift whose receiver has left still blocks the next gift");
            back = town.festivalHeldItems().takeOwed(giver);
            require(back.size() == 1, "A gift whose receiver left was not set aside for its giver");
            same(otherFlask, back.getFirst(), "The gift whose receiver left");
            require(!town.hasFestivalHeldItems(), "Something is still held after everything was handed back");
            WintertideSessionIndex.remove(townId);
            lines.add("Wintertide gift: a filled flask held for the receiver's answer came back from a restart unchanged and was set aside for its giver; a worn tool held when the festival ended, and a flask whose receiver left, likewise. Each was owed once and equal to what was given.");
        }

        private CompletableFuture<Void> await(String failure, BooleanSupplier ready) {
            return await(failure, ready, 0);
        }

        private CompletableFuture<Void> await(String failure, BooleanSupplier ready, int attempt) {
            return CompletableFuture.supplyAsync(ready::getAsBoolean, world).thenCompose(done -> {
                if (done) return CompletableFuture.completedFuture(null);
                if (attempt >= 50) return CompletableFuture.failedFuture(new IllegalStateException(failure));
                return CompletableFuture.runAsync(() -> {}, CompletableFuture.delayedExecutor(200, TimeUnit.MILLISECONDS))
                    .thenCompose(ignored -> await(failure, ready, attempt + 1));
            });
        }
    }

    /** Null when the two stacks agree in every field the engine keeps, otherwise the first field that differs. */
    private static String difference(ItemStack listed, ItemStack got) {
        if (ItemStack.isEmpty(got)) return "nothing was handed over";
        if (ItemStack.isEmpty(listed)) return "there was nothing to compare with";
        if (!listed.getItemId().equals(got.getItemId())) return "item " + listed.getItemId() + " became " + got.getItemId();
        if (listed.getQuantity() != got.getQuantity()) return "quantity " + listed.getQuantity() + " became " + got.getQuantity();
        if (Double.compare(listed.getDurability(), got.getDurability()) != 0) return "durability " + listed.getDurability() + " became " + got.getDurability();
        if (Double.compare(listed.getMaxDurability(), got.getMaxDurability()) != 0) return "maximum durability " + listed.getMaxDurability() + " became " + got.getMaxDurability();
        if (listed.getQualityIndex() != got.getQualityIndex()) return "quality " + listed.getQualityIndex() + " became " + got.getQualityIndex();
        if (!Objects.equals(ItemStackData.metadata(listed), ItemStackData.metadata(got))) return "metadata " + ItemStackData.metadata(listed) + " became " + ItemStackData.metadata(got);
        if (!listed.equals(got)) return "the engine does not find them equal";
        return null;
    }

    private static String describe(ItemStack stack) {
        var metadata = ItemStackData.metadata(stack);
        return "item id, quantity " + stack.getQuantity() + ", durability " + stack.getDurability() + " of " + stack.getMaxDurability()
            + ", quality " + stack.getQualityIndex() + " and metadata (" + (metadata == null ? "none" : metadata.size() + " keys: " + String.join(", ", metadata.keySet())) + ")";
    }

    // ------------------------------------------------------------------------------------------------ prices

    /** Someone typing commands: what they may do, and everything they were told. */
    private static class Typist implements CommandSender {
        private final String name;
        private final UUID uuid = UUID.randomUUID();
        private final Predicate<String> holds;
        private final List<String> heard = new ArrayList<>();
        private int mark;

        Typist(String name, Predicate<String> holds) {
            this.name = name;
            this.holds = holds;
        }

        @Override public synchronized void sendMessage(Message message) {
            heard.add(MessageUtil.toAnsiString(message).toString());
        }

        /** Called before a command is typed, so {@link #reply} is what that command said. */
        synchronized void typing() {
            mark = heard.size();
        }

        /** Everything said since the last command was typed. */
        synchronized String reply() {
            return mark >= heard.size() ? "(no reply)" : String.join(" | ", heard.subList(mark, heard.size()));
        }

        synchronized int count() {
            return heard.size();
        }

        @Override public String getUsername() { return name; }
        @Override public UUID getUuid() { return uuid; }
        @Override public boolean hasPermission(String id) { return holds.test(id); }
        @Override public boolean hasPermission(String id, boolean def) { return holds.test(id); }
    }

    private static final class Prices {
        private final AetherhavenPlugin plugin;
        private final ShopPriceAdmin admin;
        private final List<String> lines = new ArrayList<>();
        /** The console may do anything. Not a player, so the "item in my hand" forms do not apply to it. */
        private final Typist console = new Typist("Console (check)", id -> true);
        /** A player nobody made an operator: the server's own permission tables answer for a fresh player. */
        private final Typist player = new Typist("Player (check)", null) {
            @Override public boolean hasPermission(String id) { return PermissionsModule.get().hasPermission(getUuid(), id); }
            @Override public boolean hasPermission(String id, boolean def) { return PermissionsModule.get().hasPermission(getUuid(), id, def); }
        };
        /** Staff given the price node and nothing else beyond what every player has. */
        private final Typist staff = new Typist("Staff (check)", null) {
            @Override public boolean hasPermission(String id) {
                return AetherhavenConstants.PERMISSION_PRICE_ADMIN.equals(id) || PermissionsModule.get().hasPermission(getUuid(), id);
            }
            @Override public boolean hasPermission(String id, boolean def) { return hasPermission(id); }
        };
        private ShopPriceEntry flaskBefore, toolBefore;
        private boolean changed;
        private long toolShipped;
        volatile String stage = "setup";

        Prices(AetherhavenPlugin plugin) {
            this.plugin = plugin;
            this.admin = AetherhavenPriceCommand.admin(plugin);
        }

        String summary() {
            return String.join(" ", lines);
        }

        private CompletableFuture<Void> type(Typist who, String command) {
            who.typing();
            return CommandManager.get().handleCommand(who, command).handle((done, error) -> null);
        }

        private long live(String item) {
            return plugin.getShopPriceCatalog().getGoldPrice(item);
        }

        /** Part one: refusals, then two prices set. Leaves the flask at 45 gold and the tool at 77. */
        CompletableFuture<Void> setAndCheck() {
            stage = "setup";
            var flask = admin.show(FLASK);
            var tool = admin.show(TOOL);
            // Prices this server already set for the two items are put back at the end.
            flaskBefore = flask.source() == Source.OVERRIDE ? flask.price() : null;
            toolBefore = tool.source() == Source.OVERRIDE ? tool.price() : null;
            changed = true;
            return type(console, "ah price remove " + FLASK)
                .thenCompose(ignored -> type(console, "ah price remove " + TOOL))
                .thenCompose(ignored -> {
                    stage = "permission";
                    toolShipped = live(TOOL);
                    require(admin.show(TOOL).source() == Source.GENERATED, TOOL + " has no shipped price to test with");
                    require(admin.show(FLASK).source() != Source.OVERRIDE, "The flask's server price could not be cleared");
                    return type(player, "ah price set " + TOOL + " 77");
                })
                .thenCompose(ignored -> type(player, "ah price list"))
                .thenCompose(ignored -> type(player, "ah price reload"))
                .thenCompose(ignored -> {
                    require(live(TOOL) == toolShipped && admin.show(TOOL).source() == Source.GENERATED, "A player without the permission changed a price");
                    require(player.count() == 3, "A refused player was told " + player.count() + " things for 3 commands");
                    lines.add("A player without " + AetherhavenConstants.PERMISSION_PRICE_ADMIN + " is refused (\"" + player.reply() + "\") and nothing changes.");
                    return type(staff, "ah price show " + TOOL);
                })
                .thenCompose(ignored -> {
                    require(staff.reply().contains(String.valueOf(toolShipped)), "Staff holding the node could not look up a price: " + staff.reply());
                    lines.add("Staff holding only that node may use it.");
                    stage = "show";
                    return type(console, "ah price show " + TOOL);
                })
                .thenCompose(ignored -> {
                    lines.add("show: \"" + console.reply() + "\"");
                    return type(console, "ah price show " + FLASK);
                })
                .thenCompose(ignored -> {
                    require(!plugin.getShopPriceCatalog().hasExplicitPrice(FLASK) || admin.show(FLASK).source() != Source.DEFAULT, "show disagrees with the catalog");
                    stage = "bad input";
                    return type(console, "ah price set " + TOOL + " 0");
                })
                .thenCompose(ignored -> badInput("0 gold"))
                .thenCompose(ignored -> type(console, "ah price set " + TOOL + " -5"))
                .thenCompose(ignored -> badInput("-5 gold"))
                .thenCompose(ignored -> type(console, "ah price set " + TOOL + " 5000000"))
                .thenCompose(ignored -> badInput("5000000 gold"))
                .thenCompose(ignored -> type(console, "ah price set " + TOOL + " many"))
                .thenCompose(ignored -> badInput("\"many\" gold"))
                .thenCompose(ignored -> type(console, "ah price set " + TOOL + " 10 --per 5000"))
                .thenCompose(ignored -> badInput("a lot of 5000"))
                .thenCompose(ignored -> type(console, "ah price set No_Such_Item_Anywhere 10"))
                .thenCompose(ignored -> badInput("an unknown item"))
                .thenCompose(ignored -> type(console, "ah price set 10"))
                .thenCompose(ignored -> badInput("no item and no hand"))
                .thenCompose(ignored -> {
                    stage = "set";
                    return type(console, "ah price set " + FLASK + " 45");
                })
                .thenCompose(ignored -> {
                    require(live(FLASK) == 45 && admin.show(FLASK).source() == Source.OVERRIDE, "The flask's price was not set: " + console.reply());
                    require(plugin.getShopPriceCatalog().hasExplicitPrice(FLASK), "A priced flask still cannot be listed");
                    lines.add("set: \"" + console.reply() + "\"");
                    return type(console, "ah price set " + TOOL + " 77");
                })
                .thenCompose(ignored -> {
                    require(live(TOOL) == 77, "The tool's price was not changed: " + console.reply());
                    require(fileText().contains("\"" + TOOL + "\": 77") && fileText().contains("\"" + FLASK + "\": 45"), "The prices are not in the server's price file");
                    stage = "restart";
                    // What a server start does: the shipped table and the server's file are read from disk again.
                    plugin.reloadShopPriceCatalog();
                    require(live(TOOL) == 77 && live(FLASK) == 45, "The prices did not survive being read from disk again");
                    return type(console, "ah price reload");
                })
                .thenCompose(ignored -> {
                    require(live(TOOL) == 77 && live(FLASK) == 45 && admin.list().size() >= 2, "reload lost the prices: " + console.reply());
                    lines.add("Both prices are in " + admin.file().getFileName() + " and survive being read from disk again (\"" + console.reply() + "\").");
                    stage = "live effect";
                    var merchant = new ShopSpotRecord();
                    merchant.setItemId(TOOL);
                    merchant.setStock(1);
                    require(ShopSpotPricing.entry(plugin, merchant).getGoldPerBatch() == 77, "An NPC stall does not use the new price");
                    var job = GSON.fromJson("{\"materialPaymentBonusGold\":10,\"rewards\":[{\"kind\":\"item\",\"itemId\":\"Aetherhaven_Gold_Coin\",\"count\":10,\"grantTo\":\"player\"}]}", QuestBoardFetchEntryJson.class);
                    int pay = FetchQuestPayment.quote(job, List.of(new QuestBoardItemRequirement(TOOL, 1)), plugin.getShopPriceCatalog()).orElseThrow().getFirst().count();
                    require(pay == 87, "A new delivery job pays " + pay + " gold, expected 10 plus 77");
                    lines.add("At once: an NPC stall asks 77, and a new delivery job for one tool pays 87.");
                    return type(console, "ah price list");
                })
                .thenAccept(ignored -> lines.add("list: \"" + console.reply() + "\""));
        }

        private CompletableFuture<Void> badInput(String what) {
            require(live(TOOL) == toolShipped, what + " was accepted: the tool now costs " + live(TOOL));
            require(admin.show(FLASK).source() != Source.OVERRIDE && admin.show(TOOL).source() == Source.GENERATED, what + " changed a price");
            lines.add("Refused " + what + ": \"" + console.reply() + "\"");
            return CompletableFuture.completedFuture(null);
        }

        /** Part two, after the listings were bought: prices go back, and what was posted keeps its price. */
        CompletableFuture<Void> removeAndCheck(ShopSpotRecord posted) {
            stage = "remove";
            return type(console, "ah price remove " + TOOL)
                .thenCompose(ignored -> {
                    require(live(TOOL) == toolShipped && admin.show(TOOL).source() == Source.GENERATED, "The tool did not go back to its shipped price: " + console.reply());
                    lines.add("remove: \"" + console.reply() + "\"");
                    if (posted != null) {
                        var quote = ShopSpotPricing.entry(plugin, posted);
                        require(quote.getGoldPerBatch() == 77, "A listing posted at 77 gold now asks " + quote.getGoldPerBatch());
                        lines.add("A tool listed while the price was 77 still asks 77 after the price went back to " + toolShipped + ".");
                    }
                    return type(console, "ah price remove " + FLASK);
                })
                .thenCompose(ignored -> {
                    require(!plugin.getShopPriceCatalog().hasExplicitPrice(FLASK) || flaskShipped(), "The flask kept a server price: " + console.reply());
                    return type(console, "ah price remove " + FLASK);
                })
                .thenCompose(ignored -> {
                    lines.add("Removing a price that is not set: \"" + console.reply() + "\"");
                    plugin.reloadShopPriceCatalog();
                    require(live(TOOL) == toolShipped && admin.show(FLASK).source() != Source.OVERRIDE, "A removed price came back from disk");
                    require(!fileText().contains("\"" + TOOL + "\"") && !fileText().contains("\"" + FLASK + "\""), "A removed price is still in the file");
                    return CompletableFuture.completedFuture(null);
                });
        }

        private boolean flaskShipped() {
            return admin.show(FLASK).source() != Source.OVERRIDE;
        }

        private String fileText() {
            try {
                return Files.isRegularFile(admin.file()) ? Files.readString(admin.file()) : "";
            } catch (IOException e) {
                throw new IllegalStateException("Could not read " + admin.file() + ": " + e, e);
            }
        }

        /** Whatever happened, the server's prices are left as they were found. */
        void restore() {
            if (!changed) return;
            changed = false;
            Item flask = Item.getAssetMap().getAsset(FLASK), tool = Item.getAssetMap().getAsset(TOOL);
            if (flaskBefore != null && flask != null) admin.set(FLASK, flaskBefore.getGoldPerBatch(), flaskBefore.getBatchSize(), flask.getMaxStack());
            else admin.remove(FLASK);
            if (toolBefore != null && tool != null) admin.set(TOOL, toolBefore.getGoldPerBatch(), toolBefore.getBatchSize(), tool.getMaxStack());
            else admin.remove(TOOL);
        }
    }
}
