package com.hexvane.aetherhaven.plotcreator;

final class PlotCreatorBoundsConstants {
    static final float DEFAULT_REACH = 4f;
    /** Look distance for bounds face panel highlight and drag. */
    static final float FACE_PICK_REACH = 64f;
    /** Minimum inclusive span on each axis (1 = one block thick). */
    static final int MIN_AXIS_SPAN = 0;
    /** Reject boxes where height span is below this (flat selection). */
    static final int MIN_HEIGHT_SPAN = 1;
    static final double MIN_INITIAL_DRAG_BLOCKS = 1.0;
    static final double FACE_PANEL_THICKNESS = 0.08;
    static final double FACE_PANEL_OUTSET = 0.02;
    static final float FACE_OVERLAY_SECONDS = 6f * 60f * 60f;

    private PlotCreatorBoundsConstants() {}
}
