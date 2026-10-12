package com.hexvane.aetherhaven.quest;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.hud.AetherhavenHudRefreshSystem;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.workplace.WorkplaceQuestSteps;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import javax.annotation.Nonnull;

/**
 * Where the workplace pages and tools tell the quests what a player just did. Each call is one line at the place the
 * thing really happens, and each does nothing for a town with no quest in progress.
 *
 * <p>All of them read or change the town, so they run on the world thread, outside tick systems.
 */
public final class WorkplaceQuestHooks {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private WorkplaceQuestHooks() {}

    private static boolean idle(@Nonnull TownRecord town) {
        return town.getActiveQuestIdsSnapshot().isEmpty();
    }

    /**
     * A quest is never worth a broken page. These calls sit inside the workplace pages and the Building Staff, so a
     * fault here is logged and the player's action carries on as if no quest were watching.
     */
    private static void quietly(@Nonnull String what, @Nonnull Runnable step) {
        try {
            step.run();
        } catch (RuntimeException e) {
            LOGGER.atWarning().withCause(e).log("Quest progress for a workplace step was skipped (%s)", what);
        }
    }

    private static void saved(@Nonnull World world, @Nonnull TownManager tm, @Nonnull TownRecord town, boolean changed) {
        if (changed) {
            tm.updateTown(town);
            AetherhavenHudRefreshSystem.requestRefresh(world);
        }
    }

    /** A workplace type was bought in a villager's upgrades. */
    public static void typeUnlocked(@Nonnull World world, @Nonnull TownManager tm, @Nonnull TownRecord town) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null || idle(town)) {
            return;
        }
        quietly("type unlocked", () -> saved(world, tm, town, QuestProgressionService.onWorkplacesChanged(plugin, town)));
    }

    /** A player marked the second corner of a new workplace with the Building Staff. */
    public static void areaMarked(@Nonnull World world, @Nonnull TownManager tm, @Nonnull TownRecord town) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null || idle(town)) {
            return;
        }
        quietly("area marked", () -> saved(world, tm, town, QuestProgressionService.onWorkplaceAreaMarked(plugin, town)));
    }

    /**
     * A workplace was saved on its page: made, resized or given another type. Its station may already stand inside
     * it, and the player is right there, so the workplaces are looked through now.
     */
    public static void workplaceSaved(@Nonnull World world, @Nonnull TownManager tm, @Nonnull TownRecord town) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null || idle(town)) {
            return;
        }
        quietly("workplace saved", () -> {
            boolean changed = QuestProgressionService.onWorkplacesChanged(plugin, town);
            if (QuestProgressionService.awaitsWorld(plugin.getQuestCatalog(), town)) {
                changed |= QuestProgressionService.observeWorld(plugin, town, QuestProgressionService.worldProofs(world, plugin, town));
            }
            saved(world, tm, town, changed);
        });
    }

    /**
     * A workplace is about to be removed. What it proves is written down first, so the steps it finished stay
     * finished and the quest carries on from where it was.
     */
    public static void workplaceRemoving(@Nonnull World world, @Nonnull TownManager tm, @Nonnull TownRecord town) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null || idle(town)) {
            return;
        }
        quietly("workplace removing", () -> saved(world, tm, town, QuestProgressionService.onWorkplacesChanged(plugin, town)));
    }

    /** The station of a workplace was used or seen standing inside it. */
    public static void stationSeen(
        @Nonnull World world,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull TownsfolkWorkType type
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null || idle(town)) {
            return;
        }
        quietly("station seen", () -> {
            if (QuestProgressionService.awaitsWorld(plugin.getQuestCatalog(), town)) {
                saved(world, tm, town, QuestProgressionService.onWorkplaceStationFound(plugin, town, type));
            }
        });
    }

    /** True when a block set down here could finish a step, so the world is worth a look once it stands. */
    public static boolean watchesBlock(@Nonnull World world, int x, int z) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        TownManager tm = AetherhavenWorldRegistries.getTownManagerIfLoaded(world);
        if (plugin == null || tm == null) {
            return false;
        }
        TownRecord town = tm.findTownContainingBlock(world.getName(), x, z);
        return town != null && !idle(town) && QuestProgressionService.awaitsWorld(plugin.getQuestCatalog(), town);
    }

    /** A player set a block down. If it is the station of the workplace it stands in, that step is done. */
    public static void blockPlaced(@Nonnull World world, int x, int y, int z) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        TownManager tm = AetherhavenWorldRegistries.getTownManagerIfLoaded(world);
        if (plugin == null || tm == null) {
            return;
        }
        TownRecord town = tm.findTownContainingBlock(world.getName(), x, z);
        if (town == null || idle(town)) {
            return;
        }
        quietly("block placed", () -> lookAtPlacedBlock(world, plugin, tm, town, x, y, z));
    }

    private static void lookAtPlacedBlock(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        int x,
        int y,
        int z
    ) {
        // A bench is several blocks. The event may name any of them, and the block that says what it is, is its origin.
        int filler = ChunkSectionBlockUtil.filler(world, x, y, z);
        int ox = x - FillerBlockUtil.unpackX(filler), oy = y - FillerBlockUtil.unpackY(filler), oz = z - FillerBlockUtil.unpackZ(filler);
        var site = WorkplaceQuestSteps.stationSiteAt(WorkplaceQuestSteps.of(world), town, plugin.getGrowerPlans(), ox, oy, oz);
        if (site != null) {
            stationSeen(world, tm, town, site.type());
        }
    }
}
