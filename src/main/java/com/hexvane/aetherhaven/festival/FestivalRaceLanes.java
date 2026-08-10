package com.hexvane.aetherhaven.festival;

import com.hexvane.aetherhaven.festival.pigrace.PigRaceLanes;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Resolves race lane geometry from a festival definition, falling back to baked pig race defaults. */
public final class FestivalRaceLanes {
    private FestivalRaceLanes() {}

    @Nonnull
    public static List<PigRaceLanes.Lane> resolve(@Nullable FestivalDefinition festival) {
        if (festival != null && !festival.getRaceLanes().isEmpty()) {
            List<PigRaceLanes.Lane> out = new ArrayList<>();
            int index = 0;
            for (FestivalDefinition.RaceLaneRow row : festival.getRaceLanes()) {
                String role = row.getNpcRoleId();
                if (role.isEmpty()) {
                    continue;
                }
                out.add(
                    new PigRaceLanes.Lane(
                        index,
                        role,
                        row.getStartLocalX(),
                        row.getStartLocalY(),
                        row.getStartLocalZ(),
                        row.getFinishLocalX(),
                        row.getFinishLocalY(),
                        row.getFinishLocalZ()
                    )
                );
                index++;
            }
            if (!out.isEmpty()) {
                return List.copyOf(out);
            }
        }
        return PigRaceLanes.defaultLanes();
    }
}
