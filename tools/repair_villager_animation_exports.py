"""Repair editor export artifacts without regenerating authored villager poses.

Run after importing edited Life body animations. Unknown non-neutral tracks are
rejected for review; neutral face/leg/cosmetic tracks must not mask other slots.
"""
import copy
import json
from pathlib import Path
from villager_blockyanim import complete_channels

ROOT = Path(__file__).resolve().parents[1]
LIFE = ROOT / 'src/main/resources/Common/Characters/Animations/Aetherhaven/Life'
BODY_NODES = {'Head', 'Neck', 'Chest', 'Belly', 'Book-Top', 'Book-Bot'} | {
    f'{side}-{bone}' for side in 'LR' for bone in ('Arm', 'Forearm', 'Hand', 'Attachment')}


def neutral(node):
    defaults = {'position': {'x': 0, 'y': 0, 'z': 0},
                'orientation': {'x': 0, 'y': 0, 'z': 0, 'w': 1},
                'shapeStretch': {'x': 1, 'y': 1, 'z': 1},
                'shapeUvOffset': {'x': 0, 'y': 0}}
    for channel, frames in node.items():
        if not frames:
            continue
        if channel not in defaults:
            return False
        for frame in frames:
            delta = frame['delta']
            if any(axis not in defaults[channel] or abs(value - defaults[channel][axis]) > 1e-8
                   for axis, value in delta.items()):
                return False
    return True


def repair(animation):
    result = copy.deepcopy(animation)
    nodes = result['nodeAnimations']
    for name in list(nodes):
        if name not in BODY_NODES:
            if not neutral(nodes[name]):
                raise ValueError(f'Unexpected animated body node: {name}; review before removing')
            del nodes[name]
    return complete_channels(result)


def main():
    # Validate every input before changing any files.
    updates = {}
    for path in sorted(LIFE.glob('*.blockyanim')):
        original = json.loads(path.read_text(encoding='utf-8'))
        corrected = repair(original)
        if corrected != original:
            updates[path] = corrected
        if path.stem in ('Read', 'ReadLoop'):
            held = copy.deepcopy(corrected)
            held['nodeAnimations'] = {name: node for name, node in held['nodeAnimations'].items()
                                      if name in ('Book-Top', 'Book-Bot')}
            target = LIFE / 'Held' / path.name
            if held != json.loads(target.read_text(encoding='utf-8')):
                updates[target] = held
    for path, data in updates.items():
        path.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')
        print(path.relative_to(ROOT))
    print(f'Repaired {len(updates)} animation exports without changing authored poses.')


if __name__ == '__main__':
    main()
