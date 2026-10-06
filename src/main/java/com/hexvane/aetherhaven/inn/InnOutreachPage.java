package com.hexvane.aetherhaven.inn;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.ui.*;
import com.hexvane.aetherhaven.villager.AetherhavenRoleLabels;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Comparator;
import java.util.UUID;

/** Portrait selection and an Inn upgrade, using ordinary visitor eligibility and dawn arrival. */
public final class InnOutreachPage extends AetherhavenInteractiveCustomUIPage<InnOutreachPage.Data> {
    private final UUID townId;
    private boolean appended;
    private String selected, hint = "Choose a guest. Corin will ask them to visit at the next available morning.";
    private long frame;

    public InnOutreachPage(PlayerRef player, UUID townId) {
        super(player, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.townId = townId;
    }

    @Override public void build(Ref<EntityStore> ref, UICommandBuilder c, UIEventBuilder e, Store<EntityStore> store) {
        var plugin = AetherhavenPlugin.get(); if (plugin == null) return;
        var town = AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(), plugin).getTown(townId);
        if (town == null || !town.hasMemberOrOwner(playerRef.getUuid())) { close(); return; }
        if (!appended) { c.append("Aetherhaven/InnOutreachPage.ui"); appended = true; }
        var candidates = plugin.getVillagerDefinitionCatalog().allByNpcRoleId().values().stream()
            .filter(VillagerDefinition::isInnPoolEligible)
            .filter(d -> !town.getInnVisitorPoolExcludedRoleIds().contains(d.getNpcRoleId()))
            .filter(d -> town.getResidentNpcRecords().stream().noneMatch(r -> d.getNpcRoleId().equals(r.getNpcRoleId())
                && !com.hexvane.aetherhaven.villager.TownVillagerBinding.isVisitorKind(r.getKind())))
            .sorted(Comparator.comparingInt((VillagerDefinition d) -> TownRecruitment.minimumLevel(plugin, d.getNpcRoleId()))
                .thenComparing(d -> AetherhavenRoleLabels.displayNameForRoleId(d.getNpcRoleId())))
            .toList();
        if (candidates.stream().noneMatch(d -> d.getNpcRoleId().equals(selected)))
            selected = candidates.isEmpty() ? null : candidates.getFirst().getNpcRoleId();
        frame++;
        var outreach = town.getOutreach();
        var bounds=new Anchor();
        bounds.setWidth(Value.of(1020));
        bounds.setHeight(Value.of(outreach.unlocked() ? 650 : 730));
        c.setObject("#GuestBook.Anchor",bounds);
        c.set("#Summary.Text", town.getDisplayName() + " | Town Level " + TownPopulation.levelFor(TownPopulation.count(town))
            + " | Treasury: " + town.getTreasuryGoldCoinCount() + " gold");
        c.set("#Hint.Text", hint);
        c.set("#Upgrade.Visible", !outreach.unlocked());
        c.set("#Unlock.Disabled", !town.playerCanManageConstructions(playerRef.getUuid()) || !town.playerCanSpendTreasuryGold(playerRef.getUuid())
            || TownPopulation.levelFor(TownPopulation.count(town)) < InnOutreach.UNLOCK_LEVEL || town.getTreasuryGoldCoinCount() < InnOutreach.UNLOCK_COST);
        e.addEventBinding(CustomUIEventBindingType.Activating, "#Unlock", event("Unlock", ""), false);
        c.set("#Pending.Text", outreach.requestedRole().isEmpty() ? "No invitation pending"
            : "Invited: " + AetherhavenRoleLabels.displayNameForRoleId(outreach.requestedRole()));
        c.set("#Cancel.Visible", !outreach.requestedRole().isEmpty());
        c.set("#Cancel.Disabled", !town.playerCanManageConstructions(playerRef.getUuid()));
        e.addEventBinding(CustomUIEventBindingType.Activating, "#Cancel", event("Cancel", ""), false);
        c.clear("#Guests"); c.set("#Empty.Visible", candidates.isEmpty()); c.set("#Details.Visible", selected != null);
        for (int i = 0; i < candidates.size(); i++) {
            var d = candidates.get(i); String role = d.getNpcRoleId();
            if (i % 2 == 0) c.append("#Guests", "Aetherhaven/InnGuestRow.ui");
            String host = "#Guests[" + i / 2 + "]";
            c.append(host, "Aetherhaven/InnGuestCard.ui"); String card = host + "[" + i % 2 + "]";
            String name = AetherhavenRoleLabels.displayNameForRoleId(role);
            String portrait = NpcPortraitProvider.portraitPathForRoleId(role);
            boolean visiting = InnPoolService.isRoleVisiting(store, town, role);
            boolean eligible = InnPoolService.isVisitorRoleEligible(plugin, town, store, role);
            int requiredLevel = TownRecruitment.minimumLevel(plugin, role);
            String status = visiting ? "Already visiting" : TownPopulation.levelFor(TownPopulation.count(town)) < requiredLevel
                ? "Needs Town Level " + requiredLevel : !TownRecruitment.available(plugin,town,role)
                ? "Build the farm and ranch first" : eligible ? "Available to invite" : "Not available yet";
            c.set(card + " #Portrait.AssetPath", portrait); c.set(card + " #Name.Text", name);
            c.set(card + " #Skill.Text", AetherhavenRoleLabels.kindDisplayTitle(d.getDialogueVillagerKind()) + " | Level " + requiredLevel);
            c.set(card + " #Status.Text", status); c.set(card + " #Selected.Visible", role.equals(selected));
            e.addEventBinding(CustomUIEventBindingType.Activating, card + " #Choose", event("Select", role), false);
            if (!role.equals(selected)) continue;
            c.set("#SelectedPortrait.AssetPath", portrait); c.set("#SelectedName.Text", name);
            c.set("#SelectedStatus.Text", status);
            var building = plugin.getConstructionCatalog().get(d.getWorkConstructionId());
            c.set("#Description.Text", building == null || building.getDescription() == null ? "A new neighbor for your growing town." : building.getDescription());
            c.set("#Invite.Text", role.equals(outreach.requestedRole()) ? "Invitation sent" : "Invite to the Inn");
            c.set("#Invite.Disabled", !outreach.unlocked() || !town.playerCanManageConstructions(playerRef.getUuid())
                || !eligible || visiting || role.equals(outreach.requestedRole()));
            e.addEventBinding(CustomUIEventBindingType.Activating, "#Invite", event("Invite", role), false);
        }
    }

