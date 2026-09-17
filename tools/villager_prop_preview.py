"""Render actual blockymodel UV pixels as small shaded quads for prop review."""
import math
import numpy as np

def geometry_points(shape):
    size=shape['settings']['size'];stretch=shape.get('stretch',{})
    if shape['type']=='box':
        return np.array([[x,y,z] for x in (-.5,.5) for y in (-.5,.5) for z in (-.5,.5)]) * np.array([size[k]*stretch.get(k,1) for k in 'xyz'])
    normal=shape['settings'].get('normal','+Z');axis=normal[-1]
    u=np.array([1,0,0] if axis!='X' else [0,0,1],dtype=float)
    v=np.array([0,1,0] if axis!='Y' else [0,0,1],dtype=float)
    return np.array([u*x*size['x']+v*y*size['y'] for x,y in [(-.5,-.5),(.5,-.5),(.5,.5),(-.5,.5)]])*np.array([stretch.get(k,1) for k in 'xyz'])

def textured_faces(points, shape, texture):
    sx,sy=[int(shape['settings']['size'][k]) for k in 'xy']
    sz=int(shape['settings']['size'].get('z',1))
    # Image row zero is the top edge, while geometric +Y points upward.
    specifications=[('front',[3,2,1,0],sx,sy,.94)] if shape['type']=='quad' else [('left',[0,1,3,2],sz,sy,.72),('right',[5,4,6,7],sz,sy,.88),
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
                u=-(x+.5) if layout.get('mirror',{}).get('x') else x+.5
                v=-(y+.5) if layout.get('mirror',{}).get('y') else y+.5
                tx,ty=np.floor(offset+rotation@np.array([u,v])+1e-7).astype(int)
                assert 0<=tx<texture.width and 0<=ty<texture.height,(side,tx,ty)
                pixel=texture.getpixel((tx,ty))
                if pixel[3]<128:continue
                rgb=tuple(min(255,int(c*shade)) for c in pixel[:3])
                poly=np.array([at(x/width,y/height),at((x+1)/width,y/height),
                               at((x+1)/width,(y+1)/height),at(x/width,(y+1)/height)])
                yield (poly[:,2].mean(),poly,rgb,None)
