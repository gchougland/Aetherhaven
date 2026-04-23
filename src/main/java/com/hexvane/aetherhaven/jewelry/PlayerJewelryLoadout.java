package com.hexvane.aetherhaven.jewelry;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.protocol.ItemWithAllMetadata;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;

public final class PlayerJewelryLoadout implements Component<EntityStore> {
    private static final String K_RING1 = "r1";
    private static final String K_RING2 = "r2";
    private static final String K_NECK = "n";

    @Nonnull
    public static final BuilderCodec<PlayerJewelryLoadout> CODEC = BuilderCodec.builder(PlayerJewelryLoadout.class, PlayerJewelryLoadout::new)
        .append(
            new KeyedCodec<>("Slots", AetherhavenBsonCodecs.BSON_DOCUMENT),
            (l, v) -> l.slots = v != null ? v : new BsonDocument(),
            l -> l.slots)
        .add()
        .append(new KeyedCodec<>("StatsDirty", Codec.BOOLEAN), (l, v) -> l.statsDirty = v, l -> l.statsDirty)
        .add()
        .afterDecode(l -> l.statsDirty = true)
        .build();

    @Nullable
    private static volatile ComponentType<EntityStore, PlayerJewelryLoadout> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType = registry.registerComponent(PlayerJewelryLoadout.class, "AetherhavenPlayerJewelryLoadout", PlayerJewelryLoadout.CODEC);
    }

    @Nonnull
    public static ComponentType<EntityStore, PlayerJewelryLoadout> getComponentType() {
        ComponentType<EntityStore, PlayerJewelryLoadout> t = componentType;
        if (t == null) {
            throw new IllegalStateException("PlayerJewelryLoadout not registered");
        }
        return t;
    }

    @Nonnull
    private BsonDocument slots = new BsonDocument();

    private boolean statsDirty = true;

    public PlayerJewelryLoadout() {}

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        PlayerJewelryLoadout c = new PlayerJewelryLoadout();
        c.slots = slots != null ? slots.clone() : new BsonDocument();
        c.statsDirty = statsDirty;
        return c;
    }

    public boolean isStatsDirty() {
        return statsDirty;
    }

    public void markStatsDirty() {
        this.statsDirty = true;
    }

    public void clearStatsDirty() {
        this.statsDirty = false;
    }

    @Nullable
    public ItemStack getRing1() {
        return readSlot(K_RING1);
    }

    @Nullable
    public ItemStack getRing2() {
        return readSlot(K_RING2);
    }

    @Nullable
    public ItemStack getNecklace() {
        return readSlot(K_NECK);
    }

    public void setRing1(@Nullable ItemStack stack) {
        writeSlot(K_RING1, stack);
        statsDirty = true;
    }

    public void setRing2(@Nullable ItemStack stack) {
        writeSlot(K_RING2, stack);
        statsDirty = true;
    }

    public void setNecklace(@Nullable ItemStack stack) {
        writeSlot(K_NECK, stack);
        statsDirty = true;
    }

    @Nullable
    public ItemStack getSlot(int slot) {
        return switch (slot) {
            case 0 -> getRing1();
            case 1 -> getRing2();
            case 2 -> getNecklace();
            default -> null;
        };
    }

    public void setSlot(int slot, @Nullable ItemStack stack) {
        switch (slot) {
            case 0 -> setRing1(stack);
            case 1 -> setRing2(stack);
            case 2 -> setNecklace(stack);
            default -> {}
        }
    }

    private void writeSlot(@Nonnull String key, @Nullable ItemStack stack) {
        if (ItemStack.isEmpty(stack)) {
            slots.remove(key);
        } else {
            BsonDocument d = new BsonDocument();
            d.put("id", new BsonString(stack.getItemId()));
            d.put("q", new BsonInt32(stack.getQuantity()));
            ItemWithAllMetadata packet = stack.toPacket();
            if (packet.metadata != null && !packet.metadata.isBlank()) {
                d.put("meta", BsonDocument.parse(packet.metadata));
            }
            slots.put(key, d);
        }
    }

    @Nullable
    private ItemStack readSlot(@Nonnull String key) {
        BsonValue v = slots.get(key);
        if (v == null || !v.isDocument()) {
            return null;
        }
        BsonDocument d = v.asDocument();
        BsonValue idv = d.get("id");
        if (idv == null || !idv.isString()) {
            return null;
        }
        String id = idv.asString().getValue();
        int q = 1;
        BsonValue qv = d.get("q");
        if (qv != null && qv.isNumber()) {
            q = Math.max(1, qv.asNumber().intValue());
        }
        BsonValue mv = d.get("meta");
        BsonDocument meta = mv != null && mv.isDocument() ? mv.asDocument() : null;
        return new ItemStack(id, q, meta);
    }
}
