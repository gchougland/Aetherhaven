package com.hexvane.aetherhaven.monument;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plot.FounderMonumentBlock;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemArmor;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * The screen opened by using a founder statue or its pedestal: four armor slots that take real armor from the
 * player's pack and give it back, and the statue's pose. Only players who may manage the town's buildings can use it.
 */
public final class FounderStatuePage extends AetherhavenInteractiveCustomUIPage<FounderStatuePage.Data> {
    /** Page id named by the pedestal block and by the statue's own Use interaction. */
    public static final String PAGE_ID = "AetherhavenFounderStatue";
    static final String LANG = "aetherhaven_monument.aetherhaven.monument.";
    /** Pieces from the pack shown under one slot. More than this are counted in the line above them. */
    static final int MAX_CHOICES = 4;
    /** A player who walks further than this from the pedestal can no longer change the statue. */
    private static final double REACH = 8.0;

    private final int x;
    private final int y;
    private final int z;
    private boolean appended;
    @Nullable
    private Message hint;

    /** A piece of armor in the player's pack that fits a statue slot. */
    record Choice(short packSlot, @Nonnull String itemId) {}

    private record Context(
        @Nonnull World world,
        @Nonnull Ref<ChunkStore> blockRef,
        @Nonnull FounderMonumentBlock block,
        @Nonnull ItemContainer pack
    ) {}

