package com.hexvane.aetherhaven.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceBasePage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.inventory.ItemContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/** Reuses vanilla {@code Pages/ItemRepairPage.ui} with paid full restores (see {@link BlacksmithRepairInteraction}). */
public final class BlacksmithRepairPage extends ChoiceBasePage {
    public BlacksmithRepairPage(@Nonnull PlayerRef playerRef, @Nonnull ItemContainer itemContainer) {
        super(playerRef, buildElements(itemContainer), "Pages/ItemRepairPage.ui");
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        if (this.getElements().length > 0) {
            super.build(ref, commandBuilder, eventBuilder, store);
        } else {
            commandBuilder.append(this.getPageLayout());
            commandBuilder.clear("#ElementList");
            commandBuilder.appendInline("#ElementList", "Label { Text: %server.customUI.itemRepairPage.noItems; Style: (Alignment: Center); }");
        }
    }

    @Nonnull
    private static ChoiceElement[] buildElements(@Nonnull ItemContainer itemContainer) {
        List<ChoiceElement> elements = new ObjectArrayList<>();
        for (short slot = 0; slot < itemContainer.getCapacity(); slot++) {
            ItemStack stack = itemContainer.getItemStack(slot);
            if (!BlacksmithRepairInteraction.needsBlacksmithRepair(stack)) {
                continue;
            }
            ItemContext ctx = new ItemContext(itemContainer, slot, stack);
            elements.add(new BlacksmithRepairRowElement(stack, new BlacksmithRepairInteraction(ctx)));
        }
        return elements.toArray(ChoiceElement[]::new);
    }
}
