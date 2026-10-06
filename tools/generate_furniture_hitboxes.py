"""Build block collision boxes from authored model geometry, without touching art.

Models are centered on X/Z and use 32 pixels per block. Native block hitboxes
start at the block's corner, so the horizontal half-block offset is essential.
"""
import json
from pathlib import Path
from villager_prop_preview import world_shapes

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources'


def block_boxes(nodes):
    boxes = []
    for node, points in world_shapes(nodes):
        if node['shape']['type'] != 'box':
            continue
        boxes.append({bound: dict(zip(('X', 'Y', 'Z'),
                      (values / 32 + [0.5, 0, 0.5]).tolist()))
                      for bound, values in [('Min', points.min(axis=0)), ('Max', points.max(axis=0))]})
    return {'Boxes': boxes}


def write_hitbox(model_path, hitbox_path):
    model = json.loads(model_path.read_text(encoding='utf-8-sig'))
    hitbox_path.parent.mkdir(parents=True, exist_ok=True)
    hitbox_path.write_text(json.dumps(block_boxes(model['nodes']), indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    models = {
        'Aetherhaven_StitchingFrame': 'Artisans/StitchingFrame',
        'Aetherhaven_PatternCabinet': 'Artisans/PatternCabinet',
        'Aetherhaven_Hunt_Practice_Standard': 'Hunt/Practice_Standard',
        'Aetherhaven_Hunt_Recovery_Brazier': 'Hunt/Recovery_Brazier',
        'Aetherhaven_Hunt_Warding_Sigil': 'Hunt/Warding_Sigil',
        'Aetherhaven/Aetherhaven_Warehouse_Ledger_Desk': 'Warehouse_Ledger_Desk/Warehouse_Ledger_Desk',
    }
    for hitbox, model in models.items():
        write_hitbox(ROOT / f'Common/Blocks/Aetherhaven/{model}.blockymodel',
                     ROOT / f'Server/Item/Block/Hitboxes/{hitbox}.json')
    print(f'Updated {len(models)} furniture hitboxes. Models and textures unchanged.')
