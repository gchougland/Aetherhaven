"""Planned 4.0 alchemy buildings. Draft prefabs require in-game visual review.

Conservatory: U shaped sheltered potting rooms around an open crystal court.
Laboratory: tall brewing hall with a lower shop wing and rear ingredient pantry.
Furniture occupancy is derived from native hitboxes, including rotated fillers.
"""
import sys
if __name__ == '__main__' and '--publish' in sys.argv:
    raise SystemExit('Generated specialist publishing is retired. Use tools/import_v4_user_buildings.py '
                     'to import the authored Core prefabs. Removed Stormwind variants must not be recreated.')

from pathlib import Path
import base64
import copy
import json
import math
import uuid

ROOT = Path(__file__).resolve().parents[1]
NATIVE = ROOT.parent / 'HytaleSourceCode/hytale-shared-source/HytaleAssets'
OUT = ROOT / 'build/art/alchemy-prefabs'
ITEMS = {p.stem: p for p in (NATIVE/'Server/Item/Items').rglob('*.json')}
ITEMS.update({p.stem: p for p in (ROOT/'src/main/resources/Server/Item/Items').rglob('*.json')})
HITBOXES = {p.stem: p for p in (NATIVE/'Server/Item/Block/Hitboxes').rglob('*.json')}

def load(p):
    return json.loads(p.read_text(encoding='utf-8'))

def item_definition(name, visited=()):
    assert name not in visited, ('item inheritance cycle', name, visited)
    definition=load(ITEMS[name])
    parent=definition.get('Parent')
    if not parent: return definition
    def merge(base, changes):
        result=copy.deepcopy(base)
        for key,value in changes.items():
            result[key]=merge(result[key],value) if isinstance(value,dict) and isinstance(result.get(key),dict) else value
        return result
    return merge(item_definition(parent,visited+(name,)),definition)

def valid_block(name):
    if name=='Empty': return True
    if name.startswith('*'):
        base,separator,state=name[1:].partition('_State_Definitions_')
        return bool(separator and base in ITEMS and state in item_definition(base).get('BlockType',{}).get('State',{}).get('Definitions',{}))
    return name in ITEMS and 'BlockType' in item_definition(name)

def rectangle(x0, x1, z0, z1):
    return {(x,z) for x in range(x0,x1+1) for z in range(z0,z1+1)}

