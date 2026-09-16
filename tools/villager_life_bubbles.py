"""Lay out all thought icons in the bubble's content area, excluding its tail."""
from PIL import Image

BUBBLE_SCALE = .28
CONTENT_CENTER = (32, 32)
CANVAS_SIZE = 64
TAIL_TIP = (64, 123)


def anchor_layer(source):
    return source


def fit_icon(source):
    source = source.convert('RGBA')
    bounds = source.getchannel('A').point(lambda a: 255 if a >= 16 else 0).getbbox()
    result = Image.new('RGBA', (64, 64))
    if bounds is None:
        return anchor_layer(result)
    content = source.crop(bounds)
    content.thumbnail((52, 48), Image.Resampling.LANCZOS)
    result.alpha_composite(content, (CONTENT_CENTER[0] - content.width // 2,
                                     CONTENT_CENTER[1] - content.height // 2))
    return anchor_layer(result)
