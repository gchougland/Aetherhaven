package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.festival.NewLifeFestivalMechanic;
import com.hexvane.aetherhaven.festival.carnival.CarnivalIds;
import com.hexvane.aetherhaven.festival.pigrace.PigRaceLanes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Known festival merchant / stand NPC roles authors can place in the festival wizard. */
public final class PlotCreatorFestivalNpcRoles {
    public static final String SEED_SELLER = "Aetherhaven_Festival_Seed_Seller";
    public static final String PIG_RACE_MERCHANT = "Aetherhaven_Festival_Pig_Race_Merchant";
    public static final String CARNIVAL_MERCHANT = CarnivalIds.MERCHANT_NPC_ROLE;
    public static final String CARNIVAL_BALLOON = CarnivalIds.BALLOON_NPC_ROLE;
    public static final String CARNIVAL_WHEEL = CarnivalIds.WHEEL_NPC_ROLE;

    private PlotCreatorFestivalNpcRoles() {}

    @Nonnull
    public static List<String> choosableRoleIds() {
        return List.of(SEED_SELLER, PIG_RACE_MERCHANT, CARNIVAL_MERCHANT, CARNIVAL_BALLOON, CARNIVAL_WHEEL);
    }

    @Nullable
    public static String defaultMerchantForMechanic(@Nullable String mechanicId) {
        if (mechanicId == null || mechanicId.isBlank()) {
            return null;
        }
        String key = mechanicId.trim().toLowerCase(Locale.ROOT);
        if (NewLifeFestivalMechanic.MECHANIC_ID.equals(key)) {
            return SEED_SELLER;
        }
        if (PigRaceLanes.MECHANIC_ID.equals(key)) {
            return PIG_RACE_MERCHANT;
        }
        if (CarnivalIds.MECHANIC_ID.equals(key)) {
            return CARNIVAL_MERCHANT;
        }
        return null;
    }

    @Nonnull
    public static List<String> defaultRacePigRoleIds() {
        List<String> out = new ArrayList<>();
        for (PigRaceLanes.Lane lane : PigRaceLanes.defaultLanes()) {
            out.add(lane.npcRoleId());
        }
        return out;
    }

    @Nonnull
    public static String labelLangSuffix(@Nonnull String npcRoleId) {
        String id = npcRoleId.trim();
        if (SEED_SELLER.equals(id)) {
            return "seedSeller";
        }
        if (PIG_RACE_MERCHANT.equals(id)) {
            return "pigRaceMerchant";
        }
        if (CARNIVAL_MERCHANT.equals(id)) {
            return "carnivalMerchant";
        }
        if (CARNIVAL_BALLOON.equals(id)) {
            return "carnivalBalloon";
        }
        if (CARNIVAL_WHEEL.equals(id)) {
            return "carnivalWheel";
        }
        return "generic";
    }

    @Nonnull
    public static LinkedHashSet<String> mergeWithDraft(@Nonnull PlotCreatorDraft draft) {
        LinkedHashSet<String> out = new LinkedHashSet<>(choosableRoleIds());
        for (var npc : draft.getFestivalNpcs()) {
            if (!npc.getNpcRoleId().isEmpty()) {
                out.add(npc.getNpcRoleId());
            }
        }
        String mechanicDefault = defaultMerchantForMechanic(draft.getFestivalMechanicId());
        if (mechanicDefault != null) {
            out.add(mechanicDefault);
        }
        return out;
    }
}
