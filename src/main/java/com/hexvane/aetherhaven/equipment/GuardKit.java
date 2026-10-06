package com.hexvane.aetherhaven.equipment;

/** Pack-defined equipment links a physical kit to one class and a visual/stat profile. */
public record GuardKit(String itemId, String name, String guardClass, String equipmentProfile, String description) {
    public boolean valid() {
        return itemId != null && !itemId.isBlank() && name != null && !name.isBlank()
            && guardClass != null && com.hexvane.aetherhaven.guild.GuardDutyService.CLASSES.contains(guardClass)
            && equipmentProfile != null && !equipmentProfile.isBlank() && description != null;
    }
}
