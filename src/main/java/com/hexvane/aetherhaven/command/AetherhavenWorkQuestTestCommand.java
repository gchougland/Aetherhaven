package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCompleter;
import com.hexvane.aetherhaven.dialogue.DialogueActionExecutor;
import com.hexvane.aetherhaven.hud.AetherhavenHudSnapshotService;
import com.hexvane.aetherhaven.production.ProductionWorkplaceKinds;
import com.hexvane.aetherhaven.quest.QuestAutoPin;
import com.hexvane.aetherhaven.quest.QuestAutoStartService;
import com.hexvane.aetherhaven.quest.QuestProgressionService;
import com.hexvane.aetherhaven.quest.WorkplaceQuestHooks;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.quest.data.QuestObjective;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.ClaimedTerritoryChunkRecord;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.ui.PlayerTownJournalState;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hexvane.aetherhaven.workplace.TownWorkplaces;
import com.hexvane.aetherhaven.workplace.WorkplaceArea;
import com.hexvane.aetherhaven.workplace.WorkplaceRules;
import com.hexvane.aetherhaven.workplace.WorkplaceStations;
import com.hexvane.aetherhaven.workplace.WorkplaceTypePicker;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import com.hypixel.hytale.server.core.universe.world.storage.provider.EmptyChunkStorageProvider;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.VoidWorldGenProvider;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.joml.Vector3i;

/**
 * The first workplace quest on a real server, in a disposable world, for every workplace type.
 *
 * <p>Each type gets a town of its own. The town's supervising building is finished the way a real build finishes, and
 * the quest must start by that alone. The four steps are then done through the calls the pages and the Building Staff
 * make, with a real bench block set down in the world, and the closing talk through the call Lyren's dialogue makes.
 * After every step the quest's state, the pin of a town member and the line the HUD tracker would draw are checked.
 * Towns as an older save would hold them follow.
 *
 * <p>No player can join this server, so the member is a journal state held here and not an entity. That the client
 * draws the tracker is not shown.
 */
final class AetherhavenWorkQuestTestCommand extends AbstractAsyncCommand {
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final String QUEST = "q_first_workplace";
    private static final int FLOOR = 127, Y = 128;
    /** The types whose every step is written out in the result. The others are named with their bench. */
    private static final Set<TownsfolkWorkType> NARRATED = Set.of(TownsfolkWorkType.SMELTING, TownsfolkWorkType.FARMING, TownsfolkWorkType.MYCOLOGY);

    AetherhavenWorkQuestTestCommand() {
        super("workquest", "Walk the first workplace quest through every workplace type in an isolated world");
        setPermissionGroups("hytale:WorldEditor");
    }

