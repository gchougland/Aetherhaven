package com.hexvane.aetherhaven.workplace;

/** Native crystal soil crops. These are ingredients rather than town food crops. */
public record AlchemicalCrop(String family, int tier) {
    public AlchemicalCrop {
        if((!"Health".equals(family) && !"Stamina".equals(family) && !"Mana".equals(family))||tier<1||tier>3)
            throw new IllegalArgumentException("Unknown native alchemical crop");
    }
    public static final String SOIL = "Soil_Dirt_Crystal";
    private record BlockMatch(AlchemicalCrop crop, String item, String block, String statePrefix) {}
    private static final BlockMatch[] BLOCK_MATCHES = blockMatches();

    private static BlockMatch[] blockMatches() {
        var matches = new BlockMatch[9];
        int index = 0;
        for (String family : new String[]{"Health", "Stamina", "Mana"}) for (int tier = 1; tier <= 3; tier++) {
            var crop = new AlchemicalCrop(family, tier);
            String block = crop.plantedBlock();
            matches[index++] = new BlockMatch(crop, crop.item(), block, block + "_");
        }
        return matches;
    }
    public String item() { return "Plant_Crop_" + family + tier; }
    public String seed() { return "Plant_Seeds_" + family + tier; }
    public String plantedBlock() { return item() + "_Block"; }
    public int skill() { return switch (tier) { case 1 -> 1; case 2 -> 4; default -> 7; }; }
    public static AlchemicalCrop fromBlock(String id) {
        if (id == null) return null;
        int offset = id.startsWith("*") ? 1 : 0;
        if (!id.startsWith("Plant_Crop_", offset)) return null;
        int length = id.length() - offset;
        for (var match : BLOCK_MATCHES) {
            if ((length == match.item.length() && id.startsWith(match.item, offset))
                || (length == match.block.length() && id.startsWith(match.block, offset))
                || id.startsWith(match.statePrefix, offset)) return match.crop;
        }
        return null;
    }
}
