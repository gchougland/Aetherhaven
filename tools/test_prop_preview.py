"""Regression checks for preview transforms and texture orientation."""
import math
import unittest
import numpy as np
from PIL import Image
from villager_prop_preview import world_shapes, textured_faces, geometry_points
from check_ledger_geometry import find_overlaps


def node(position, offset, children=None, angle=0):
    return {'position':dict(zip('xyz',position)),
            'orientation':{'z':math.sin(angle/2),'w':math.cos(angle/2)},
            'shape':{'type':'box','offset':dict(zip('xyz',offset)),
                     'settings':{'size':{'x':2,'y':2,'z':2}},
                     'textureLayout':{'front':{'offset':{'x':0,'y':0}}}},
            'children':children or []}


class PropPreviewTest(unittest.TestCase):
    def test_coplanar_checker_detects_partial_overlap(self):
        a=node((0,0,0),(0,0,0));b=node((1,0,0),(0,0,0))
        for i,n in enumerate((a,b)): n.update(id=str(i),name='fixture')
        issues=find_overlaps({'nodes':[a,b]})
        self.assertEqual(len(issues),1)
        self.assertEqual(issues[0][2],2)

    def test_coplanar_checker_allows_shared_edge(self):
        a=node((0,0,0),(0,0,0));b=node((2,0,0),(0,0,0))
        for i,n in enumerate((a,b)): n.update(id=str(i),name='fixture')
        self.assertEqual(find_overlaps({'nodes':[a,b]}),[])

    def test_child_inherits_shape_offset(self):
        child=node((24,-7,-8),(0,0,0))
        parent=node((-16,29,0),(-24,7,8),[child])
        shapes=list(world_shapes([parent]))
        np.testing.assert_allclose(shapes[0][1].mean(axis=0),(-40,36,8))
        np.testing.assert_allclose(shapes[1][1].mean(axis=0),(-16,29,0))

    def test_rotated_offset_and_child_frame(self):
        parent=node((10,0,0),(2,0,0),[node((0,3,0),(1,0,0))],math.pi/2)
        shapes=list(world_shapes([parent]))
        np.testing.assert_allclose(shapes[0][1].mean(axis=0),(10,2,0),atol=1e-12)
        np.testing.assert_allclose(shapes[1][1].mean(axis=0),(7,3,0),atol=1e-12)

    def test_front_texture_row_zero_is_upper_left(self):
        shape=node((0,0,0),(0,0,0))['shape']
        tex=Image.new('RGBA',(2,2),(0,0,0,0));tex.putpixel((0,0),(255,0,0,255))
        faces=list(textured_faces(geometry_points(shape),shape,tex))
        self.assertEqual(len(faces),1)
        np.testing.assert_allclose(faces[0][1].mean(axis=0),(-.5,.5,1))


if __name__=='__main__': unittest.main()
