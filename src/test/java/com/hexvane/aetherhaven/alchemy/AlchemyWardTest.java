package com.hexvane.aetherhaven.alchemy;

import static org.junit.jupiter.api.Assertions.*;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import org.junit.jupiter.api.*;

@Tag("town")
class AlchemyWardTest {
    @Test void blastWardDistinguishesExplosionsFromOtherEnvironmentalDamage() {
        assertTrue(AlchemyBlastWardSystem.blocks(new Damage.EnvironmentSource("explosion"),"Environment"));
        assertFalse(AlchemyBlastWardSystem.blocks(new Damage.EnvironmentSource("fall"),"Environment"));
        assertFalse(AlchemyBlastWardSystem.blocks(new Damage.EnvironmentSource("drowning"),"Drowning"));
    }
    @Test void projectileExplosionsAreProtectedWithoutGrantingArrowImmunity() {
        var projectile=new Damage.ProjectileSource(null,null);
        assertTrue(AlchemyBlastWardSystem.blocks(projectile,"Environment"));
        assertFalse(AlchemyBlastWardSystem.blocks(projectile,"Projectile"));
        assertFalse(AlchemyBlastWardSystem.blocks(projectile,"Physical"));
    }
    @Test void cleansingOnlyRemovesNativePoisonDebuffs() {
        assertTrue(AlchemyPoisonWardSystem.isPoison("Poison"));assertTrue(AlchemyPoisonWardSystem.isPoison("Poison_T3"));
        assertFalse(AlchemyPoisonWardSystem.isPoison("Aetherhaven_Alchemy_PoisonWard1"));
        assertFalse(AlchemyPoisonWardSystem.isPoison("Ability_Poison_Imbue_Buff"));
    }
}
