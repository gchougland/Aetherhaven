package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.villager.AetherhavenRoleLabels;
import com.hexvane.aetherhaven.villager.audit.VillagerAuditService;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Notifies online town members and writes server log when a town-linked NPC dies. */
public final class TownVillagerDeathNotifier {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String CHAT_KEY = "aetherhaven_ui_town.aetherhaven.town.villagerDeath.chat";
    private static final String LOCATION_UNKNOWN_KEY = "aetherhaven_ui_town.aetherhaven.town.villagerDeath.locationUnknown";
    private static final String CAUSE_BY_ENTITY_KEY = "aetherhaven_ui_town.aetherhaven.town.villagerDeath.causeByEntity";
    private static final String CAUSE_BY_DAMAGE_KEY = "aetherhaven_ui_town.aetherhaven.town.villagerDeath.causeByDamage";
    private static final String CAUSE_UNKNOWN_KEY = "aetherhaven_ui_town.aetherhaven.town.villagerDeath.causeUnknown";

    public enum DeathCategory {
        VISITOR("Inn visitor"),
        GUARD("Guard"),
        CITIZEN("Citizen"),
        VILLAGER("Villager"),
        TOURIST("Traveler");

        private final String logLabel;

        DeathCategory(@Nonnull String logLabel) {
            this.logLabel = logLabel;
        }

        @Nonnull
        public String logLabel() {
            return logLabel;
        }
    }

    private TownVillagerDeathNotifier() {}

