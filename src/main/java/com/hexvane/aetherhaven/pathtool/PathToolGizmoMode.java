package com.hexvane.aetherhaven.pathtool;

public enum PathToolGizmoMode {
    /** Move selected control node. */
    Translate,
    /** Adjust node yaw (drives Bezier tangents / spline shape). */
    Rotate,
    /** Use key cements; avoids accidental place while editing. */
    Commit
}
