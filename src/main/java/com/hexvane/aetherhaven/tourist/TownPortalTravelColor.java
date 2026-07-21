package com.hexvane.aetherhaven.tourist;

import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.server.core.ui.PatchStyle;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Town colors and teleport icon tinting for the visitor portal travel UI. */
public final class TownPortalTravelColor {
    /** Main mod asset ({@code src/main/resources/Common/UI/Custom/teleport.png}); do not copy into Commerce subplugin packs. */
    public static final String TELEPORT_ICON_ASSET = "UI/Custom/teleport.png";

    /** Preset portal colors players can choose (no free-form hex entry). */
    @Nonnull
    public static final String[] PRESET_HEX = {
        "#E85D5D",
        "#E8985D",
        "#E8C85D",
        "#9AD45D",
        "#5DC985",
        "#5DC9B8",
        "#5DA8E8",
        "#6D5DE8",
        "#B85DE8",
        "#E85DA8",
        "#C9A882",
        "#8A8A98",
        "#4A6B42",
        "#5C4A72",
        "#3D4F6A",
        "#D4AF37",
    };

    private TownPortalTravelColor() {}

    @Nullable
    public static String getStoredHex(@Nonnull TownRecord town) {
        String raw = town.getVisitorPortalNetworkColor();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return normalizeHex(raw.trim());
    }

    /** Display color: saved preset, or a stable default from the town id. */
    @Nonnull
    public static String resolveHex(@Nonnull TownRecord town) {
        String stored = getStoredHex(town);
        if (stored != null) {
            return stored;
        }
        return defaultHexForTownId(town.getTownId());
    }

    /** Hex used to highlight the preset grid (snaps unknown stored values to nearest preset). */
    @Nonnull
    public static String normalizePresetHex(@Nonnull String hex) {
        String n = normalizeHex(hex);
        if (n != null && isPreset(n)) {
            return n;
        }
        if (n != null) {
            return nearestPreset(n);
        }
        return PRESET_HEX[0];
    }

    public static boolean isPreset(@Nonnull String hex) {
        String n = normalizeHex(hex);
        if (n == null) {
            return false;
        }
        for (String preset : PRESET_HEX) {
            if (preset.equalsIgnoreCase(n)) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    public static String nearestPreset(@Nonnull String hex) {
        String n = normalizeHex(hex);
        if (n == null) {
            return PRESET_HEX[0];
        }
        int target = hexToRgb(n);
        String best = PRESET_HEX[0];
        int bestDist = Integer.MAX_VALUE;
        for (String preset : PRESET_HEX) {
            int d = colorDistance(target, hexToRgb(preset));
            if (d < bestDist) {
                bestDist = d;
                best = preset;
            }
        }
        return best;
    }

    private static int colorDistance(int a, int b) {
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int dr = ar - br;
        int dg = ag - bg;
        int db = ab - bb;
        return dr * dr + dg * dg + db * db;
    }

    @Nonnull
    public static String defaultHexForTownId(@Nonnull UUID townId) {
        int hash = townId.hashCode();
        int r = 0x60 + (hash & 0x7F);
        int g = 0x90 + ((hash >> 8) & 0x5F);
        int b = 0xC0 + ((hash >> 16) & 0x3F);
        return String.format("#%02X%02X%02X", r, g, b);
    }

    /** Map tile pixels: R bits 24–31, G 16–23, B 8–15, A 0–7 (see {@link com.hexvane.aetherhaven.map.TownBorderMapRenderer}). */
    public static int toOpaqueArgb(@Nonnull TownRecord town) {
        return mapPixelColor(resolveHex(town));
    }

    public static int mapPixelColor(@Nonnull String hex) {
        String color = normalizeHex(hex);
        if (color == null) {
            color = "#FFFFFF";
        }
        int r = Integer.parseInt(color.substring(1, 3), 16);
        int g = Integer.parseInt(color.substring(3, 5), 16);
        int b = Integer.parseInt(color.substring(5, 7), 16);
        return (r << 24) | (g << 16) | (b << 8) | 0xFF;
    }

    public static void applyStoredHex(@Nonnull TownRecord town, @Nullable String pickerHex) {
        if (pickerHex == null || pickerHex.isBlank()) {
            town.setVisitorPortalNetworkColor(null);
            return;
        }
        String normalized = normalizeHex(pickerHex.trim());
        if (normalized == null || !isPreset(normalized)) {
            return;
        }
        town.setVisitorPortalNetworkColor(normalized);
    }

    /**
     * Tint for {@code TextButton} icons whose texture is declared in {@code .ui} (see {@code @PortalTeleportTintIconStyle}).
     * Do not build {@link PatchStyle} with {@link #TELEPORT_ICON_ASSET} from Java — that path fails to resolve and shows
     * a tintable missing-texture placeholder. Only override {@code Style.*.Background.Color} (same as villager rescue).
     */
    public static void applyTeleportIconTint(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull String textButtonSelector,
        @Nonnull String hexColor
    ) {
        String color = normalizeHex(hexColor);
        if (color == null) {
            color = "#FFFFFF";
        }
        commandBuilder.set(textButtonSelector + ".Visible", true);
        commandBuilder.set(textButtonSelector + ".Style.Default.Background.Color", color);
        commandBuilder.set(textButtonSelector + ".Style.Hovered.Background.Color", color);
        commandBuilder.set(textButtonSelector + ".Style.Pressed.Background.Color", color);
        commandBuilder.set(textButtonSelector + ".Style.Disabled.Background.Color", color);
    }

    @Nonnull
    public static PatchStyle solidColorPatch(@Nonnull String hex) {
        String color = normalizeHex(hex);
        if (color == null) {
            color = "#888888";
        }
        return new PatchStyle().setColor(Value.of(color));
    }

    @Nullable
    static String normalizeHex(@Nonnull String raw) {
        String s = raw.startsWith("#") ? raw : "#" + raw;
        if (s.length() != 7 && s.length() != 9) {
            return null;
        }
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f') && (c < 'A' || c > 'F')) {
                return null;
            }
        }
        return s.length() == 7 ? s.toUpperCase() : s.substring(0, 7).toUpperCase();
    }

    private static int hexToRgb(@Nonnull String hex) {
        String s = normalizeHex(hex);
        if (s == null) {
            return 0xFFFFFF;
        }
        int r = Integer.parseInt(s.substring(1, 3), 16);
        int g = Integer.parseInt(s.substring(3, 5), 16);
        int b = Integer.parseInt(s.substring(5, 7), 16);
        return (r << 16) | (g << 8) | b;
    }
}
