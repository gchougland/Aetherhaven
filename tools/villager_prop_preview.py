"""Render actual blockymodel UV pixels as small shaded quads for prop review."""
import math
import numpy as np


def textured_faces(points, shape, texture):
    sx,sy,sz=[int(shape['settings']['size'][k]) for k in 'xyz']
    specifications=[('left',[0,1,3,2],sz,sy,.72),('right',[5,4,6,7],sz,sy,.88),
                    ('bottom',[0,4,5,1],sx,sz,.60),('top',[2,6,7,3],sx,sz,1.06),
                    ('back',[4,0,2,6],sx,sy,.76),('front',[1,5,7,3],sx,sy,.94)]
    for side,indices,width,height,shade in specifications:
        layout=shape['textureLayout'].get(side)
        if layout is None:continue
        quad=points[indices];angle=math.radians(layout.get('angle',0))
        rotation=np.array([[math.cos(angle),-math.sin(angle)],[math.sin(angle),math.cos(angle)]])
        offset=np.array([layout['offset']['x'],layout['offset']['y']])
        def at(u,v):return quad[0]+(quad[1]-quad[0])*u+(quad[3]-quad[0])*v
        for y in range(height):
            for x in range(width):
                u=width-x-.5 if layout.get('mirror',{}).get('x') else x+.5
                v=height-y-.5 if layout.get('mirror',{}).get('y') else y+.5
                tx,ty=np.floor(offset+rotation@np.array([u,v])+1e-7).astype(int)
                assert 0<=tx<texture.width and 0<=ty<texture.height,(side,tx,ty)
                pixel=texture.getpixel((tx,ty))
                if pixel[3]<128:continue
                rgb=tuple(min(255,int(c*shade)) for c in pixel[:3])
                poly=np.array([at(x/width,y/height),at((x+1)/width,y/height),
                               at((x+1)/width,(y+1)/height),at(x/width,(y+1)/height)])
                yield (poly[:,2].mean(),poly,rgb,None)
