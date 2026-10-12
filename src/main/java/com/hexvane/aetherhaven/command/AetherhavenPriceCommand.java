package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.shopspot.ShopPriceAdmin;
import com.hexvane.aetherhaven.shopspot.ShopPriceAdmin.Outcome;
import com.hexvane.aetherhaven.shopspot.ShopPriceAdmin.Result;
import com.hexvane.aetherhaven.shopspot.ShopPriceAdmin.Shown;
import com.hexvane.aetherhaven.shopspot.ShopPriceCatalog.Source;
import com.hexvane.aetherhaven.shopspot.ShopPriceEntry;
import com.hexvane.aetherhaven.shopspot.ShopPriceFiles;
import com.hexvane.aetherhaven.shopspot.ShopPriceLiveRefresh;
import com.hexvane.aetherhaven.shopspot.ShopPriceOverrides;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.ParseResult;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.SingleArgumentType;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.command.system.suggestion.SuggestionResult;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.permissions.provider.HytalePermissionsProvider;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * {@code /ah price}: set, change, remove and look up item prices on a running server.
 *
 * <pre>
 * /ah price set &lt;item&gt; &lt;gold&gt; [--per &lt;count&gt;]   set or change a price
 * /ah price set &lt;gold&gt; [--per &lt;count&gt;]          the same for the item in your hand
 * /ah price remove &lt;item&gt;                       back to the shipped price
 * /ah price remove                              the same for the item in your hand
 * /ah price show &lt;item&gt;                         the price and where it comes from
 * /ah price show                                the same for the item in your hand
 * /ah price list                                every price set on this server
 * /ah price reload                              read the price file again after a hand edit
 * </pre>
 *
 * Needs {@link AetherhavenConstants#PERMISSION_PRICE_ADMIN}, which operators hold. The forms that name an item also
 * work from the server console. Prices are kept in the server's own file, see {@link ShopPriceOverrides}; what a
 * changed price reaches is written on {@link ShopPriceAdmin}.
 */
public final class AetherhavenPriceCommand extends AbstractCommandCollection {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String LANG = "aetherhaven_shop.commands.aetherhaven.price.";

    public AetherhavenPriceCommand() {
        super("price", LANG + "desc");
        this.requirePermission(NODE);
        this.setPermissionGroups(OPERATORS);
        this.addSubCommand(new SetCommand());
        this.addSubCommand(new RemoveCommand());
        this.addSubCommand(new ShowCommand());
        this.addSubCommand(new ListCommand());
        this.addSubCommand(new ReloadCommand());
    }

    /**
     * One node guards the whole tree. Naming the operator group matters: a command that names no group takes its
     * parent's, and {@code /ah} itself is open to every player.
     */
    private static final String NODE = AetherhavenConstants.PERMISSION_PRICE_ADMIN;
    private static final String OPERATORS = HytalePermissionsProvider.GROUP_ADMIN;

    // ---- set

    private static final class SetCommand extends CommandBase {
        @Nonnull
        private final RequiredArg<Item> itemArg = this.withRequiredArg("item", LANG + "arg.item", ArgTypes.ITEM_ASSET);
        @Nonnull
        private final RequiredArg<Integer> goldArg = this.withRequiredArg("gold", LANG + "arg.gold", ArgTypes.INTEGER);
        @Nonnull
        private final OptionalArg<Integer> perArg = this.withOptionalArg("per", LANG + "arg.per", ArgTypes.INTEGER);

        SetCommand() {
            super("set", LANG + "set.desc");
            this.requirePermission(NODE);
            this.setPermissionGroups(OPERATORS);
            this.addUsageVariant(new SetHeldCommand());
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            Item item = itemArg.get(context);
            set(context, item, goldArg.get(context), perArg.provided(context) ? perArg.get(context) : null);
        }
    }

    private static final class SetHeldCommand extends AbstractPlayerCommand {
        @Nonnull
        private final RequiredArg<Integer> goldArg = this.withRequiredArg("gold", LANG + "arg.gold", ArgTypes.INTEGER);
        @Nonnull
        private final OptionalArg<Integer> perArg = this.withOptionalArg("per", LANG + "arg.per", ArgTypes.INTEGER);

        SetHeldCommand() {
            super(LANG + "set.hand.desc");
            this.requirePermission(NODE);
            this.setPermissionGroups(OPERATORS);
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            Item item = heldItem(context, store, ref);
            if (item != null) {
                set(context, item, goldArg.get(context), perArg.provided(context) ? perArg.get(context) : null);
            }
        }
    }

    private static void set(@Nonnull CommandContext context, @Nonnull Item item, int gold, @Nullable Integer per) {
        ShopPriceAdmin prices = prices(context);
        if (prices == null) {
            return;
        }
        Result result = prices.set(item.getId(), gold, per, item.getMaxStack());
        if (failed(context, result, item.getMaxStack())) {
            return;
        }
        Shown now = result.now();
        Shown before = result.before();
        context.sendMessage(
            Message.translation(LANG + (result.outcome() == Outcome.CHANGED ? "set.changed" : "set.done"))
                .param("item", name(item.getId()))
                .param("price", amount(now.price()))
                .param("before", amount(before.price()))
                .param("source", source(before.source()))
        );
        context.sendMessage(Message.translation(LANG + "set.note"));
        applied(context, "set " + item.getId() + " to " + plain(now.price()) + ", was " + plain(before.price()) + " (" + before.source() + ")");
    }

    // ---- remove

    private static final class RemoveCommand extends CommandBase {
        @Nonnull
        private final RequiredArg<String> itemArg = this.withRequiredArg("item", LANG + "arg.override", OVERRIDDEN_ITEM);

        RemoveCommand() {
            super("remove", LANG + "remove.desc");
            this.requirePermission(NODE);
            this.setPermissionGroups(OPERATORS);
            this.addUsageVariant(new RemoveHeldCommand());
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            remove(context, itemArg.get(context).trim());
        }
    }

    private static final class RemoveHeldCommand extends AbstractPlayerCommand {
        RemoveHeldCommand() {
            super(LANG + "remove.hand.desc");
            this.requirePermission(NODE);
            this.setPermissionGroups(OPERATORS);
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            Item item = heldItem(context, store, ref);
            if (item != null) {
                remove(context, item.getId());
            }
        }
    }

    private static void remove(@Nonnull CommandContext context, @Nonnull String itemId) {
        ShopPriceAdmin prices = prices(context);
        if (prices == null) {
            return;
        }
        Result result = prices.remove(itemId);
        if (failed(context, result, 1)) {
            return;
        }
        Shown now = result.now();
        if (result.outcome() == Outcome.NOT_OVERRIDDEN) {
            context.sendMessage(
                Message.translation(LANG + "remove.none")
                    .param("item", name(itemId))
                    .param("price", amount(now.price()))
                    .param("source", source(now.source()))
            );
            return;
        }
        context.sendMessage(
            Message.translation(LANG + "remove.done")
                .param("item", name(itemId))
                .param("price", amount(now.price()))
                .param("source", source(now.source()))
                .param("before", amount(result.before().price()))
        );
        context.sendMessage(Message.translation(LANG + "set.note"));
        applied(context, "removed the price of " + itemId + ", now " + plain(now.price()) + " (" + now.source() + "), was " + plain(result.before().price()));
    }

    // ---- show

    private static final class ShowCommand extends CommandBase {
        @Nonnull
        private final RequiredArg<Item> itemArg = this.withRequiredArg("item", LANG + "arg.item", ArgTypes.ITEM_ASSET);

        ShowCommand() {
            super("show", LANG + "show.desc");
            this.requirePermission(NODE);
            this.setPermissionGroups(OPERATORS);
            this.addUsageVariant(new ShowHeldCommand());
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            show(context, itemArg.get(context).getId());
        }
    }

    private static final class ShowHeldCommand extends AbstractPlayerCommand {
        ShowHeldCommand() {
            super(LANG + "show.hand.desc");
            this.requirePermission(NODE);
            this.setPermissionGroups(OPERATORS);
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            Item item = heldItem(context, store, ref);
            if (item != null) {
                show(context, item.getId());
            }
        }
    }

    private static void show(@Nonnull CommandContext context, @Nonnull String itemId) {
        ShopPriceAdmin prices = prices(context);
        if (prices == null) {
            return;
        }
        Shown shown = prices.show(itemId);
        context.sendMessage(
            Message.translation(LANG + "show.line")
                .param("item", name(itemId))
                .param("price", amount(shown.price()))
                .param("source", source(shown.source()))
        );
        if (shown.source() == Source.OVERRIDE) {
            context.sendMessage(
                shown.beneath() == null
                    ? Message.translation(LANG + "show.beneathNone")
                    : Message.translation(LANG + "show.beneath")
                        .param("price", amount(shown.beneath().entry()))
                        .param("source", source(shown.beneath().source()))
            );
        } else if (shown.source() == Source.DEFAULT) {
            context.sendMessage(Message.translation(LANG + "show.unpriced"));
        }
    }

    // ---- list, reload

    private static final class ListCommand extends CommandBase {
        ListCommand() {
            super("list", LANG + "list.desc");
            this.requirePermission(NODE);
            this.setPermissionGroups(OPERATORS);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            ShopPriceAdmin prices = prices(context);
            if (prices == null) {
                return;
            }
            Map<String, ShopPriceEntry> set = prices.list();
            if (set.isEmpty()) {
                context.sendMessage(Message.translation(LANG + "list.empty"));
                return;
            }
            context.sendMessage(Message.translation(LANG + "list.header").param("count", String.valueOf(set.size())));
            for (var e : set.entrySet()) {
                context.sendMessage(
                    Message.translation(LANG + "list.line").param("item", name(e.getKey())).param("price", amount(e.getValue()))
                );
            }
        }
    }

    private static final class ReloadCommand extends CommandBase {
        ReloadCommand() {
            super("reload", LANG + "reload.desc");
            this.requirePermission(NODE);
            this.setPermissionGroups(OPERATORS);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            ShopPriceAdmin prices = prices(context);
            if (prices == null) {
                return;
            }
            Result result = prices.reload();
            if (failed(context, result, 1)) {
                return;
            }
            int count = prices.list().size();
            context.sendMessage(Message.translation(LANG + "reload.done").param("count", String.valueOf(count)));
            applied(context, "read the price file again, " + count + " server price(s) in force");
        }
    }

    // ---- shared

    /** The price service over the live catalog, or null (after telling the sender) when the mod is not running. */
    @Nullable
    private static ShopPriceAdmin prices(@Nonnull CommandContext context) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            context.sendMessage(Message.translation("aetherhaven_commands_root.commands.aetherhaven.reload.noPlugin"));
            return null;
        }
        return admin(plugin);
    }

    /** The one way prices are changed on a server; the in-server check uses it too. */
    @Nonnull
    public static ShopPriceAdmin admin(@Nonnull AetherhavenPlugin plugin) {
        return new ShopPriceAdmin(ShopPriceFiles.pricesPath(plugin), plugin::getShopPriceCatalog, plugin::reloadShopPriceCatalog);
    }

    /** Tells the sender why nothing happened. True when the command should stop. */
    private static boolean failed(@Nonnull CommandContext context, @Nonnull Result result, int lotLimit) {
        switch (result.outcome()) {
            case REFUSED -> {
                ShopPriceOverrides.Refusal refusal = result.refusal();
                long max =
                    refusal == ShopPriceOverrides.Refusal.LOT_TOO_LARGE
                        ? Math.min(ShopPriceOverrides.MAX_LOT, Math.max(1, lotLimit))
                        : ShopPriceOverrides.MAX_GOLD;
                context.sendMessage(
                    Message.translation(LANG + "refused." + (refusal != null ? refusal.key() : "tooLow")).param("max", String.valueOf(max))
                );
                return true;
            }
            case FILE_UNREADABLE -> {
                context.sendMessage(Message.translation(LANG + "file.unreadable").param("detail", String.valueOf(result.detail())));
                return true;
            }
            case FILE_FAILED -> {
                context.sendMessage(Message.translation(LANG + "file.failed").param("detail", String.valueOf(result.detail())));
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** After a change that is now live: bring players' item tooltips up to date and leave a line in the log. */
    private static void applied(@Nonnull CommandContext context, @Nonnull String what) {
        ShopPriceLiveRefresh.afterPriceChange();
        CommandSender sender = context.sender();
        LOGGER.atInfo().log("Item prices: %s %s.", sender.getUsername(), what);
    }

    @Nullable
    private static Item heldItem(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        ItemStack held = InventoryComponent.getItemInHand(store, ref);
        Item item = ItemStack.isEmpty(held) ? null : Item.getAssetMap().getAsset(held.getItemId());
        if (item == null) {
            context.sendMessage(Message.translation(LANG + "hand.empty"));
        }
        return item;
    }

    /** "Copper Ingot (Ingredient_Bar_Copper)", or only the id for an item this server no longer has. */
    @Nonnull
    private static Message name(@Nonnull String itemId) {
        Item item = Item.getAssetMap().getAsset(itemId);
        if (item == null || item.getTranslationKey() == null || item.getTranslationKey().isBlank()) {
            return Message.raw(itemId);
        }
        return Message.translation(LANG + "item").param("name", Message.translation(item.getTranslationKey())).param("id", itemId);
    }

    @Nonnull
    private static Message amount(@Nonnull ShopPriceEntry price) {
        return price.isBatched()
            ? Message.translation(LANG + "amount.lot")
                .param("gold", String.valueOf(price.getGoldPerBatch()))
                .param("count", String.valueOf(price.getBatchSize()))
            : Message.translation(LANG + "amount.each").param("gold", String.valueOf(price.getGoldPerBatch()));
    }

    @Nonnull
    private static String plain(@Nonnull ShopPriceEntry price) {
        return price.isBatched()
            ? price.getGoldPerBatch() + " gold for " + price.getBatchSize()
            : price.getGoldPerBatch() + " gold each";
    }

    @Nonnull
    private static Message source(@Nonnull Source source) {
        return Message.translation(LANG + "source." + source.name());
    }

    /** An item id for {@code remove}: any text, so a price for an item whose mod was taken out can still be removed. */
    private static final SingleArgumentType<String> OVERRIDDEN_ITEM = new SingleArgumentType<>(
        LANG + "argtype.override.name",
        LANG + "argtype.override.usage",
        "Ingredient_Bar_Copper"
    ) {
        @Override
        public String parse(@Nonnull String input, @Nonnull ParseResult parseResult) {
            return input;
        }

        @Override
        public void suggest(
            @Nonnull CommandSender sender,
            @Nonnull String textAlreadyEntered,
            int numParametersTyped,
            @Nonnull SuggestionResult result
        ) {
            AetherhavenPlugin plugin = AetherhavenCommandSuggest.plugin();
            if (plugin != null) {
                AetherhavenCommandSuggest.suggestPrefix(result, textAlreadyEntered, plugin.getShopPriceCatalog().overrides().keySet());
            }
        }
    };
}
