package com.hexvane.aetherhaven.tourist;

import com.hexvane.aetherhaven.AetherhavenConstants;
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

    public static final int PRESET_COUNT = PRESET_HEX.length;

    private static final String BLOCK_VARIANT_PREFIX = AetherhavenConstants.TOURIST_PORTAL_BLOCK_TYPE_ID + "_C";
    private static final String IDLE_PARTICLE_PREFIX = AetherhavenConstants.TOURIST_PORTAL_IDLE_PARTICLE + "_C";
    private static final String BURST_PARTICLE_PREFIX = AetherhavenConstants.TOURIST_PORTAL_SPAWN_BURST_PARTICLE + "_C";

    private TownPortalTravelColor() {}

    /** Preset index 0–15 for the town's resolved portal color. */
    public static int presetIndexForTown(@Nonnull TownRecord town) {
        String preset = normalizePresetHex(resolveHex(town));
        for (int i = 0; i < PRESET_HEX.length; i++) {
            if (PRESET_HEX[i].equalsIgnoreCase(preset)) {
                return i;
            }
        }
        return 0;
    }

    @Nonnull
    public static String blockTypeIdForTown(@Nonnull TownRecord town) {
        return blockTypeIdForPresetIndex(presetIndexForTown(town));
    }

    @Nonnull
    public static String blockTypeIdForPresetIndex(int index) {
        return BLOCK_VARIANT_PREFIX + formatVariantSuffix(index);
    }

    @Nonnull
    public static String idleParticleSystemIdForTown(@Nonnull TownRecord town) {
        return idleParticleSystemIdForPresetIndex(presetIndexForTown(town));
    }

    @Nonnull
    public static String burstParticleSystemIdForTown(@Nonnull TownRecord town) {
        return burstParticleSystemIdForPresetIndex(presetIndexForTown(town));
    }

    @Nonnull
    public static String idleParticleSystemIdForPresetIndex(int index) {
        return IDLE_PARTICLE_PREFIX + formatVariantSuffix(index);
    }

    @Nonnull
    public static String burstParticleSystemIdForPresetIndex(int index) {
        return BURST_PARTICLE_PREFIX + formatVariantSuffix(index);
    }

    public static boolean isTouristPortalBlockTypeId(@Nullable String blockTypeId) {
        if (blockTypeId == null || blockTypeId.isBlank()) {
            return false;
        }
        if (AetherhavenConstants.TOURIST_PORTAL_BLOCK_TYPE_ID.equals(blockTypeId)) {
            return true;
        }
        if (!blockTypeId.startsWith(BLOCK_VARIANT_PREFIX)) {
            return false;
        }
        String suffix = blockTypeId.substring(BLOCK_VARIANT_PREFIX.length());
        if (suffix.length() != 2) {
            return false;
        }
        for (int i = 0; i < 2; i++) {
            char c = suffix.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        int variant = Integer.parseInt(suffix);
        return variant >= 1 && variant <= PRESET_COUNT;
    }

    @Nonnull
    private static String formatVariantSuffix(int index) {
        int clamped = Math.max(0, Math.min(index, PRESET_COUNT - 1));
        return String.format("%02d", clamped + 1);
    }

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