class Building:
    def __init__(self, name, stormwind, bounds):
        self.name, self.stormwind = name, stormwind
        self.blocks, self.entities, self.roofs, self.rooms = {}, [], {}, set()
        self.roof_joins = {}
        self.roof = 'Wood_Darkwood_Roof' if stormwind else 'Wood_Softwood_Roof'
        self.plank = 'Wood_Hardwood_Planks' if stormwind else 'Wood_Softwood_Planks'
        self.wall = 'Wood_Village_Wall_White_Full' if stormwind else 'Wood_Village_Wall_Full'
        self.stone = 'Rock_Stone_Brick_Smooth' if stormwind else 'Rock_Stone_Cobble'
        self.window = 'Furniture_Tavern_Window' if stormwind else 'Furniture_Village_Window'
        for x,z in rectangle(*bounds):
            for y in range(-1,13): self.put(x,y,z,'Empty')

    def put(self,x,y,z,name,rotation=0,filler=0):
        b = dict(x=x,y=y,z=z,name=name)
        if rotation: b['rotation']=rotation
        if filler: b['filler']=filler
        self.blocks[x,y,z]=b

    def empty(self,x,y,z):
        return self.blocks.get((x,y,z),{}).get('name','Empty')=='Empty'

    def furniture(self,x,y,z,name,rotation=0):
        definition=item_definition(name)
        block=definition.get('BlockType',{})
        hitbox=HITBOXES.get(block.get('HitboxType',''))
        occupied={(0,0,0)}
        if hitbox:
            boxes=load(hitbox).get('Boxes',[])
            if boxes:
                # Native FillerBlockUtil uses the enclosing bounding box, not
                # the union of detail boxes. Beds reserve air above the mattress.
                lo={axis:min(box['Min'][axis] for box in boxes) for axis in 'XYZ'}
                hi={axis:max(box['Max'][axis] for box in boxes) for axis in 'XYZ'}
                for dx in range(math.floor(lo['X']),max(math.ceil(hi['X']),math.floor(lo['X'])+1)):
                    for dy in range(math.floor(lo['Y']),max(math.ceil(hi['Y']),math.floor(lo['Y'])+1)):
                        for dz in range(math.floor(lo['Z']),max(math.ceil(hi['Z']),math.floor(lo['Z'])+1)):
                            # Rotate occupied unit cubes around the native block centre.
                            rx,rz=dx,dz
                            for _ in range(rotation): rx,rz=rz,-rx
                            occupied.add((rx,dy,rz))
        for dx,dy,dz in occupied:
            assert self.empty(x+dx,y+dy,z+dz),(name,'furniture collision',x+dx,y+dy,z+dz)
        for dx,dy,dz in occupied:
            filler=(dx&31)|((dz&31)<<5)|((dy&31)<<10)
            self.put(x+dx,y+dy,z+dz,name,rotation,filler)

    def room(self,x0,x1,z0,z1,axis='x',eave=3):
        self.rooms |= rectangle(x0,x1,z0,z1)
        for x,z in rectangle(x0-1,x1+1,z0-1,z1+1):
            a,lo,hi=(x,x0-1,x1+1) if axis=='x' else (z,z0-1,z1+1)
            mid=(lo+hi)/2
            # Even spans have two equal-height summit cells. Both need ridge
            # tiles; leaving one sloped exposes a vertical strip at the peak.
            ridge=abs(a-mid)<=.5
            candidate=(eave+min(a-lo,hi-a),3 if a<mid else 1,ridge)
            if axis=='z': candidate=(candidate[0],2 if a<mid else 0,candidate[2])
            old=self.roofs.get((x,z))
            if old is None or candidate[0]>old[0]:
                self.roofs[x,z]=candidate
                self.roof_joins[x,z]=set() if candidate[2] else {candidate[1]}
            elif candidate[0]==old[0] and not candidate[2]:
                self.roof_joins[x,z].add(candidate[1])
                if old[2]: self.roofs[x,z]=candidate

    def structure(self):
        for x,z in self.rooms:
            edge=any((x+dx,z+dz) not in self.rooms for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)])
            self.put(x,-1,z,self.stone if edge else self.plank)
            if edge:
                # Wall fill follows the roof at every boundary cell, closing gables.
                for y in range(self.roofs[x,z][0]):
                    name='*'+self.wall+'_State_Definitions_'+('Bottom' if y==0 else 'Top' if y==2 else 'Middle') if y<3 else self.plank
                    self.put(x,y,z,name)
                self.put(x,3,z,self.plank)
        for (x,z),(y,rot,ridge) in self.roofs.items():
            directions=self.roof_joins[x,z]
            corner=next((r for r in range(4) if directions=={r,(r+1)%4}),None)
            if corner is not None:
                # Concave valleys need both slopes. A straight tile leaves a triangular hole.
                self.put(x,y,z,'*'+self.roof+'_State_Definitions_Inverted_Corner_Left',corner)
            else: self.put(x,y,z,self.roof+'_Flat' if ridge else self.roof,rot)

    def doorway(self,x,z,width=2,along='x'):
        for d in range(width):
            for y in range(3): self.put(x+(d if along=='x' else 0),y,z+(d if along=='z' else 0),'Empty')

    def windows(self,locations):
        for x,z,rot in locations:
            for y in (1,2): self.put(x,y,z,self.window,rot)

    def post(self,x,z,height=4):
        for y in range(height): self.put(x,y,z,'Wood_Beech_Trunk')

    def prop(self,item,x,y,z,scale=1,yaw=0):
        template=load(ROOT/'src/main/resources/Server/Prefabs/Blacksmith_Shop.prefab.json')['entities'][0]
        entity=copy.deepcopy(template);c=entity['Components']
        # Block and item renderers are separate native entity paths. Item-only
        # decorations (cloth, hides, tools) must not advertise a block type.
        if valid_block(item): c['BlockEntity']['BlockTypeKey']=item
        else: c.pop('BlockEntity',None)
        c['Item']['Item']['Id']=item
        c['Transform']={'Position':{'X':x,'Y':y,'Z':z},'Rotation':{'Pitch':0.0,'Yaw':yaw,'Roll':0.0}}
        c['EntityScale']['Scale']=scale
        stable=uuid.uuid5(uuid.NAMESPACE_URL,f'aetherhaven/{self.name}/{self.stormwind}/{len(self.entities)}')
        c['UUID']['UUID']['$binary']=base64.b64encode(stable.bytes).decode()
        self.entities.append(entity)

    def save(self):
        for name in {b['name'] for b in self.blocks.values()}:
            assert valid_block(name),('missing block asset',name)
        for entity in self.entities:
            assert entity['Components']['Item']['Item']['Id'] in ITEMS,('missing decoration item',entity)
            block=entity['Components'].get('BlockEntity')
            assert not block or valid_block(block['BlockTypeKey']),('item decoration used as block',entity)
        for b in self.blocks.values():
            f=b.get('filler',0)
            if not f: continue
            def signed(n): return n-32 if n&16 else n
            dx,dz,dy=signed(f&31),signed((f>>5)&31),signed((f>>10)&31)
            anchor=self.blocks[b['x']-dx,b['y']-dy,b['z']-dz]
            assert anchor['name']==b['name'] and anchor.get('rotation',0)==b.get('rotation',0) and not anchor.get('filler'),('orphan filler',b)
        prefab=dict(version=8,blockIdVersion=11,anchorX=0,anchorY=0,anchorZ=0,blocks=list(self.blocks.values()),fluids=[],entities=self.entities)
        stem=self.name+('_Stormwind' if self.stormwind else '_Core')
        OUT.mkdir(parents=True,exist_ok=True)
        (OUT/(stem+'_Draft.prefab.json')).write_text(json.dumps(prefab,indent=2)+'\n',encoding='utf-8')
        stage=copy.deepcopy(prefab)
        stage['blocks'].extend(dict(x=x,y=-2,z=z,name='Rock_Stone_Brick_Smooth') for x,z in rectangle(-5,28,-8,27))
        (OUT/(stem+'_Stage.prefab.json')).write_text(json.dumps(stage,indent=2)+'\n',encoding='utf-8')
        print(stem, sum(b['name']!='Empty' for b in self.blocks.values()),'blocks',len(self.entities),'decorative entities')