    @Override protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        if (!RUNNING.compareAndSet(false, true)) { report(ctx, "A workplace quest check is already running."); return CompletableFuture.completedFuture(null); }
        String name = "aetherhaven_workquest_check_" + UUID.randomUUID().toString().replace("-", "");
        var universe = Universe.get(); var path = universe.validateWorldPath(name);
        if (Files.exists(path)) { RUNNING.set(false); return CompletableFuture.failedFuture(new IllegalStateException("Fixture already exists")); }
        var config = new WorldConfig(); com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig.ensureAndGet(config);
        config.setWorldGenProvider(new VoidWorldGenProvider()); config.setChunkStorageProvider(EmptyChunkStorageProvider.INSTANCE);
        config.setSavingPlayers(false); config.setCanSaveChunks(false); config.setSaveNewChunks(false); config.setSpawningNPC(false);
        config.setGameTimePaused(true); config.setBlockTicking(false); config.setDeleteOnRemove(true); config.setDeleteOnUniverseStart(true);
        var notes = new ArrayList<String>();
        return universe.makeWorld(name, path, config).thenCompose(world -> exercise(world, notes).orTimeout(150, TimeUnit.SECONDS).whenCompleteAsync((done, error) -> {
            if (error == null) report(ctx, "Workplace quest isolated world check: PASSED. " + String.join(" ", notes));
            else { Throwable cause = error; while (cause.getCause() != null) cause = cause.getCause(); report(ctx, "Workplace quest isolated world check: FAILED: " + cause + " | reached: " + String.join(" ", notes)); }
            universe.removeWorld(name);
        })).whenComplete((done, error) -> RUNNING.set(false));
    }

    /** Chunks the fixture keeps running. No player holds them, so each step switches them back on. */
    private static final Set<Integer> LIVE = ConcurrentHashMap.newKeySet();

    private static CompletableFuture<Void> load(World world, int chunkX) {
        return CompletableFuture.allOf(world.getChunkStore().getChunkSectionReferenceAsync(chunkX, 3, 0, GetChunkFlags.SET_TICKING),
            world.getChunkStore().getChunkSectionReferenceAsync(chunkX, 4, 0, GetChunkFlags.SET_TICKING)).thenCompose(ignored -> onWorld(world, () -> {
            LIVE.add(chunkX);
            for (int x = chunkX * 32; x < chunkX * 32 + 32; x++) for (int z = 0; z < 32; z++)
                require(ChunkSectionBlockUtil.setBlockByKey(world, x, FLOOR, z, "Rock_Stone", 0), "Floor placement failed");
        }));
    }

    /** One town for one workplace type, in a chunk of its own. */
    private static final class Fixture {
        final TownsfolkWorkType type; final int chunk; final UUID owner = UUID.randomUUID(); final TownRecord town;
        final PlayerTownJournalState member = new PlayerTownJournalState();
        VillagerDefinition villager; PlotInstance building;
        Fixture(World world, TownsfolkWorkType type, int chunk, String name) {
            this.type = type; this.chunk = chunk;
            town = new TownRecord(UUID.randomUUID(), owner, world.getName(), chunk * 32 + 16, Y, 16, 1, 1, 0);
            // Exactly its own chunk, so a block set down is found to be in this town and no other.
            town.getClaimedTerritoryChunks().add(ClaimedTerritoryChunkRecord.of(chunk, 0));
            town.setDisplayName(name);
        }
        int x(int local) { return chunk * 32 + local; }
        WorkplaceArea area(int slot) { return new WorkplaceArea(x(2 + slot * 9), Y, 2, x(9 + slot * 9), 9); }
    }

    private static CompletableFuture<Void> exercise(World world, List<String> notes) {
        LIVE.clear();
        var plugin = AetherhavenPlugin.get();
        var types = WorkplaceTypePicker.creatable();
        var manager = new TownManager[1];
        CompletableFuture<Void> chain = onWorld(world, () -> {
            manager[0] = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            var quest = plugin.getQuestCatalog().get(QUEST);
            require(quest != null, "The quest is not loaded. Is the Quests feature switched on?");
            require(quest.autoPin() && quest.autoStart() != null, "The quest does not start and pin by itself");
        });
        for (int i = 0; i < types.size(); i++) {
            int chunk = i; var type = types.get(i);
            var fixture = new Fixture[1];
            chain = chain.thenCompose(ignored -> load(world, chunk))
                .thenCompose(ignored -> onWorld(world, () -> fixture[0] = finishBuilding(world, plugin, manager[0], type, chunk)))
                // Nothing here starts the quest. Finishing the building queued the look that does.
                .thenCompose(ignored -> await(world, "the quest to start by itself for " + type.id, () -> fixture[0].town.hasQuestActive(QUEST)))
                .thenCompose(ignored -> onWorld(world, () -> walk(world, plugin, manager[0], fixture[0], notes)));
        }
        int next = types.size();
        chain = chain.thenCompose(ignored -> load(world, next)).thenCompose(ignored -> load(world, next + 1)).thenCompose(ignored -> load(world, next + 2))
            .thenCompose(ignored -> onWorld(world, () -> olderSaves(world, plugin, manager[0], next, notes)));
        // The last town's workplace lies in a chunk that is not loaded yet, as a town's do while its world starts.
        var hidden = new Fixture[1];
        chain = chain.thenCompose(ignored -> onWorld(world, () -> hidden[0] = outOfSight(world, plugin, manager[0], next + 3)))
            .thenCompose(ignored -> load(world, next + 3))
            .thenCompose(ignored -> onWorld(world, () -> comeIntoSight(world, plugin, manager[0], hidden[0], notes)));
        return chain;
    }

    /** The supervising villager of a type, by the data: the villager whose resident kind is the type's supervisor. */
    private static VillagerDefinition supervisor(AetherhavenPlugin plugin, TownsfolkWorkType type) {
        String kind = WorkplaceRules.supervisor(type);
        var roles = new TreeSet<>(plugin.getVillagerDefinitionCatalog().allByNpcRoleId().keySet());
        for (String role : roles)
            if (kind.equals(ProductionWorkplaceKinds.residentBindingKindForNpcRoleId(plugin, role))) return plugin.getVillagerDefinitionCatalog().byNpcRoleId(role);
        throw new IllegalStateException("No villager is the " + kind);
    }

    private static void settle(Fixture f, VillagerDefinition villager, PlotInstanceState state) {
        f.villager = villager;
        require(villager.getWorkConstructionId() != null, villager.getNpcRoleId() + " has no building");
        f.building = new PlotInstance(UUID.randomUUID(), villager.getWorkConstructionId(), state,
            new PlotFootprintRecord(f.x(20), Y, 20, f.x(29), Y + 8, 29), f.x(20), Y, 20, 0);
        f.town.addPlotInstance(f.building);
        f.town.getResidentNpcRecords().add(new ResidentNpcRecord(villager.getNpcRoleId(), WorkplaceRules.supervisor(f.type), f.building.getPlotId(), UUID.randomUUID()));
    }

    /** Founds a town, moves the type's villager in, and finishes their building through the game's own completion. */
    private static Fixture finishBuilding(World world, AetherhavenPlugin plugin, TownManager manager, TownsfolkWorkType type, int chunk) {
        var f = new Fixture(world, type, chunk, "Workquest " + type.id);
        manager.putTown(f.town);
        require(QuestAutoStartService.evaluate(world, plugin, manager, f.town).isEmpty() && !f.town.hasQuestActive(QUEST),
            type.id + ": a town with no villager was given the quest");
        settle(f, supervisor(plugin, type), PlotInstanceState.ASSEMBLING);
        require(plugin.getConstructionCatalog().get(f.building.getConstructionId()) != null, "No building " + f.building.getConstructionId());
        require(QuestAutoStartService.evaluate(world, plugin, manager, f.town).isEmpty() && !f.town.hasQuestActive(QUEST),
            type.id + ": the quest started while the building was still going up");
        ConstructionCompleter.finishBuild(world, plugin, f.owner, f.building.getPlotId(), new Vector3i(f.x(24), Y, 24), Rotation.None);
        require(f.building.getState() == PlotInstanceState.COMPLETE, type.id + ": the building did not finish");
        return f;
    }

    private static List<String> done(AetherhavenPlugin plugin, TownRecord town) {
        var def = plugin.getQuestCatalog().get(QUEST);
        return def.objectivesOrEmpty().stream().filter(o -> QuestProgressionService.isObjectiveComplete(town, QUEST, o)).map(QuestObjective::id).toList();
    }

    private static String current(AetherhavenPlugin plugin, TownRecord town) {
        var objective = QuestProgressionService.currentObjective(plugin, town, QUEST);
        return objective == null ? "none" : objective.id();
    }

    /** The line the HUD tracker draws for the quest, or null when the tracker has no row for it. */
    private static String hudLine(World world, AetherhavenPlugin plugin, TownRecord town, PlayerTownJournalState member) {
        for (var row : new AetherhavenHudSnapshotService(plugin).pinnedQuests(town, null, null, world.getEntityStore().getStore(), member.getPinnedQuestIds()))
            if (QUEST.equals(row.id())) return plain(row.objectives().getAnsiMessage(), "HUD line");
        return null;
    }

    private static String journalLine(World world, AetherhavenPlugin plugin, TownRecord town, String objectiveId) {
        QuestDefinition def = plugin.getQuestCatalog().get(QUEST);
        for (var objective : def.objectivesOrEmpty())
            if (objectiveId.equals(objective.id()))
                return plain(plugin.getQuestCatalog().formatObjectiveLine(objective, null, town, world.getEntityStore().getStore(), plugin, QUEST).getAnsiMessage(), "journal line");
        throw new IllegalStateException("No objective " + objectiveId);
    }

    /** A line a player could read: written, every gap filled, and not a bare language key. */
    private static String plain(String text, String what) {
        require(text != null && !text.isBlank(), "The " + what + " is empty");
        require(!text.contains("{") && !text.contains("}"), "The " + what + " has an unfilled gap: " + text);
        require(!text.contains("aetherhaven_workplace_quest") && !text.contains("workplaceQuest."), "The " + what + " is a bare language key: " + text);
        return text;
    }

    private static String itemName(String itemId) {
        var item = Item.getAssetMap().getAsset(itemId);
        require(item != null, "No item " + itemId);
        String name = I18nModule.get().getMessage("en-US", item.getTranslationKey());
        require(name != null && !name.isBlank(), itemId + " has no English name");
        return name;
    }

    /** Checks the quest after a step and returns what a player would see of it. */
    private static String after(World world, AetherhavenPlugin plugin, Fixture f, List<String> expectDone, String expectCurrent) {
        String label = f.type.id + " after " + (expectDone.isEmpty() ? "start" : expectDone.getLast());
        require(f.town.hasQuestActive(QUEST), label + ": the quest is not active");
        require(done(plugin, f.town).equals(expectDone), label + ": steps done are " + done(plugin, f.town) + ", expected " + expectDone);
        require(current(plugin, f.town).equals(expectCurrent), label + ": the tracker is on " + current(plugin, f.town) + ", expected " + expectCurrent);
        require(f.member.isQuestPinned(QUEST), label + ": the quest is no longer pinned");
        String line = hudLine(world, plugin, f.town, f.member);
        require(line != null, label + ": the HUD tracker has no row for the pinned quest");
        return "steps " + expectDone.size() + "/5 pinned=yes tracker=\"" + line + "\"";
    }

    /** The four steps, the way a player does them, then Lyren. */
    private static void walk(World world, AetherhavenPlugin plugin, TownManager manager, Fixture f, List<String> notes) {
        var type = f.type; var town = f.town; var sites = town.getApprenticeWorkplaces();
        boolean narrate = NARRATED.contains(type);
        var said = new StringBuilder("[" + type.id + "] ");
        require(town.wasQuestAutoStarted(QUEST), type.id + ": started but not marked as given");
        // A member arrives. Nobody pins anything by hand.
        require(!f.member.isQuestPinned(QUEST), type.id + ": pinned before any member arrived");
        var pinned = QuestAutoPin.apply(f.member, town, plugin.getQuestCatalog(), id -> false);
        require(pinned.size() == 1 && pinned.getFirst().added(), type.id + ": a member was not given the pin");
        String started = after(world, plugin, f, List.of(), "unlock");
        String villagerName = f.villager.getDisplayName();
        require(villagerName == null || villagerName.isBlank() || started.contains(villagerName), type.id + ": the first line does not name " + villagerName + ": " + started);
        String building = plain(plugin.getConstructionCatalog().get(f.building.getConstructionId()).displayNameMessage().getAnsiMessage(), "building name");
        require(started.contains(building), type.id + ": the first line does not name the building " + building + ": " + started);
        String unlockLine = journalLine(world, plugin, town, "unlock");
        require(unlockLine.contains(WorkplaceRules.label(type)), type.id + ": the unlock step does not offer " + WorkplaceRules.label(type) + ": " + unlockLine);
        if (narrate) said.append("started by finishing ").append(building).append(": ").append(started).append(" journal=\"").append(unlockLine).append("\"");

        // 1. The upgrades page buys the type for the player, from the treasury here.
        town.addTreasuryGoldCoins(TownWorkplaces.TYPE_UNLOCK_COST);
        String refused = sites.buyUnlock(town, f.owner, type, new CombinedItemContainer(new SimpleItemContainer((short) 1)));
        require(refused == null, type.id + ": unlock refused: " + refused);
        manager.updateTown(town);
        WorkplaceQuestHooks.typeUnlocked(world, manager, town);
        String unlocked = after(world, plugin, f, List.of("unlock"), "mark");
        if (narrate) said.append(" | unlock: ").append(unlocked);

        // 2. The Building Staff marks the second corner.
        WorkplaceQuestHooks.areaMarked(world, manager, town);
        String marked = after(world, plugin, f, List.of("unlock", "mark"), "choose");
        String chooseLine = journalLine(world, plugin, town, "choose");
        require(chooseLine.contains(WorkplaceRules.label(type)), type.id + ": the choose step does not name the unlocked type: " + chooseLine);
        if (narrate) said.append(" | mark: ").append(marked);

        // The station this type needs, from the loaded items and recipes.
        var station = WorkplaceStations.find(type);
        require(station != null, type.id + ": no loaded item places this type's station");
        String bench = itemName(station.itemId());
        String block = Item.getAssetMap().getAsset(station.itemId()).getBlockId();
        var area = f.area(0);
        int bx = area.minX() + 2, bz = area.minZ() + 2;
        int way = type.ordinal() % 3;
        // One town in three has its bench standing before the area is saved, as a player who builds first would.
        if (way == 1) require(ChunkSectionBlockUtil.setBlockByKey(world, bx, Y, bz, block, 0, 0), type.id + ": the bench could not be placed");

        // 3. The workplace page saves the area with its type.
        String siteName = "Fixture " + type.id;
        refused = sites.save(town, null, type, area, siteName);
        require(refused == null, type.id + ": workplace not saved: " + refused);
        manager.updateTown(town);
        WorkplaceQuestHooks.workplaceSaved(world, manager, town);
        if (way == 1) {
            String both = after(world, plugin, f, List.of("unlock", "mark", "choose", "bench"), "talk");
            if (narrate) said.append(" | choose, with the bench already inside: ").append(both);
        } else {
            String chosen = after(world, plugin, f, List.of("unlock", "mark", "choose"), "bench");
            require(chosen.contains(bench) && chosen.contains(siteName), type.id + ": the bench step does not name " + bench + " and " + siteName + ": " + chosen);
            String benchLine = journalLine(world, plugin, town, "bench");
            require(benchLine.contains(bench) && benchLine.contains(WorkplaceRules.label(type)), type.id + ": the journal does not name the bench and type: " + benchLine);
            if (narrate) said.append(" | choose: ").append(chosen).append(" journal=\"").append(benchLine).append("\"");
            if (type == TownsfolkWorkType.SMELTING) removeAndMarkAgain(world, plugin, manager, f, area, siteName, said);

            // 4. The bench is set down inside the workplace.
            require(ChunkSectionBlockUtil.setBlockByKey(world, bx, Y, bz, block, 0, 0), type.id + ": the bench could not be placed");
            require(current(plugin, town).equals("bench"), type.id + ": the step ticked before anything looked at the bench");
            if (way == 0) WorkplaceQuestHooks.blockPlaced(world, bx, Y, bz);
            // One town in three gets no event at all: the slow look finds the bench.
            else QuestAutoStartService.evaluate(world, plugin, manager, town);
            String placed = after(world, plugin, f, List.of("unlock", "mark", "choose", "bench"), "talk");
            if (narrate) said.append(" | bench (").append(way == 0 ? "set down" : "found by the slow look").append("): ").append(placed);
        }

        // 5. Lyren's dialogue completes it.
        require(QuestProgressionService.advanceDialogueTurnIn(plugin, town, QUEST), type.id + ": Lyren could not close the quest");
        DialogueActionExecutor.applyQuestCompletion(world, plugin, town, manager, QUEST);
        require(town.hasQuestCompleted(QUEST) && !town.hasQuestActive(QUEST), type.id + ": the quest did not complete");
        require(hudLine(world, plugin, town, f.member) == null, type.id + ": the tracker still shows a finished quest");

        // A second workplace, and the slow look, start nothing again.
        if (sites.slots(WorkplaceRules.supervisor(type)) > sites.used(WorkplaceRules.supervisor(type))) {
            require(sites.save(town, null, type, f.area(1), "Second " + type.id) == null, type.id + ": second workplace not saved");
            WorkplaceQuestHooks.areaMarked(world, manager, town);
            WorkplaceQuestHooks.workplaceSaved(world, manager, town);
        }
        require(QuestAutoStartService.evaluate(world, plugin, manager, town).isEmpty() && !town.hasQuestActive(QUEST), type.id + ": the quest started a second time");

        var made = station.craftedAt();
        String where = made == null ? "no recipe" : made.pocket() ? "Pocket Crafting"
            : (station.craftedAtItemId() == null ? made.benchId() : itemName(station.craftedAtItemId())) + (made.tier() > 1 ? " tier " + made.tier() : "");
        if (narrate) notes.add(said.append(" | Lyren: completed, tracker row gone, a second workplace started nothing.").toString());
        else notes.add("[" + type.id + "] " + f.villager.getDisplayName() + " at " + building + ", bench " + bench + " (" + where + "), "
            + (way == 0 ? "set down" : way == 1 ? "inside before saving" : "found by the slow look") + ": 5/5.");
    }

    /** The player removes the workplace half way, the way its page does, and marks it again. Nothing is lost. */
    private static void removeAndMarkAgain(World world, AetherhavenPlugin plugin, TownManager manager, Fixture f, WorkplaceArea area, String name, StringBuilder said) {
        var town = f.town; var sites = town.getApprenticeWorkplaces();
        var site = sites.sites().getFirst();
        WorkplaceQuestHooks.workplaceRemoving(world, manager, town);
        String refused = sites.remove(town, f.owner, site.id());
        require(refused == null, "Removal refused: " + refused);
        manager.updateTown(town);
        String gone = after(world, plugin, f, List.of("unlock", "mark", "choose"), "bench");
        require(sites.sites().isEmpty(), "The workplace is still there");
        said.append(" | workplace removed: ").append(gone);
        require(sites.save(town, null, f.type, area, name) == null, "The workplace could not be marked again");
        manager.updateTown(town);
        WorkplaceQuestHooks.workplaceSaved(world, manager, town);
        after(world, plugin, f, List.of("unlock", "mark", "choose"), "bench");
    }

    /** Towns as a save from before the quest existed holds them, read the way the game reads every town. */
    private static void olderSaves(World world, AetherhavenPlugin plugin, TownManager manager, int chunk, List<String> notes) {
        // A supervising building and nothing else: given the quest, and a member who arrives later is pinned.
        var bare = new Fixture(world, TownsfolkWorkType.FARMING, chunk, "Workquest older bare");
        manager.putTown(bare.town);
        settle(bare, supervisor(plugin, bare.type), PlotInstanceState.COMPLETE);
        require(QuestAutoStartService.evaluate(world, plugin, manager, bare.town).equals(List.of(QUEST)), "An older town with a supervising building was not given the quest");
        QuestAutoPin.apply(bare.member, bare.town, plugin.getQuestCatalog(), id -> false);
        String bareLine = after(world, plugin, bare, List.of(), "unlock");
        // The player leaves the quest. It is not pushed on them again, and Lyren can still offer it.
        bare.town.clearActiveQuest(QUEST);
        require(QuestAutoStartService.evaluate(world, plugin, manager, bare.town).isEmpty() && !bare.town.hasQuestActive(QUEST), "An abandoned quest came back by itself");
        require(QuestAutoStartService.offerable(plugin.getQuestCatalog().get(QUEST), bare.town, plugin.getConstructionCatalog()), "Lyren cannot offer an abandoned quest again");

        // A workplace marked and typed, with no bench in it: given the quest at its last step.
        var floor = new Fixture(world, TownsfolkWorkType.CRAFTING, chunk + 1, "Workquest older floor");
        manager.putTown(floor.town);
        settle(floor, supervisor(plugin, floor.type), PlotInstanceState.COMPLETE);
        require(floor.town.getApprenticeWorkplaces().unlock(floor.town, floor.type) == null, "Unlock failed");
        require(floor.town.getApprenticeWorkplaces().save(floor.town, null, floor.type, floor.area(0), "Old workshop") == null, "Old workplace not saved");
        require(QuestAutoStartService.evaluate(world, plugin, manager, floor.town).equals(List.of(QUEST)), "An older town with a bare workplace was not given the quest");
        QuestAutoPin.apply(floor.member, floor.town, plugin.getQuestCatalog(), id -> false);
        String floorLine = after(world, plugin, floor, List.of("unlock", "mark", "choose"), "bench");

        // A working workplace with its bench: not offered. Marked done with no banner, no reward and no pin.
        var working = new Fixture(world, TownsfolkWorkType.BLACKSMITHING, chunk + 2, "Workquest older working");
        manager.putTown(working.town);
        settle(working, supervisor(plugin, working.type), PlotInstanceState.COMPLETE);
        require(working.town.getApprenticeWorkplaces().unlock(working.town, working.type) == null, "Unlock failed");
        var forge = working.area(0);
        require(working.town.getApprenticeWorkplaces().save(working.town, null, working.type, forge, "Old forge") == null, "Old workplace not saved");
        String anvil = Item.getAssetMap().getAsset(WorkplaceStations.find(working.type).itemId()).getBlockId();
        require(ChunkSectionBlockUtil.setBlockByKey(world, forge.minX() + 2, Y, forge.minZ() + 2, anvil, 0, 0), "The anvil could not be placed");
        long gold = working.town.getTreasuryGoldCoinCount();
        require(QuestAutoStartService.evaluate(world, plugin, manager, working.town).isEmpty(), "A town that already has a working workplace was offered the quest");
        require(working.town.hasQuestCompleted(QUEST) && !working.town.hasQuestActive(QUEST), "The working town's quest was not marked done");
        require(working.town.getTreasuryGoldCoinCount() == gold, "A quest nobody did paid a reward");
        require(QuestAutoPin.apply(working.member, working.town, plugin.getQuestCatalog(), id -> false).isEmpty() && !working.member.isQuestPinned(QUEST), "A quest nobody was given was pinned");
        notes.add("Older saves: a town with only a supervising building was given the quest on its first read (" + bareLine
            + "), and after the player left it, it did not come back while Lyren could still offer it; a town with a workplace and no bench was given it at the last step ("
            + floorLine + "); a town whose workplace already had its bench was not offered it: marked done, no reward, no pin.");
    }

    /** A town with a working workplace whose chunk is not loaded: nothing can be decided, so nothing is shown. */
    private static Fixture outOfSight(World world, AetherhavenPlugin plugin, TownManager manager, int chunk) {
        var f = new Fixture(world, TownsfolkWorkType.SMELTING, chunk, "Workquest older unseen");
        manager.putTown(f.town);
        settle(f, supervisor(plugin, f.type), PlotInstanceState.COMPLETE);
        require(f.town.getApprenticeWorkplaces().unlock(f.town, f.type) == null, "Unlock failed");
        require(f.town.getApprenticeWorkplaces().save(f.town, null, f.type, f.area(0), "Far smeltery") == null, "Far workplace not saved");
        require(ChunkSectionBlockUtil.worldChunkIfTicking(world, f.x(4), 4) == null, "The far chunk is already loaded, so this proves nothing");
        require(QuestAutoStartService.evaluate(world, plugin, manager, f.town).isEmpty(), "A town whose workplace nobody can see was offered the quest");
        require(!f.town.hasQuestActive(QUEST) && !f.town.hasQuestCompleted(QUEST), "The unseen town was decided without a look");
        return f;
    }

    /** A member walks up: the chunk loads, the furnace is seen, and the town is quietly marked as knowing how. */
    private static void comeIntoSight(World world, AetherhavenPlugin plugin, TownManager manager, Fixture f, List<String> notes) {
        var area = f.area(0);
        String furnace = Item.getAssetMap().getAsset(WorkplaceStations.find(f.type).itemId()).getBlockId();
        require(ChunkSectionBlockUtil.setBlockByKey(world, area.minX() + 2, Y, area.minZ() + 2, furnace, 0, 0), "The furnace could not be placed");
        require(QuestAutoStartService.evaluate(world, plugin, manager, f.town).isEmpty(), "The seen town was offered the quest");
        require(f.town.hasQuestCompleted(QUEST) && !f.town.hasQuestActive(QUEST), "The seen town's quest was not marked done");
        notes.add("A town whose workplace lay in a chunk that was not loaded was left undecided, and marked done without a word once the chunk loaded and its furnace was seen.");
    }

    /** Looks again every 150 ms, for up to 30 seconds. */
    private static CompletableFuture<Void> await(World world, String what, Supplier<Boolean> ready) { return await(world, what, ready, 200); }
    private static CompletableFuture<Void> await(World world, String what, Supplier<Boolean> ready, int left) {
        var met = new boolean[1];
        return CompletableFuture.runAsync(() -> { }, CompletableFuture.delayedExecutor(150, TimeUnit.MILLISECONDS))
            .thenCompose(ignored -> onWorld(world, () -> met[0] = ready.get())).thenCompose(ignored -> {
                if (met[0]) return CompletableFuture.completedFuture(null);
                if (left <= 0) return CompletableFuture.failedFuture(new IllegalStateException("Gave up waiting for " + what));
                return await(world, what, ready, left - 1);
            });
    }
    private static CompletableFuture<Void> onWorld(World world, Runnable action) {
        return CompletableFuture.runAsync(() -> {
            for (int chunkX : LIVE) {
                var chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, chunkX * 32, 0);
                if (chunk != null) ChunkSectionBlockUtil.startTicking(world, chunk);
            }
            action.run();
        }, world);
    }
    private static void require(boolean yes, String issue) { if (!yes) throw new IllegalStateException(issue); }
    private static void report(CommandContext ctx, String message) { ctx.sendMessage(Message.raw(message)); HytaleLogger.forEnclosingClass().atInfo().log(message); }
}
