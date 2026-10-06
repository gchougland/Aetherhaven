"""Generate the 4.0 warehouse draft for in-game review, not automatic release.

Floor plan: main hall x2..12/z2..12, ledger wing x13..18/z8..12,
front loading canopy x4..10/z-1..1. Two pitched roofs meet over the
inner corner. Native filler cells are explicit and furniture cannot
silently overwrite structural blocks. Run from the repository root.
"""
from pathlib import Path
import base64
import copy
import json
import uuid
import argparse
import sys

RETIRED_PUBLISH = ('Generated specialist publishing is retired. Use tools/import_v4_user_buildings.py '
                   'to import the authored Core prefabs. Removed Stormwind variants must not be recreated.')
if __name__ == '__main__' and '--publish' in sys.argv:
    raise SystemExit(RETIRED_PUBLISH)

from generate_v4_alchemy_buildings import valid_block

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'build/art/warehouse-prefabs'
OUT.mkdir(parents=True, exist_ok=True)


def generate(stormwind=False):
    blocks = {}
    roof = 'Wood_Darkwood_Roof' if stormwind else 'Wood_Softwood_Roof'
    plank = 'Wood_Hardwood_Planks' if stormwind else 'Wood_Softwood_Planks'
    wall = 'Wood_Village_Wall_White_Full' if stormwind else 'Wood_Village_Wall_Full'
    stone = 'Rock_Stone_Brick_Smooth' if stormwind else 'Rock_Stone_Cobble'

    def put(x,y,z,name,rotation=0,filler=0):
        block=dict(x=x,y=y,z=z,name=name)
        if rotation: block['rotation']=rotation
        if filler: block['filler']=filler
        blocks[x,y,z]=block

    def empty(x,y,z): return blocks.get((x,y,z),{}).get('name','Empty')=='Empty'

    def furnishing(x,y,z,name,rotation=0,fillers=()):
        cells=[(0,0,0),*fillers]
        for dx,dy,dz in cells:
            assert empty(x+dx,y+dy,z+dz), (name,'collision',(x+dx,y+dy,z+dz),blocks.get((x+dx,y+dy,z+dz)))
        for dx,dy,dz in cells:
            packed=(dx&31)|((dz&31)<<5)|((dy&31)<<10)
            put(x+dx,y+dy,z+dz,name,rotation,packed)

    # Explicit air prevents old foliage appearing in the rooms after construction.
    for x in range(0,21):
        for z in range(-2,16):
            for y in range(-1,12): put(x,y,z,'Empty')
    hall={(x,z) for x in range(2,13) for z in range(2,13)}
    office={(x,z) for x in range(13,19) for z in range(8,13)}
    rooms=hall|office
    porch={(x,z) for x in range(4,11) for z in range(-1,2)}
    for x,z in rooms|porch:
        put(x,-1,z,plank if (x,z) in rooms else stone)
    for x in range(5,10): put(x,-1,-2,'Rock_Stone_Brick_Stairs' if stormwind else 'Rock_Stone_Cobble_Stairs',2)
    # A narrow foundation edge gives the irregular footprint a readable silhouette.
    for x,z in rooms:
        if any((x+dx,z+dz) not in rooms for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)]): put(x,-1,z,stone)

    # Roof surfaces use the upper envelope at the intersection. Lower interior
    # roof fragments are omitted, so neither roof passes through the other room.
    surfaces={}
    joins={}
    for x in range(1,14):
        for z in range(1,14):
            surfaces[x,z]=(3+min(x-1,13-x),3 if x<7 else 1, x==7)
            joins[x,z]=set() if x==7 else {surfaces[x,z][1]}
    for x in range(11,20):
        for z in range(7,14):
            candidate=(3+min(z-7,13-z),2 if z<10 else 0,z==10)
            old=surfaces.get((x,z))
            if old is None or candidate[0]>old[0]:
                surfaces[x,z]=candidate
                joins[x,z]=set() if candidate[2] else {candidate[1]}
            elif candidate[0]==old[0] and not candidate[2]:
                joins[x,z].add(candidate[1])
                if old[2]: surfaces[x,z]=candidate
    for x,z in rooms:
        edge=any((x+dx,z+dz) not in rooms for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)])
        if edge:
            roof_y=surfaces[x,z][0]
            for y in range(roof_y):
                put(x,y,z,('*'+wall+'_State_Definitions_'+('Bottom' if y==0 else 'Top' if y==2 else 'Middle')) if y<3 else plank)
    # Timber posts and eave ties follow both rooms, including their reentrant corner.
    for x,z in [(2,2),(7,2),(12,2),(2,7),(2,12),(7,12),(12,12),(18,12),(18,8),(13,8)]:
        for y in range(4): put(x,y,z,'Wood_Beech_Trunk')
    for x,z in rooms:
        if any((x+dx,z+dz) not in rooms for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)]): put(x,3,z,plank)
    # Main loading entrance is broad and level. Ledger office connects internally.
    for x in range(6,9):
        for y in range(3): put(x,y,2,'Empty')
    for z in (9,10):
        for y in range(3): put(12,y,z,'Empty')
    for x,z,rotation in [(2,5,1),(2,9,1),(5,12,0),(9,12,0),(15,12,0),(15,8,0),(18,10,1)]:
        for y in (1,2): put(x,y,z,'Furniture_Tavern_Window' if stormwind else 'Furniture_Village_Window',rotation)
    for (x,z),(y,rotation,ridge) in surfaces.items():
        corner=next((r for r in range(4) if joins[x,z]=={r,(r+1)%4}),None)
        if corner is not None:
            put(x,y,z,'*'+roof+'_State_Definitions_Inverted_Corner_Left',corner)
        else:
            put(x,y,z,roof+'_Flat' if ridge else roof,rotation)
    # Covered loading porch has a low flat canopy below the main front gable.
    for x in range(4,11):
        for z in range(-1,2): put(x,3,z,roof+'_Flat')
    for x in (4,10):
        for y in range(3): put(x,y,-1,'Wood_Beech_Trunk')
    # Tall triangular gables are enclosed above the front opening.
    for y in range(4,surfaces[7,2][0]): put(7,y,2,'Wood_Beech_Trunk')
    for x in (6,8): put(x,5,2,'Furniture_Village_Window')

    # Keep a three block clear route through the centre of the store hall.
    for x,z,height in [(3,4,2),(3,5,1),(3,6,2),(11,4,1),(11,5,2),(10,10,2),(11,10,1),(3,10,1)]:
        for y in range(height): furnishing(x,y,z,'Furniture_Village_Crate')
    for x in (4,5,9,10): furnishing(x,1,11,'Furniture_Village_Shelf_Full',2)
    furnishing(16,0,11,'Aetherhaven_Warehouse_Ledger_Desk',0,[(-1,0,0)])
    furnishing(17,0,9,'Furniture_Village_Chair',2,[(0,1,0)])
    furnishing(14,0,11,'Aetherhaven_Management_Block',0)
    furnishing(5,0,8,'Furniture_Village_Counter',0,[(-1,0,0)])
    for z in (5,8):
        for x in range(3,12): put(x,4,z,plank)
    for x in range(13,18): put(x,4,10,plank)
    for x,z in [(5,5),(9,8),(16,10)]: furnishing(x,3,z,'Deco_Lantern_Ceiling')

    template=json.loads((ROOT/'src/main/resources/Server/Prefabs/Blacksmith_Shop.prefab.json').read_text(encoding='utf-8'))['entities'][0]
    entities=[]
    for i,(item,x,y,z,scale,yaw) in enumerate([
        ('Furniture_Village_Crate',4.55,1.08,8.45,.35,.35),
        ('Food_Bread',3.5,1.02,10.5,.75,-.4),
        ('Ingredient_Charcoal',10.5,2.02,10.5,.85,.8),
    ]):
        entity=copy.deepcopy(template);c=entity['Components']
        if valid_block(item): c['BlockEntity']['BlockTypeKey']=item
        else: c.pop('BlockEntity',None)
        c['Item']['Item']['Id']=item
        c['Transform']={'Position':{'X':x,'Y':y,'Z':z},'Rotation':{'Pitch':0.0,'Yaw':yaw,'Roll':0.0}}
        c['EntityScale']['Scale']=scale
        stable=uuid.uuid5(uuid.NAMESPACE_URL,f'aetherhaven/warehouse/{stormwind}/{i}')
        c['UUID']['UUID']['$binary']=base64.b64encode(stable.bytes).decode()
        entities.append(entity)
    # No furniture filler points to missing or differently rotated anchors.
    for b in blocks.values():
        f=b.get('filler',0)
        if not f:continue
        def signed(n): return n-32 if n&16 else n
        dx,dz,dy=signed(f&31),signed((f>>5)&31),signed((f>>10)&31)
        anchor=blocks[b['x']-dx,b['y']-dy,b['z']-dz]
        assert anchor['name']==b['name'] and anchor.get('rotation',0)==b.get('rotation',0) and not anchor.get('filler')
    prefab={'version':8,'blockIdVersion':11,'anchorX':0,'anchorY':0,'anchorZ':0,'blocks':list(blocks.values()),'fluids':[],'entities':entities}
    filename='Warehouse_Stormwind_Draft.prefab.json' if stormwind else 'Warehouse_Core_Draft.prefab.json'
    (OUT/filename).write_text(json.dumps(prefab,indent=2)+'\n',encoding='utf-8')
    stage=copy.deepcopy(prefab)
    stage['blocks'].extend(dict(x=x,y=-2,z=z,name='Rock_Stone_Brick_Smooth') for x in range(-5,26) for z in range(-12,20))
    (OUT/filename.replace('_Draft','_Stage')).write_text(json.dumps(stage,indent=2)+'\n',encoding='utf-8')
    print(filename,len(blocks),'cells',sum(b['name']!='Empty' for b in blocks.values()),'visible blocks',len(entities),'props')
    return prefab

def publish(prefabs):
    """Historical drafts remain available, but cannot replace authored buildings."""
    raise SystemExit(RETIRED_PUBLISH)

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--publish',action='store_true',help='Retired. Use tools/import_v4_user_buildings.py instead')
    args=parser.parse_args()
    prefabs={style:generate(style) for style in (False,True)}
    if args.publish:publish(prefabs)