def conservatory(stormwind):
    b=Building('Conservatory',stormwind,(0,20,0,21))
    b.room(2,18,12,18,'z')
    b.room(2,6,3,12,'x')
    b.room(14,18,5,12,'x')
    b.structure()
    for x,z in [(2,3),(6,3),(2,12),(6,12),(14,5),(18,5),(14,12),(18,12),(2,18),(10,18),(18,18)]:b.post(x,z)
    b.doorway(6,7,2,'z');b.doorway(14,8,2,'z');b.doorway(9,12,3)
    for x in (4,16):b.doorway(x,12,1)
    b.windows([(2,7,1),(2,10,1),(4,3,0),(18,8,1),(16,5,0),(5,18,0),(10,18,0),(15,18,0)])
    for x,z in [(4,3),(16,5)]: b.put(x,4,z,b.window)
    # Open court: two narrow seed beds flank a generous central approach.
    for x,z in rectangle(7,13,3,11):b.put(x,-1,z,b.stone)
    for x in (7,13):
        for z in range(5,11):b.put(x,-1,z,'Soil_Dirt_Crystal')
    for x,z in rectangle(8,12,0,2):b.put(x,-1,z,b.stone)
    for x in range(8,13): b.put(x,-1,-1,'Rock_Stone_Cobble_Stairs',2)
    # Flower boxes and living ivy distinguish the garden from an ordinary house.
    for x,z,flower in [(3,2,'Plant_Flower_Orchid_Pink'),(5,2,'Plant_Flower_Common_Cyan'),
                       (15,4,'Plant_Flower_Common_Violet2'),(17,4,'Plant_Flower_Common_White2')]:
        b.furniture(x,0,z,'Furniture_Village_Planter')
        b.put(x,1,z,flower)
    for x,z in [(2,2),(6,2),(14,4),(18,4)]:
        b.put(x,0,z,'Plant_Leaves_Oak')
    # Specimens occupy stone plinths, never usable crystal soil planting cells.
    for i,(x,z,crop) in enumerate([(7,5,'Health1'),(13,6,'Mana1'),(7,9,'Stamina1')]):
        b.put(x,-1,z,b.stone)
        b.put(x,0,z,'Rock_Stone_Brick_Smooth_Half')
        b.prop('Plant_Crop_'+crop,x+.5,.5,z+.5,.9,i*.35)
    b.furniture(5,0,5,'Bench_Farming')
    b.furniture(5,0,10,'Furniture_Village_Counter')
    b.furniture(16,0,7,'Bench_Farming')
    b.furniture(16,0,10,'Furniture_Village_Counter')
    b.furniture(5,0,16,'Furniture_Village_Table')
    b.furniture(7,0,16,'Furniture_Village_Stool')
    b.furniture(15,1,17,'Furniture_Village_Shelf_Full',2)
    b.furniture(17,0,16,'Aetherhaven_Management_Block')
    for x,z in [(4,8),(16,9),(7,15),(13,15)]:
        b.put(x,4,z,b.plank);b.furniture(x,3,z,'Deco_Lantern_Ceiling')
    b.prop('Plant_Crop_Health1',4.6,1.02,10.4,.8,.3)
    b.prop('Plant_Crop_Mana1',15.5,1.02,10.5,.8,-.4)
    b.prop('Plant_Crop_Stamina1',5.5,1.02,16.5,.8,.5)
    b.save()