    private EventData event(String action, String role) {
        return new EventData().append("Action", action).append("Role", role).append("Frame", Long.toString(frame));
    }

    @Override public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, Data data) {
        var plugin = AetherhavenPlugin.get(); if (plugin == null) return;
        var manager = AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(), plugin);
        var town = manager.getTown(townId);
        if (town == null || !town.hasMemberOrOwner(playerRef.getUuid())) { close(); return; }
        if (!Long.toString(frame).equals(data.frame)) hint = "The guest book changed. Please choose again.";
        else if ("Select".equals(data.action)) selected = data.role;
        else if (!town.playerCanManageConstructions(playerRef.getUuid())) hint = "You need permission to manage town buildings.";
        else if ("Unlock".equals(data.action)) {
            String error = town.getOutreach().unlock(town, playerRef.getUuid());
            hint = error == null ? "Outreach is ready. Choose a guest to invite." : error;
            manager.updateTown(town);
        } else if ("Cancel".equals(data.action) && town.getOutreach().unlocked()) {
            town.getOutreach().request(null); manager.updateTown(town); hint = "The pending invitation has been cancelled.";
        } else if ("Invite".equals(data.action)) {
            if (!town.getOutreach().unlocked()) hint = "Unlock outreach first.";
            else if (data.role == null || data.role.isBlank() || !town.isInnActive() || !InnPoolService.isVisitorRoleEligible(plugin, town, store, data.role)) hint = "That guest is not available yet.";
            else if (InnPoolService.isRoleVisiting(store, town, data.role)) hint = "That guest is already staying at the Inn.";
            else {
                town.getOutreach().request(data.role); manager.updateTown(town);
                hint = "Invitation sent. They will take the next free morning guest spot. Current guests on quests keep their rooms.";
            }
        }
        var c = new UICommandBuilder(); var e = new UIEventBuilder(); build(ref, c, e, store); sendUpdate(c, e, false);
    }

    public static final class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d,v)->d.action=v,d->d.action).add()
            .append(new KeyedCodec<>("Role", Codec.STRING), (d,v)->d.role=v,d->d.role).add()
            .append(new KeyedCodec<>("Frame", Codec.STRING), (d,v)->d.frame=v,d->d.frame).add().build();
        String action, role, frame;
    }
}
