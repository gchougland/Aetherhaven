package com.hexvane.aetherhaven.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceBasePage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.inventory.ItemContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/** Paid full restores (see {@link BlacksmithRepairInteraction}); shows gold cost per item. */
public final class BlacksmithRepairPage extends ChoiceBasePage {
    private static final String PAGE_LAYOUT = "Aetherhaven/BlacksmithRepairPage.ui";

    public BlacksmithRepairPage(@Nonnull PlayerRef playerRef, @Nonnull ItemContainer itemContainer) {
        super(playerRef, buildElements(itemContainer), PAGE_LAYOUT);
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        commandBuilder.append(getPageLayout());
        AetherhavenUiLocalization.applyBlacksmithRepairPage(commandBuilder);
        commandBuilder.clear("#ElementList");

        ChoiceElement[] elements = getElements();
        if (elements == null || elements.length == 0) {
            commandBuilder.appendInline(
                "#ElementList",
                "Label { Text: %server.customUI.itemRepairPage.noItems; Style: (Alignment: Center); }"
            );
            return;
        }

        for (int i = 0; i < elements.length; i++) {
            String selector = "#ElementList[" + i + "]";
            ChoiceElement element = elements[i];
            element.addButton(commandBuilder, eventBuilder, selector, playerRef);
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector,
                EventData.of("Index", Integer.toString(i)),
                false
            );
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
