package com.hexvane.aetherhaven.economy;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.hunter.*;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hypixel.hytale.assetstore.*;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.event.*;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class SharedGoldPurchasesTest {
    private static Object previousItemStore;
    private final UUID owner = UUID.randomUUID();
    private static class ItemStore extends AssetStore<String, Item, DefaultAssetMap<String, Item>> {
        private final EventBus events = new EventBus(false);
        ItemStore(Config config) { super(config); }
        @Override protected IEventBus getEventBus() { return events; }
        @Override public void addFileMonitor(String pack, Path path) {}
        @Override public void removeFileMonitor(Path path) {}
        @Override protected void handleRemoveOrUpdate(Set<String> removed, Map<String, Item> updated, AssetUpdateQuery query) {}
        private static class Config extends Builder<String, Item, DefaultAssetMap<String, Item>, Config> {
            Config() { super(String.class, Item.class, new DefaultAssetMap<>()); setPath("Items"); setCodec(Item.CODEC); setKeyFunction(Item::getId); }
            @Override public ItemStore build() { return new ItemStore(this); }
        }
    }
    @BeforeAll static void assets() throws Exception {
        var field=Item.class.getDeclaredField("ASSET_STORE");field.setAccessible(true);
        previousItemStore=field.get(null);field.set(null,new ItemStore.Config().build());
    }
    @AfterAll static void restore() throws Exception {
        var field=Item.class.getDeclaredField("ASSET_STORE");field.setAccessible(true);field.set(null,previousItemStore);
    }
    private TownRecord town(long gold) {
        var town=new TownRecord();town.setOwner(owner,"Owner");town.setTreasuryGoldCoinCount(gold);return town;
    }
    private static CombinedItemContainer inventory(int... coins) {
        var bag=new SimpleItemContainer((short)10);
        for(short i=0;i<coins.length;i++)if(coins[i]>0)bag.setItemStackForSlot(i,new ItemStack(GoldCoinPayment.coinItemId(),coins[i]),false);
        return new CombinedItemContainer(bag);
    }
    private static int coins(CombinedItemContainer inventory) { return InventoryMaterials.count(inventory,GoldCoinPayment.coinItemId()); }
    private void residents(TownRecord town) {
        for(int i=0;i<6;i++)town.getResidentNpcRecords().add(new ResidentNpcRecord("Core_"+i,i==0?"blacksmith":"core_"+i,null,UUID.randomUUID()));
    }
    private UUID memberWithoutTreasury(TownRecord town) {
        var id=UUID.randomUUID();town.putMember(id,TownMemberRole.BOTH);
        town.putMemberPermissions(id,new Gson().fromJson("{\"manageConstructions\":true,\"spendTreasuryGold\":false}",TownMemberPermissions.class));
        assertTrue(town.hasMemberOrOwner(id));assertFalse(town.playerCanSpendTreasuryGold(id));return id;
    }

    @Test void mixedPaymentUsesTreasuryFirstAndAllInventoryStacks() {
        var town=town(30);var inv=inventory(10,15,50);
        assertEquals(105,GoldCoinPayment.totalAvailable(town,inv));
        assertEquals(new GoldCoinPayment.SpendBreakdown(30,40),GoldCoinPayment.trySpendReturningBreakdown(town,inv,70,true));
        assertEquals(0,town.getTreasuryGoldCoinCount());assertEquals(35,coins(inv));
    }
    @Test void insufficientInventoryOnlyPaymentDoesNotConsumePartialCoins() {
        var inv=inventory(10,15);var town=town(500);
        assertFalse(GoldCoinPayment.trySpend(town,inv,30,false));
        assertFalse(GoldCoinPayment.trySpend(null,inv,30));
        assertEquals(25,coins(inv));assertEquals(500,town.getTreasuryGoldCoinCount());
    }
    @Test void insufficientCombinedPaymentLeavesBothBalancesUnchanged() {
        var inv=inventory(15);var town=town(10);
        assertFalse(GoldCoinPayment.trySpend(town,inv,30));
        assertEquals(15,coins(inv));assertEquals(10,town.getTreasuryGoldCoinCount());
    }
    @Test void treasuryOnlyPaymentLeavesInventoryAlone() {
        var inv=inventory(15);var town=town(100);
        assertTrue(GoldCoinPayment.trySpend(town,inv,60));
        assertEquals(15,coins(inv));assertEquals(40,town.getTreasuryGoldCoinCount());
    }
    @Test void unavailableMaterialDoesNotConsumeGoldOrOtherMaterials() {
        var inv=inventory(50);var town=town(20);
        inv.setItemStackForSlot((short)1,new ItemStack("Ingredient_Bone_Fragment",5),false);
        assertNull(GoldCoinPayment.trySpendReturningBreakdown(town,inv,40,true,List.of(new ItemStack("Ingredient_Bone_Fragment",10))));
        assertEquals(50,coins(inv));assertEquals(20,town.getTreasuryGoldCoinCount());
        assertEquals(5,InventoryMaterials.count(inv,"Ingredient_Bone_Fragment"));
    }
    @Test void workplaceUnlockCombinesFundsAndDuplicateDoesNotCharge() {
        var town=town(40);residents(town);var inv=inventory(70);var sites=town.getApprenticeWorkplaces();
        assertNull(sites.buyUnlock(town,owner,TownsfolkWorkType.SMELTING,inv));
        assertEquals(0,town.getTreasuryGoldCoinCount());assertEquals(10,coins(inv));
        assertNotNull(sites.buyUnlock(town,owner,TownsfolkWorkType.SMELTING,inv));assertEquals(10,coins(inv));
    }
    @Test void workplaceSlotsCanUseInventoryWithoutTreasuryPermission() {
        var town=town(500);residents(town);var member=memberWithoutTreasury(town);var inv=inventory(100);
        assertNull(town.getApprenticeWorkplaces().buySlot(town,member,"blacksmith",0,inv));
        assertEquals(500,town.getTreasuryGoldCoinCount());assertEquals(0,coins(inv));
        assertNotNull(town.getApprenticeWorkplaces().buySlot(town,member,"blacksmith",0,inv));
    }
    @Test void billCombinesFundsAndDuplicateOrUnknownBillDoesNotCharge() {
        var town=town(5);var inv=inventory(50);town.getPayroll().ensureCurrentBill("worker");
        var bill=town.getPayroll().bills().getFirst();
        assertTrue(TownPayrollService.pay(town,owner,bill.id(),inv));assertTrue(bill.paid());
        assertEquals(35,coins(inv));assertEquals(0,town.getTreasuryGoldCoinCount());
        assertFalse(TownPayrollService.pay(town,owner,bill.id(),inv));
        assertFalse(TownPayrollService.pay(town,owner,"missing",inv));assertEquals(35,coins(inv));
    }
    @Test void memberCanPayBillWithOwnCoinsButCannotUseRestrictedTreasury() {
        var town=town(500);var member=memberWithoutTreasury(town);var inv=inventory(15);town.getPayroll().ensureCurrentBill("worker");
        var bill=town.getPayroll().bills().getFirst();
        assertFalse(TownPayrollService.pay(town,member,bill.id(),inv));assertFalse(bill.paid());assertEquals(15,coins(inv));
        inv.setItemStackForSlot((short)1,new ItemStack(GoldCoinPayment.coinItemId(),10),false);
        assertTrue(TownPayrollService.pay(town,member,bill.id(),inv));assertEquals(5,coins(inv));assertEquals(500,town.getTreasuryGoldCoinCount());
    }
    @Test void outreachUnlockAndInviteCombineFunds() {
        var town=new Gson().fromJson("{\"innActive\":true}",TownRecord.class);town.setOwner(owner,"Owner");residents(town);
        town.setTreasuryGoldCoinCount(30);var inv=inventory(100,50);
        assertNull(town.getOutreach().unlock(town,owner,inv));assertEquals(80,coins(inv));assertEquals(0,town.getTreasuryGoldCoinCount());
        assertNull(town.getOutreach().invite(town,owner,"guest",inv));assertEquals(30,coins(inv));
        assertNotNull(town.getOutreach().invite(town,owner,"other",inv));assertEquals(30,coins(inv));
    }
    @Test void hunterUnlockRemovesGoldAndDropsTogetherThenRunUsesRemainingCoins() {
        var town=town(15);var inv=inventory(50);var family=HuntFamily.get("bone");
        inv.setItemStackForSlot((short)1,new ItemStack(family.icon(),family.unlockItems()),false);
        assertNull(HuntPurchases.unlock(town,owner,inv,family.id()));
        assertEquals(0,town.getTreasuryGoldCoinCount());assertEquals(25,coins(inv));assertEquals(0,InventoryMaterials.count(inv,family.icon()));
        assertNotNull(HuntPurchases.unlock(town,owner,inv,family.id()));assertEquals(25,coins(inv));
        assertNull(HuntPurchases.begin(town,owner,inv));assertNotNull(town.getHunts().run());assertEquals(10,coins(inv));
        assertNotNull(HuntPurchases.begin(town,owner,inv));assertEquals(10,coins(inv));
    }
    @Test void hunterTrainingAllowsOwnCoinsWithoutTreasuryAccessAndRejectsOutsiders() {
        var town=town(500);var member=memberWithoutTreasury(town);var inv=inventory(100);
        assertNotNull(HuntPurchases.upgrade(town,UUID.randomUUID(),inv,"power"));assertEquals(100,coins(inv));
        assertNull(HuntPurchases.upgrade(town,member,inv,"power"));assertEquals(50,coins(inv));
        assertNotNull(HuntPurchases.upgrade(town,member,inv,"power"));assertEquals(50,coins(inv));assertEquals(1,town.getHunts().level("power"));
        assertEquals(500,town.getTreasuryGoldCoinCount());
    }
}
