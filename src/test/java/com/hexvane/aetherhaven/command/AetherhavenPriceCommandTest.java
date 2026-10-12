package com.hexvane.aetherhaven.command;

import static org.junit.jupiter.api.Assertions.*;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Who may run {@code /ah price}. The real command tree is built and asked with the engine's own
 * {@link AbstractCommand#hasPermission}, the check the command dispatcher makes before it runs anything.
 */
@Tag("town")
class AetherhavenPriceCommandTest {
    private static final String NODE = "aetherhaven.price.admin";

    private record Sender(String name, Predicate<String> holds) implements CommandSender {
        @Override public void sendMessage(Message message) {}
        @Override public String getUsername() { return name; }
        @Override public UUID getUuid() { return UUID.nameUUIDFromBytes(name.getBytes()); }
        @Override public boolean hasPermission(String id) { return holds.test(id); }
        @Override public boolean hasPermission(String id, boolean def) { return holds.test(id); }
    }

    /** Every command a typed {@code /ah price ...} can end at: the collection, its subcommands and their variants. */
    private static List<AbstractCommand> everyForm(AbstractCommand price) {
        List<AbstractCommand> out = new ArrayList<>();
        out.add(price);
        for (AbstractCommand sub : price.getSubCommands().values()) {
            out.add(sub);
            out.addAll(sub.getVariantCommands());
        }
        return out;
    }

    @Test void theWholeTreeNeedsItsOwnNodeAndOperatorsHoldIt() {
        assertEquals(NODE, AetherhavenConstants.PERMISSION_PRICE_ADMIN);
        var price = new AetherhavenPriceCommand();
        assertEquals(Set.of("set", "remove", "show", "list", "reload"), price.getSubCommands().keySet());
        var forms = everyForm(price);
        // price, five subcommands, and the "item in my hand" forms of set, remove and show.
        assertEquals(9, forms.size());

        // An ordinary player: whatever else they hold, without this node every form is refused.
        var player = new Sender("player", id -> !NODE.equals(id) && !"*".equals(id));
        // Staff given just this node, and nothing an operator has.
        var staff = new Sender("staff", NODE::equals);
        // An operator: the hytale:Admin group holds "*". The engine's own matcher decides whether that covers the node.
        var operator = new Sender("operator", id -> Boolean.TRUE.equals(PermissionsModule.hasPermission(Set.of("*"), id)));
        for (AbstractCommand form : forms) {
            String what = form.getName() == null ? "a held item form" : form.getName();
            assertEquals(NODE, form.getPermission(), what);
            assertEquals(List.of("hytale:Admin"), form.getPermissionGroups(), what);
            assertFalse(form.hasPermission(player), what + " must refuse a player without the node");
            assertTrue(form.hasPermission(staff), what + " must accept the node alone");
            assertTrue(form.hasPermission(operator), what + " must accept an operator");
        }
    }

    @Test void theNodeIsNotHandedToEveryPlayerThroughTheOpenRootCommand() {
        // /ah itself is open to the default group, and a subcommand that names no group takes its parent's.
        var root = new AbstractCommandCollection("ah", "root") {
            { setPermissionGroups("hytale:Adventurer"); requirePermission("test.root"); }
        };
        root.addSubCommand(new AetherhavenPriceCommand());
        var byGroup = root.getPermissionGroupsRecursive();
        assertEquals(Set.of("test.root"), byGroup.get("hytale:Adventurer"), "the default group must not receive the price node");
        assertEquals(Set.of(NODE), byGroup.get("hytale:Admin"));
        assertEquals(Set.of("hytale:Adventurer", "hytale:Admin"), byGroup.keySet());
    }

    @Test void theItemInHandFormsTakeOneArgumentFewerThanTheNamedForms() {
        var price = new AetherhavenPriceCommand();
        // set <item> <gold> and set <gold>; remove <item> and remove; show <item> and show.
        assertEquals(2, price.getSubCommand("set").getRequiredArguments().size());
        assertNotNull(price.getSubCommand("set").getVariantByArgCount(1));
        assertEquals(1, price.getSubCommand("remove").getRequiredArguments().size());
        assertNotNull(price.getSubCommand("remove").getVariantByArgCount(0));
        assertEquals(1, price.getSubCommand("show").getRequiredArguments().size());
        assertNotNull(price.getSubCommand("show").getVariantByArgCount(0));
        assertTrue(price.getSubCommand("set").getOptionalArguments().containsKey("per"));
        assertTrue(price.getSubCommand("list").getRequiredArguments().isEmpty());
        assertTrue(price.getSubCommand("reload").getRequiredArguments().isEmpty());
    }
}
