"""Render the native sheep including its fleece, omitted by the native generated icon."""
import io, json, math, zipfile
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
from villager_prop_preview import world_shapes, textured_faces
from render_villager_portrait import frames

ROOT = Path(__file__).resolve().parents[1]
ASSETS = Path.home() / 'AppData/Roaming/Hytale/install/release/package/game/latest/Assets.zip'

def render():
    with zipfile.ZipFile(ASSETS) as assets:
        def model(path): return json.loads(assets.read('Common/' + path))
        def texture(path): return Image.open(io.BytesIO(assets.read('Common/' + path))).convert('RGBA')
        definition = json.loads(assets.read('Server/Models/Livestock/Sheep.json'))
        body = model(definition['Model'])
        bones = frames(body['nodes'])
        tex = texture(definition['Texture'])
        geometry = [(node, points, tex) for node, points in world_shapes(body['nodes'])]
        wool = definition['RandomAttachmentSets']['Fleece']['Wool']
        tex = texture(wool['Texture'])
        for root in model(wool['Model'])['nodes']:
            rotation, position = bones[root['name']]
            geometry.extend((node, points, tex) for node, points in world_shapes(root.get('children', []), rotation, position))
    yaw, pitch = math.radians(-35), math.radians(15)
    camera = np.array([[1,0,0],[0,math.cos(pitch),-math.sin(pitch)],[0,math.sin(pitch),math.cos(pitch)]]) @ np.array([[math.cos(yaw),0,math.sin(yaw)],[0,1,0],[-math.sin(yaw),0,math.cos(yaw)]])
    faces = [face for node, points, tex in geometry for face in textured_faces((camera @ points.T).T, node['shape'], tex)]
    points = np.concatenate([face[1] for face in faces])
    low, high = points.min(axis=0), points.max(axis=0)
    center = (low + high) / 2
    scale = 460 / max((high-low)[:2])
    image = Image.new('RGBA', (512,512)); draw = ImageDraw.Draw(image)
    for _, points, rgb, _ in sorted(faces, key=lambda face:face[0]):
        draw.polygon([(256+(p[0]-center[0])*scale,256-(p[1]-center[1])*scale) for p in points],fill=(*rgb,255))
    output=ROOT/'src/main/resources/Common/Icons/ModelsGenerated/Aetherhaven_Barn_Sheep.png'
    image.resize((64,64),Image.Resampling.LANCZOS).save(output)
    print(output)

if __name__ == '__main__': render()
