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
 * Resolves display labels for vanilla {@link ToolKeybindSlot} binding ids in CustomUIHud rows.
 *
 * <p>{@code HotkeyLabel} only resolves remaps inside item Legend HUDs, not CustomUIHud. This helper
 * reads the local Hytale client {@code Settings.json} when present (singleplayer / integrated host)
 * and otherwise falls back to vanilla default labels.
 *
 * <p>Settings binding {@code SourceType}: {@code 0} keyboard, {@code 1} mouse, {@code 2} gamepad
 * (ignored for HUD text).
 */
public final class ToolKeybindDisplay {
    private static final int SOURCE_KEYBOARD = 0;
    private static final int SOURCE_MOUSE = 1;
    private static final int SOURCE_GAMEPAD = 2;

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
    static Map<String, String> parseSettingsOverrides(@Nonnull String json) {
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
            JsonObject binding = element.getAsJsonObject();
            int sourceType = binding.has("SourceType") ? binding.get("SourceType").getAsInt() : SOURCE_KEYBOARD;
            if (sourceType == SOURCE_GAMEPAD) {
                continue;
            }
            String label = formatBinding(binding, sourceType);
            if (label == null || label.isBlank()) {
                continue;
            }
            if (sourceType == SOURCE_KEYBOARD) {
                keyboard = label;
            } else if (sourceType == SOURCE_MOUSE && mouse == null) {
                mouse = label;
            }
        }
        if (keyboard != null) {
            return keyboard;
        }
        return mouse;
    }

    @Nullable
    private static String formatBinding(@Nonnull JsonObject binding, int sourceType) {
        if (sourceType == SOURCE_MOUSE) {
            int button = binding.has("MouseButton")
                ? binding.get("MouseButton").getAsInt()
                : binding.has("Button") ? binding.get("Button").getAsInt() : -1;
            return switch (button) {
                case 0 -> "LMB";
                case 1 -> "RMB";
                case 2 -> "MMB";
                default -> null;
            };
        }
        if (sourceType != SOURCE_KEYBOARD) {
            return null;
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
    static String scancodeToLabel(int scancode) {
        // SDL scancodes (same numbering Hytale Settings.json uses).
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
            case 42 -> "Backspace";
            case 43 -> "Tab";
            case 44 -> "Space";
            case 45 -> "-";
            case 46 -> "=";
            case 47 -> "[";
            case 48 -> "]";
            case 49 -> "\\";
            case 51 -> ";";
            case 52 -> "'";
            case 53 -> "`";
            case 54 -> ",";
            case 55 -> ".";
            case 56 -> "/";
            case 57 -> "Caps";
            case 58 -> "F1";
            case 59 -> "F2";
            case 60 -> "F3";
            case 61 -> "F4";
            case 62 -> "F5";
            case 63 -> "F6";
            case 64 -> "F7";
            case 65 -> "F8";
            case 66 -> "F9";
            case 67 -> "F10";
            case 68 -> "F11";
            case 69 -> "F12";
            case 73 -> "Insert";
            case 74 -> "Home";
            case 75 -> "PgUp";
            case 76 -> "Delete";
            case 77 -> "End";
            case 78 -> "PgDn";
            case 79 -> "Right";
            case 80 -> "Left";
            case 81 -> "Down";
            case 82 -> "Up";
            case 83 -> "NumLock";
            case 84 -> "Num/";
            case 85 -> "Num*";
            case 86 -> "Num-";
            case 87 -> "Num+";
            case 88 -> "NumEnter";
            case 89 -> "Num1";
            case 90 -> "Num2";
            case 91 -> "Num3";
            case 92 -> "Num4";
            case 93 -> "Num5";
            case 94 -> "Num6";
            case 95 -> "Num7";
            case 96 -> "Num8";
            case 97 -> "Num9";
            case 98 -> "Num0";
            case 99 -> "Num.";
            case 224 -> "LCtrl";
            case 225 -> "LShift";
            case 226 -> "LAlt";
            case 228 -> "RCtrl";
            case 229 -> "RShift";
            case 230 -> "RAlt";
            default -> null;
        };
    }
}
