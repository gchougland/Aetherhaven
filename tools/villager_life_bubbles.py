"""Lay out all thought icons in the bubble's content area, excluding its tail."""
from PIL import Image

BUBBLE_SCALE = .28
CONTENT_CENTER = (64, 51)
CANVAS_SIZE = 256
TAIL_TIP = (35, 110)
LAYER_OFFSET = (CANVAS_SIZE // 2 - TAIL_TIP[0], CANVAS_SIZE // 2 - TAIL_TIP[1])


def anchor_layer(source):
    result = Image.new('RGBA', (CANVAS_SIZE, CANVAS_SIZE))
    result.alpha_composite(source, LAYER_OFFSET)
    return result


def fit_icon(source):
    source = source.convert('RGBA')
    bounds = source.getchannel('A').point(lambda a: 255 if a >= 16 else 0).getbbox()
    result = Image.new('RGBA', (128, 128))
    if bounds is None:
        return anchor_layer(result)
    content = source.crop(bounds)
    content.thumbnail((52, 48), Image.Resampling.LANCZOS)
    result.alpha_composite(content, (CONTENT_CENTER[0] - content.width // 2,
                                     CONTENT_CENTER[1] - content.height // 2))
    return anchor_layer(result)
