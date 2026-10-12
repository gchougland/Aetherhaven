package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.clinic.ClinicPage;
import com.hexvane.aetherhaven.clinic.TownClinic;
import com.hexvane.aetherhaven.crafter.PatternBook;
import com.hexvane.aetherhaven.equipment.ProfessionKit;
import com.hexvane.aetherhaven.equipment.ProfessionKitCatalog;
import com.hexvane.aetherhaven.equipment.ProfessionKitService;
import com.hexvane.aetherhaven.hunter.HuntPage;
import com.hexvane.aetherhaven.hunter.TownHunts;
import com.hexvane.aetherhaven.production.HerdProductionState;
import com.hexvane.aetherhaven.propshop.FurnitureMerchantShopService;
import com.hexvane.aetherhaven.propshop.FurnitureMerchantShopSlotRecord;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkProgress;
import com.hexvane.aetherhaven.ui.PageDelta;
import com.hexvane.aetherhaven.warehouse.SmeltingOutputStore;
import com.hexvane.aetherhaven.warehouse.TownWarehouse;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch;
import com.hexvane.aetherhaven.warehouse.WarehousePage;
import com.hexvane.aetherhaven.workplace.BuildingOrders;
import com.hexvane.aetherhaven.workplace.WorkplaceOrders;
import com.hexvane.aetherhaven.workplace.WorkplaceOrdersPage;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomUICommand;
import com.hypixel.hytale.protocol.packets.interface_.CustomUICommandType;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBinding;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Presses on storage and workplace pages, made after the town changed behind the page.
 *
 * <p>Every case is the same three steps on the loaded game's real items and containers: remember what a page would
 * have drawn, let the town work (a courier stores something, a craft finishes, a wave is won), then do what the
 * page's button does. A press must be honoured when what it names can still be done, and refused only when it truly
 * cannot, with nothing lost or made twice. Everything is made for the check. No world, player or town is changed.
 */
final class AetherhavenPagesTestCommand extends AbstractAsyncCommand {
    private static final String BAR = "Ingredient_Bar_Iron", LEATHER = "Ingredient_Leather_Light", SAP = "Ingredient_Tree_Sap",
        ESSENCE = "Ingredient_Life_Essence", SEEDS = "Plant_Seeds_Wheat";
    private static final TownWarehouse.Page GENERAL = TownWarehouse.Page.GENERAL, OUTPUT = TownWarehouse.Page.OUTPUT;

    AetherhavenPagesTestCommand() {
        super("pages", "Press storage and workplace page buttons after the town changed behind them");
        setPermissionGroups("hytale:WorldEditor");
    }

