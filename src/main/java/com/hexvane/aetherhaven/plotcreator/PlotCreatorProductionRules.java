package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.production.ProductionWorkplaceKinds;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import java.util.List;

/** Current authoring requirements apply to new builds and edited legacy designs alike. */
public final class PlotCreatorProductionRules {
    private PlotCreatorProductionRules() {}

    public static boolean requiresBarnPens(PlotCreatorDraft draft,AetherhavenPlugin plugin) {
        if(draft.isFestivalMode()||draft.isWallMode()||draft.isPropMode()||draft.isDecorationOnly())return false;
        return PlotBuildingKindRequirements.workplaceRolesForDraft(draft,plugin).contains(TownVillagerBinding.KIND_RANCHER)
            || "plot_barn".equals(draft.getConstructionId()) || draft.getCountsAsConstructionIds().contains("plot_barn");
    }

    static boolean requiresHuntStorage(PlotCreatorDraft draft,AetherhavenPlugin plugin) {
        if(draft.isFestivalMode()||draft.isWallMode()||draft.isPropMode()||draft.isDecorationOnly())return false;
        var ids=new java.util.ArrayList<String>(draft.getCountsAsConstructionIds());
        ids.add(draft.getConstructionId());ids.add(draft.getEditingConstructionId());
        for(String id:ids)if(id!=null&&(com.hexvane.aetherhaven.hunter.HuntService.CONSTRUCTION.equals(id.trim())
            ||plugin!=null&&plugin.getConstructionCatalog().matchesGameplayConstruction(id.trim(),com.hexvane.aetherhaven.hunter.HuntService.CONSTRUCTION)))return true;
        return PlotBuildingKindRequirements.workplaceRolesForDraft(draft,plugin).contains("monster_hunter");
    }

    public static boolean requiresMiningNodes(PlotCreatorDraft draft, AetherhavenPlugin plugin) {
        if (draft.isFestivalMode() || draft.isWallMode() || draft.isPropMode()) return false;
        if (draft.isLoadedPhysicalMining() || !draft.getMiningNodes().isEmpty()) return true;
        if (isMine(draft.getConstructionId()) || isMine(draft.getEditingConstructionId())
            || draft.getCountsAsConstructionIds().stream().anyMatch(PlotCreatorProductionRules::isMine)) return true;
        return PlotBuildingKindRequirements.workplaceRolesForDraft(draft,plugin).contains(TownVillagerBinding.KIND_MINER);
    }

    private static boolean isMine(String id) {
        return id != null && TownVillagerBinding.KIND_MINER.equals(
            ProductionWorkplaceKinds.residentBindingKindForGameplayConstruction(id.trim()));
    }

    public static void enforceSelectedSpots(PlotCreatorDraft draft) {
        if(requiresBarnPens(draft,AetherhavenPlugin.get())&&draft.getSelectedSpots().stream().noneMatch(s->s.type()==PlotCreatorSubstepType.BARN_PEN))
            draft.getSelectedSpots().add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.BARN_PEN,5));
        if(requiresHuntStorage(draft,AetherhavenPlugin.get())&&draft.getSelectedSpots().stream().noneMatch(s->s.type()==PlotCreatorSubstepType.PRODUCTION_STORAGE))
            draft.getSelectedSpots().add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.PRODUCTION_STORAGE,1));
        if (!requiresMiningNodes(draft,AetherhavenPlugin.get())) return;
        draft.getSelectedSpots().removeIf(s -> s.type()==PlotCreatorSubstepType.MINING_NODE
            || s.type()==PlotCreatorSubstepType.WORK_POI && TownVillagerBinding.KIND_MINER.equals(s.workResidentKind()));
        draft.getSelectedSpots().add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.MINING_NODE,1));
    }

    static void enforceRequirements(PlotCreatorDraft draft, AetherhavenPlugin plugin,
                                    List<PlotBuildingKindRequirements.SubstepRequirement> requirements) {
        if(requiresBarnPens(draft,plugin)&&requirements.stream().noneMatch(s->s.type()==PlotCreatorSubstepType.BARN_PEN))
            requirements.add(new PlotBuildingKindRequirements.SubstepRequirement(PlotCreatorSubstepType.BARN_PEN,5));
        if(requiresHuntStorage(draft,plugin)&&requirements.stream().noneMatch(r->r.type()==PlotCreatorSubstepType.PRODUCTION_STORAGE))
            requirements.add(new PlotBuildingKindRequirements.SubstepRequirement(PlotCreatorSubstepType.PRODUCTION_STORAGE,1));
        if (!requiresMiningNodes(draft,plugin)) return;
        requirements.removeIf(r -> r.type()==PlotCreatorSubstepType.MINING_NODE
            || r.type()==PlotCreatorSubstepType.WORK_POI && (TownVillagerBinding.KIND_MINER.equals(r.workResidentKind())
                || r.workResidentKind()==null && PlotBuildingKindRequirements.workplaceRolesForDraft(draft,plugin).size()<=1));
        requirements.add(new PlotBuildingKindRequirements.SubstepRequirement(PlotCreatorSubstepType.MINING_NODE,1));
    }

    /** Save barrier independent of the chooser, including direct writer callers. */
    public static String validationError(PlotCreatorDraft draft, AetherhavenPlugin plugin) {
        if(requiresBarnPens(draft,plugin)) {
            var pens=draft.getPois().stream().filter(p->p.getTags().contains("BARN_PEN")).toList();
            if(pens.size()!=5||pens.stream().map(p->p.getLocalX()+":"+p.getLocalY()+":"+p.getLocalZ()).distinct().count()!=5||pens.stream().anyMatch(p->!draft.isInsideBounds(PlotCreatorLocalCoords.toWorldBlock(draft,new int[]{p.getLocalX(),p.getLocalY(),p.getLocalZ()}))))return "barnPensRequired";
        }
        if(requiresHuntStorage(draft,plugin)&&(draft.getProductionStorageLocalPos()==null
            ||!draft.isInsideBounds(PlotCreatorLocalCoords.toWorldBlock(draft,draft.getProductionStorageLocalPos()))))return "huntStorageRequired";
        if (!requiresMiningNodes(draft,plugin)) return null;
        if (draft.getMiningNodes().isEmpty()) return "miningUpgradeRequired";
        if (draft.miningLayout().validationError()!=null) return "miningNodes";
        for (var node : draft.getMiningNodes()) {
            if (!draft.isInsideBounds(PlotCreatorLocalCoords.toWorldBlock(draft,new int[]{node.x(),node.y(),node.z()})))
                return "miningNodes";
        }
        return null;
    }
}
