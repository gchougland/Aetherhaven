package com.hexvane.aetherhaven.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Resolves display labels for vanilla {@link ToolKeybindSlot} binding ids.
 * Uses the local Hytale client settings file when present (singleplayer / integrated server),
 * otherwise falls back to vanilla default labels.
 */
public final class ToolKeybindDisplay {
    private static final Map<String, String> VANILLA_DEFAULTS = Map.ofEntries(
        Map.entry("PrimaryItemAction", "LMB"),
        Map.entry("SecondaryItemAction", "RMB"),
        Map.entry("BlockInteractAction", "F"),
        Map.entry("Ability1ItemAction", "Q"),
        Map.entry("Ability2ItemAction", "E"),
        Map.entry("Ability3ItemAction", "R"),
        Map.entry("PickBlock", "MMB"),
        Map.entry("UiCancel", "Esc"),
        Map.entry("Sprint", "Shift"),
        Map.entry("FlyDown", "Ctrl"),
        Map.entry("Jump", "Space"),
        Map.entry("MoveForwards", "WASD")
    );

    private static volatile Map<String, String> settingsOverrides = Map.of();
    private static volatile long settingsMtime;

    private ToolKeybindDisplay() {}

    @Nonnull
    public static String labelFor(@Nullable PlayerRef playerRef, @Nonnull ToolKeybindSlot slot) {
        refreshSettingsCache();
        String bindingId = slot.inputBindingKey();
        String fromSettings = settingsOverrides.get(bindingId);
        if (fromSettings != null && !fromSettings.isBlank()) {
            return fromSettings;
        }
        return VANILLA_DEFAULTS.getOrDefault(bindingId, slot.defaultLabel());
    }

    private static void refreshSettingsCache() {
        Path settings = resolveSettingsPath();
        if (settings == null || !Files.isRegularFile(settings)) {
            settingsOverrides = Map.of();
            settingsMtime = 0;
            return;
        }
        try {
            long mtime = Files.getLastModifiedTime(settings).toMillis();
            if (mtime == settingsMtime) {
                return;
            }
            String json = Files.readString(settings, StandardCharsets.UTF_8);
            settingsOverrides = parseSettingsOverrides(json);
            settingsMtime = mtime;
        } catch (IOException ignored) {
            settingsOverrides = Map.of();
            settingsMtime = 0;
        }
    }

    @Nullable
    private static Path resolveSettingsPath() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            return null;
        }
        return Path.of(appData, "Hytale", "UserData", "Settings.json");
    }

    @Nonnull
    private static Map<String, String> parseSettingsOverrides(@Nonnull String json) {
        Map<String, String> out = new HashMap<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject actions = root.getAsJsonObject("InputActions");
        if (actions == null) {
            return Map.copyOf(out);
        }
        for (Map.Entry<String, JsonElement> entry : actions.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject action = entry.getValue().getAsJsonObject();
            String actionName = action.has("Name") ? action.get("Name").getAsString() : entry.getKey();
            String label = bestBindingLabel(action.getAsJsonArray("Bindings"));
            if (label == null || label.isBlank()) {
                continue;
            }
            out.put(actionName, label);
            if (!entry.getKey().equals(actionName)) {
                out.put(entry.getKey(), label);
            }
        }
        return Map.copyOf(out);
    }

    @Nullable
    private static String bestBindingLabel(@Nullable JsonArray bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return null;
        }
        String keyboard = null;
        String mouse = null;
        for (JsonElement element : bindings) {
            if (!element.isJsonObject()) {
                continue;
            }
            String label = formatBinding(element.getAsJsonObject());
            if (label == null || label.isBlank()) {
                continue;
            }
            int sourceType = element.getAsJsonObject().has("SourceType")
                ? element.getAsJsonObject().get("SourceType").getAsInt()
                : 0;
            if (sourceType == 0) {
                keyboard = label;
            } else if (mouse == null) {
                mouse = label;
            }
        }
        if (keyboard != null) {
            return keyboard;
        }
        return mouse;
    }

    @Nullable
    private static String formatBinding(@Nonnull JsonObject binding) {
        int sourceType = binding.has("SourceType") ? binding.get("SourceType").getAsInt() : 0;
        if (sourceType != 0) {
            return switch (sourceType) {
                case 1 -> "LMB";
                case 2 -> "RMB";
                case 3 -> "MMB";
                default -> null;
            };
        }
        int scancode = binding.has("Scancode") ? binding.get("Scancode").getAsInt() : -1;
        String key = scancodeToLabel(scancode);
        if (key == null) {
            return null;
        }
        if (binding.has("RequiredModifiers")) {
            int mods = binding.get("RequiredModifiers").getAsInt();
            if ((mods & 1) != 0) {
                key = "Shift+" + key;
            }
            if ((mods & 2) != 0) {
                key = "Ctrl+" + key;
            }
            if ((mods & 4) != 0) {
                key = "Alt+" + key;
            }
        }
        return key;
    }

    @Nullable
    private static String scancodeToLabel(int scancode) {
        return switch (scancode) {
            case 4 -> "A";
            case 5 -> "B";
            case 6 -> "C";
            case 7 -> "D";
            case 8 -> "E";
            case 9 -> "F";
            case 10 -> "G";
            case 11 -> "H";
            case 12 -> "I";
            case 13 -> "J";
            case 14 -> "K";
            case 15 -> "L";
            case 16 -> "M";
            case 17 -> "N";
            case 18 -> "O";
            case 19 -> "P";
            case 20 -> "Q";
            case 21 -> "R";
            case 22 -> "S";
            case 23 -> "T";
            case 24 -> "U";
            case 25 -> "V";
            case 26 -> "W";
            case 27 -> "X";
            case 28 -> "Y";
            case 29 -> "Z";
            case 30 -> "1";
            case 31 -> "2";
            case 32 -> "3";
            case 33 -> "4";
            case 34 -> "5";
            case 35 -> "6";
            case 36 -> "7";
            case 37 -> "8";
            case 38 -> "9";
            case 39 -> "0";
            case 40 -> "Enter";
            case 41 -> "Esc";
            case 57 -> "Space";
            case 225 -> "Ctrl";
            case 224 -> "Shift";
            case 226 -> "Alt";
            default -> null;
        };
    }
}
