package com.hexvane.aetherhaven.guild;

import com.hexvane.aetherhaven.townsfolk.*;
import com.hexvane.aetherhaven.equipment.data.EquipmentProfileDefinition;

/** Provisional combat curve. Equipment adds effective attributes without changing learned values. */
public record GuardCombatStats(float health, float damageMultiplier, float damageReduction, int speed) {
    public static GuardCombatStats derive(TownsfolkProgress progress, EquipmentProfileDefinition equipment) {
        int strength=effective(progress,equipment,TownsfolkAttribute.STRENGTH);
        int intelligence=effective(progress,equipment,TownsfolkAttribute.INTELLIGENCE);
        int vitality=effective(progress,equipment,TownsfolkAttribute.VITALITY);
        int dexterity=effective(progress,equipment,TownsfolkAttribute.DEXTERITY);
        int speed=effective(progress,equipment,TownsfolkAttribute.SPEED);
        String role=equipment.getGuardNpcRole().toLowerCase(java.util.Locale.ROOT);
        int primary=role.contains("mage")?intelligence:role.contains("archer")||role.contains("rogue")?dexterity:strength;
        return new GuardCombatStats(120+12*vitality, .75f+.05f*primary,
            Math.min(.40f,.008f*vitality+.004f*dexterity),speed);
    }
    private static int effective(TownsfolkProgress p,EquipmentProfileDefinition e,TownsfolkAttribute a) {
        return Math.clamp(p.attribute(a)+e.attributeBonus(a.id),1,40);
    }
    public static float movementMultiplier(int speed) { return 1+.015f*(Math.clamp(speed,1,40)-5); }
}
