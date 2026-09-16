# Villager emotion particle art

Created with the built-in image-generation tool. No external game's sprites are used.
The five final runtime PNGs are in `src/main/resources/Common/Particles/Aetherhaven/Emotions/`:
`Surprise.png`, `Confusion.png`, `Question.png`, `Shock.png`, and `Gloom.png`.
The packing script preserves alpha, crops transparent padding and scales the art
into 64×64 textures with a narrow filtering margin. Original generations remain
under the tool's generated_images directory; filenames are recorded in the script.

## Prompt set

Surprise and Confusion used this common prompt:

> Use case: stylized-concept. Asset type: one original transparent PNG billboard particle sprite for a Hytale-style voxel fantasy game's villager emotions. True transparent background, no checkerboard baked in, no backdrop, no text labels, no border, no watermark. Square image, one centered isolated symbol, generous empty transparent padding (symbol occupies middle 65%). Hand-painted pixel-art style with chunky angular silhouette, subtly shaded facets, crisp edges, few colors; must remain very readable when reduced to 64 pixels. Flat front view, no cast shadow outside symbol.

- Surprise: A cheerful golden-orange four-point comic surprise burst, with one long upper ray and three shorter asymmetric rays, warm pale yellow center and burnt orange rim. Only this burst.
- Confusion: A single lavender-purple loose tangled spiral squiggle, like a confused cartoon thought scribble, two and a half irregular loops, thick chunky angular stroke, pale lilac upper edge and deep violet underside. Only this squiggle.

Question, Shock and Gloom used this common prompt:

> Create one original 64-pixel readable Hytale-style pixel-art villager emotion particle sprite. True transparent background (preserve alpha, no fake checkerboard), square front-view composition, chunky angular silhouette with subtly painted shaded facets and crisp edges. One centered isolated symbol occupying 70 percent of the canvas, no label, no text except the requested punctuation, no watermark or border.

- Question: A playful teal-blue question mark, chunky curved hook and separate square dot, turquoise highlights and dark blue edge.
- Shock: A sharp coral-red cartoon lightning zigzag expressing shock, cream-orange highlight edge and crimson lower facets.
- Gloom: A small droopy indigo-purple raincloud with a flattened angular puffy silhouette and three short blue teardrops beneath it, subdued periwinkle highlights, melancholy mood.

Runtime effects now clone native loved-gift Hearts system/spawner settings exactly; only IDs, sprite paths, and white rather than red tint differ. The original generated sprites are unchanged.
