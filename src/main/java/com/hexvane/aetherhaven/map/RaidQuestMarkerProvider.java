package com.hexvane.aetherhaven.map;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.questboard.QuestBoardSlotRecord;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownPlayerResolution;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerBuilder;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Compass markers for living raid quest mobs. Reads {@link RaidQuestCompassCache} only — never touches
 * {@code EntityStore} (world-map thread is not the world thread).
 */
public final class RaidQuestMarkerProvider implements WorldMapManager.MarkerProvider {
    public static final RaidQuestMarkerProvider INSTANCE = new RaidQuestMarkerProvider();
    public static final String MARKER_ICON = "PortalInvasion.png";
    private static final String MARKER_ID_PREFIX = "aetherhaven-raid-";

    private RaidQuestMarkerProvider() {}

    @Override
    public void update(@Nonnull World world, @Nonnull Player player, @Nonnull MarkersCollector collector) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        @SuppressWarnings("removal")
        UUID playerUuid = player.getUuid();

        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = TownPlayerResolution.resolveFallbackAffiliatedTown(tm, playerUuid);
        if (town == null || !world.getName().equals(town.getWorldName())) {
            return;
        }

        List<RaidQuestCompassCache.Entry> entries = RaidQuestCompassCache.entriesForTown(world.getName(), town.getTownId());
        for (RaidQuestCompassCache.Entry entry : entries) {
            if (!isActiveRaidMob(town, entry)) {
                continue;
            }
            MapMarker marker = buildMarker(entry);
            if (marker != null) {
                collector.addIgnoreViewDistance(marker);
            }
        }
    }

    private static boolean isActiveRaidMob(@Nonnull TownRecord town, @Nonnull RaidQuestCompassCache.Entry entry) {
        QuestBoardSlotRecord slot = town.findBoardSlotByInstanceId(entry.instanceId());
        if (slot == null || !slot.isAccepted() || !slot.isRaidQuest()) {
            return false;
        }
        return slot.raidSpawnedEntityUuidsOrEmpty().contains(entry.mobUuid().toString());
    }

    @Nonnull
    public static String markerId(@Nonnull String instanceId, @Nonnull UUID mobUuid) {
        return MARKER_ID_PREFIX + instanceId + "-" + mobUuid;
    }

    public static boolean isRaidQuestMarkerId(@Nonnull String id) {
        return id.startsWith(MARKER_ID_PREFIX);
    }

    @Nullable
    private static MapMarker buildMarker(@Nonnull RaidQuestCompassCache.Entry entry) {
        Message label = raidTargetLabel(entry.targetLabelLangKey());
        return new MapMarkerBuilder(
            markerId(entry.instanceId(), entry.mobUuid()),
            MARKER_ICON,
            MapMarkerTransforms.at(entry.x(), entry.y(), entry.z())
        )
            .withName(label)
            .build();
    }

    @Nonnull
    private static Message raidTargetLabel(@Nullable String langKey) {
        if (langKey != null && !langKey.isBlank()) {
            return Message.translation(langKey.trim());
        }
        return Message.translation("aetherhaven_quest_board.aetherhaven.questBoard.targets.goblin");
    }
}
