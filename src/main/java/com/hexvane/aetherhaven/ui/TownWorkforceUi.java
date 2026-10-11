package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.economy.TownPayroll;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.*;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;

/** Journal work grid. Actual task selection uses the same saved priority and employment state. */
final class TownWorkforceUi {
    private TownWorkforceUi() {}

    /**
     * What one open journal has already put on screen. Rebuilding the grid from nothing on every click threw away its
     * scroll position, so the page and its rows are appended once and only their contents are set afterwards.
     */
    static final class Layout {
        private boolean appended;
        private final JournalRowLayout rows = new JournalRowLayout("#WorkRows", "Aetherhaven/TownWorkRow.ui");
    }

    /** The kinds of work that have a column, in order. Commerce only exists while player shops do. */
    static java.util.List<TownsfolkWorkType> columns() {
        var columns = new java.util.ArrayList<TownsfolkWorkType>();
        for (var type : TownsfolkWorkType.values()) {
            if (!type.availableForNewWork()) continue;
            if (type == TownsfolkWorkType.COMMERCE && !commerceLoaded()) continue;
            columns.add(type);
        }
        return columns;
    }

    private static boolean commerceLoaded() {
        try {
            return com.hexvane.aetherhaven.plugin.AetherhavenFeatures.isLoaded(com.hexvane.aetherhaven.plugin.AetherhavenPluginIds.COMMERCE);
        } catch (RuntimeException | LinkageError unavailable) {
            return true;
        }
    }

    static void build(UICommandBuilder c, UIEventBuilder e, TownRecord town, AetherhavenPlugin plugin,
                      Store<EntityStore> store, UUID player, Layout layout) {
        var columns = columns();
        if (!layout.appended) {
            c.clear("#WorkPage");
            c.append("#WorkPage", "Aetherhaven/TownWorkforce.ui");
            int column = 0;
            for (var type : columns) {
                c.append("#WorkColumns", "Aetherhaven/TownWorkHeader.ui");
                String h = "#WorkColumns[" + column++ + "]";
                c.set(h + " #Icon.AssetPath", "UI/Custom/" + type.icon);
                c.set(h + " #Label.TextSpans", t(type.id + ".short"));
                c.set(h + ".TooltipTextSpans", t(type.id));
            }
            layout.appended = true;
        }
        c.set("#WorkIntro.TextSpans", t("intro").param("wage", TownPayroll.WEEKLY_WAGE));
        c.set("#WorkFooter.TextSpans", t("footer"));
        boolean allowed = town != null && player != null && town.hasMemberOrOwner(player) && town.playerCanManageConstructions(player);
        if (town == null || plugin == null) {
            layout.rows.resize(c, 0);
            c.set("#WorkEmpty.Visible", true);
            c.set("#WorkEmpty.TextSpans", t("noTown"));
            return;
        }
        var manager = AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(), plugin);
        var roster = TownsfolkWorkRoster.list(town, manager, store, plugin);
        c.set("#WorkEmpty.Visible", roster.isEmpty());
        c.set("#WorkEmpty.TextSpans", t("empty"));
        int rowIndex = 0;
        // Rows that are already on screen are kept and only refilled. Only new rows get their cells appended.
        int kept = layout.rows.resize(c, roster.size());
        for (var worker : roster) {
            boolean fresh = rowIndex >= kept;
            String row = "#WorkRows[" + rowIndex++ + "]";
            var progress = worker.progress();
            boolean overdue = town.getPayroll().overdue(worker.characterId());
            boolean shopClerk = town.getPlotInstances().stream()
                .anyMatch(plot -> worker.characterId().equals(plot.getShopClerkId()));
            String state = !worker.alive() ? "dead" : worker.guard() ? "guard" : overdue ? "overdue"
                : !progress.isEmployed() ? "unemployed" : shopClerk ? "shopClerk" : "employed";
            c.set(row + " #Name.Text", worker.name());
            c.set(row + " #Portrait.AssetPath",
                NpcPortraitProvider.portraitPathForTownsfolk(plugin.getTownsfolkCharacterCatalog().byId(worker.characterId())));
            c.set(row + " #Status.TextSpans", t(state));
            c.set(row + " #Employment.TextSpans", t(progress.isEmployed() ? "dismiss" : "hire"));
            c.set(row + " #Employment.Disabled", !allowed || worker.guard() || !worker.alive());
            e.addEventBinding(CustomUIEventBindingType.Activating, row + " #Employment",
                new EventData().append("Action", "WorkEmployment").append("WorkCharacter", worker.characterId()), false);
            c.set(row + " #GuardDuty.TextSpans", t(worker.guard() ? "guardOn" : "guardOff"));
            c.set(row + " #GuardDuty.TooltipTextSpans", t(worker.guard() ? "guardEndHint" : "guardStartHint"));
            c.set(row + " #GuardDuty.Disabled", !allowed || !worker.alive());
            e.addEventBinding(CustomUIEventBindingType.Activating, row + " #GuardDuty",
                new EventData().append("Action", "WorkGuardDuty").append("WorkCharacter", worker.characterId()), false);
            int cell = 0;
            for (var type : columns) {
                if (fresh) c.append(row + " #Cells", "Aetherhaven/TownWorkCell.ui");
                String selector = row + " #Cells[" + cell++ + "] #Priority";
                int priority = progress.priority(type);
                int skill = progress.skill(type.skill);
                String skillLabel=row+" #Cells["+(cell-1)+"] #Skill";
                c.set(skillLabel+".Text","Lv "+skill);
                c.set(skillLabel+".Style.TextColor",skill==0?"#778591":skill>=7?"#eccb81":skill>=4?"#86d9b0":"#becdd9");
                c.set(selector + ".Text", skill == 0 ? "X" : priority == 0 ? "·" : Integer.toString(priority));
                c.set(selector + ".Disabled", !allowed || worker.guard() || !worker.alive() || skill == 0);
                c.set(selector + ".TooltipTextSpans", t("cell").param("profession", t(type.id)).param("skill", skill)
                    .param("priority", priority == 0 ? t("off") : Message.raw(Integer.toString(priority))));
                e.addEventBinding(CustomUIEventBindingType.Activating, selector,
                    new EventData().append("Action", "WorkPriority").append("WorkCharacter", worker.characterId())
                        .append("WorkType", type.id), false);
            }
        }
    }

    static void handle(String action, String character, String workType, TownRecord town, TownManager manager,
                       AetherhavenPlugin plugin, Store<EntityStore> store, UUID player) {
        if (!town.hasMemberOrOwner(player) || !town.playerCanManageConstructions(player) || character == null) return;
        var worker = TownsfolkWorkRoster.list(town, manager, store, plugin).stream()
            .filter(w -> w.characterId().equals(character)).findFirst().orElse(null);
        if (worker == null || worker.guard() || !worker.alive()) return;
        var progress = worker.progress();
        if (action.equals("WorkEmployment")) {
            progress.setEmployed(!progress.isEmployed());
            if (progress.isEmployed()) town.getPayroll().ensureCurrentBill(character);
        } else {
            var type = TownsfolkWorkType.byId(workType);
            if (type == null || !columns().contains(type) || progress.skill(type.skill) == 0) return;
            progress.setPriority(type, (progress.priority(type) + 1) % 5);
        }
        manager.updateTown(town);
    }
    static Message t(String key) { return Message.translation("aetherhaven_workforce." + key); }
}
