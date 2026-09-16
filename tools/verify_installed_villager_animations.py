"""Check villager animation references in installed packs, not just build outputs.

Example: --mods run/mods --mods build/dev-plugin
This is read-only and does not start a server. Cross-mod tables are checked
against the combined Common asset inventory of the actual selected jars.
"""
import argparse
from pathlib import Path
import re
import zipfile


def verify(folders):
    jars = sorted({p.resolve() for folder in folders for p in folder.glob('*.jar')
                   if not p.name.endswith(('-sources.jar', '-javadoc.jar'))})
    available = set()
    references = []
    for jar in jars:
        with zipfile.ZipFile(jar) as archive:
            for name in archive.namelist():
                if name.startswith('Common/'):
                    available.add(name.removeprefix('Common/'))
                elif name.startswith('Server/') and name.endswith('.json'):
                    for path in re.findall(r'"([^"\r\n]+\.blockyanim)"', archive.read(name).decode('utf-8-sig')):
                        if path.startswith(('Characters/Animations/Aetherhaven/', 'Items/Animations/Aetherhaven/', 'NPC/Gear/Animations/AetherhavenFaces/')):
                            references.append((jar, name, path))
    missing = [(jar, name, path) for jar, name, path in references if path not in available]
    if missing:
        for jar, name, path in missing[:20]:
            print(f'{jar.name}: {name}: missing {path}')
        raise SystemExit(f'{len(missing)} unresolved villager animation references across {len(jars)} installed jars.')
    if not references:
        raise SystemExit('No villager animation references found; select the actual mod directories.')
    print(f'Verified {len(references)} villager animation references across {len(jars)} installed jars; none missing.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mods', type=Path, action='append', required=True)
    verify(parser.parse_args().mods)
