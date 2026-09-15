package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiInteractionKind;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Cosmetic work / leisure activity while a villager is in autonomy POI USE. */
public enum VillagerWorkActivity {
    MINE("Mine", "Pickaxe", "SFX_Tool_T1_Swing", "SFX_Stone_Hit", "Block_Hit_Stone", false, 1.0f),
    CHOP("Chop", "Hatchet", "SFX_Tool_T1_Swing", "SFX_Wood_Hit", "Block_Hit_Wood", false, 1.0f),
    WATER("Water", "Watering_Can", "SFX_Tool_Watering_Can_Water", null, "Watering_Can", false, 1.0f),
    TILL("Till", "Hoe", null, "SFX_Hoe_T1_Till", "Block_Build_Generic_Dust", false, 1.0f),
    /** Blacksmith forge: hammer swing + quiet metal clang. */
    SMITH("Mine", "Pickaxe", "SFX_Tool_T1_Swing", "SFX_Metal_Hit", "Block_Hit_Metal", false, 0.15f),
    /** Quiet busywork fidget at a desk / bench (no tool swings). */
    CRAFT(null, null, null, null, null, true, 1.0f),
    READ(null, null, null, null, null, true, 1.0f),
    SWEEP(null, null, null, null, null, true, 1.0f),
    INSPECT(null, null, null, null, null, true, 1.0f),
    TEND(null, null, null, null, null, true, 1.0f),
    LEISURE(null, null, null, null, null, true, 1.0f);

    public static final String TAG_PREFIX = "workActivity:";

    @Nullable
    private final String itemAnimationId;
    /** Fallback {@link com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations} id when held item has none. */
    @Nullable
    private final String fallbackItemAnimationsId;
    @Nullable
    private final String swingSoundEventId;
    @Nullable
    private final String hitSoundEventId;
    @Nullable
    private final String hitParticleSystemId;
    private final boolean leisure;
    /** 0–1 volume scale for swing/hit SFX. */
    private final float soundVolume;

    VillagerWorkActivity(
        @Nullable String itemAnimationId,
        @Nullable String fallbackItemAnimationsId,
        @Nullable String swingSoundEventId,
        @Nullable String hitSoundEventId,
        @Nullable String hitParticleSystemId,
        boolean leisure,
        float soundVolume
    ) {
        this.itemAnimationId = itemAnimationId;
        this.fallbackItemAnimationsId = fallbackItemAnimationsId;
        this.swingSoundEventId = swingSoundEventId;
        this.hitSoundEventId = hitSoundEventId;
        this.hitParticleSystemId = hitParticleSystemId;
        this.leisure = leisure;
        this.soundVolume = Math.max(0f, Math.min(1f, soundVolume));
    }

    @Nullable
    String itemAnimationId() {
        return itemAnimationId;
    }

    @Nullable
    String fallbackItemAnimationsId() {
        return fallbackItemAnimationsId;
    }

    @Nullable
    String swingSoundEventId() {
        return swingSoundEventId;
    }

    @Nullable
    String hitSoundEventId() {
        return hitSoundEventId;
    }

    @Nullable
    String hitParticleSystemId() {
        return hitParticleSystemId;
    }

    boolean isLeisure() {
        return leisure;
    }

    float soundVolume() {
        return soundVolume;
    }

    boolean playsToolAction() {
        return itemAnimationId != null && !itemAnimationId.isBlank();
    }

    @Nonnull
    static VillagerWorkActivity resolve(@Nonnull PoiEntry poi, @Nullable String bindingKind) {
        // Sleep / eat never drive tool swings (binding kind must not bleed onto beds).
        if (poi.getInteractionKind() == PoiInteractionKind.SLEEP || PoiScoring.isEatPoi(poi)) {
            return LEISURE;
        }
        VillagerWorkActivity tagged = fromTags(poi.getTags());
        if (!PoiScoring.isWorkPoi(poi)) {
            return tagged != null && tagged.isLeisure() ? tagged : LEISURE;
        }
        // Desk staff ignore tool-swing tags (legacy craft desks) so they stay quiet immediately.
        if (isDeskRoleBinding(bindingKind)) {
            // Older shops used read/craft as generic desk placeholders. Let the
            // resident's job supply those actions, including already saved POIs.
            if (tagged != null && tagged.isLeisure() && tagged != READ && tagged != CRAFT) return tagged;
            return switch (bindingKind.trim().toLowerCase(Locale.ROOT)) {
                case TownVillagerBinding.KIND_CHEF -> CRAFT;
                case TownVillagerBinding.KIND_FLORIST -> TEND;
                case TownVillagerBinding.KIND_INNKEEPER -> SWEEP;
                case TownVillagerBinding.KIND_MERCHANT, TownVillagerBinding.KIND_FURNITURE_MERCHANT,
                     TownVillagerBinding.KIND_CRYSTAL_KEEPER, TownVillagerBinding.KIND_PYROTECHNIC -> INSPECT;
                default -> READ;
            };
        }
        // Bard stands at the stage; no craft / tool overlays (even on legacy craft-tagged spots).
        if (isBardBinding(bindingKind)) {
            return LEISURE;
        }
        // Blacksmith always forges with the hammer, even on legacy craft-tagged desks.
        if (isBlacksmithBinding(bindingKind)) {
            return SMITH;
        }
        if (tagged != null) {
            return tagged;
        }
        // Role defaults only at real work spots — never from inn-pool visitor kinds.
        if (bindingKind != null && !TownVillagerBinding.isVisitorKind(bindingKind)) {
            String kind = bindingKind.trim().toLowerCase(Locale.ROOT);
            return switch (kind) {
                case TownVillagerBinding.KIND_MINER -> MINE;
                case TownVillagerBinding.KIND_LOGGER -> CHOP;
                case TownVillagerBinding.KIND_FARMER -> WATER;
                case TownVillagerBinding.KIND_RANCHER -> TEND;
                case TownVillagerBinding.KIND_BUILDER -> CRAFT;
                default -> LEISURE;
            };
        }
        return LEISURE;
    }

