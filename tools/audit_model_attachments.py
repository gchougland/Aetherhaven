"""Report attachment roots absent from an authored model's assembled skeleton.

Read only. Resolve mod assets before native assets, and follow model inheritance.
This catches missing targets without changing or stripping cosmetic geometry.
"""
from pathlib import Path
import json
from functools import lru_cache
import argparse
import os
import zipfile

ROOT = Path(__file__).resolve().parents[1]
NATIVE = Path(os.environ.get('APPDATA', str(Path.home()/'AppData/Roaming'))) / 'Hytale/install/release/package/game/latest/Assets.zip'
RES = ROOT / 'src/main/resources'
ARCHIVES = []
MODELS = {}
ASSETS = {}
AUTHORED = set()
MODEL_FOLDERS = ('Server/Models/', 'Server/Aetherhaven/NpcModels/')

def read(path):
    if isinstance(path, tuple):
        archive, key = path
        return json.loads(archive.read(key).decode('utf-8-sig'))
    return json.loads(path.read_text(encoding='utf-8-sig'))

def configure(native, packs, authored=RES):
    for archive in ARCHIVES:
        archive.close()
    ARCHIVES.clear()
    MODELS.clear()
    ASSETS.clear()
    AUTHORED.clear()
    # Explicit pack order is the audit's override order. It is not a prediction
    # of the server's dependency ordering or its saved runtime attachments.
    for index, root in enumerate([native, authored, *packs]):
        if root.is_file():
            archive = zipfile.ZipFile(root)
            ARCHIVES.append(archive)
            entries = {key: (archive, key) for key in archive.namelist() if not key.endswith('/')}
        else:
            entries = {p.relative_to(root).as_posix(): p for p in root.rglob('*') if p.is_file()}
        ASSETS.update(entries)
        for key, source in entries.items():
            if key.startswith(MODEL_FOLDERS) and key.endswith('.json'):
                name = Path(key).stem
                MODELS[name] = source
                if index:
                    AUTHORED.add(name)
    model.cache_clear()
    mesh.cache_clear()

@lru_cache(None)
def model(name):
    data = read(MODELS[name])
    inherited = dict(model(data['Parent'])) if data.get('Parent') in MODELS else {}
    inherited.update(data)
    return inherited

@lru_cache(None)
def mesh(name):
    source = ASSETS.get('Common/'+name)
    if source is not None:
        return read(source).get('nodes', [])
    return []

def names(nodes):
    return {n.get('name') for n in nodes} | {v for n in nodes for v in names(n.get('children', []))}

def pieces(nodes):
    for node in nodes:
        if node.get('shape',{}).get('settings',{}).get('isPiece',False):
            yield node
        yield from pieces(node.get('children',[]))

def audit(name):
    definition = model(name)
    base = definition.get('Model', '')
    if not mesh(base):
        return [(base, ['MISSING BASE MODEL FILE'])]
    available = names(mesh(base))
    attachments = definition.get('DefaultAttachments', [])
    # isPiece marks attachment targets, including nested targets. Plain geometry
    # roots (for example Origin) need not target a matching base skeleton bone.
    # Resolve dependencies repeatedly, but never let a target satisfy itself.
    pending = [(a['Model'], mesh(a['Model'])) for a in attachments if a.get('Model')]
    while pending:
        ready = [(path, roots) for path, roots in pending if roots and all(n.get('name') in available for n in pieces(roots))]
        if not ready:
            return [(path, [n.get('name') for n in pieces(roots) if n.get('name') not in available] if roots else ['MISSING MODEL FILE']) for path, roots in pending]
        for entry in ready:
            available.update(names(entry[1]))
            pending.remove(entry)
    return []

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pack', action='append', type=Path, default=[], help='Additional asset directory, jar or zip to audit; later packs override earlier ones')
    parser.add_argument('--native', type=Path, default=NATIVE, help='Installed release Assets.zip')
    args = parser.parse_args()
    configure(args.native,args.pack)
    print('Native reference:',args.native)
    print('Scope: static DefaultAttachments only; runtime cosmetics and random attachment combinations need native inspection.')
    for name in sorted(AUTHORED):
        issues = audit(name)
        if issues:
            print(name, json.dumps(issues))
