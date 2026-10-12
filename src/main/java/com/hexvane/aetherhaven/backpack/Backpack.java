package com.hexvane.aetherhaven.backpack;

import com.hexvane.aetherhaven.jewelry.AetherhavenBsonCodecs;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.util.*;
import org.bson.*;

/** A portable inventory belongs to its item, never to the player who last opened it. */
public final class Backpack {
    public static final String ITEM="Aetherhaven_Backpack", KEY="AetherhavenBackpack";
    public enum Tier {
        SIMPLE("Simple",15,1), IRON("Iron",20,1), COBALT("Cobalt",30,1), THORIUM("Thorium",20,2), ADAMANTITE("Adamantite",40,3);
        public final String label; public final int slots, stacks;
        Tier(String label,int slots,int stacks){this.label=label;this.slots=slots;this.stacks=stacks;}
    }
    public static String itemId(Tier tier){return tier==Tier.SIMPLE?ITEM:ITEM+"_"+tier.label;}
    public Tier tier=Tier.SIMPLE;
    public int compression;
    public boolean magnet, magnetOn, jukebox;
    public String song="";
    public String name="";
    public String displayName(){return name.isBlank()?tier.label+" Backpack":name;}
    public boolean rename(String value){
        if(value==null)return false;
        String clean=value.strip();
        if(clean.length()>30||clean.codePoints().anyMatch(Character::isISOControl))return false;
        name=clean;return true;
    }
    public final UUID id;
    public final ItemStack[] items=new ItemStack[40];
    private Backpack(UUID id){this.id=id;}
    public static boolean isBackpack(ItemStack stack){return !ItemStack.isEmpty(stack)&&Arrays.stream(Tier.values()).anyMatch(t->itemId(t).equals(stack.getItemId()))&&stack.getQuantity()==1;}
    public static Backpack read(ItemStack stack){
        if(!isBackpack(stack))return null;
        var data=stack.getFromMetadataOrNull(KEY,AetherhavenBsonCodecs.BSON_DOCUMENT);
        if(data==null){var pack=new Backpack(UUID.randomUUID());for(var tier:Tier.values())if(itemId(tier).equals(stack.getItemId()))pack.tier=tier;return pack;}
        try {
            var pack=new Backpack(UUID.fromString(data.getString("id").getValue()));
            pack.tier=Tier.valueOf(data.getString("tier").getValue());
            pack.compression=Math.clamp(data.getInt32("compression",new BsonInt32(0)).getValue(),0,3);
            pack.magnet=data.getBoolean("magnet",BsonBoolean.FALSE).getValue();
            pack.magnetOn=data.getBoolean("magnetOn",BsonBoolean.FALSE).getValue();
            pack.jukebox=data.getBoolean("jukebox",BsonBoolean.FALSE).getValue();
            pack.song=data.getString("song",new BsonString("")).getValue();
            pack.rename(data.getString("name",new BsonString("")).getValue());
            var rows=data.getArray("items");
            if(rows.size()>pack.items.length)return null;
            for(int i=0;i<rows.size();i++)if(!rows.get(i).isNull()){
                var held=ItemStack.CODEC.decode(rows.get(i),new ExtraInfo());
                if(isBackpack(held)||ItemStack.isEmpty(held)||i>=pack.tier.slots||held.getQuantity()>pack.limit(held))return null;
                pack.items[i]=held;
            }
            return pack;
        }catch(RuntimeException ex){return null;} // Damaged data must never be silently overwritten.
    }
    public int multiplier(){return tier.stacks+compression;}
    public static String stacksLabel(int count){return count+(count==1?" stack":" stacks");}
    public int limit(ItemStack stack){return com.hexvane.aetherhaven.inventory.StackedSlots.limit(stack,multiplier());}
    public int used(){int n=0;for(var s:items)if(!ItemStack.isEmpty(s))n++;return n;}
    /** Returns the number accepted. Native stack metadata is retained. */
    public int insert(ItemStack stack){
        if(ItemStack.isEmpty(stack)||isBackpack(stack))return 0;
        return com.hexvane.aetherhaven.inventory.StackedSlots.insert(items,tier.slots,stack,multiplier());
    }
    /** True when the bag already holds a stack this one merges with. */
    public boolean holds(ItemStack stack){return !isBackpack(stack)&&com.hexvane.aetherhaven.inventory.StackedSlots.holds(items,tier.slots,stack);}
    public ItemStack write(ItemStack original){
        var data=new BsonDocument().append("id",new BsonString(id.toString())).append("tier",new BsonString(tier.name()))
            .append("compression",new BsonInt32(compression)).append("magnet",BsonBoolean.valueOf(magnet))
            .append("magnetOn",BsonBoolean.valueOf(magnetOn)).append("jukebox",BsonBoolean.valueOf(jukebox)).append("song",new BsonString(song)).append("name",new BsonString(name));
        var rows=new BsonArray();for(var held:items)rows.add(ItemStack.isEmpty(held)?BsonNull.VALUE:ItemStack.CODEC.encode(held,new ExtraInfo()));data.append("items",rows);
        var description=Message.raw(tier.label+" Backpack\n"+used()+" / "+tier.slots+" slots occupied\n"+stacksLabel(multiplier())+" per slot").color("#e8c984");
        if(magnet)description.insert(Message.raw("\nMagnet: "+(magnetOn?"on":"off")).color("#91c8ff"));
        if(jukebox)description.insert(Message.raw("\nJukebox installed").color("#c7b0ef"));
        return com.hexvane.aetherhaven.inventory.ItemStackData.withItemId(original,itemId(tier)).withQuantity(1).withMetadata(KEY,data).withMetadata(ItemDisplayMetadata.KEYED_CODEC,new ItemDisplayMetadata(Message.raw(displayName()),description));
    }
    public static ItemStack create(){return new Backpack(UUID.randomUUID()).write(new ItemStack(ITEM,1));}
}
