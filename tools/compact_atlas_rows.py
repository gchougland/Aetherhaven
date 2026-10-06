"""Remove unused bottom rows while preserving native pixel UVs and painted detail."""
from __future__ import annotations

import json
import math
import shutil
from datetime import datetime, timezone
from pathlib import Path

from PIL import Image


def crop_unused_rows(image, models, height):
    """Require every face UV and visible pixel to fit before cropping an atlas."""
    assert image.mode == 'RGBA', image.mode
    assert 0 < height <= image.height
    bounds = [0, 0]

    def visit(nodes):
        for node in nodes:
            shape = node.get('shape', {})
            size = shape.get('settings', {}).get('size', {})
            for face, uv in shape.get('textureLayout', {}).items():
                if shape['type'] == 'box':
                    axes = {'front': ('x', 'y'), 'back': ('x', 'y'),
                            'left': ('z', 'y'), 'right': ('z', 'y'),
                            'top': ('x', 'z'), 'bottom': ('x', 'z')}[face]
                else:
                    assert shape['type'] == 'quad', shape['type']
                    axes = ('x', 'y')
                width, face_height = (size[axis] for axis in axes)
                angle = uv.get('angle', 0)
                mirror = uv.get('mirror', {})
                # A transformed face needs the exact native UV corner transform.
                # Current authored atlases use only unrotated, unmirrored faces.
                assert angle == 0, ('Rotated UVs are not supported', angle)
                assert isinstance(mirror, dict) and not any(mirror.values()), ('Mirrored UVs are not supported', mirror)
                x, y = (uv['offset'][axis] for axis in ('x', 'y'))
                assert x >= 0 and y >= 0, (x, y)
                bounds[0] = max(bounds[0], x + width)
                bounds[1] = max(bounds[1], y + face_height)
            visit(node.get('children', []))

    for model in models:
        visit(model['nodes'])
    assert bounds[0] <= image.width and bounds[1] <= height, (bounds, image.size, height)
    assert image.getchannel('A').crop((0, height, image.width, image.height)).getbbox() is None
    cropped = image.crop((0, 0, image.width, height))
    assert cropped.tobytes() == image.crop((0, 0, image.width, height)).tobytes()
    return cropped


def main():
    root = Path(__file__).resolve().parents[1]
    common = root / 'src/main/resources/Common'
    hunt = common / 'Blocks/Aetherhaven/Hunt'
    jobs = [(hunt / 'Hunt.png', sorted(hunt.glob('*.blockymodel')), 128)]
    portal = common / 'NPC/Aetherhaven/Hunt'
    jobs.append((portal / 'Hunt.png', [portal / 'Portal.blockymodel'], 128))
    for path in sorted((common / 'Items/Aetherhaven/Profession_Kits').glob('*.png')):
        height = 64 if path.stem == 'CourierPack_Kit' else 32
        jobs.append((path, [path.with_suffix('.blockymodel')], height))
    frame = common / 'Blocks/Aetherhaven/Artisans/StitchingFrame.png'
    jobs.append((frame, [frame.with_suffix('.blockymodel')], 64))
    # Validate the whole batch before replacing any image.
    planned = []
    for path, model_paths, height in jobs:
        original = Image.open(path).convert('RGBA')
        models = [json.loads(p.read_text(encoding='utf-8')) for p in model_paths]
        cropped = crop_unused_rows(original, models, height)
        if original.size != cropped.size:
            planned.append((path, original, cropped))
    stamp = datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S-%f')
    backup = root / 'build/backups' / ('atlas-unused-rows-' + stamp)
    saved_pixels = 0
    for path, original, cropped in planned:
        saved = backup / path.relative_to(root)
        saved.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, saved)
        cropped.save(path, optimize=True)
        with Image.open(path) as persisted:
            assert persisted.size == cropped.size
            assert persisted.tobytes() == cropped.tobytes()
        saved_pixels += math.prod(original.size) - math.prod(cropped.size)
        print(path.relative_to(common), original.size, '->', cropped.size)
    print(f'{len(planned)} atlases compacted; {saved_pixels * 4:,} RGBA bytes removed; backups: {backup}')


if __name__ == '__main__':
    main()
