package com.hexvane.aetherhaven.pathtool;

public enum PathToolGizmoMode {
    /** Move selected control node. */
    Translate,
    /** Adjust node yaw (drives Bezier tangents / spline shape). */
    Rotate,
    /** Use key cements; avoids accidental place while editing. */
    Commit,
    /** View and remove committed paths. */
    Remove,
    /** Open the in game path style manager. */
    StyleDesigner,
    /** Chest grid: block ids the path may replace (per player). */
    ReplaceFilter
}
