package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.economy.api.AetherhavenEconomy;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

/** Row layout for {@code Aetherhaven/BlacksmithRepairElement.ui}. */
public final class BlacksmithRepairRowElement extends ChoiceElement {
    /** FontSize of the page's labels ($C.@DefaultLabelStyle), the line a price is drawn in. */
    static final int FONT_SIZE = 16;

    private final ItemStack itemStack;

    public BlacksmithRepairRowElement(@Nonnull ItemStack itemStack, @Nonnull BlacksmithRepairInteraction interaction) {
        this.itemStack = itemStack;
        this.interactions = new ChoiceInteraction[]{interaction};
    }

    @Override
    public void addButton(
        @Nonnull UICommandBuilder commandBuilder,
        UIEventBuilder eventBuilder,
        String selector,
        PlayerRef playerRef
    ) {
        int durabilityPercentage = (int) Math.round(this.itemStack.getDurability() / this.itemStack.getMaxDurability() * 100.0);
        int cost = BlacksmithRepairInteraction.goldCost(this.itemStack, AetherhavenConstants.BLACKSMITH_REPAIR_COST_FULL);
        commandBuilder.append("#ElementList", "Aetherhaven/BlacksmithRepairElement.ui");
        commandBuilder.set(selector + " #Icon.ItemId", this.itemStack.getItemId().toString());
        commandBuilder.set(selector + " #Name.TextSpans", Message.translation(this.itemStack.getItem().getTranslationKey()));
        AetherhavenEconomy.show(commandBuilder, selector + " #Cost #Gold", cost, FONT_SIZE);
        commandBuilder.set(selector + " #Durability.Text", durabilityPercentage + "%");
    }
}
