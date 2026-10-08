package com.hexvane.aetherhaven.workplace;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("town")
class RancherPageAccessTest {
    @Test void shopAndBarnCannotReplayEachOthersMutationsOrOpenUpgrades(){
        assertTrue(RancherHerdPage.allowedAction(true,"Buy"));
        assertFalse(RancherHerdPage.allowedAction(false,"Buy"));
        for(String action:java.util.List.of("Supply","Rename","ChooseAnimal","Assign","Collect","Courier")){
            assertTrue(RancherHerdPage.allowedAction(false,action));
            assertFalse(RancherHerdPage.allowedAction(true,action));
        }
        for(boolean shop:new boolean[]{true,false}){
            assertFalse(RancherHerdPage.allowedAction(shop,"Shop"));
            assertFalse(RancherHerdPage.allowedAction(shop,"Upgrades"));
        }
    }
}
