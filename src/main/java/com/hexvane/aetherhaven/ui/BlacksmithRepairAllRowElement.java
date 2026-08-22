package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

/** Top row for fix all: label, total gold cost, and how many items would be restored. */
public final class BlacksmithRepairAllRowElement extends ChoiceElement {
    private final int totalCost;
    private final int itemCount;

    public BlacksmithRepairAllRowElement(int totalCost, int itemCount, @Nonnull BlacksmithRepairAllInteraction interaction) {
        this.totalCost = totalCost;
        this.itemCount = itemCount;
        this.interactions = new ChoiceInteraction[]{interaction};
    }

    @Override
    public void addButton(
        @Nonnull UICommandBuilder commandBuilder,
        UIEventBuilder eventBuilder,
        String selector,
        PlayerRef playerRef
    ) {
        commandBuilder.append("#ElementList", "Aetherhaven/BlacksmithRepairElement.ui");
        commandBuilder.set(selector + " #Icon.ItemId", AetherhavenConstants.ITEM_GOLD_COIN);
        commandBuilder.set(
            selector + " #Name.TextSpans",
            Message.translation("aetherhaven_misc.aetherhaven.blacksmith.repair.fixAll")
        );
        commandBuilder.set(
            selector + " #Cost.TextSpans",
            Message.translation("aetherhaven_misc.aetherhaven.blacksmith.repair.rowCost").param("cost", this.totalCost)
        );
        commandBuilder.set(
            selector + " #Durability.TextSpans",
            Message.translation("aetherhaven_misc.aetherhaven.blacksmith.repair.fixAll.count")
                .param("count", this.itemCount)
        );
    }
}
