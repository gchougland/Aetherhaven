"""Pack generated transparent art as compact 64px particles, then author brief head accents."""
import copy,json
from pathlib import Path
from PIL import Image,ImageDraw
from generate_villager_life_assets import RES,ROOT,write_json

SOURCES={
 'Surprise':'exec-be6f78ec-2c8d-415b-bc28-0da329c6c2c6.png',
 'Confusion':'exec-c0d32765-709f-451a-8e49-16d69ba22e6b.png',
 'Question':'exec-5277c36d-de70-4287-ad2d-ae09b42919ee.png',
 'Shock':'exec-8bb9345b-c291-46d1-8d5c-147902429daf.png',
 'Gloom':'exec-a06348d2-8657-45fc-93f6-cddbbbf69c17.png'}

def main():
    source=Path.home()/'.codex/generated_images/01a0a0b1-e162-7fd1-b297-1f5761ec1952'
    target=RES/'Common/Particles/Aetherhaven/Emotions';target.mkdir(parents=True,exist_ok=True)
    sheet=Image.new('RGBA',(160*5,190),'#303a49')
    for i,(name,filename) in enumerate(SOURCES.items()):
        path=target/(name+'.png')
        if (source/filename).exists():
            original=Image.open(source/filename).convert('RGBA')
            assert original.getchannel('A').getextrema()[0]==0,'Must preserve real transparency'
            bounds=original.getchannel('A').point(lambda v:255 if v>=16 else 0).getbbox()
            sprite=original.crop(bounds);sprite.thumbnail((58,58),Image.Resampling.LANCZOS)
            packed=Image.new('RGBA',(64,64));packed.alpha_composite(sprite,((64-sprite.width)//2,(64-sprite.height)//2));packed.save(path)
        sprite=Image.open(path);assert sprite.size==(64,64)
        sheet.alpha_composite(sprite.resize((128,128),Image.Resampling.NEAREST),(i*160+16,10))
        ImageDraw.Draw(sheet).text((i*160+14,153),name,fill='white')
        # Clone the exact proven loved-gift Hearts effect, changing only the
        # texture and tint. Keep its renderer, sizes, opacity, timing and motion.
        assets=ROOT.parent/'HytaleSourceCode/hytale-shared-source/HytaleAssets'
        spawn=json.loads((assets/'Server/Particles/NPC/Emotions/Spawners/Hearts.particlespawner').read_text())
        system=json.loads((assets/'Server/Particles/NPC/Emotions/Hearts.particlesystem').read_text())
        spawn['Particle']['Texture']=f'Particles/Aetherhaven/Emotions/{name}.png'
        for frame in [spawn['Particle']['InitialAnimationFrame'],*spawn['Particle']['Animation'].values()]:
            if 'Color' in frame:frame['Color']='#ffffff'
        system['Spawners'][0]['SpawnerId']='Aetherhaven_Emotion_'+name
        folder=RES/'Server/Particles/Aetherhaven/Emotions'
        write_json(folder/f'Aetherhaven_Emotion_{name}.particlespawner',spawn)
        write_json(folder/f'Aetherhaven_Emotion_{name}.particlesystem',system)
    sheet.convert('RGB').save(ROOT/'build/villager-life-preview/emotion-particles.png')
    print('Packed five original 64x64 transparent emotion sprites and effects based on the original loved-gift Hearts system.')

if __name__=='__main__':main()