    @Override protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        String result;
        try {
            result = "Pages isolated check PASSED: " + run();
        } catch (RuntimeException failed) {
            result = "Pages isolated check FAILED: " + failed.getMessage();
        }
        ctx.sendMessage(Message.raw(result));
        HytaleLogger.forEnclosingClass().atInfo().log(result);
        return CompletableFuture.completedFuture(null);
    }

    private static String run() {
        for (String item : List.of(BAR, LEATHER, SAP, ESSENCE, SEEDS)) require(Item.getAssetMap().getAsset(item) != null, "Missing game item " + item);
        var done = new ArrayList<String>();
        var town = new TownRecord();
        var warehouse = town.getWarehouse();
        int bars = 12;

        // ---- Deposit: the page drew 8 bars in inventory slot 0, then couriers changed storage.
        var bag = bag(4);
        bag.setItemStackForSlot((short) 0, new ItemStack(BAR, 8), false);
        bag.setItemStackForSlot((short) 1, new ItemStack(BAR, 4), false);
        var drawn = bag.getItemStack((short) 0);
        long revision = warehouse.revision();
        townWorks(warehouse);
        var deposit = warehouse.deposit(GENERAL, bag, (short) 0, drawn, TownWarehouse.Amount.STACK);
        require(deposit.moved() == 8 && deposit.refusal() == null, "Deposit after storage changed was refused: " + deposit.error());
        require(warehouse.revision() > revision + 1, "Storage did not change behind the page");
        require(count(bag, BAR) + warehouse.count(GENERAL, BAR) == bars, "Deposit lost or made bars");
        done.add("deposit stored 8 of 8 Iron Ingots although storage had moved on from revision " + revision + " to " + (warehouse.revision() - 1));

        // ---- Withdraw: the page drew the pile of 8, then a courier added 4 to that very pile.
        short pile = warehouse.locate(GENERAL, (short) 0, new ItemStack(BAR, 1));
        require(pile >= 0, "Stored bars not found");
        var drawnPile = warehouse.stack(GENERAL, pile);
        require(warehouse.deposit(GENERAL, bag, (short) 1, bag.getItemStack((short) 1), TownWarehouse.Amount.STACK).moved() == 4, "Courier fixture failed");
        townWorks(warehouse);
        var withdraw = warehouse.withdraw(GENERAL, bag, pile, drawnPile, TownWarehouse.Amount.ONE);
        require(withdraw.moved() == 1, "Take one after the pile grew was refused: " + withdraw.error());
        var stack = warehouse.withdraw(GENERAL, bag, pile, drawnPile, TownWarehouse.Amount.STACK);
        require(stack.moved() == 11, "Take a stack after the pile grew moved " + stack.moved());
        require(count(bag, BAR) + warehouse.count(GENERAL, BAR) == bars && count(bag, BAR) == bars, "Withdrawal lost or made bars");
        done.add("withdrawal took 1 then 11 from a pile drawn at 8 that a courier had raised to 12");

        // ---- Gone: every bar was taken. The same press is refused, names the item and changes nothing.
        long quiet = warehouse.revision();
        var gone = warehouse.withdraw(GENERAL, bag, pile, drawnPile, TownWarehouse.Amount.STACK);
        require(gone.moved() == 0 && gone.refusal() == TownWarehouse.Refusal.GONE && warehouse.revision() == quiet, "A press for goods that are gone was not refused cleanly");
        String told = WarehousePage.refusalText(gone.refusal(), "Iron Ingot", false, "General storage");
        require(told.equals("No Iron Ingot is left in General storage. Nothing was moved."), "Unexpected refusal text: " + told);
        done.add("withdrawal of goods taken by someone else answered \"" + told + "\"");

        // ---- A list that moved: leather now sits where the bars were drawn and the bars sit elsewhere.
        var other = bag(4);
        other.setItemStackForSlot((short) 0, new ItemStack(LEATHER, 8), false);
        other.setItemStackForSlot((short) 1, new ItemStack(BAR, 5), false);
        require(warehouse.deposit(GENERAL, other, (short) 0, other.getItemStack((short) 0), TownWarehouse.Amount.STACK).moved() == 8, "Leather fixture failed");
        require(warehouse.deposit(GENERAL, other, (short) 1, other.getItemStack((short) 1), TownWarehouse.Amount.STACK).moved() == 5, "Bar fixture failed");
        short leatherAt = warehouse.locate(GENERAL, (short) 0, new ItemStack(LEATHER, 1));
        short barsAt = warehouse.locate(GENERAL, leatherAt, drawnPile);
        require(leatherAt >= 0 && barsAt >= 0 && barsAt != leatherAt, "Moved list fixture failed");
        var taker = bag(2);
        var moved = warehouse.withdraw(GENERAL, taker, leatherAt, drawnPile, TownWarehouse.Amount.STACK);
        require(moved.moved() == 5 && count(taker, BAR) == 5 && count(taker, LEATHER) == 0 && warehouse.count(GENERAL, LEATHER) == 8,
            "A press on a moved list took the wrong row");
        done.add("a press drawn on a slot that now holds Light Leather took the 5 Iron Ingots from slot " + barsAt + " and no leather");

        // ---- Reserved and full: the two other true reasons.
        require(warehouse.reserve(UUID.randomUUID(), UUID.randomUUID(), "fixture", List.of(new MaterialQuantity(LEATHER, null, null, 8, null)),
            List.of(new ItemStack(SEEDS, 1))) != null, "Reservation fixture failed");
        require(warehouse.withdraw(GENERAL, taker, leatherAt, new ItemStack(LEATHER, 8), TownWarehouse.Amount.STACK).refusal() == TownWarehouse.Refusal.RESERVED,
            "Reserved goods were handed over");
        var full = bag(1);
        full.setItemStackForSlot((short) 0, new ItemStack(SEEDS, Math.max(1, Item.getAssetMap().getAsset(SEEDS).getMaxStack())), false);
        require(warehouse.acceptGuardSpoils(new ItemStack(SAP, 3)), "Sap fixture failed");
        short sapAt = warehouse.locate(GENERAL, (short) 0, new ItemStack(SAP, 1));
        var noRoom = warehouse.withdraw(GENERAL, full, sapAt, new ItemStack(SAP, 3), TownWarehouse.Amount.STACK);
        require(noRoom.refusal() == TownWarehouse.Refusal.NO_ROOM && warehouse.count(GENERAL, SAP) == 3, "A full inventory lost goods");
        done.add("reserved goods and a full inventory were refused as RESERVED and NO_ROOM with nothing moved");

        // ---- Store matching items after storage changed.
        var carrying = bag(3);
        carrying.setItemStackForSlot((short) 0, new ItemStack(SAP, 4), false);
        carrying.setItemStackForSlot((short) 1, new ItemStack(ESSENCE, 2), false);
        townWorks(warehouse);
        var matching = warehouse.depositMatching(carrying, 0, carrying.getCapacity());
        require(matching.moved() == 4 && matching.matched() && count(carrying, ESSENCE) == 2 && warehouse.count(GENERAL, SAP) == 7, "Store matching items failed after storage changed");
        done.add("store matching items moved 4 Tree Sap and left what storage does not hold");

        // ---- A kit equipped from storage after storage changed; the worn kit comes back in its place.
        var kits = new ProfessionKitCatalog(List.of(new ProfessionKit(ESSENCE, "Fixture kit", "logistics", 0, 1), new ProfessionKit(SEEDS, "Old kit", "logistics", 0, 1)));
        var progress = new TownsfolkProgress();
        var worn = new ItemStack(SEEDS, 1);
        progress.setProfessionKitData(TownsfolkProgress.encodeProfessionKit(worn));
        var kit = new ItemStack(ESSENCE, 1);
        require(warehouse.acceptCompletedOutput(kit), "Kit fixture failed");
        short kitAt = warehouse.locate(OUTPUT, (short) 0, kit);
        townWorks(warehouse);
        String equip = warehouse.equipProfessionKit(OUTPUT, kitAt, kit, progress, kits);
        require(equip == null && kit.equals(progress.professionKit()) && worn.equals(warehouse.stack(OUTPUT, kitAt)), "Kit swap after storage changed failed: " + equip);
        require(ProfessionKitService.TAKEN.equals(warehouse.equipProfessionKit(OUTPUT, kitAt, kit, progress, kits)), "A kit that is gone was equipped again");
        done.add("a kit was equipped from storage after storage changed, and a second press answered \"" + ProfessionKitService.TAKEN + "\"");

        // ---- Smelted goods: the furnace added to the pile after the page drew it.
        var smelting = new SmeltingOutputStore();
        var job = UUID.randomUUID(); var origin = UUID.randomUUID(); var furnace = bag(4);
        require(smelting.reserve(job, List.of(new ItemStack(BAR, 6))), "Smelting fixture failed");
        furnace.setItemStackForSlot((short) 0, new ItemStack(BAR, 2), false);
        require(smelting.collect(job, origin, new WarehouseBatch.Point(10, 100, 10), furnace) == 2, "Smelting collection fixture failed");
        var drawnBars = smelting.origin(origin).stack((short) 0);
        furnace.setItemStackForSlot((short) 0, new ItemStack(BAR, 3), false);
        require(smelting.collect(job, origin, new WarehouseBatch.Point(10, 100, 10), furnace) == 3, "Smelting collection fixture failed");
        var smelted = bag(1);
        require(smelting.withdraw(origin, (short) 0, drawnBars, 5, smelted) == 5 && smelting.count(BAR) == 0, "Smelted goods were refused after the furnace added to them");
        done.add("smelted goods drawn at 2 were collected as the 5 the furnace had made");

        // ---- Workplace tasks: a townsfolk finished a craft, which moves the list's revision.
        var tasks = new WorkplaceOrders();
        require(tasks.add("first", WorkplaceOrders.Mode.COUNT, 3) == null && tasks.add("second", WorkplaceOrders.Mode.FOREVER, 1) == null, "Task fixture failed");
        var first = tasks.list().getFirst(); var second = tasks.list().getLast();
        long tasksDrawn = tasks.revision();
        tasks.recordCompletion(first.id());
        require(tasks.revision() != tasksDrawn, "Finished work did not move the task list");
        require(tasks.add("third", WorkplaceOrders.Mode.COUNT, 1) == null, "Add task after a craft finished was refused");
        require(tasks.change(second.id(), "Up") == null && tasks.list().getFirst() == second, "Move task after a craft finished was refused");
        require(tasks.change(second.id(), "Pause") == null && tasks.change(second.id(), "Pause") == null && second.paused(), "Pause seen twice undid itself");
        require("That task is already first.".equals(tasks.change(second.id(), "Up")), "A task that cannot move was not refused with its reason");
        done.add("tasks were added, moved and paused after a finished craft moved the list");

        // ---- Project board: another project was queued behind the page.
        var board = new BuildingOrders();
        var author = UUID.randomUUID();
        require(board.add(UUID.randomUUID(), author) == null && board.add(UUID.randomUUID(), author) == null, "Project fixture failed");
        var top = board.list().getFirst(); var next = board.list().getLast();
        require(board.add(UUID.randomUUID(), author) == null, "Project fixture failed");
        require(board.move(next.id(), -1) && board.list().getFirst().equals(next) && board.remove(top.id()) && !board.remove(top.id()), "Project presses after the board moved failed");
        require(board.pause(true) && !board.pause(true) && board.paused(), "Pause yard seen twice undid itself");
        done.add("projects were moved and removed by id after the board changed");

        // ---- Clinic: the Doctor treated a patient, which drops the supply the old check compared.
        var clinic = new TownClinic();
        require(clinic.supply(2), "Clinic fixture failed");
        long clinicDrawn = clinic.revision();
        require(clinic.treat(10, 100) > 0 && clinic.revision() != clinicDrawn, "Clinic fixture failed");
        require(ClinicPage.supplyIssue(clinic, 5, 5) == null, "Stocking medicine after a treatment was refused");
        String short1 = ClinicPage.supplyIssue(clinic, 5, 2);
        require("You need 5 Lesser Health Potions and carry 2. Nothing was taken.".equals(short1), "Unexpected clinic refusal: " + short1);
        done.add("medicine could be stocked after a treatment moved the supply");

        // ---- Hunt: the hunt changed behind the page; training names the level it buys.
        var hunts = new TownHunts();
        long huntDrawn = hunts.revision();
        require(hunts.upgrade("storage") && hunts.revision() != huntDrawn, "Hunt fixture failed");
        require(HuntPage.tierIssue(hunts.level("vitality"), "1", "Vitality") == null, "Training after the hunt changed was refused");
        require(hunts.upgrade("vitality") && HuntPage.tierIssue(hunts.level("vitality"), "1", "Vitality") != null, "One training offer could be bought twice");
        done.add("training was offered after the hunt changed and one offer cannot be bought twice");

        // ---- Tobin's bench: he finished the first prop, so every other commission moved up one place.
        var book = new PatternBook();
        for (String prop : List.of("a", "b", "c", "d", "e")) book.learn(prop);
        require(book.commission("a", Map.of(BAR, 5)) && book.commission("b", Map.of(BAR, 10)) && book.commission("c", Map.of(BAR, 15)), "Bench fixture failed");
        require(book.work(100, prop -> 100) == 1 && "c".equals(book.queue().get(1).prop()), "Bench fixture failed");
        int benchAt = book.locate(1, "b");
        require(benchAt == 0 && Map.of(BAR, 10).equals(book.cancel(benchAt)) && book.locate(1, "b") < 0, "Cancel on a moved bench hit the wrong commission");
        done.add("cancel drawn at bench place 2 cancelled that commission at place 1, not the one that moved into place 2");

        // ---- Barn: another animal was bought behind the page.
        var herd = new HerdProductionState();
        require(herd.buy("Sheep", 10, () -> true) == null, "Barn fixture failed");
        var sheep = herd.animals().getFirst().id();
        long herdDrawn = herd.revision();
        require(herd.buy("Sheep", 10, () -> true) == null && herd.revision() != herdDrawn, "Barn fixture failed");
        require(herd.assign(0, sheep, 1) && !herd.assign(0, sheep, 1) && herd.slot(0).id().equals(sheep), "Pen press after the barn changed failed");
        done.add("an animal was put in its pen after another purchase changed the barn");

        // ---- Finished goods on the task page: the slot the press named now holds another item.
        var crafted = bag(4);
        crafted.setItemStackForSlot((short) 1, new ItemStack(LEATHER, 2), false);
        crafted.setItemStackForSlot((short) 3, new ItemStack(BAR, 9), false);
        require(WorkplaceOrdersPage.heldSlot(4, (short) 1, BAR, crafted::getItemStack) == 3 && WorkplaceOrdersPage.heldSlot(4, (short) 1, SAP, crafted::getItemStack) == -1,
            "Collect on a moved Crafted Goods list would take the wrong item");
        done.add("collect follows the item to its slot and never takes what replaced it");

        // ---- Prop shop: another customer bought one; the offer a Buy press named is still there.
        var shop = new TownRecord();
        shop.getFurnitureMerchantShopSlots().add(new FurnitureMerchantShopSlotRecord("fixture_prop", 2));
        String offers = FurnitureMerchantShopService.offersToken(shop), stock = FurnitureMerchantShopService.inventoryToken(shop);
        shop.getFurnitureMerchantShopSlots().getFirst().setStock(1);
        require(!stock.equals(FurnitureMerchantShopService.inventoryToken(shop)) && offers.equals(FurnitureMerchantShopService.offersToken(shop))
            && "fixture_prop".equals(FurnitureMerchantShopService.offerAt(shop, false, 0)), "A sale to another customer would refuse this customer's press");
        done.add("a sale to another customer leaves this customer's offer valid");

        // ---- Timed pages: nothing is sent while nothing moves, and only the moved value when one does.
        var delta = new PageDelta();
        delta.sent(page("40 / 800", "12 / 20"), new CustomUIEventBinding[0]);
        int quietSends = 0;
        for (int tick = 0; tick < 600; tick++) if (delta.next(page("40 / 800", "12 / 20"), new CustomUIEventBinding[0]) != null) quietSends++;
        int busySends = 0, busyValues = 0;
        for (int tick = 1; tick <= 60; tick++) {
            var update = delta.next(page("40 / 800", (12 + tick / 5) + " / 20"), new CustomUIEventBinding[0]);
            if (update == null) continue;
            busySends++; busyValues += update.commands().length;
            require(!update.whole(), "A moved value redrew the whole page");
        }
        require(quietSends == 0 && busySends == 12 && busyValues == 12, "Timed page sent " + quietSends + " quiet and " + busySends + " busy updates");
        done.add("a page refreshed every second sent 0 updates in 600 quiet seconds and 12 single value updates in a minute with one value moving every 5 seconds");

        return done.size() + " presses and checks made after the town changed behind the page. " + String.join("; ", done) + ". No world, player or town was changed.";
    }

    /** What couriers and finished crafts do to storage while a page is open. */
    private static void townWorks(TownWarehouse warehouse) {
        long before = warehouse.revision();
        require(warehouse.acceptGuardSpoils(new ItemStack(SEEDS, 1)), "Courier fixture failed");
        require(warehouse.revision() > before, "Storage revision did not move");
    }

    /** A made up page of one value and one row, as a build would list it. No page is opened or sent. */
    private static CustomUICommand[] page(String supply, String health) {
        String total = id("Total"), rows = id("Rows");
        return new CustomUICommand[]{
            new CustomUICommand(CustomUICommandType.Set, total + ".Text", supply, null),
            new CustomUICommand(CustomUICommandType.Clear, rows, null, null),
            new CustomUICommand(CustomUICommandType.Append, rows, null, "Fixture/Row"),
            new CustomUICommand(CustomUICommandType.Set, rows + "[0] " + id("Amount") + ".Text", health, null)};
    }
    private static String id(String name) { return '#' + name; }

    private static SimpleItemContainer bag(int slots) { return new SimpleItemContainer((short) slots); }

    private static int count(SimpleItemContainer bag, String item) {
        int total = 0;
        for (short slot = 0; slot < bag.getCapacity(); slot++) {
            var stack = bag.getItemStack(slot);
            if (!ItemStack.isEmpty(stack) && item.equals(stack.getItemId())) total += stack.getQuantity();
        }
        return total;
    }

    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
