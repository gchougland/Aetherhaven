package com.hexvane.aetherhaven.equipment;

import com.hexvane.aetherhaven.townsfolk.TownsfolkSkill;

/** Pack supplied work equipment. Bonuses never raise recipe eligibility or unlock an incapable skill. */
public record ProfessionKit(String itemId, String name, String skill, double workSpeedBonus, int extraCarryingStacks) {
    public TownsfolkSkill profession() {
        for (var value : TownsfolkSkill.values()) if (value.id.equals(skill)) return value;
        return null;
    }
    public boolean valid() {
        return itemId != null && !itemId.isBlank() && name != null && !name.isBlank() && profession() != null
            && Double.isFinite(workSpeedBonus) && workSpeedBonus >= 0 && workSpeedBonus <= 1
            && extraCarryingStacks >= 0 && extraCarryingStacks <= 3
            && (extraCarryingStacks == 0 || profession() == TownsfolkSkill.LOGISTICS);
    }
    public String description() {
        return extraCarryingStacks > 0 ? "Carries " + extraCarryingStacks + " extra stack" + (extraCarryingStacks == 1 ? "." : "s.")
            : Math.round(workSpeedBonus * 100) + "% faster " + skill + " work. Skill requirements stay the same.";
    }
}
