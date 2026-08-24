package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.ui.UiMaterialLabels;
import com.hypixel.hytale.server.core.Message;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Rebuilds persisted town log lines as the same player-facing messages used live. */
public final class TownLogMessage {
    private static final String PARAM_CAUSE_KEY = "_causeKey";
    private static final String PARAM_CAUSE_SOURCE = "_causeSource";
    private static final String PARAM_CAUSE_DAMAGE = "_causeDamageType";
    private static final String PARAM_LOCATION_KEY = "_locationKey";
    private static final String PARAM_LOCATION_RAW = "_locationRaw";
    private static final String PARAM_ITEM_ID = "_itemId";

    private TownLogMessage() {}

    @Nonnull
    public static Message render(@Nonnull TownLogEntry entry) {
        String key = entry.getMessageKey();
        if (key.isEmpty()) {
            return Message.raw("");
        }
        Message msg = Message.translation(key);
        Map<String, String> params = entry.getParams();
        for (Map.Entry<String, String> e : params.entrySet()) {
            String k = e.getKey();
            if (k == null || k.startsWith("_")) {
                continue;
            }
            String v = e.getValue();
            if (v == null) {
                continue;
            }
            if ("item".equals(k) && params.containsKey(PARAM_ITEM_ID)) {
                msg = msg.param(k, UiMaterialLabels.itemNameMessage(params.get(PARAM_ITEM_ID)));
            } else if (!"location".equals(k) && !"cause".equals(k)) {
                msg = msg.param(k, v);
            }
        }
        if (params.containsKey(PARAM_LOCATION_KEY)) {
            String locKey = params.get(PARAM_LOCATION_KEY);
            if (locKey != null && !locKey.isBlank()) {
                msg = msg.param("location", Message.translation(locKey));
            }
        } else if (params.containsKey(PARAM_LOCATION_RAW)) {
            String raw = params.get(PARAM_LOCATION_RAW);
            if (raw != null) {
                msg = msg.param("location", Message.raw(raw));
            }
        }
        if (params.containsKey(PARAM_CAUSE_KEY)) {
            msg = msg.param("cause", renderCause(params));
        }
        return msg;
    }

    @Nonnull
    private static Message renderCause(@Nonnull Map<String, String> params) {
        String causeKey = params.get(PARAM_CAUSE_KEY);
        if (causeKey == null || causeKey.isBlank()) {
            return Message.translation("aetherhaven_ui_town.aetherhaven.town.villagerDeath.causeUnknown");
        }
        if (causeKey.endsWith(".causeByEntity")) {
            String source = params.get(PARAM_CAUSE_SOURCE);
            if (source != null && !source.isBlank()) {
                return Message.translation(causeKey).param("source", source);
            }
        }
        if (causeKey.endsWith(".causeByDamage")) {
            String damage = params.get(PARAM_CAUSE_DAMAGE);
            if (damage != null && !damage.isBlank()) {
                if (damage.startsWith("server.")) {
                    return Message.translation(causeKey).param("damageType", Message.translation(damage));
                }
                return Message.translation(causeKey)
                    .param("damageType", Message.translation("server.general.damageCauses." + damage.toLowerCase(Locale.ROOT)));
            }
        }
        return Message.translation(causeKey);
    }

    @Nonnull
    public static Map<String, String> deathParams(
        @Nonnull String name,
        @Nonnull String category,
        boolean locationUnknown,
        @Nonnull String locationRaw,
        @Nonnull String causeKey,
        @Nullable String causeSource,
        @Nullable String causeDamageTypeKey
    ) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("name", name);
        p.put("category", category);
        if (locationUnknown) {
            p.put(PARAM_LOCATION_KEY, "aetherhaven_ui_town.aetherhaven.town.villagerDeath.locationUnknown");
        } else {
            p.put(PARAM_LOCATION_RAW, locationRaw);
        }
        p.put(PARAM_CAUSE_KEY, causeKey);
        if (causeSource != null && !causeSource.isBlank()) {
            p.put(PARAM_CAUSE_SOURCE, causeSource);
        }
        if (causeDamageTypeKey != null && !causeDamageTypeKey.isBlank()) {
            p.put(PARAM_CAUSE_DAMAGE, causeDamageTypeKey);
        }
        return p;
    }

    @Nonnull
    public static Map<String, String> taxParams(@Nonnull String amount, @Nonnull String townName) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("amount", amount);
        p.put("town", townName);
        return p;
    }

    @Nonnull
    public static Map<String, String> shopSaleParams(
        @Nonnull String buyer, @Nonnull String itemId, @Nonnull String count, @Nonnull String gold
    ) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("buyer", buyer);
        p.put("count", count);
        p.put("gold", gold);
        p.put(PARAM_ITEM_ID, itemId);
        p.put("item", itemId);
        return p;
    }
}
