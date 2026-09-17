"""Complete the node schema expected by Hytale's client animation reader.

The server's BlockyAnimationCache only decodes duration, so a successful server
decode does not validate these channels. Vanilla exports include empty arrays
for unused channels; omitting them causes client-side NullReferenceExceptions.
"""
import math

CHANNELS = ('position', 'orientation', 'shapeStretch', 'shapeVisible', 'shapeUvOffset')


def complete_channels(animation):
    for node in animation['nodeAnimations'].values():
        for channel in CHANNELS:
            node.setdefault(channel, [])
        for frame in node['orientation']:
            q = frame['delta']
            squared_length = sum(q[axis] ** 2 for axis in 'xyzw')
            # Editor exports can round unit quaternions to five decimal places.
            # Repair only rounding drift; a malformed rotation must still fail.
            if 1e-5 < abs(squared_length - 1) < 1e-3:
                length = math.sqrt(squared_length)
                frame['delta'] = {axis: round(q[axis] / length, 7) for axis in 'xyzw'}
    return animation
