package com.hexvane.aetherhaven.feast;

import com.hexvane.aetherhaven.construction.MaterialRequirement;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class FeastCatalog {
    public static final FeastDefinition STEWARDS_LEDGER =
        new FeastDefinition(
            "feast_stewards_ledger",
            "server.aetherhaven.feast.stewards.name",
            "server.aetherhaven.feast.stewards.description",
            List.of(
                MaterialRequirement.ofItem("Food_Bread", 8),
                MaterialRequirement.ofItem("Food_Cheese", 6),
                MaterialRequirement.ofItem("Food_Pie_Pumpkin", 4)
            ),
            50,
            FeastEffectKind.STEWARDS_TAX,
            "../fast-food.png"
        );

    public static final FeastDefinition HEARTHGLASS_VIGIL =
        new FeastDefinition(
            "feast_hearthglass_vigil",
            "server.aetherhaven.feast.hearthglass.name",
            "server.aetherhaven.feast.hearthglass.description",
            List.of(
                MaterialRequirement.ofItem("Food_Wildmeat_Cooked", 8),
                MaterialRequirement.ofItem("Food_Fish_Grilled", 6),
                MaterialRequirement.ofItem("Food_Vegetable_Cooked", 6)
            ),
            75,
            FeastEffectKind.HEARTHGLASS_DECAY,
            "../taco.png"
        );

    public static final FeastDefinition BERRYCIRCLE_CONCORD =
        new FeastDefinition(
            "feast_berrycircle_concord",
            "server.aetherhaven.feast.berrycircle.name",
            "server.aetherhaven.feast.berrycircle.description",
            List.of(
                MaterialRequirement.ofItem("Food_Salad_Berry", 4),
                MaterialRequirement.ofItem("Food_Kebab_Fruit", 6),
                MaterialRequirement.ofItem("Food_Popcorn", 8)
            ),
            100,
            FeastEffectKind.BERRYCIRCLE_REP,
            "../salad.png"
        );

    @Nonnull
    public static final List<FeastDefinition> ALL = List.of(STEWARDS_LEDGER, HEARTHGLASS_VIGIL, BERRYCIRCLE_CONCORD);

    @Nullable
    public static FeastDefinition findById(@Nonnull String feastId) {
        String k = feastId.trim();
        for (FeastDefinition d : ALL) {
            if (d.id().equals(k)) {
                return d;
            }
        }
        return null;
    }

    private FeastCatalog() {}
}
