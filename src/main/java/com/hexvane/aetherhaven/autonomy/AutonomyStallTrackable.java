package com.hexvane.aetherhaven.autonomy;

/** Phase-agnostic progress stall tracking for villager and tourist autonomy recovery. */
public interface AutonomyStallTrackable {
    double getAutonomySampleX();

    double getAutonomySampleZ();

    double getAutonomyAnchorX();

    double getAutonomyAnchorZ();

    double getAutonomyGoalDistSq();

    int getAutonomyStallTicks();

    void setAutonomySamplePosition(double x, double z);

    void setAutonomyAnchorPosition(double x, double z);

    void setAutonomyGoalDistSq(double distSq);

    void setAutonomyStallTicks(int ticks);

    void resetAutonomyStallTracking();
}
