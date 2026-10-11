package com.hexvane.aetherhaven.autonomy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class VillagerEatingFoodTest {
    @Test void prowlGetsCornAndOtherResidentsKeepTheirNormalFood() {
        assertEquals("Plant_Crop_Corn_Item", VillagerEatingFood.forCharacter("prowl"));
        assertEquals(com.hexvane.aetherhaven.AetherhavenConstants.CAMPFIRE_EAT_ITEM_ID, VillagerEatingFood.forCharacter("other"));
        assertEquals(com.hexvane.aetherhaven.AetherhavenConstants.CAMPFIRE_EAT_ITEM_ID, VillagerEatingFood.forCharacter(null));
    }
}
