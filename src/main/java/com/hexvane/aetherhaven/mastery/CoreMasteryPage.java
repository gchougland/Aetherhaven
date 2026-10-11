package com.hexvane.aetherhaven.mastery;

import com.hexvane.aetherhaven.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.ui.*;
import com.hexvane.aetherhaven.reputation.*;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.*;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;

public final class CoreMasteryPage extends AetherhavenInteractiveCustomUIPage<CoreMasteryPage.Data> {
    private final UUID townId;
    private final String kind;
    private final Ref<EntityStore> npcRef;
    private final boolean fromJournal;
    private UUID residentId;
    private boolean equipmentTab;
    private boolean appended;
    private long frame;
    private int shownLevel;
    private ItemStack shownTool;
    private final Map<String, Choice> choices = new HashMap<>();
    private record Choice(short slot, ItemStack item) {}
    private String hint = "Stored resources remain available while you train or change tools.";
    public CoreMasteryPage(PlayerRef player, UUID town, String kind) {
        this(player, town, kind, null);
    }
    public CoreMasteryPage(PlayerRef player, UUID town, String kind, Ref<EntityStore> npcRef) {
        this(player, town, kind, npcRef, null, false);
    }
    public CoreMasteryPage(PlayerRef player, UUID town, String kind, Ref<EntityStore> npcRef, UUID residentId, boolean fromJournal) {
        super(player, CustomPageLifetime.CanDismissOrCloseThroughInteraction, Data.CODEC);
        this.townId = town; this.kind = kind; this.npcRef = npcRef;
        this.residentId = residentId; this.fromJournal = fromJournal;
        if (!CoreVillagerMasteryService.supports(kind)) hint = "Grow your friendship to discover rewards and gift preferences.";
        if ("alchemist".equals(kind)) hint = "Higher Mastery unlocks more ingredient slots and larger flasks at the laboratory.";
    }
    private boolean allowed(TownRecord town) {
        return town != null && kind != null
            && town.hasMemberOrOwner(playerRef.getUuid())
            && town.getResidentNpcRecords().stream().anyMatch(resident -> kind.equals(resident.getKind())
                && resident.getTownsfolkIdentity() == null
                && !TownVillagerBinding.KIND_TOWNSFOLK.equals(resident.getKind())
                && !TownVillagerBinding.KIND_GUARD.equals(resident.getKind())
                && (residentId == null || residentId.equals(resident.getLastEntityUuid())));
    }
    /**
     * Inn and guild visitors are not in the town's resident records, so the profile works from the visitor standing in
     * front of the player: friendship, rewards and the gifts already discovered.
     */
    private boolean liveVisitor(TownRecord town, Store<EntityStore> store) {
        if (town == null || kind == null || !TownVillagerBinding.isVisitorKind(kind) || npcRef == null || !npcRef.isValid()
            || !town.hasMemberOrOwner(playerRef.getUuid())) return false;
        var binding = store.getComponent(npcRef, TownVillagerBinding.getComponentType());
        return binding != null && townId.equals(binding.getTownId()) && kind.equals(binding.getKind());
    }
    @Override public void build(Ref<EntityStore> ref, UICommandBuilder c, UIEventBuilder e, Store<EntityStore> store) {
        var plugin = AetherhavenPlugin.get(); if (plugin == null) return;
        var town = AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(), plugin).getTown(townId);
        if (!allowed(town) && !liveVisitor(town, store)) { close(); return; }
        if (!appended) {
            c.append("Aetherhaven/CoreMasteryPage.ui");
            c.append("#NeedsHost", "Aetherhaven/ResidentNeeds.ui");
            for (int i = 0; i < 10; i++) c.append("#Hearts", "Aetherhaven/HeartSlot.ui");
            appended = true;
        }
        boolean mastery = CoreVillagerMasteryService.supports(kind);
        var progress = mastery ? CoreVillagerMasteryService.progress(town, kind) : null;
        shownLevel = mastery ? progress.level() : 0; shownTool = mastery ? progress.tool() : null; frame++; choices.clear();
        c.set("#MasteryPanel.Visible", mastery);
        if (mastery) {
            boolean complete = shownLevel == CoreVillagerMastery.MAX_LEVEL;
            c.set("#MasteryXp.Text", complete ? "Mastery complete" : progress.xpInLevel() + " / " + progress.xpToNextLevel() + " XP");
            c.set("#MasteryXpBar.Value", complete ? 1.0f : (float) progress.xpInLevel() / progress.xpToNextLevel());
        }
        c.set("#ReturnToJournal.Visible", fromJournal);
        c.set("#ReturnToJournal.Text", "Back to Journal");
        if (fromJournal) e.addEventBinding(CustomUIEventBindingType.Activating, "#ReturnToJournal", event("ReturnToJournal"), false);
        boolean canTrain = town.playerCanManageConstructions(playerRef.getUuid());
        c.set("#Profession.Text", kind.replace('_', ' ').toUpperCase(Locale.ROOT));
        c.set("#Town.Text", town.getDisplayName());
        c.set("#Level.TextSpans", text("level").param("level", shownLevel).param("max", CoreVillagerMastery.MAX_LEVEL));
        for (int i = 0; i < CoreVillagerMastery.MAX_LEVEL; i++)
            c.setObject("#MasterySegments #Pip" + i + ".Background", new com.hypixel.hytale.server.core.ui.PatchStyle()
                .setColor(com.hypixel.hytale.server.core.ui.Value.of(i < shownLevel ? "#d4b36a" : "#30434a")));
        var trainingInventory = InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING);
        boolean hasCatalyst = trainingInventory != null && com.hexvane.aetherhaven.inventory.InventoryMaterials.count(
            trainingInventory, AetherhavenConstants.ITEM_VERDANT_CATALYST) > 0;
        c.set("#Train.Disabled", !canTrain || shownLevel >= CoreVillagerMastery.MAX_LEVEL || !hasCatalyst);
        c.set("#Train.TooltipText", shownLevel >= CoreVillagerMastery.MAX_LEVEL ? "Fully trained"
            : !hasCatalyst ? "Carry one Verdant Catalyst to train" : "Add 1 Mastery level using one Verdant Catalyst");
        c.set("#Train.TextSpans", text(shownLevel >= CoreVillagerMastery.MAX_LEVEL ? "complete" : "train"));
        c.set("#Hint.Text", canTrain || !mastery ? hint : "You can view this profile. Building permission is required to train or change equipment.");
        boolean hasTool = CoreVillagerMasteryService.toolFamily(kind) != null;
        if (!hasTool) equipmentTab = false;
        c.set("#EquipmentTab.Visible", hasTool);
        c.set("#EquipmentTab.Disabled", equipmentTab);
        c.set("#FriendshipTab.Disabled", !equipmentTab);
        c.set("#FriendshipPanel.Visible", !equipmentTab);
        c.set("#ToolPanel.Visible", equipmentTab);
        renderFriendship(c, town, store, plugin);
        c.clear("#Tools");
        if (hasTool) {
            var id = CoreVillagerMasteryService.toolId(town, kind);
            c.set("#ToolIcon.ItemId", id);
            c.set("#ToolName.Text", label(id) + (ItemStack.isEmpty(shownTool) ? "\nStarting tool" : "\nEquipped"));
            var inventory = InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_STORAGE_BACKPACK);
            if (inventory != null) for (short slot = 0; slot < inventory.getCapacity(); slot++) {
                var item = inventory.getItemStack(slot);
                if (ItemStack.isEmpty(item) || item.getQuantity() != 1 || item.isBroken()
                    || CoreVillagerMasteryService.toolTier(kind, item.getItemId()) < 0) continue;
                String key = Integer.toString(choices.size()); choices.put(key, new Choice(slot, item));
                c.append("#Tools", "Aetherhaven/CoreMasteryToolRow.ui");
                String row = "#Tools[" + key + "]";
                c.set(row + " #Icon.ItemId", item.getItemId()); c.set(row + " #Name.Text", label(item.getItemId()));
                c.set(row + " #Equip.Disabled", !canTrain);
                e.addEventBinding(CustomUIEventBindingType.Activating, row + " #Equip", event("Equip").append("Choice", key), false);
            }
            c.set("#Empty.Visible", choices.isEmpty());
        }
        for (String action : List.of("Train", "Refresh", "FriendshipTab", "EquipmentTab"))
            e.addEventBinding(CustomUIEventBindingType.Activating, "#" + action, event(action), false);
    }
    private void renderFriendship(UICommandBuilder c, TownRecord town, Store<EntityStore> store, AetherhavenPlugin plugin) {
        boolean live = false;
        if (npcRef != null && npcRef.isValid()) {
            var binding = store.getComponent(npcRef, TownVillagerBinding.getComponentType());
            var uuid = store.getComponent(npcRef, UUIDComponent.getComponentType());
            if (binding != null && uuid != null && townId.equals(binding.getTownId()) && kind.equals(binding.getKind())) {
                residentId = uuid.getUuid(); live = true;
            }
        }
        var resident = town.getResidentNpcRecords().stream().filter(r -> kind.equals(r.getKind()))
            .filter(r -> residentId == null || residentId.equals(r.getLastEntityUuid())).findFirst().orElse(null);
        if (resident == null && !(live && liveVisitor(town, store))) { close(); return; }
        if (resident != null) residentId = resident.getLastEntityUuid();
        var visiting = resident == null ? store.getComponent(npcRef, com.hypixel.hytale.server.npc.entities.NPCEntity.getComponentType()) : null;
        String role = resident != null ? resident.getNpcRoleId() : visiting == null ? "" : visiting.getRoleName();
        ResidentNeedsUi.render(c, store, town, residentId, TownResidentEligibility.usesVillagerNeeds(kind, role, plugin));
        var savedIdentity=resident==null?null:resident.getTownsfolkIdentity();
        var identity = live ? TownResidentDisplay.resolveFromEntity(store, npcRef, role, plugin)
            : TownResidentDisplay.resolveOffline(plugin, role, savedIdentity==null?null:savedIdentity.getCharacterId(),savedIdentity==null?null:savedIdentity.getModelAssetId());
        c.set("#Name.Text", identity.displayName()); c.set("#Portrait.AssetPath", identity.portraitPath());
        var personal = town.getPlayerVillagerReputation().get(playerRef.getUuid().toString());
        var entry = personal == null || residentId == null ? null : personal.get(residentId.toString());
        int reputation = entry == null ? 0 : entry.getReputation();
        c.set("#Reputation.TextSpans", text("reputation").param("value", reputation));
        ReputationHeartUi.applyHearts(c, "#Hearts", reputation);
        var rewards = ReputationRewardCatalog.forRoleSorted(role);
        c.clear("#Rewards"); c.set("#NoRewards.Visible", rewards.isEmpty());
        for (int i = 0; i < rewards.size(); i++) {
            var reward = CoreVillagerProfileView.reward(rewards.get(i), entry);
            c.append("#Rewards", reward.unlocked() ? "Aetherhaven/CoreProfileReward.ui" : "Aetherhaven/CoreProfileRewardLocked.ui");
            String row = "#Rewards[" + i + "]";
            c.set(row + " #Threshold.TextSpans", text("reward.threshold").param("value", reward.reputation()));
            if (!reward.unlocked()) continue;
            // No item identity is ever sent for a locked milestone, including hidden icons.
            var item = reward.item().isBlank() ? null : Item.getAssetMap().getAsset(reward.item());
            c.set(row + " #Icon.Visible", item != null);
            if (item != null) c.set(row + " #Icon.ItemId", reward.item());
            Message label = reward.item().isBlank() ? text("reward.special") : itemName(reward.item());
            c.set(row + " #Reward.TextSpans", reward.recipe() ? text("reward.recipe").param("item", label)
                : reward.count() > 0 ? text("reward.items").param("count", reward.count()).param("item", label) : label);
            c.set(row + " #Status.TextSpans", text(reward.claimed() ? "reward.claimed" : "reward.ready"));
        }
        var gifts = CoreVillagerProfileView.discoveredGifts(town.getVillagerGiftLogByRoleId().getOrDefault(role, List.of()), playerRef.getUuid());
        c.clear("#Gifts"); int displayed = 0;
        for (var gift : gifts) {
            if (Item.getAssetMap().getAsset(gift.item()) == null) continue;
            c.append("#Gifts", "Aetherhaven/CoreProfileGift.ui");
            String row = "#Gifts[" + displayed++ + "]";
            c.set(row + " #Icon.ItemId", gift.item()); c.set(row + " #Name.TextSpans", itemName(gift.item()));
            c.set(row + " #Preference.TextSpans", text("gift." + gift.preference().toWireId()));
            c.set(row + " #Preference.Style.TextColor", switch (gift.preference()) {
                case LOVE -> "#deb08e"; case LIKE -> "#99c7b5"; case NEUTRAL -> "#a5b6c1"; case DISLIKE -> "#d28f8f";
            });
        }
        c.set("#NoGifts.Visible", displayed == 0); c.set("#Gifts.Visible", displayed > 0);
    }
    private static Message text(String key) { return Message.translation("aetherhaven_mastery." + key); }
    private static Message itemName(String id) {
        var item = Item.getAssetMap().getAsset(id);
        return item == null ? Message.raw(id.replace('_', ' ')) : Message.translation(item.getTranslationKey());
    }
    private EventData event(String action) { return EventData.of("Action", action).append("Frame", Long.toString(frame)); }
    private static String label(String id) { return id.replace("Tool_", "").replace('_', ' '); }
    @Override public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, Data data) {
        var plugin = AetherhavenPlugin.get(); if (plugin == null) return;
        var manager = AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(), plugin);
        var town = manager.getTown(townId); if (!allowed(town) && !liveVisitor(town, store)) { close(); return; }
        if ("ReturnToJournal".equals(data.action) && fromJournal) {
            QuestJournalPage.openTown(ref, store, playerRef, townId); return;
        }
        var progress = CoreVillagerMasteryService.supports(kind) ? CoreVillagerMasteryService.progress(town, kind) : null;
        if (!Long.toString(frame).equals(data.frame) || progress != null && (shownLevel != progress.level() || !Objects.equals(shownTool, progress.tool()))) {
            hint = "Mastery or equipment changed. Choose again.";
        } else if (("Train".equals(data.action) || "Equip".equals(data.action)) && !town.playerCanManageConstructions(playerRef.getUuid())) {
            hint = "You need permission to manage buildings to train or change equipment.";
        } else if ("FriendshipTab".equals(data.action)) {
            equipmentTab = false;
        } else if ("EquipmentTab".equals(data.action) && CoreVillagerMasteryService.toolFamily(kind) != null) {
            equipmentTab = true;
        } else if ("Train".equals(data.action) && progress != null && progress.level() < CoreVillagerMastery.MAX_LEVEL) {
            var inventory = InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_STORAGE_BACKPACK);
            if (inventory == null || !inventory.removeItemStack(new ItemStack(AetherhavenConstants.ITEM_VERDANT_CATALYST, 1)).succeeded()) {
                hint = "Bring one Verdant Catalyst to raise Mastery.";
            } else {
                progress.train(); manager.updateTown(town); hint = "Mastery increased by one.";
            }
        } else if ("Equip".equals(data.action) && progress != null) {
            var choice = choices.get(data.choice);
            String issue = choice == null ? "Choose a tool again." : CoreVillagerMasteryService.equip(progress, kind,
                InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_STORAGE_BACKPACK), choice.slot(), choice.item(), shownTool);
            hint = issue == null ? "Tool equipped." : issue;
            if (issue == null) manager.updateTown(town);
        }
        var c = new UICommandBuilder(); var e = new UIEventBuilder(); build(ref, c, e, store); sendUpdate(c, e, false);
    }
    public static final class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action).add()
            .append(new KeyedCodec<>("Choice", Codec.STRING), (d, v) -> d.choice = v, d -> d.choice).add()
            .append(new KeyedCodec<>("Frame", Codec.STRING), (d, v) -> d.frame = v, d -> d.frame).add().build();
        String action, choice, frame;
    }
}