    public static void notifyTownMembers(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> victimRef,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nullable String roleId,
        @Nonnull String bindingKind,
        @Nonnull DeathCategory category,
        @Nullable UUID entityUuid,
        @Nullable DeathComponent death
    ) {
        String displayName = resolveDisplayName(store, victimRef, plugin, roleId, bindingKind);
        String causeLabel = deathCauseLabel(store, death);
        String locationLabel = deathLocationLabel(store, victimRef);
        Message chat =
            Message.translation(CHAT_KEY)
                .param("name", displayName)
                .param("category", category.logLabel())
                .param("location", deathLocationMessage(store, victimRef))
                .param("cause", deathCauseMessage(store, death));
        Query<EntityStore> q = Query.and(Player.getComponentType(), UUIDComponent.getComponentType(), PlayerRef.getComponentType());
        store.forEachChunk(
            q,
            (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                    PlayerRef pr = chunk.getComponent(i, PlayerRef.getComponentType());
                    if (uc == null || pr == null) {
                        continue;
                    }
                    if (!town.hasMemberOrOwner(uc.getUuid())) {
                        continue;
                    }
                    pr.sendMessage(chat);
                }
            }
        );
        LOGGER.atInfo().log(
            "Town %s: %s died (%s, location=%s, cause=%s, kind=%s, entity=%s)",
            town.getDisplayName(),
            displayName,
            category.logLabel(),
            locationLabel,
            causeLabel,
            bindingKind,
            entityUuid != null ? entityUuid : "unknown"
        );
        VillagerAuditService.logDeath(plugin, store, victimRef, "death_handler", causeLabel);
    }

    @Nonnull
    private static Message deathCauseMessage(@Nonnull Store<EntityStore> store, @Nullable DeathComponent death) {
        if (death == null) {
            return Message.translation(CAUSE_UNKNOWN_KEY);
        }
        Damage info = death.getDeathInfo();
        if (info != null) {
            Damage.Source src = info.getSource();
            if (src instanceof Damage.EntitySource entitySource) {
                Ref<EntityStore> killerRef = entitySource.getRef();
                if (killerRef.isValid()) {
                    Message killerName = entityDisplayName(store, killerRef);
                    if (killerName != null) {
                        return Message.translation(CAUSE_BY_ENTITY_KEY).param("source", killerName);
                    }
                }
            }
        }
        Message damageType = translatedDamageCause(info, death);
        return Message.translation(CAUSE_BY_DAMAGE_KEY).param("damageType", damageType);
    }

    @Nullable
    private static Message entityDisplayName(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> entityRef) {
        DisplayNameComponent displayNameComponent = store.getComponent(entityRef, DisplayNameComponent.getComponentType());
        if (displayNameComponent != null && displayNameComponent.getDisplayName() != null) {
            return displayNameComponent.getDisplayName();
        }
        PlayerRef playerRef = store.getComponent(entityRef, PlayerRef.getComponentType());
        if (playerRef != null && playerRef.getUsername() != null && !playerRef.getUsername().isBlank()) {
            return Message.raw(playerRef.getUsername());
        }
        return null;
    }

    @Nonnull
    private static Message translatedDamageCause(@Nullable Damage info, @Nullable DeathComponent death) {
        DamageCause cause = null;
        if (info != null) {
            cause = DamageCause.getAssetMap().getAsset(info.getDamageCauseIndex());
        }
        if (cause == null && death != null) {
            cause = death.getDeathCause();
        }
        if (cause != null && cause.getId() != null && !cause.getId().isBlank()) {
            String causeId = cause.getId().toLowerCase(Locale.ROOT);
            return Message.translation("server.general.damageCauses." + causeId);
        }
        return Message.translation("server.general.damageCauses.unknown");
    }

    @Nonnull
    private static Message deathLocationMessage(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> victimRef) {
        if (!victimRef.isValid()) {
            return Message.translation(LOCATION_UNKNOWN_KEY);
        }
        TransformComponent tc = store.getComponent(victimRef, TransformComponent.getComponentType());
        if (tc == null) {
            return Message.translation(LOCATION_UNKNOWN_KEY);
        }
        Vector3d pos = tc.getPosition();
        int x = (int) Math.floor(pos.x);
        int y = (int) Math.floor(pos.y);
        int z = (int) Math.floor(pos.z);
        return Message.raw("(" + x + ", " + y + ", " + z + ")");
    }

    @Nonnull
    private static String deathLocationLabel(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> victimRef) {
        if (!victimRef.isValid()) {
            return "unknown";
        }
        TransformComponent tc = store.getComponent(victimRef, TransformComponent.getComponentType());
        if (tc == null) {
            return "unknown";
        }
        Vector3d pos = tc.getPosition();
        int x = (int) Math.floor(pos.x);
        int y = (int) Math.floor(pos.y);
        int z = (int) Math.floor(pos.z);
        return "(" + x + ", " + y + ", " + z + ")";
    }

    @Nonnull
    private static String deathCauseLabel(@Nonnull Store<EntityStore> store, @Nullable DeathComponent death) {
        if (death == null) {
            return "unknown";
        }
        Damage info = death.getDeathInfo();
        if (info != null) {
            Damage.Source src = info.getSource();
            if (src instanceof Damage.EntitySource entitySource) {
                Ref<EntityStore> killerRef = entitySource.getRef();
                if (killerRef.isValid()) {
                    PlayerRef playerRef = store.getComponent(killerRef, PlayerRef.getComponentType());
                    if (playerRef != null && playerRef.getUsername() != null && !playerRef.getUsername().isBlank()) {
                        return "entity:" + playerRef.getUsername();
                    }
                    return "entity";
                }
            }
        }
        DamageCause cause = death.getDeathCause();
        if (cause != null && cause.getId() != null && !cause.getId().isBlank()) {
            return cause.getId();
        }
        return "unknown";
    }

    @Nonnull
    private static String resolveDisplayName(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> victimRef,
        @Nonnull AetherhavenPlugin plugin,
        @Nullable String roleId,
        @Nonnull String bindingKind
    ) {
        if (victimRef.isValid()) {
            String rid = roleId != null && !roleId.isBlank() ? roleId : "";
            return TownResidentDisplay.resolveFromEntity(store, victimRef, rid, plugin).displayName();
        }
        if (roleId != null && !roleId.isBlank()) {
            return AetherhavenRoleLabels.displayNameForRoleId(roleId);
        }
        return AetherhavenRoleLabels.listLinePlainEnglish(null, bindingKind);
    }
}
