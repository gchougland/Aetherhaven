"""Complete the node schema expected by Hytale's client animation reader.

The server's BlockyAnimationCache only decodes duration, so a successful server
decode does not validate these channels. Vanilla exports include empty arrays
for unused channels; omitting them causes client-side NullReferenceExceptions.
"""

CHANNELS = ('position', 'orientation', 'shapeStretch', 'shapeVisible', 'shapeUvOffset')


def complete_channels(animation):
    for node in animation['nodeAnimations'].values():
        for channel in CHANNELS:
            node.setdefault(channel, [])
    return animation
