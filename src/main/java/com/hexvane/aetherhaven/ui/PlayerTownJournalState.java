package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Last opened tab in the Town Journal UI, persisted on the player entity. */
public final class PlayerTownJournalState implements Component<EntityStore> {
    public static final int MAX_PINNED_QUESTS = 3;

    public enum JournalTab {
        TOWN,
        GUIDE,
        QUESTS,
        SETTINGS;

        @Nonnull
        public static JournalTab fromPersisted(@Nullable String s) {
            if (s == null || s.isBlank()) {
                return QUESTS;
            }
            return switch (s.trim().toUpperCase()) {
                case "TOWN" -> TOWN;
                case "GUIDE" -> GUIDE;
                case "SETTINGS" -> SETTINGS;
                default -> QUESTS;
            };
        }

        @Nonnull
        public String persisted() {
            return name();
        }
    }

    public enum SettingsSubTab {
        PERSONAL,
        SERVER;

        @Nonnull
        public static SettingsSubTab fromPersisted(@Nullable String s) {
            if (s == null || s.isBlank()) {
                return PERSONAL;
            }
            return switch (s.trim().toUpperCase()) {
                case "SERVER" -> SERVER;
                default -> PERSONAL;
            };
        }

        @Nonnull
        public String persisted() {
            return name();
        }
    }

