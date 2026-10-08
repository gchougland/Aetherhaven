package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import java.util.UUID;

/** Separate from core building plots, so marketplace prefabs need no apprentice markers. */
public final class ApprenticeWorkplace {
    private UUID id = UUID.randomUUID();
    private String name;
    private String type;
    private WorkplaceArea area;
    private WorkplaceOrders orders;
    private boolean charcoalFuel;
    private WorkplaceFood food;
    private BuildingOrders building;
    public BuildingOrders existingBuilding(){return building;}
    public BuildingOrders building(){if(building==null)building=new BuildingOrders();return building;}
    private ExcavationPlan excavation;
    private UUID mineBaseId;
    private UUID minePrerequisiteId;
    private MinePreferences minePreferences;
    public record MinePreferences(MineshaftSelection.Direction direction, ExcavationPlan.Layout layout,
                                  int length, ExcavationFittings.Mode fittings) {}
    public MinePreferences minePreferences(){return minePreferences;}
    public void minePreferences(MinePreferences value){minePreferences=value;}
    public UUID mineBaseId(){return mineBaseId;}
    public UUID minePrerequisiteId(){return minePrerequisiteId;}
    void attachTunnel(UUID base,UUID prerequisite){mineBaseId=java.util.Objects.requireNonNull(base);minePrerequisiteId=prerequisite;}
    // The unscheduled designation remembers its choices without replacing the saved excavation order.
    private ExcavationPlan.Entrance mineshaftEntrance;
    private ExcavationPlan.Layout mineshaftLayout;
    public ExcavationPlan.Entrance mineshaftEntrance() {
        return mineshaftEntrance != null ? mineshaftEntrance : excavation == null ? ExcavationPlan.Entrance.NORTH : excavation.entrance();
    }
    public ExcavationPlan.Layout mineshaftLayout() {
        return mineshaftLayout != null ? mineshaftLayout : excavation == null ? ExcavationPlan.Layout.STRAIGHT : excavation.layout();
    }
    public void setMineshaftConfiguration(ExcavationPlan.Entrance entrance, ExcavationPlan.Layout layout) {
        mineshaftEntrance = java.util.Objects.requireNonNull(entrance);
        mineshaftLayout = java.util.Objects.requireNonNull(layout);
    }
    public ExcavationPlan existingExcavation(){return excavation;}
    public ExcavationPlan excavation(){if(excavation==null)excavation=new ExcavationPlan();return excavation;}
    public WorkplaceFood food() { if (food == null) food = new WorkplaceFood(); return food; }
    public boolean charcoalFuel() { return charcoalFuel; }
    public void setCharcoalFuel(boolean charcoal) { charcoalFuel=charcoal; }


    public ApprenticeWorkplace(String name, TownsfolkWorkType type, WorkplaceArea area) {
        this.name = name;
        this.type = type.id;
        this.area = area;
    }
    public UUID id() { return id; }
    public String name() { return name; }
    public TownsfolkWorkType type() { return TownsfolkWorkType.byId(type); }
    public WorkplaceArea area() { return area; }
    public WorkplaceOrders orders() { if (orders == null) orders = new WorkplaceOrders(); return orders; }
    void configure(String name, TownsfolkWorkType type, WorkplaceArea area) {
        if (type() != type || !java.util.Objects.equals(this.area, area)) food = new WorkplaceFood(food().ranch());
        this.name = name;
        this.type = type.id;
        this.area = area;
    }
}
