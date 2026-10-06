package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.jewelry.JewelryMetadata;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.util.concurrent.ThreadLocalRandom;

/** Rolls the declared grade only when attended work commits. An appraisal bench reveals rather than improves it. */
public final class JewelryWorkplaceOutput {
    private JewelryWorkplaceOutput() {}
    public static ItemStack finish(WorkplaceRecipe recipe,ItemStack planned,boolean appraise) {
        if(recipe.rarity()==null)return planned;
        if(!recipe.output().equals(planned.getItemId())||planned.getQuantity()!=1)return null;
        return JewelryMetadata.rollCrafted(planned.getItemId(),recipe.rarity(),appraise,ThreadLocalRandom.current());
    }
}