    public FounderStatuePage(@Nonnull PlayerRef player, int x, int y, int z) {
        super(player, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static void registerPageSupplier(@Nonnull PluginBase core) {
        OpenCustomUIInteraction.registerCustomPageSupplier(
            core,
            FounderStatuePage.class,
            PAGE_ID,
            (ref, componentAccessor, playerRef, context) -> open(ref, playerRef, context)
        );
    }

    /** Same rule as the town's other building screens: a member who may manage constructions. */
    static boolean canManage(@Nullable TownRecord town, @Nonnull UUID player) {
        return town != null && town.hasMemberOrOwner(player) && town.playerCanManageConstructions(player);
    }

    /** Which statue slot an item is armor for, or null when it is not armor. */
    @Nullable
    static FounderStatueLoadout.Slot slotOf(@Nullable ItemStack stack) {
        Item item = known(stack);
        ItemArmor armor = item != null ? item.getArmor() : null;
        return armor != null && armor.getArmorSlot() != null
            ? FounderStatueLoadout.Slot.byKey(armor.getArmorSlot().name())
            : null;
    }

    /** The item asset of a stack, or null for an empty stack or an item this game no longer has. */
    @Nullable
    static Item known(@Nullable ItemStack stack) {
        return ItemStack.isEmpty(stack) ? null : Item.getAssetMap().getAsset(stack.getItemId());
    }

    /** Armor in the pack that fits a slot, in pack order. Armor the player is wearing is never offered. */
    @Nonnull
    static List<Choice> choices(@Nonnull ItemContainer pack, @Nonnull FounderStatueLoadout.Slot slot) {
        List<Choice> out = new ArrayList<>();
        pack.forEach((index, stack) -> {
            if (slotOf(stack) == slot) {
                out.add(new Choice(index, stack.getItemId()));
            }
        });
        return out;
    }

    @Nullable
    private static CustomUIPage open(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull InteractionContext context
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null || !FounderMonumentBlock.isRegistered()) {
            return null;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        Vector3i pedestal = pedestalOf(world, store, context);
        if (pedestal == null) {
            return null;
        }
        Ref<ChunkStore> blockRef = ChunkSectionBlockUtil.blockEntityRefAt(world, pedestal.x, pedestal.y, pedestal.z);
        FounderMonumentBlock block = monumentAt(blockRef);
        if (blockRef == null || block == null) {
            return null;
        }
        TownRecord town = townOf(world, plugin, block, pedestal.x, pedestal.z);
        if (town == null) {
            // Nobody manages a statue whose town is gone, so whatever it wears is set down for the player to take.
            boolean released = FounderMonumentStatueRestoreSystem.releaseArmor(world, blockRef);
            playerRef.sendMessage(Message.translation(LANG + (released ? "townGoneArmor" : "townGone")));
            return null;
        }
        if (!canManage(town, playerRef.getUuid())) {
            playerRef.sendMessage(Message.translation(LANG + "noPermission"));
            return null;
        }
        return new FounderStatuePage(playerRef, pedestal.x, pedestal.y, pedestal.z);
    }

    /** The pedestal the player used, whether they aimed at the block or at the statue standing on it. */
    @Nullable
    private static Vector3i pedestalOf(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull InteractionContext context
    ) {
        BlockPosition target = context.getTargetBlock();
        if (target != null
            && monumentAt(ChunkSectionBlockUtil.blockEntityRefAt(world, target.x, target.y, target.z)) != null) {
            return new Vector3i(target.x, target.y, target.z);
        }
        Ref<EntityStore> statue = context.getTargetEntity();
        if (statue == null || !statue.isValid()
            || store.getComponent(statue, FounderMonumentStatueSkin.getComponentType()) == null) {
            return null;
        }
        TransformComponent at = store.getComponent(statue, TransformComponent.getComponentType());
        if (at == null) {
            return null;
        }
        Vector3d feet = at.getPosition();
        Ref<ChunkStore> blockRef = FounderMonumentSpawnService.findMonumentBlockNear(world, feet.x, feet.y, feet.z);
        if (blockRef == null || !blockRef.isValid()) {
            return null;
        }
        BlockModule.BlockStateInfo info =
            blockRef.getStore().getComponent(blockRef, BlockModule.BlockStateInfo.getComponentType());
        Vector3i pos = new Vector3i();
        return info != null && info.fillWorldPos(blockRef.getStore(), pos) ? pos : null;
    }

    @Nullable
    private static FounderMonumentBlock monumentAt(@Nullable Ref<ChunkStore> blockRef) {
        return blockRef != null && blockRef.isValid()
            ? blockRef.getStore().getComponent(blockRef, FounderMonumentBlock.getComponentType())
            : null;
    }

    /** The town a pedestal belongs to: the one saved on it, or for older pedestals the town whose land it stands on. */
    @Nullable
    private static TownRecord townOf(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull FounderMonumentBlock block,
        int blockX,
        int blockZ
    ) {
        TownManager towns = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        String saved = block.getTownId().trim();
        if (!saved.isEmpty()) {
            try {
                return towns.getTown(UUID.fromString(saved));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return towns.findTownContainingBlock(world.getName(), blockX, blockZ);
    }

    @Nullable
    private Context current(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
        TransformComponent at = store.getComponent(ref, TransformComponent.getComponentType());
        if (plugin == null || player == null || at == null) {
            return null;
        }
        World world = store.getExternalData().getWorld();
        Ref<ChunkStore> blockRef = ChunkSectionBlockUtil.blockEntityRefAt(world, x, y, z);
        FounderMonumentBlock block = monumentAt(blockRef);
        if (blockRef == null || block == null || !canManage(townOf(world, plugin, block, x, z), player.getUuid())) {
            return null;
        }
        Vector3d p = at.getPosition();
        double dx = p.x - (x + 0.5);
        double dy = p.y - (y + 0.5);
        double dz = p.z - (z + 0.5);
        if (dx * dx + dy * dy + dz * dz > REACH * REACH) {
            return null;
        }
        ItemContainer pack = InventoryComponent.getCombined(store, ref, InventoryComponent.BACKPACK_STORAGE_HOTBAR);
        return pack != null ? new Context(world, blockRef, block, pack) : null;
    }

    @Nonnull
    private static Message itemName(@Nullable ItemStack stack) {
        if (ItemStack.isEmpty(stack)) {
            return Message.translation(LANG + "empty");
        }
        Item item = known(stack);
        String key = item != null ? item.getTranslationKey() : null;
        return key != null && !key.isBlank() ? Message.translation(key) : Message.translation(LANG + "unknownPiece");
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder b,
        @Nonnull UIEventBuilder e,
        @Nonnull Store<EntityStore> store
    ) {
        Context c = current(ref, store);
        if (c == null) {
            close();
            return;
        }
        FounderStatueLoadout loadout = c.block().getLoadout();
        if (!appended) {
            appended = true;
            b.append("Aetherhaven/FounderStatuePage.ui");
            b.set("#TitleText.TextSpans", Message.translation(LANG + "title"));
            b.set("#ArmorHeading.TextSpans", Message.translation(LANG + "armorHeading"));
            b.set("#PoseHeading.TextSpans", Message.translation(LANG + "poseHeading"));
            for (FounderStatueLoadout.Slot slot : FounderStatueLoadout.Slot.values()) {
                String card = "#ArmorRow[" + slot.ordinal() + "]";
                b.append("#ArmorRow", "Aetherhaven/FounderStatueArmorSlot.ui");
                b.set(card + " #SlotName.TextSpans", Message.translation(LANG + "slot." + slot.key().toLowerCase()));
                b.set(card + " #TakeOff.TextSpans", Message.translation(LANG + "takeOff"));
                e.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    card + " #TakeOff",
                    EventData.of("Action", "TakeOff").append("Key", slot.key()),
                    false
                );
            }
            for (FounderStatuePose pose : FounderStatuePose.values()) {
                String button = "#PoseRow[" + pose.ordinal() + "]";
                b.append("#PoseRow", "Aetherhaven/FounderStatuePose.ui");
                b.set(button + " #PoseName.TextSpans", Message.translation(pose.nameKey()));
                b.set(button + " #Chosen.TextSpans", Message.translation(LANG + "chosen"));
                b.set(button + ".TooltipTextSpans", Message.translation(pose.descriptionKey()));
                e.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    button,
                    EventData.of("Action", "Pose").append("Key", pose.id()),
                    false
                );
            }
        }
        String name = c.block().getLabel().trim();
        b.set(
            "#Intro.TextSpans",
            name.isEmpty()
                ? Message.translation(LANG + "introNoName")
                : Message.translation(LANG + "intro").param("name", name)
        );
        for (FounderStatueLoadout.Slot slot : FounderStatueLoadout.Slot.values()) {
            String card = "#ArmorRow[" + slot.ordinal() + "]";
            ItemStack worn = loadout.get(slot);
            // An icon for an item the client does not know would break the page, so such a piece shows by name only.
            boolean drawable = known(worn) != null;
            b.set(card + " #WornIcon.Visible", drawable);
            if (drawable) {
                b.set(card + " #WornIcon.ItemId", worn.getItemId());
            }
            b.set(card + " #ItemName.TextSpans", itemName(worn));
            b.set(card + " #TakeOff.Disabled", worn == null);
            List<Choice> carried = choices(c.pack(), slot);
            int shown = Math.min(carried.size(), MAX_CHOICES);
            b.set(
                card + " #ChoiceHint.TextSpans",
                carried.isEmpty()
                    ? Message.translation(LANG + "noneCarried")
                    : carried.size() > shown
                        ? Message.translation(LANG + "moreCarried")
                            .param("shown", String.valueOf(shown))
                            .param("count", String.valueOf(carried.size()))
                        : Message.translation(LANG + "fromPack")
            );
            b.clear(card + " #Choices");
            for (int i = 0; i < shown; i++) {
                Choice choice = carried.get(i);
                String button = card + " #Choices[" + i + "]";
                b.append(card + " #Choices", "Aetherhaven/FounderStatueArmorChoice.ui");
                b.set(button + " #Icon.ItemId", choice.itemId());
                b.set(
                    button + ".TooltipTextSpans",
                    Message.join(
                        itemName(c.pack().getItemStack(choice.packSlot())),
                        Message.raw(". "),
                        Message.translation(LANG + "wearTooltip")
                    )
                );
                e.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    button,
                    EventData.of("Action", "Wear").append("Key", slot.key() + "|" + choice.packSlot() + "|" + choice.itemId()),
                    false
                );
            }
        }
        FounderStatuePose current = FounderStatuePose.byId(loadout.getPoseId());
        for (FounderStatuePose pose : FounderStatuePose.values()) {
            String button = "#PoseRow[" + pose.ordinal() + "]";
            b.set(button + " #Selected.Visible", pose == current);
            b.set(button + " #Chosen.Visible", pose == current);
        }
        b.set("#Hint.TextSpans", hint != null ? hint : Message.translation(LANG + "hint.default"));
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Data data) {
        Context c = current(ref, store);
        if (c == null || data.action == null) {
            close();
            return;
        }
        hint = null;
        FounderStatueLoadout loadout = c.block().getLoadout();
        boolean armorChanged = false;
        switch (data.action) {
            case "Pose" -> {
                FounderStatuePose pose = FounderStatuePose.byId(data.key);
                if (!pose.savedId().equals(loadout.getPoseId())) {
                    loadout.setPoseId(pose.savedId());
                    FounderMonumentSpawnService.markPedestalNeedsSaving(c.blockRef());
                    // A pose is part of the statue's model, so the statue is rebuilt the same way it is first placed.
                    FounderMonumentStatueRestoreSystem.respawnStatueAt(c.world(), x, y, z);
                }
                hint = Message.translation(LANG + "hint.pose").param("pose", Message.translation(pose.nameKey()));
            }
            case "TakeOff" -> armorChanged = takeOff(c, FounderStatueLoadout.Slot.byKey(data.key));
            case "Wear" -> armorChanged = wear(c, data.key);
            default -> {}
        }
        if (armorChanged) {
            FounderMonumentSpawnService.markPedestalNeedsSaving(c.blockRef());
            if (!FounderMonumentSpawnService.refreshStatueArmor(store, c.block().getStatueEntityUuid(), loadout)) {
                FounderMonumentStatueRestoreSystem.respawnStatueAt(c.world(), x, y, z);
            }
        }
        UICommandBuilder b = new UICommandBuilder();
        UIEventBuilder e = new UIEventBuilder();
        build(ref, b, e, store);
        sendUpdate(b, e, false);
    }

    /** Where a piece is set down when the pack has no room: on the pedestal, at the statue's feet. */
    @Nonnull
    private Vector3d feet() {
        return new Vector3d(x + 0.5, y + 1.0, z + 0.5);
    }

    private boolean takeOff(@Nonnull Context c, @Nullable FounderStatueLoadout.Slot slot) {
        ItemStack worn = slot != null ? c.block().getLoadout().get(slot) : null;
        if (worn == null) {
            return false;
        }
        boolean[] setDown = new boolean[1];
        FounderStatueArmorTransfer.takeOff(
            c.block().getLoadout(),
            slot,
            c.pack(),
            stack -> setDown[0] |= FounderMonumentStatueRestoreSystem.dropStack(c.world(), stack, feet())
        );
        hint = Message.translation(LANG + (setDown[0] ? "hint.setDown" : "hint.takenOff")).param("item", itemName(worn));
        return true;
    }

    private boolean wear(@Nonnull Context c, @Nullable String key) {
        String[] parts = key != null ? key.split("\\|", 3) : new String[0];
        FounderStatueLoadout.Slot slot = parts.length == 3 ? FounderStatueLoadout.Slot.byKey(parts[0]) : null;
        if (slot == null) {
            return false;
        }
        short packSlot;
        try {
            packSlot = Short.parseShort(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        FounderStatueLoadout loadout = c.block().getLoadout();
        ItemStack before = loadout.get(slot);
        boolean[] setDown = new boolean[1];
        FounderStatueArmorTransfer.Result result =
            FounderStatueArmorTransfer.wear(
                loadout,
                slot,
                c.pack(),
                packSlot,
                parts[2],
                stack -> slotOf(stack) == slot,
                c.pack(),
                stack -> setDown[0] |= FounderMonumentStatueRestoreSystem.dropStack(c.world(), stack, feet())
            );
        switch (result) {
            case DONE -> {
                Message item = itemName(loadout.get(slot));
                hint = before == null
                    ? Message.translation(LANG + "hint.worn").param("item", item)
                    : Message.translation(LANG + (setDown[0] ? "hint.swappedSetDown" : "hint.swapped"))
                        .param("item", item)
                        .param("old", itemName(before));
            }
            case WRONG_PIECE -> hint = Message.translation(LANG + "hint.wrongPiece");
            default -> hint = Message.translation(LANG + "hint.packChanged");
        }
        return result == FounderStatueArmorTransfer.Result.DONE;
    }

    public static final class Data {
        public static final BuilderCodec<Data> CODEC =
            BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .add()
                .append(new KeyedCodec<>("Key", Codec.STRING), (d, v) -> d.key = v, d -> d.key)
                .add()
                .build();

        String action;
        String key;
    }
}
