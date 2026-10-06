"""Pack original painted source swatches into blockymodel UV faces at native density.

This does no procedural repainting: it crops and resamples the imagegen source.
The full source and provenance live in tools/art/painted-materials.
"""
from pathlib import Path
from functools import lru_cache
from PIL import Image

SOURCE=Path(__file__).parent/'art/painted-materials/material-sheet.png'
METALS=Path(__file__).parent/'art/painted-materials/metal-sheet.png'
MATERIALS={'leather':0,'edge':1,'dark':1,'strap':0,'wood':2,'brass':3,'gold':3,
           'iron':4,'copper':5,'stone':6,'slate':7,'green':8,'red':9,'purple':10,
           'blue':11,'paper':12,'string':12,'glass':13,'teal':14,'thorium':15}

@lru_cache(maxsize=256)
def painted_patch(width,height,material):
    metal_order={'copper':0,'iron':1,'thorium':2,'cobalt':3,'adamantite':4,'brass':5,'gold':5}
    is_metal=material in metal_order
    sheet=Image.open(METALS if is_metal else SOURCE).convert('RGBA');index=metal_order[material] if is_metal else MATERIALS[material]
    columns,rows=(3,2) if is_metal else (4,4)
    # Tile boundaries follow exact quarter divisions, regardless of imagegen output resolution.
    x,y=index%columns,index//columns
    tile=sheet.crop((round(x*sheet.width/columns),round(y*sheet.height/rows),round((x+1)*sheet.width/columns),round((y+1)*sheet.height/rows)))
    # Trim the thin swatch divider. Artwork provides all highlights and material detail.
    pad=max(2,tile.width//64);tile=tile.crop((pad,pad,tile.width-pad,tile.height-pad))
    return tile.resize((width,height),Image.Resampling.LANCZOS)
