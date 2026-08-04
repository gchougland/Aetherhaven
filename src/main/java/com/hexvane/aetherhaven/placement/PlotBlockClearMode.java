package com.hexvane.aetherhaven.placement;

/** How plot teardown clears world blocks before repaste or town removal. */
public enum PlotBlockClearMode {
    /** Clear every cell in the stored footprint AABB (town dissolution). */
    FULL_FOOTPRINT,
    /** Clear only cells listed in the prefab file at {@link PrefabVolumeClearSpec#anchor()}. */
    SPARSE_PREFAB,
    /** Skip block clear; caller repastes with {@link com.hexvane.aetherhaven.prefab.ConstructionAnimator} prep. */
    NONE
}
