package com.hexvane.aetherhaven.plotcreator;

public enum PlotCreatorStep {
    WELCOME,
    BOUNDS,
    ANCHOR,
    PREFAB_SAVE,
    KIND,
    IDENTITY,
    TAGS,
    VARIANT,
    FESTIVAL,
    IMPORTANT_SPOTS,
    SUBSTEP,
    /** Wall style authoring: one node per wall piece, with bounds and connection point substeps inside each. */
    WALL_PIECES,
    MATERIALS,
    CONFIGURE,
    REVIEW,
    DONE
}
