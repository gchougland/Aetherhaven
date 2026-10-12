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

def textured_faces(points, shape, texture, strict=True):
    sx,sy=[int(shape['settings']['size'][k]) for k in 'xy']
    sz=int(shape['settings']['size'].get('z',1))
    # Image row zero is the top edge, while geometric +Y points upward.
    # TL, TR, BR, BL, matching BlockyModelLoader's native face mapping.
    specifications=[('front',[3,2,1,0],sx,sy,.94)] if shape['type']=='quad' else [('left',[2,3,1,0],sz,sy,.72),('right',[7,6,4,5],sz,sy,.88),
                    ('bottom',[1,5,4,0],sx,sz,.60),('top',[2,6,7,3],sx,sz,1.06),
                    ('back',[6,2,0,4],sx,sy,.76),('front',[3,7,5,1],sx,sy,.94)]
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
                if not (0<=tx<texture.width and 0<=ty<texture.height):
                    # Native cosmetics carry a few stray faces mapped off the sheet (Robe_Wizard Sleeve3).
                    if strict:raise AssertionError((side,tx,ty))
                    continue
                pixel=texture.getpixel((tx,ty))
                if pixel[3]<128:continue
                rgb=tuple(min(255,int(c*shade)) for c in pixel[:3])
                poly=np.array([at(x/width,y/height),at((x+1)/width,y/height),
                               at((x+1)/width,(y+1)/height),at(x/width,(y+1)/height)])
                yield (poly[:,2].mean(),poly,rgb,None)


def rotation_matrix(q):
    x,y,z,w=[q.get(k,0 if k!='w' else 1) for k in 'xyzw']
    norm=math.sqrt(x*x+y*y+z*z+w*w)
    x,y,z,w=[v/norm for v in (x,y,z,w)]
    return np.array([[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],
                     [2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],
                     [2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]])


def world_shapes(nodes, parent_rotation=None, parent_position=None):
    """Shape offsets move the node frame, including descendants, in blockymodels."""
    parent_rotation=np.eye(3) if parent_rotation is None else parent_rotation
    parent_position=np.zeros(3) if parent_position is None else parent_position
    for node in nodes:
        rotation=parent_rotation @ rotation_matrix(node.get('orientation',{}))
        shape=node.get('shape',{})
        position=parent_position+parent_rotation @ np.array([node.get('position',{}).get(k,0) for k in 'xyz'])
        position+=rotation @ np.array([shape.get('offset',{}).get(k,0) for k in 'xyz'])
        if shape.get('visible',True) and shape.get('type') in ('box','quad'):
            points=(rotation @ geometry_points(shape).T).T+position
            yield node,points
        yield from world_shapes(node.get('children',[]),rotation,position)