def laboratory(stormwind):
    b=Building('Laboratory',stormwind,(0,23,0,22))
    b.room(8,16,3,17,'x',4)
    b.room(2,8,5,11,'z',3)
    b.room(16,20,12,18,'x',3)
    b.structure()
    for x,z in [(8,3),(12,3),(16,3),(8,17),(12,17),(16,17),(2,5),(2,11),(8,11),(20,12),(20,18)]:b.post(x,z,5 if x in (8,12,16) else 4)
    b.doorway(4,5,2);b.doorway(8,7,2,'z');b.doorway(16,14,2,'z')
    b.windows([(2,8,1),(5,11,0),(10,3,0),(14,3,0),(16,7,1),(16,10,1),(11,17,0),(14,17,0),(20,15,1)])
    for x in range(11,14):
        for y in (5,6): b.put(x,y,3,b.window)
    # A small reading loft breaks up the tall hall and is reached by real stairs.
    for x,z in rectangle(10,14,4,8): b.put(x,4,z,b.plank)
    for z in range(9,15):
        step_y=13-z
        for y in range(step_y): b.put(13,y,z,b.plank)
        b.put(13,step_y,z,'Wood_Hardwood_Stairs' if stormwind else 'Wood_Softwood_Stairs')
    fence='Wood_Hardwood_Fence' if stormwind else 'Wood_Softwood_Fence'
    for x in (10,11,12,14): b.put(x,5,8,fence)
    for x in (10,14):
        for z in range(4,8): b.put(x,5,z,fence,1)
    b.furniture(11,5,5,'Furniture_Village_Stool')
    b.furniture(12,5,4,'Furniture_Village_Table')
    b.prop('Deco_Book_Pile_Small',12.45,6.02,4.5,.7,.2)
    for x,z in rectangle(3,6,1,4):b.put(x,-1,z,b.stone)
    for x in range(3,7): b.put(x,-1,0,'Rock_Stone_Cobble_Stairs',2)
    for x,z in rectangle(3,6,3,4):b.put(x,3,z,b.roof+'_Flat')
    for x in (3,6):b.post(x,3,3)
    b.furniture(5,0,9,'Furniture_Village_Counter')
    b.furniture(3,0,9,'Furniture_Village_Stool')
    b.furniture(11,0,5,'Bench_Alchemy')
    b.furniture(11,0,10,'Bench_Alchemy')
    b.furniture(14,0,14,'Furniture_Village_Table')
    b.furniture(12,0,14,'Furniture_Village_Stool')
    b.furniture(10,0,15,'Alchemy_Cauldron')
    b.furniture(19,1,17,'Furniture_Village_Shelf_Full',2)
    b.furniture(18,0,13,'Furniture_Village_Crate')
    b.furniture(19,0,13,'Aetherhaven_Management_Block')
    # Stone chimney is independent of furniture and emerges through the rear roof.
    for y in range(9): b.put(8,y,16,b.stone)
    for x,z in [(5,8),(12,7),(12,12),(18,15)]:
        b.put(x,4,z,b.plank);b.furniture(x,3,z,'Deco_Lantern_Ceiling')
    b.prop('Potion_Health_Lesser',4.6,1.02,9.45,.7,.2)
    b.prop('Potion_Stamina_Lesser',5.25,1.02,9.4,.7,-.4)
    b.prop('Plant_Crop_Health1',14.45,1.02,14.45,.7,.6)
    b.prop('Ingredient_Charcoal',18.5,1.02,13.5,.65,.7)
    b.save()

if __name__=='__main__':
    for stormwind in (False,True):
        conservatory(stormwind)
        laboratory(stormwind)
