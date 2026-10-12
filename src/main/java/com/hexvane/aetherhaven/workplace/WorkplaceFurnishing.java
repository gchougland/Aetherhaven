package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;

/** Optional equipment for attended bench work, separate from recipe requirements. */
public record WorkplaceFurnishing(String blockId, String name, String workplace, double workSpeedBonus) {
    public TownsfolkWorkType type() { return TownsfolkWorkType.byId(workplace); }
    public boolean valid() {
        var type=type();
        return blockId!=null&&!blockId.isBlank()&&name!=null&&!name.isBlank()&&type!=null
            && type!=TownsfolkWorkType.SMELTING&&type!=TownsfolkWorkType.LOGISTICS
            && type!=TownsfolkWorkType.FARMING&&type!=TownsfolkWorkType.RANCHING
            && type!=TownsfolkWorkType.CULTIVATION&&type!=TownsfolkWorkType.MINING&&!GrowerPlan.supports(type)
            && Double.isFinite(workSpeedBonus)&&workSpeedBonus>0&&workSpeedBonus<=.25;
    }
}