    @Nonnull
    public static final BuilderCodec<PlayerTownJournalState> CODEC =
        BuilderCodec.builder(PlayerTownJournalState.class, PlayerTownJournalState::new)
            .append(
                new KeyedCodec<>("LastTab", Codec.STRING),
                (c, v) -> c.lastTab = JournalTab.fromPersisted(v),
                c -> c.lastTab.persisted())
            .add()
            .append(
                new KeyedCodec<>("ShowTownBordersOnMap", Codec.BOOLEAN),
                (c, v) -> {
                    if (v != null) {
                        c.showTownBordersOnMap = v;
                    }
                },
                c -> c.showTownBordersOnMap)
            .add()
            .append(
                new KeyedCodec<>("LastSettingsSubTab", Codec.STRING),
                (c, v) -> c.lastSettingsSubTab = SettingsSubTab.fromPersisted(v),
                c -> c.lastSettingsSubTab.persisted())
            .add()
            .append(
                new KeyedCodec<>("RtsPickFovOverride", Codec.FLOAT),
                (c, v) -> {
                    if (v != null) {
                        c.rtsPickFovOverride = v;
                    }
                },
                c -> c.rtsPickFovOverride)
            .add()
            .append(
                new KeyedCodec<>("RtsPickAspectOverride", Codec.FLOAT),
                (c, v) -> {
                    if (v != null) {
                        c.rtsPickAspectOverride = v;
                    }
                },
                c -> c.rtsPickAspectOverride)
            .add()
            .append(new KeyedCodec<>("HudShowTime", Codec.BOOLEAN), (c, v) -> c.hudShowTime = valueOr(v, true), c -> c.hudShowTime)
            .add()
            .append(new KeyedCodec<>("HudEnabled", Codec.BOOLEAN), (c, v) -> c.hudEnabled = valueOr(v, true), c -> c.hudEnabled)
            .add()
            .append(
                new KeyedCodec<>("HudBackgroundOpacity", Codec.FLOAT),
                (c, v) -> c.hudBackgroundOpacity = clampOpacity(v != null ? v : 0f),
                c -> c.hudBackgroundOpacity
            )
            .add()
            .append(new KeyedCodec<>("HudShowDate", Codec.BOOLEAN), (c, v) -> c.hudShowDate = valueOr(v, true), c -> c.hudShowDate)
            .add()
            .append(new KeyedCodec<>("HudShowGold", Codec.BOOLEAN), (c, v) -> c.hudShowGold = valueOr(v, true), c -> c.hudShowGold)
            .add()
            .append(new KeyedCodec<>("HudShowQuests", Codec.BOOLEAN), (c, v) -> c.hudShowQuests = valueOr(v, true), c -> c.hudShowQuests)
            .add()
            .append(new KeyedCodec<>("HudStatusPlacement", Codec.STRING), (c, v) -> c.hudStatusPlacement = placement(v, "TOP_RIGHT"), c -> c.hudStatusPlacement)
            .add()
            .append(new KeyedCodec<>("HudStatusX", Codec.INTEGER), (c, v) -> c.hudStatusX = offset(v, 0), c -> c.hudStatusX)
            .add()
            .append(new KeyedCodec<>("HudStatusY", Codec.INTEGER), (c, v) -> c.hudStatusY = offset(v, 0), c -> c.hudStatusY)
            .add()
            .append(new KeyedCodec<>("HudQuestPlacement", Codec.STRING), (c, v) -> c.hudQuestPlacement = placement(v, "TOP_RIGHT"), c -> c.hudQuestPlacement)
            .add()
            .append(new KeyedCodec<>("HudQuestX", Codec.INTEGER), (c, v) -> c.hudQuestX = offset(v, 0), c -> c.hudQuestX)
            .add()
            .append(new KeyedCodec<>("HudQuestY", Codec.INTEGER), (c, v) -> c.hudQuestY = questYOffset(v, c.hudQuestPlacement), c -> c.hudQuestY)
            .add()
            .append(new KeyedCodec<>("HudPinnedQuests", Codec.STRING), (c, v) -> c.decodePinnedQuests(v), PlayerTownJournalState::encodePinnedQuests)
            .add()
            .append(
                new KeyedCodec<>("DialogueSpeechEnabled", Codec.BOOLEAN),
                (c, v) -> c.dialogueSpeechEnabled = valueOr(v, true),
                c -> c.dialogueSpeechEnabled
            )
            .add()
            .append(
                new KeyedCodec<>("DialogueSpeechVolumePercent", Codec.INTEGER),
                (c, v) -> c.dialogueSpeechVolumePercent = clampSpeechVolume(v != null ? v : 70),
                c -> c.dialogueSpeechVolumePercent
            )
            .add()
            .append(
                new KeyedCodec<>("ActiveTownId", Codec.STRING),
                (c, v) -> c.activeTownId = v != null ? v.trim() : "",
                c -> c.activeTownId.isEmpty() ? null : c.activeTownId
            )
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, PlayerTownJournalState> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType =
            registry.registerComponent(PlayerTownJournalState.class, "AetherhavenPlayerTownJournalState", PlayerTownJournalState.CODEC);
    }

    @Nonnull
    public static ComponentType<EntityStore, PlayerTownJournalState> getComponentType() {
        ComponentType<EntityStore, PlayerTownJournalState> t = componentType;
        if (t == null) {
            throw new IllegalStateException("PlayerTownJournalState not registered");
        }
        return t;
    }

    @Nonnull
    private JournalTab lastTab = JournalTab.QUESTS;

    private boolean showTownBordersOnMap = true;

    @Nonnull
    private SettingsSubTab lastSettingsSubTab = SettingsSubTab.PERSONAL;

    /** {@code <= 0} means use {@link AetherhavenConstants} default. */
    private float rtsPickFovOverride;

    private float rtsPickAspectOverride;

    private boolean hudShowTime = true;
    private boolean hudEnabled = true;
    private float hudBackgroundOpacity;
    private boolean hudShowDate = true;
    private boolean hudShowGold = true;
    private boolean hudShowQuests = true;
    @Nonnull
    private String hudStatusPlacement = "TOP_RIGHT";
    private int hudStatusX;
    private int hudStatusY;
    @Nonnull
    private String hudQuestPlacement = "TOP_RIGHT";
    private int hudQuestX;
    private int hudQuestY = 164;
    @Nonnull
    private final List<String> hudPinnedQuests = new ArrayList<>();

    private boolean dialogueSpeechEnabled = true;
    /** 0–100; default 70. */
    private int dialogueSpeechVolumePercent = 70;

    @Nonnull
    private String activeTownId = "";

    public PlayerTownJournalState() {}

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        PlayerTownJournalState c = new PlayerTownJournalState();
        c.lastTab = lastTab;
        c.showTownBordersOnMap = showTownBordersOnMap;
        c.lastSettingsSubTab = lastSettingsSubTab;
        c.rtsPickFovOverride = rtsPickFovOverride;
        c.rtsPickAspectOverride = rtsPickAspectOverride;
        c.hudShowTime = hudShowTime;
        c.hudEnabled = hudEnabled;
        c.hudBackgroundOpacity = hudBackgroundOpacity;
        c.hudShowDate = hudShowDate;
        c.hudShowGold = hudShowGold;
        c.hudShowQuests = hudShowQuests;
        c.hudStatusPlacement = hudStatusPlacement;
        c.hudStatusX = hudStatusX;
        c.hudStatusY = hudStatusY;
        c.hudQuestPlacement = hudQuestPlacement;
        c.hudQuestX = hudQuestX;
        c.hudQuestY = hudQuestY;
        c.hudPinnedQuests.addAll(hudPinnedQuests);
        c.dialogueSpeechEnabled = dialogueSpeechEnabled;
        c.dialogueSpeechVolumePercent = dialogueSpeechVolumePercent;
        c.activeTownId = activeTownId;
        return c;
    }

    @Nullable
    public UUID getActiveTownId() {
        if (activeTownId.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(activeTownId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setActiveTownId(@Nullable UUID townId) {
        activeTownId = townId != null ? townId.toString() : "";
    }

    public void clearActiveTownId() {
        activeTownId = "";
    }

    public boolean isActiveTownValid(@Nonnull TownManager tm, @Nonnull UUID playerUuid) {
        UUID id = getActiveTownId();
        if (id == null) {
            return false;
        }
        TownRecord t = tm.getTown(id);
        return t != null && t.hasMemberOrOwner(playerUuid);
    }

    @Nonnull
    public JournalTab getLastTab() {
        return lastTab;
    }

    public void setLastTab(@Nonnull JournalTab tab) {
        this.lastTab = tab;
    }

    public boolean isShowTownBordersOnMap() {
        return showTownBordersOnMap;
    }

    public void setShowTownBordersOnMap(boolean showTownBordersOnMap) {
        this.showTownBordersOnMap = showTownBordersOnMap;
    }

    @Nonnull
    public SettingsSubTab getLastSettingsSubTab() {
        return lastSettingsSubTab;
    }

    public void setLastSettingsSubTab(@Nonnull SettingsSubTab lastSettingsSubTab) {
        this.lastSettingsSubTab = lastSettingsSubTab;
    }

    public void clearRtsPickOverrides() {
        rtsPickFovOverride = 0f;
        rtsPickAspectOverride = 0f;
    }

    public void setRtsPickOverrides(float verticalFovDeg, float aspectRatio) {
        rtsPickFovOverride = verticalFovDeg;
        rtsPickAspectOverride = aspectRatio;
    }

    public float effectiveRtsPickVerticalFovDeg() {
        return rtsPickFovOverride > 0f ? rtsPickFovOverride : AetherhavenConstants.RTS_COMMAND_PICK_VERTICAL_FOV_DEG;
    }

    public float effectiveRtsPickAspectRatio() {
        return rtsPickAspectOverride > 0f ? rtsPickAspectOverride : AetherhavenConstants.RTS_COMMAND_PICK_ASPECT_RATIO;
    }

    public boolean isHudShowTime() {
        return hudShowTime;
    }

    public boolean isHudEnabled() {
        return hudEnabled;
    }

    public void setHudEnabled(boolean hudEnabled) {
        this.hudEnabled = hudEnabled;
    }

    public float getHudBackgroundOpacity() {
        return hudBackgroundOpacity;
    }

    public void setHudBackgroundOpacity(float hudBackgroundOpacity) {
        this.hudBackgroundOpacity = clampOpacity(hudBackgroundOpacity);
    }

    public boolean isHudShowDate() {
        return hudShowDate;
    }

    public boolean isHudShowGold() {
        return hudShowGold;
    }

    public boolean isHudShowQuests() {
        return hudShowQuests;
    }

    @Nonnull
    public String getHudStatusPlacement() {
        return hudStatusPlacement;
    }

    public int getHudStatusX() {
        return hudStatusX;
    }

    public int getHudStatusY() {
        return hudStatusY;
    }

    @Nonnull
    public String getHudQuestPlacement() {
        return hudQuestPlacement;
    }

    public int getHudQuestX() {
        return hudQuestX;
    }

    public int getHudQuestY() {
        return hudQuestY;
    }

    public void setHudPreferences(
        boolean showTime,
        boolean showDate,
        boolean showGold,
        boolean showQuests,
        @Nullable String statusPlacement,
        int statusX,
        int statusY,
        @Nullable String questPlacement,
        int questX,
        int questY
    ) {
        hudShowTime = showTime;
        hudShowDate = showDate;
        hudShowGold = showGold;
        hudShowQuests = showQuests;
        hudStatusPlacement = placement(statusPlacement, "TOP_RIGHT");
        hudStatusX = offset(statusX, 0);
        hudStatusY = offset(statusY, 0);
        hudQuestPlacement = placement(questPlacement, "TOP_RIGHT");
        hudQuestX = offset(questX, 0);
        hudQuestY = questYOffset(questY, hudQuestPlacement);
    }

    public void resetHudPreferences() {
        setHudPreferences(true, true, true, true, "TOP_RIGHT", 0, 0, "TOP_RIGHT", 0, 164);
        hudBackgroundOpacity = 0f;
        dialogueSpeechEnabled = true;
        dialogueSpeechVolumePercent = 70;
    }

    public boolean isDialogueSpeechEnabled() {
        return dialogueSpeechEnabled;
    }

    public void setDialogueSpeechEnabled(boolean dialogueSpeechEnabled) {
        this.dialogueSpeechEnabled = dialogueSpeechEnabled;
    }

    public int getDialogueSpeechVolumePercent() {
        return dialogueSpeechVolumePercent;
    }

    public void setDialogueSpeechVolumePercent(int percent) {
        dialogueSpeechVolumePercent = clampSpeechVolume(percent);
    }

    /** Linear gain 0..1 for SoundUtil volumeModifier. */
    public float getDialogueSpeechVolumeLinear() {
        return dialogueSpeechVolumePercent / 100f;
    }

    public void setDialogueSpeechPreferences(boolean enabled, int volumePercent) {
        dialogueSpeechEnabled = enabled;
        dialogueSpeechVolumePercent = clampSpeechVolume(volumePercent);
    }

    @Nonnull
    public List<String> getPinnedQuestIds() {
        return Collections.unmodifiableList(hudPinnedQuests);
    }

    public boolean isQuestPinned(@Nullable String questId) {
        return questId != null && hudPinnedQuests.contains(questId.trim());
    }

    public boolean pinQuest(@Nullable String questId) {
        if (questId == null) {
            return false;
        }
        String id = questId.trim();
        if (id.isEmpty() || hudPinnedQuests.contains(id) || hudPinnedQuests.size() >= MAX_PINNED_QUESTS) {
            return false;
        }
        hudPinnedQuests.add(id);
        return true;
    }

    public boolean unpinQuest(@Nullable String questId) {
        return questId != null && hudPinnedQuests.remove(questId.trim());
    }

    public boolean retainPinnedQuests(@Nonnull Set<String> activeQuestIds) {
        return hudPinnedQuests.removeIf(id -> !activeQuestIds.contains(id));
    }

    public int activePinnedQuestCount(@Nonnull Set<String> activeQuestIds) {
        int count = 0;
        for (String id : hudPinnedQuests) {
            if (activeQuestIds.contains(id)) {
                count++;
            }
        }
        return count;
    }

    private void decodePinnedQuests(@Nullable String encoded) {
        hudPinnedQuests.clear();
        if (encoded == null || encoded.isBlank()) {
            return;
        }
        for (String line : encoded.split("\n")) {
            if (hudPinnedQuests.size() >= MAX_PINNED_QUESTS) {
                break;
            }
            pinQuest(line);
        }
    }

    @Nonnull
    private String encodePinnedQuests() {
        return String.join("\n", hudPinnedQuests);
    }

    private static boolean valueOr(@Nullable Boolean value, boolean fallback) {
        return value != null ? value : fallback;
    }

    @Nonnull
    private static String placement(@Nullable String value, @Nonnull String fallback) {
        if (value == null) {
            return fallback;
        }
        return switch (value.trim().toUpperCase()) {
            case "TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT", "CUSTOM" -> value.trim().toUpperCase();
            default -> fallback;
        };
    }

    private static int offset(@Nullable Integer value, int fallback) {
        return value != null ? offset(value.intValue(), fallback) : fallback;
    }

    private static int offset(int value, int fallback) {
        if (value < 0) {
            return 0;
        }
        return Math.min(value, 4000);
    }

    private static int questYOffset(@Nullable Integer value, @Nonnull String placement) {
        int resolved = offset(value, 164);
        // Move the old preset default far enough below the full status panel.
        return (resolved == 130 || resolved == 144) && !"CUSTOM".equals(placement) ? 164 : resolved;
    }

    private static float clampOpacity(float value) {
        if (!Float.isFinite(value)) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, value));
    }

    private static int clampSpeechVolume(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
