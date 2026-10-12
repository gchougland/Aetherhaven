package com.hexvane.aetherhaven.workplace;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The posts of a support frame are native beams, which never turn, and the native beam texture is lit on two faces
 * only. A joining piece turned a quarter therefore shows lit wood above shaded posts. The joining pieces must match the
 * posts under them in every tunnel direction.
 */
@Tag("town")
class ExcavationFittingsJoinerTest {
    private static final int UNTURNED=RotationTuple.NONE.index();
    private static final int QUARTER=RotationTuple.of(Rotation.Ninety,Rotation.None,Rotation.None).index();
    private static final Set<String> ALONG_X=Set.of(ExcavationFittings.CORNER,ExcavationFittings.CEILING);
    private static final Set<String> ALONG_Z=Set.of(ExcavationFittings.CORNER_CROSS,ExcavationFittings.CEILING_CROSS);

    @Test void joiningPiecesAreNeverTurnedAndLieAcrossTheTunnel(){
        for(var direction:MineshaftSelection.Direction.values())
            for(var layout:List.of(ExcavationPlan.Layout.STRAIGHT,ExcavationPlan.Layout.DEEP_STAIRS_DOWN,ExcavationPlan.Layout.ROOM)){
                var entrance=MineshaftSelection.entrance(direction);
                boolean northSouth=entrance==ExcavationPlan.Entrance.NORTH||entrance==ExcavationPlan.Entrance.SOUTH;
                var fittings=ExcavationFittings.plan(MineshaftSelection.area(10,100,20,direction,12,layout),entrance,layout,ExcavationFittings.Mode.SUPPORTS);
                int corners=0,ceilings=0,posts=0;
                for(var fitting:fittings){
                    assertEquals(ExcavationFittings.BEAM,fitting.item(),"Every support costs one Hardwood Beam");
                    assertEquals(UNTURNED,fitting.rotation(),direction+" "+layout+" "+fitting.block()+" must sit unturned like the native posts");
                    if(ExcavationFittings.BEAM.equals(fitting.block())){posts++;continue;}
                    assertTrue((northSouth?ALONG_X:ALONG_Z).contains(fitting.block()),direction+" "+layout+" used "+fitting.block());
                    if(fitting.block().contains("Corner"))corners++;else ceilings++;
                }
                assertTrue(posts>0&&corners>0&&ceilings>0,direction+" "+layout+" should plan posts, corners and ceiling beams");
            }
    }

    @Test void ordersSavedWithATurnedJoiningPieceAreBuiltUnturned(){
        var gson=new Gson();
        for(var piece:List.of(ExcavationFittings.CORNER,ExcavationFittings.CEILING)){
            var saved=gson.toJsonTree(new ExcavationFittings.Fitting(new Point(4,70,9),ExcavationFittings.BEAM,UNTURNED,piece,new Point(4,69,9))).getAsJsonObject();
            // What a build before the cross pieces stored for a frame in an east to west tunnel.
            saved.addProperty("block",piece);
            saved.addProperty("rotation",QUARTER);
            var restored=gson.fromJson(saved,ExcavationFittings.Fitting.class);
            assertEquals(piece+"_Cross",restored.block());
            assertEquals(UNTURNED,restored.rotation());
            assertEquals(ExcavationFittings.BEAM,restored.item());
            assertEquals(new Point(4,69,9),restored.support());
            // Saving the order again and reading it back gives the same piece.
            var again=gson.fromJson(gson.toJsonTree(restored),ExcavationFittings.Fitting.class);
            assertEquals(piece+"_Cross",again.block());
            assertEquals(UNTURNED,again.rotation());
            var straight=new ExcavationFittings.Fitting(new Point(4,70,9),ExcavationFittings.BEAM,UNTURNED,piece,new Point(4,69,9));
            assertEquals(piece,straight.block());
            assertEquals(UNTURNED,straight.rotation());
        }
        var post=new ExcavationFittings.Fitting(new Point(4,70,9),ExcavationFittings.BEAM,UNTURNED,ExcavationFittings.BEAM,new Point(4,69,9));
        assertEquals(ExcavationFittings.BEAM,post.block());
    }

    @Test void everyJoiningPieceShipsItsItemModelAndHitboxOnTheNativeHardwoodBeamTexture() throws Exception {
        Path resources=Path.of("src","main","resources");
        for(var id:List.of(ExcavationFittings.CORNER,ExcavationFittings.CEILING,ExcavationFittings.CORNER_CROSS,ExcavationFittings.CEILING_CROSS)){
            Path item=resources.resolve("Server/Item/Items/Aetherhaven/"+id+".json");
            assertTrue(Files.isRegularFile(item),"Missing item "+item);
            var block=JsonParser.parseString(Files.readString(item)).getAsJsonObject().getAsJsonObject("BlockType");
            assertEquals("Blocks/Aetherhaven/"+id+".blockymodel",block.get("CustomModel").getAsString());
            assertTrue(Files.isRegularFile(resources.resolve("Common").resolve(block.get("CustomModel").getAsString())),"Missing model for "+id);
            assertEquals(id,block.get("HitboxType").getAsString());
            assertTrue(Files.isRegularFile(resources.resolve("Server/Item/Block/Hitboxes/"+id+".json")),"Missing hitbox for "+id);
            assertEquals("Blocks/Structures/Tubes/Long_Thin_Textures/Hardwood.png",
                block.getAsJsonArray("CustomModelTexture").get(0).getAsJsonObject().get("Texture").getAsString());
            assertEquals(ExcavationFittings.BEAM,block.getAsJsonObject("Gathering").getAsJsonObject("Breaking").get("ItemId").getAsString());
        }
    }
}