    /** Vary standing shop work without making seated readers sweep or replacing real tool work. */
    static VillagerWorkActivity chooseBeat(PoiEntry poi, String bindingKind, boolean mounted, double roll) {
        VillagerWorkActivity primary = resolve(poi, bindingKind);
        boolean seated = mounted || poi.getInteractionKind() == PoiInteractionKind.SIT;
        if (primary == SWEEP && seated) return READ;
        if (!PoiScoring.isWorkPoi(poi) || seated || !isShopkeeper(bindingKind)
            || poi.getInteractionKind() == PoiInteractionKind.SLEEP || PoiScoring.isEatPoi(poi)) return primary;
        VillagerWorkActivity explicit = fromTags(poi.getTags());
        if (explicit != null && explicit != READ && explicit != CRAFT) return primary;
        if (roll >= 0 && roll < .25) return primary == SWEEP ? INSPECT : SWEEP;
        if (roll >= .25 && roll < .4) return READ;
        return primary;
    }

    private static boolean isShopkeeper(String kind) {
        if (kind == null || TownVillagerBinding.isVisitorKind(kind)) return false;
        return switch (kind.trim().toLowerCase(Locale.ROOT)) {
            case TownVillagerBinding.KIND_INNKEEPER, TownVillagerBinding.KIND_MERCHANT,
                TownVillagerBinding.KIND_CHEF, TownVillagerBinding.KIND_FLORIST,
                TownVillagerBinding.KIND_FURNITURE_MERCHANT, TownVillagerBinding.KIND_PYROTECHNIC,
                TownVillagerBinding.KIND_CRYSTAL_KEEPER -> true;
            default -> false;
        };
    }

    private static boolean isBardBinding(@Nullable String bindingKind) {
        if (bindingKind == null || TownVillagerBinding.isVisitorKind(bindingKind)) {
            return false;
        }
        return TownVillagerBinding.KIND_BARD.equals(bindingKind.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean isBlacksmithBinding(@Nullable String bindingKind) {
        if (bindingKind == null || TownVillagerBinding.isVisitorKind(bindingKind)) {
            return false;
        }
        return TownVillagerBinding.KIND_BLACKSMITH.equals(bindingKind.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean isDeskRoleBinding(@Nullable String bindingKind) {
        if (bindingKind == null || TownVillagerBinding.isVisitorKind(bindingKind)) {
            return false;
        }
        return switch (bindingKind.trim().toLowerCase(Locale.ROOT)) {
            case TownVillagerBinding.KIND_INNKEEPER,
                TownVillagerBinding.KIND_GUILD_MASTER,
                TownVillagerBinding.KIND_ELDER,
                TownVillagerBinding.KIND_MERCHANT,
                TownVillagerBinding.KIND_CHEF,
                TownVillagerBinding.KIND_FLORIST,
                TownVillagerBinding.KIND_FURNITURE_MERCHANT,
                TownVillagerBinding.KIND_PYROTECHNIC,
                TownVillagerBinding.KIND_CRYSTAL_KEEPER,
                TownVillagerBinding.KIND_PRIESTESS -> true;
            default -> false;
        };
    }

    /**
     * Field producers rotate among several work spots on the same plot each USE window. Desk roles stay put.
     */
    public static boolean rotatesWorkStations(@Nullable String bindingKind) {
        if (bindingKind == null || TownVillagerBinding.isVisitorKind(bindingKind)) {
            return false;
        }
        return switch (bindingKind.trim().toLowerCase(Locale.ROOT)) {
            case TownVillagerBinding.KIND_FARMER,
                TownVillagerBinding.KIND_MINER,
                TownVillagerBinding.KIND_LOGGER,
                TownVillagerBinding.KIND_RANCHER -> true;
            default -> false;
        };
    }

    @Nullable
    private static VillagerWorkActivity fromTags(@Nonnull Set<String> tags) {
        for (String raw : tags) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String t = raw.trim();
            if (!t.regionMatches(true, 0, TAG_PREFIX, 0, TAG_PREFIX.length())) {
                continue;
            }
            String id = t.substring(TAG_PREFIX.length()).trim().toLowerCase(Locale.ROOT);
            return switch (id) {
                case "mine" -> MINE;
                case "chop" -> CHOP;
                case "water" -> WATER;
                case "till" -> TILL;
                case "smith", "forge", "anvil" -> SMITH;
                case "craft", "build" -> CRAFT;
                case "read" -> READ;
                case "sweep", "clean" -> SWEEP;
                case "inspect", "sort" -> INSPECT;
                case "tend" -> TEND;
                case "leisure", "fun" -> LEISURE;
                default -> null;
            };
        }
        return null;
    }
}
