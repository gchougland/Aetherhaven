package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.townsfolk.*;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class TownsfolkProfilePage extends AetherhavenInteractiveCustomUIPage<TownsfolkProfilePage.Data> {
    private final UUID townId;
    private final String characterId;
    private final UUID residentId;
    private boolean appended;
    private final boolean fromJournal;

    public TownsfolkProfilePage(PlayerRef player, UUID townId, String characterId, UUID residentId) {
        this(player, townId, characterId, residentId, false);
    }
    public TownsfolkProfilePage(PlayerRef player, UUID townId, String characterId, UUID residentId, boolean fromJournal) {
        super(player, CustomPageLifetime.CanDismissOrCloseThroughInteraction, Data.CODEC);
        this.fromJournal = fromJournal;
        this.townId = townId;
        this.characterId = characterId;
        this.residentId = residentId;
    }

    @Override public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands,
            @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        boolean initial = !appended;
        if (initial) { commands.append("Aetherhaven/TownsfolkProfilePage.ui"); appended = true; }
        commands.set("#ReturnToJournal.Visible", fromJournal);
        if (fromJournal) events.addEventBinding(CustomUIEventBindingType.Activating, "#ReturnToJournal", new EventData().append("Kind", "journal"), false);
        var plugin = AetherhavenPlugin.get();
        if (plugin == null) return;
        var manager = AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(), plugin);
        var town = manager.getTown(townId);
        if (town == null || !town.hasMemberOrOwner(playerRef.getUuid())) { close(); return; }
        events.addEventBinding(CustomUIEventBindingType.Activating, "#Refresh", new EventData().append("Kind", "refresh"), false);
        var definition = plugin.getTownsfolkCharacterCatalog().byId(characterId);
        var progress = TownsfolkProgressService.getOrCreate(town, manager, plugin, characterId);
        var player = store.getComponent(ref, PlayerRef.getComponentType());
        boolean canTrain = player != null && TownsfolkProgressService.canTrain(town, player.getUuid(), residentId);
        commands.set("#Name.Text", definition != null && definition.getDisplayName() != null ? definition.getDisplayName() : characterId);
        commands.set("#Summary.Text", "Mastery " + progress.masteryLevel() + " / " + TownsfolkProgress.MAX_MASTERY_LEVEL);
        for (int i = 0; i < TownsfolkProgress.MAX_MASTERY_LEVEL; i++)
            commands.setObject("#MasteryPip" + i + ".Background", new com.hypixel.hytale.server.core.ui.PatchStyle()
                .setColor(com.hypixel.hytale.server.core.ui.Value.of(i < progress.masteryLevel() ? "#d4b36a" : "#30434a")));
        commands.set("#MasteryXp.Text", progress.masteryLevel() == TownsfolkProgress.MAX_MASTERY_LEVEL ? "Mastery complete"
            : progress.masteryXpInLevel() + " / " + progress.masteryXpToNextLevel() + " XP");
        commands.set("#MasteryXp.TooltipText", "Earn mastery by completing profession work. Each level gives one skill point. Every fifth level gives two attribute points.");
        commands.set("#MasteryXpBar.Value",progress.masteryLevel()==TownsfolkProgress.MAX_MASTERY_LEVEL?1.0f:(float)progress.masteryXpInLevel()/progress.masteryXpToNextLevel());
        commands.set("#SkillPoints.Text", "Skill points: " + progress.masterySkillPoints());
        commands.set("#AttributePoints.Text", "Attribute points: " + progress.masteryAttributePoints());
        commands.set("#Hint.Text", "One Gaia Shard adds 1 attribute. One Verdant Catalyst adds 1 skill. Mastery points add 1 without using an item.");
        commands.set("#Portrait.AssetPath", NpcPortraitProvider.portraitPathForTownsfolk(definition));
        commands.set("#Identity.Text", definition != null && definition.getDisplayName() != null ? definition.getDisplayName() : characterId);
        commands.set("#TownName.Text", town.getDisplayName());
        var equipped = progress.professionKit();
        var kit = ItemStack.isEmpty(equipped) ? null : plugin.getProfessionKits().find(equipped.getItemId());
        commands.set("#KitIcon.Visible", !ItemStack.isEmpty(equipped));
        if (!ItemStack.isEmpty(equipped)) commands.set("#KitIcon.ItemId", equipped.getItemId());
        commands.set("#KitName.Text", ItemStack.isEmpty(equipped) ? "Profession kit\nChoose equipment" : kit == null ? "Unavailable kit" : kit.name());
        commands.set("#Equipment.TooltipText", kit == null ? "Choose a profession kit from your inventory or town storage." : kit.description());
        commands.set("#Equipment.Disabled", !canTrain);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#Equipment", new EventData().append("Kind", "equipment"), false);
        var guardStack=progress.guardKit();
        var guardKit=ItemStack.isEmpty(guardStack)?null:plugin.getGuardKits().find(guardStack.getItemId());
        commands.set("#GuardKitIcon.Visible",!ItemStack.isEmpty(guardStack));
        if(!ItemStack.isEmpty(guardStack))commands.set("#GuardKitIcon.ItemId",guardStack.getItemId());
        commands.set("#GuardKitName.Text",guardKit==null?"Guard kit\nStandard equipment":guardKit.name());
        commands.set("#GuardEquipment.TooltipText",guardKit==null?"Choose guard equipment from your inventory or town storage.":guardKit.description());
        commands.set("#GuardEquipment.Disabled",!canTrain);
        events.addEventBinding(CustomUIEventBindingType.Activating,"#GuardEquipment",new EventData().append("Kind","guardEquipment"),false);
        commands.set("#GuardDuty.Disabled", !canTrain);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#GuardDuty", new EventData().append("Kind", "guardDuty"), false);
        int row = 0;
        var guard=town.getHiredGuardRecords().stream().filter(g->characterId.equals(g.getCharacterId())).findFirst().orElse(null);
        var guardEquipment=guard==null?null:plugin.getEquipmentProfileCatalog().byId(guard.getEquipmentProfileId());
        String guardClass=guard==null?null:com.hexvane.aetherhaven.equipment.GuardKitService.classFor(guard.getEquipmentProfileId(),plugin.getGuardKits());
        commands.set("#DutyLabel.Text",guardClass==null?"TOWNSFOLK":guardClass.substring("guard_".length()).toUpperCase(java.util.Locale.ROOT)+" GUARD");
        for (var attribute : TownsfolkAttribute.values()) {
            if (initial) commands.append("#Attributes", "Aetherhaven/TownsfolkAttributeCard.ui");
            updateStat(commands, events, "#Attributes[" + row++ + "]", attribute.id, attribute.icon,
                progress.attribute(attribute), TownsfolkProgress.MAX_ATTRIBUTE, "attribute", progress.attributeCost(attribute), canTrain, progress.masteryAttributePoints());
            if(guardEquipment!=null&&guardEquipment.usesAttributeCombat()) {
                int learned=progress.attribute(attribute),bonus=guardEquipment.attributeBonus(attribute.id);
                String card="#Attributes["+(row-1)+"]";
                commands.set(card+" #Level.Text",Integer.toString(learned+bonus));
                commands.set(card+" #Tier.TextSpans",Message.raw(learned+" learned +"+bonus+" gear"));
                commands.set(card+" #Level.TooltipText",learned+" learned + "+bonus+" from guard equipment = "+(learned+bonus)+" effective");
            }
        }
        row = 0;
        for (var skill : TownsfolkSkill.values()) {
            String host = "#SkillRow" + row / 3;
            if (initial) commands.append(host, "Aetherhaven/TownsfolkSkillCard.ui");
            updateStat(commands, events, host + "[" + row % 3 + "]", skill.id, skill.icon, progress.skill(skill),
                TownsfolkProgress.MAX_SKILL, "skill", progress.skillCost(skill), canTrain, progress.masterySkillPoints());
            row++;
        }
    }

    private void updateStat(UICommandBuilder commands, UIEventBuilder events, String row, String id, String icon,
                           int level, int maximum, String kind, int cost, boolean canTrain, int masteryPoints) {
        commands.set(row + " #Icon.AssetPath", "UI/Custom/" + icon);
        commands.set(row + " #Name.Text", Message.translation("aetherhaven_townsfolk_profile." + id));
        commands.set(row + " #Level.Text", Integer.toString(level));
        commands.set(row + " #Level.TooltipText", "Level " + level + " of " + maximum);
        if (kind.equals("skill")) for (int i = 0; i < maximum; i++) {
            commands.setObject(row + " #Pip" + i + ".Background", new com.hypixel.hytale.server.core.ui.PatchStyle()
                .setColor(com.hypixel.hytale.server.core.ui.Value.of(i < level ? "#7caf96" : "#30434a")));
        }
        String tier = level == 0 ? "incapable" : kind.equals("attribute") ? "attribute" : level >= 4 ? "great" : level >= 2 ? "good" : "unfamiliar";
        commands.set(row + " #Tier.TextSpans", kind.equals("attribute") ? Message.raw("/ " + maximum)
            : Message.translation("aetherhaven_townsfolk_profile." + tier));
        commands.set(row + " #Train.Text", cost == 0 ? (level == 0 ? "Locked" : "Max") : kind.equals("attribute") ? "Shard +1" : "Catalyst +1");
        commands.set(row + " #Train.TooltipText", cost == 0 ? (level == 0 ? "Cannot perform this profession" : "Fully trained")
            : "Add 1 using one " + (kind.equals("attribute") ? "Gaia Shard" : "Verdant Catalyst"));
        commands.set(row + " #Train.Disabled", !canTrain || cost == 0);
        events.addEventBinding(CustomUIEventBindingType.Activating, row + " #Train",
            new EventData().append("Kind", kind).append("Stat", id).append("Level", Integer.toString(level)), false);
        commands.set(row + " #SpendPoint.Text", "Point +1");
        commands.set(row + " #SpendPoint.TooltipText", cost == 0 ? (level == 0 ? "Cannot perform this profession" : "Fully trained")
            : masteryPoints == 0 ? "Earn mastery levels to gain points" : "Use one mastery point to add 1 without using an item");
        commands.set(row + " #SpendPoint.Disabled", !canTrain || cost == 0 || masteryPoints == 0);
        events.addEventBinding(CustomUIEventBindingType.Activating, row + " #SpendPoint",
            new EventData().append("Kind", kind + "Point").append("Stat", id).append("Level", Integer.toString(level)), false);
    }

    @Override public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Data data) {
        var plugin = AetherhavenPlugin.get();
        var player = store.getComponent(ref, PlayerRef.getComponentType());
        if (plugin == null || player == null) return;
        var manager = AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(), plugin);
        var town = manager.getTown(townId);
        if ("journal".equals(data.kind) && fromJournal) { QuestJournalPage.openTown(ref, store, playerRef, townId); return; }
        if (town == null || !town.hasMemberOrOwner(player.getUuid())) { close(); return; }
        if ("refresh".equals(data.kind)) { refresh(ref, store); return; }
        if (!TownsfolkProgressService.canTrain(town, player.getUuid(), residentId)) return;
        var progress = TownsfolkProgressService.getOrCreate(town, manager, plugin, characterId);
        if ("guardEquipment".equals(data.kind)) {
            store.getComponent(ref,com.hypixel.hytale.server.core.entity.entities.Player.getComponentType()).getPageManager()
                .openCustomPage(ref,store,new GuardKitPage(playerRef,townId,characterId,residentId));
            return;
        }
        if ("guardDuty".equals(data.kind)) {
            store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType()).getPageManager()
                .openCustomPage(ref,store,new GuardDutyPage(playerRef,townId,residentId,characterId));
            return;
        }
        if ("equipment".equals(data.kind)) {
            store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType()).getPageManager()
                .openCustomPage(ref,store,new ProfessionKitPage(playerRef,townId,characterId,residentId));
            return;
        }
        TownsfolkAttribute attribute = null;
        TownsfolkSkill skill = null;
        if ("attribute".equals(data.kind) || "attributePoint".equals(data.kind)) for (var value : TownsfolkAttribute.values()) if (value.id.equals(data.stat)) attribute = value;
        if ("skill".equals(data.kind) || "skillPoint".equals(data.kind)) for (var value : TownsfolkSkill.values()) if (value.id.equals(data.stat)) skill = value;
        if (attribute == null && skill == null) return;
        // A repeated or stale click cannot spend a second item or point against the old displayed level.
        int shownLevel;
        try { shownLevel = Integer.parseInt(data.level); } catch (RuntimeException invalid) { return; }
        boolean usePoint = data.kind.endsWith("Point");
        var inventory = usePoint ? null : InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING);
        var result = TownsfolkTraining.train(town, player.getUuid(), residentId, progress, attribute, skill, shownLevel, usePoint, inventory);
        if (result == TownsfolkTraining.Result.MISSING_MATERIALS) {
            player.sendMessage(Message.translation("aetherhaven_townsfolk_profile.missingMaterials"));
            var feedback = new UICommandBuilder();
            feedback.set("#Hint.Text", Message.translation("aetherhaven_townsfolk_profile.missingMaterials"));
            sendUpdate(feedback, new UIEventBuilder(), false);
            return;
        }
        if (result == TownsfolkTraining.Result.TRAINED) manager.updateTown(town);
        refresh(ref, store);
    }

    private void refresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        var commands = new UICommandBuilder();
        var events = new UIEventBuilder();
        build(ref, commands, events, store);
        sendUpdate(commands, events, false);
    }

    public static final class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
            .append(new KeyedCodec<>("Kind", Codec.STRING), (d, v) -> d.kind = v, d -> d.kind).add()
            .append(new KeyedCodec<>("Stat", Codec.STRING), (d, v) -> d.stat = v, d -> d.stat).add()
            .append(new KeyedCodec<>("Level", Codec.STRING), (d, v) -> d.level = v, d -> d.level).add().build();
        String kind;
        String stat;
        String level;
    }
}
