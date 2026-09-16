"""Intern complete animation timelines, independent of voice, race and binding ID.

Animation sets and action tables keep their public IDs. Only the Common asset
path is shared. Duration, hold behavior, node names, UVs and every keyframe are
part of the identity; different rigs/recordings are never merged by filename.
"""
import copy
import hashlib
import json
from villager_blockyanim import complete_channels


def fingerprint(data):
    def normalize(value):
        if isinstance(value, dict):
            return {key: normalize(item) for key, item in value.items()}
        if isinstance(value, list):
            return [normalize(item) for item in value]
        if isinstance(value, float) and value.is_integer():
            return int(value)
        return value
    payload = json.dumps(normalize(complete_channels(copy.deepcopy(data))),
                         sort_keys=True, separators=(',', ':'))
    return hashlib.sha256(payload.encode()).hexdigest()


class AnimationPool:
    def __init__(self, resources, write, templates=()):
        self.common = resources.resolve() / 'Common'
        self.write = write
        self.assets = {}
        self.targets = set()
        self.reused = 0
        # Authored templates remain editable inputs. Prefer them over copies.
        for path in sorted(templates):
            self.assets.setdefault(fingerprint(json.loads(path.read_text())),
                                   path.resolve().relative_to(self.common).as_posix())

    def emit(self, target, data):
        digest = fingerprint(data)
        if digest in self.assets:
            self.reused += 1
            target = self.assets[digest]
        else:
            self.write(self.common / target, data)
            self.assets[digest] = target
        self.targets.add(target)
        return target

    def prune(self, folders):
        """Remove only obsolete outputs in explicitly owned generator folders."""
        removed = 0
        for folder in folders:
            root = (self.common / folder).resolve()
            if not root.is_relative_to(self.common) or root == self.common:
                raise ValueError(f'Unsafe animation output root: {root}')
            for path in root.rglob('*.blockyanim'):
                if path.resolve().relative_to(self.common).as_posix() not in self.targets:
                    path.unlink()
                    removed += 1
        print(f'Animation reuse: {self.reused} shared exports; removed {removed} obsolete files.', flush=True)
