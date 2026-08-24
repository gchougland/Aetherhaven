package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Appends persisted town log entries and saves via {@link TownManager}. */
public final class TownLogService {
    public static final String KEY_DEATH = "aetherhaven_ui_town.aetherhaven.town.villagerDeath.chat";
    public static final String KEY_TAX = "aetherhaven_ui_shell.aetherhaven.ui.treasury.notificationTaxCollected";
    public static final String KEY_SHOP_SALE = "aetherhaven_shop.aetherhaven.shop.soldNotify";

    private static final String CAUSE_BY_ENTITY_KEY = "aetherhaven_ui_town.aetherhaven.town.villagerDeath.causeByEntity";
    private static final String CAUSE_BY_DAMAGE_KEY = "aetherhaven_ui_town.aetherhaven.town.villagerDeath.causeByDamage";
    private static final String CAUSE_UNKNOWN_KEY = "aetherhaven_ui_town.aetherhaven.town.villagerDeath.causeUnknown";

    private TownLogService() {}

    public static void logDeath(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> victimRef,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull String displayName,
        @Nonnull TownVillagerDeathNotifier.DeathCategory category,
        @Nullable DeathComponent death
    ) {
        long day = VillagerReputationService.currentGameEpochDay(store);
        Map<String, String> params = buildDeathParams(store, victimRef, displayName, category, death);
        appendAndSave(town, tm, new TownLogEntry(day, KEY_DEATH, params));
    }

    public static void logTaxCollected(@Nonnull TownRecord town, @Nonnull TownManager tm, long goldAdded, long gameEpochDay) {
        if (goldAdded <= 0L) {
            return;
        }
        Map<String, String> params = TownLogMessage.taxParams(Long.toString(goldAdded), town.getDisplayName());
        appendAndSave(town, tm, new TownLogEntry(gameEpochDay, KEY_TAX, params));
    }

    public static void logShopSale(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull String buyerName,
        @Nonnull String itemId,
        int count,
        long gold
    ) {
        long day = VillagerReputationService.currentGameEpochDay(store);
        Map<String, String> params =
            TownLogMessage.shopSaleParams(buyerName, itemId, Integer.toString(count), Long.toString(gold));
        appendAndSave(town, tm, new TownLogEntry(day, KEY_SHOP_SALE, params));
    }

    public static void clear(@Nonnull TownRecord town, @Nonnull TownManager tm) {
        town.clearTownLog();
        tm.updateTown(town);
    }

    /** Appends without saving; caller must invoke {@link TownManager#updateTown}. */
    public static void appendEntry(@Nonnull TownRecord town, @Nonnull TownLogEntry entry) {
        town.appendTownLog(entry);
    }

    private static void appendAndSave(@Nonnull TownRecord town, @Nonnull TownManager tm, @Nonnull TownLogEntry entry) {
        town.appendTownLog(entry);
        tm.updateTown(town);
    }

    @Nonnull
    private static Map<String, String> buildDeathParams(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> victimRef,
        @Nonnull String displayName,
        @Nonnull TownVillagerDeathNotifier.DeathCategory category,
        @Nullable DeathComponent death
    ) {
        boolean locationUnknown = true;
        String locationRaw = "";
        if (victimRef.isValid()) {
            TransformComponent tc = store.getComponent(victimRef, TransformComponent.getComponentType());
            if (tc != null) {
                Vector3d pos = tc.getPosition();
                int x = (int) Math.floor(pos.x);
                int y = (int) Math.floor(pos.y);
                int z = (int) Math.floor(pos.z);
                locationRaw = "(" + x + ", " + y + ", " + z + ")";
                locationUnknown = false;
            }
        }
        String causeKey = CAUSE_UNKNOWN_KEY;
        String causeSource = null;
        String causeDamage = null;
        Damage info = death != null ? death.getDeathInfo() : null;
        if (death != null) {
            if (info != null) {
                Damage.Source src = info.getSource();
                if (src instanceof Damage.EntitySource entitySource) {
                    Ref<EntityStore> killerRef = entitySource.getRef();
                    if (killerRef.isValid()) {
                        PlayerRef playerRef = store.getComponent(killerRef, PlayerRef.getComponentType());
                        if (playerRef != null && playerRef.getUsername() != null && !playerRef.getUsername().isBlank()) {
                            causeKey = CAUSE_BY_ENTITY_KEY;
                            causeSource = playerRef.getUsername();
                        } else {
                            DisplayNameComponent dnc = store.getComponent(killerRef, DisplayNameComponent.getComponentType());
                            if (dnc != null && dnc.getDisplayName() != null) {
                                causeKey = CAUSE_BY_ENTITY_KEY;
                                causeSource = dnc.getDisplayName().getRawText();
                            }
                        }
                    }
                }
            }
            if (CAUSE_UNKNOWN_KEY.equals(causeKey)) {
                DamageCause cause = null;
                if (info != null) {
                    cause = DamageCause.getAssetMap().getAsset(info.getDamageCauseIndex());
                }
                if (cause == null) {
                    cause = death.getDeathCause();
                }
                if (cause != null && cause.getId() != null && !cause.getId().isBlank()) {
                    causeKey = CAUSE_BY_DAMAGE_KEY;
                    causeDamage = cause.getId().toLowerCase(Locale.ROOT);
                }
            }
        }
        return TownLogMessage.deathParams(
            displayName,
            category.logLabel(),
            locationUnknown,
            locationRaw,
            causeKey,
            causeSource,
            causeDamage
        );
    }
}
