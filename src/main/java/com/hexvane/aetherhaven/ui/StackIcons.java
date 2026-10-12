package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.blockpalette.BlockPaletteConstants;
import com.hexvane.aetherhaven.blockpalette.BlockPaletteIconResolver;
import com.hexvane.aetherhaven.blockpalette.BlockPaletteItemMetadata;
import com.hexvane.aetherhaven.jewelry.AetherhavenBsonCodecs;
import com.hexvane.aetherhaven.plot.PlotTokenMetadata;
import com.hexvane.aetherhaven.prop.PropItemMetadata;
import com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Pictures and names for stacks whose look comes from what they carry instead of from their item: plot tokens, props
 * and block palettes. The player's own inventory swaps a per-stack item in for these. A custom page cannot, so it
 * draws the same picture file that item would have used.
 */
public final class StackIcons {
    /** A page redraws on every click. Looking a picture up touches the disk, so an answer is kept for a short while. */
    private static final long REMEMBER_MS = 15_000;
    private static final int REMEMBER_MOST = 512;
    private record Remembered(@Nullable String path, long until) {}
    private static final ConcurrentHashMap<String, Remembered> REMEMBERED = new ConcurrentHashMap<>();

    private StackIcons() {}

    /** The picture file for this stack, or null when its item's ordinary icon is the right one. */
    @Nullable
    public static String assetPath(@Nullable ItemStack stack) {
        String subject = subject(stack);
        if (subject == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        Remembered known = REMEMBERED.get(subject);
        if (known != null && known.until() > now) {
            return known.path();
        }
        String path = assetPath(stack, AetherhavenPlugin.get(), CommonAssetRegistry::hasCommonAsset);
        if (REMEMBERED.size() >= REMEMBER_MOST) {
            REMEMBERED.clear();
        }
        REMEMBERED.put(subject, new Remembered(path, now + REMEMBER_MS));
        return path;
    }

    /** What the picture depends on: the kind of stack and the thing it stands for. Null for ordinary items. */
    @Nullable
    private static String subject(@Nullable ItemStack stack) {
        if (ItemStack.isEmpty(stack)) {
            return null;
        }
        String itemId = stack.getItemId();
        String of;
        if (AetherhavenConstants.PLOT_TOKEN_UNIFIED.equals(itemId)) {
            of = PlotTokenMetadata.readConstructionId(stack);
        } else if (PropItemMetadata.PROP_ITEM_ID.equals(itemId)) {
            of = PropItemMetadata.readPropId(stack);
        } else if (BlockPaletteConstants.ITEM_ID.equals(itemId)) {
            of = BlockPaletteItemMetadata.readPaletteId(stack);
        } else {
            return null;
        }
        return of == null ? null : itemId + "|" + of;
    }

    /**
     * @param sent whether the client has been given a picture file. A path it lacks would draw as a blank square, so
     *             the item's own icon is kept instead.
     */
    @Nullable
    static String assetPath(@Nullable ItemStack stack, @Nullable AetherhavenPlugin plugin, @Nonnull Predicate<String> sent) {
        if (ItemStack.isEmpty(stack) || plugin == null) {
            return null;
        }
        Path data = plugin.getDataDirectory();
        String path = null;
        String itemId = stack.getItemId();
        if (AetherhavenConstants.PLOT_TOKEN_UNIFIED.equals(itemId)) {
            String construction = PlotTokenMetadata.readConstructionId(stack);
            // The same choice the token's inventory icon makes: no picture of its own keeps the plain token.
            if (construction != null && ConstructionTokenIconPath.isIconAvailable(construction, data)) {
                path = ConstructionTokenIconPath.forConstructionId(construction, data);
            }
        } else if (PropItemMetadata.PROP_ITEM_ID.equals(itemId)) {
            String prop = PropItemMetadata.readPropId(stack);
            if (prop != null && PropIconPath.isIconAvailable(prop, data)) {
                PropIconPath.registerRuntimeIconIfPresent(plugin, prop);
                path = PropIconPath.forPropId(prop, data);
            }
        } else if (BlockPaletteConstants.ITEM_ID.equals(itemId)) {
            String palette = BlockPaletteItemMetadata.readPaletteId(stack);
            var definition = palette == null ? null : plugin.getBlockPaletteCatalog().get(palette);
            if (definition != null) {
                path = BlockPaletteIconResolver.resolveIconAssetPath(definition);
            }
        }
        return path != null && !path.isBlank() && sent.test(path) ? path : null;
    }

    /** The name written on this stack itself, as its inventory tooltip shows it, or null to use the item's name. */
    @Nullable
    public static String instanceName(@Nullable ItemStack stack) {
        if (ItemStack.isEmpty(stack)) {
            return null;
        }
        try {
            var properties = stack.getFromMetadataOrNull(PropItemMetadata.INSTANCE_TRANSLATION_PROPERTIES_KEY, AetherhavenBsonCodecs.BSON_DOCUMENT);
            var name = properties == null ? null : properties.get("Name");
            return name != null && name.isString() && !name.asString().getValue().isBlank() ? name.asString().getValue().trim() : null;
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    /**
     * Fills a slot that has an {@code ItemIcon #Icon} and an {@code AssetImage #Art}: the stack's own picture when it
     * has one, otherwise its item icon.
     *
     * @param at selector of the slot, without a trailing space
     */
    public static void show(@Nonnull UICommandBuilder commands, @Nonnull String at, @Nonnull ItemStack stack) {
        String art = assetPath(stack);
        commands.set(at + " #Art.Visible", art != null);
        commands.set(at + " #Icon.Visible", art == null);
        if (art != null) {
            commands.set(at + " #Art.AssetPath", art);
        } else {
            commands.set(at + " #Icon.ItemId", stack.getItemId());
        }
    }
}
