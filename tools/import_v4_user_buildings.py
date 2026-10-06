"""Import the reviewed 4.0 buildings without editing the author's Downloads files.

Original generated assets are copied into build/backups before every replacement.
This importer adds only gameplay furnishings, planted crops, and export cleanup.
The native plot creator generates the final icons after these assets are loaded.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
import shutil
import sys
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVER = ROOT / 'src/main/resources/Server'
COMMON = ROOT / 'src/main/resources/Common'
NATIVE = ROOT.parent / 'HytaleSourceCode/hytale-shared-source/HytaleAssets'
BUILDINGS = SERVER / 'Aetherhaven/Buildings'
SPECS = {
    'alchemist': ('Alchemist_Lab', 'Laboratory', (-4, 2, -2), (-3, 1, 1), 'mix', 'South'),
    'doctor': ('Clinic', 'Clinic', (-1, 2, -4), (0, 1, -2), 'read', 'South'),
    'druid': ('Druid_Sanctuary', 'Conservatory', (2, 1, 3), (1, 1, -1), 'tend', 'South'),
    'monster_hunter': ('Monster_Arena', 'Hunter_Lodge', (-6, 1, -2), (-7, 1, 1), 'read', 'South'),
    'warehouse': ('Warehouse', 'Warehouse', (4, 1, 2), (3, 1, 1), 'read', 'East'),
}
RETIRED = ('alchemist', 'doctor', 'druid', 'monster_hunter', 'warehouse', 'crafter', 'leatherworker')
CROPS = (
    ('Health1', (-6, 1, -1)), ('Health2', (-6, 1, 1)), ('Health3', (-5, 1, 3)),
    ('Mana1', (-4, 1, -2)), ('Mana2', (-4, 1, 0)), ('Mana3', (-4, 1, 3)),
    ('Stamina1', (-2, 1, -2)), ('Stamina2', (-2, 1, 2)), ('Stamina3', (-1, 1, 1)),
)


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    args = parser.parse_args()
    source = args.source.resolve()
    backup = ROOT / 'build/backups' / ('v4-generated-buildings-' + datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S'))
    items = {p.stem: read(p) for root in (NATIVE/'Server/Item/Items', SERVER/'Item/Items') for p in root.rglob('*.json')}

    def save_original(path):
        path = path.resolve()
        assert path.is_relative_to(ROOT), path
        if path.is_file():
            out = backup / path.relative_to(ROOT)
            out.parent.mkdir(parents=True, exist_ok=True)
            if not out.exists():
                shutil.copy2(path, out)

    def block_asset(name):
        asset = items.get(name, {})
        return 'BlockType' in asset or bool(asset.get('Parent') and block_asset(asset['Parent']))

    reports = []
    for kind, (original, target, management, stand, activity, front) in SPECS.items():
        source_path = source / (original + '.prefab.json')
        prefab_path = SERVER / 'Prefabs' / (target + '.prefab.json')
        definition_path = BUILDINGS / ('plot_' + kind + '.json')
        for path in (prefab_path, definition_path):
            save_original(path)
        prefab = read(source_path)
        cells = {(b['x'], b['y'], b['z']): b for b in prefab['blocks']}
        changes = []

        def place(pos, name, rotation=0, replace=()):
            old = cells.get(pos, {'name': 'Empty'})
            assert old['name'] == 'Empty' or old['name'] in replace, (kind, pos, old)
            cells[pos] = dict(zip('xyz', pos), name=name)
            if rotation:
                cells[pos]['rotation'] = rotation
            changes.append({'position': pos, 'from': old['name'], 'to': name})

        for b in cells.values():
            component_holder = b.get('components', {})
            components = component_holder.get('Components', {})
            components.pop('PlacedByInteraction', None)
            if not components:
                b.pop('components', None)
        cleaned_entities = []
        for entity in prefab.get('entities', []):
            components = entity.get('Components', {})
            item = components.get('Item', {}).get('Item', {}).get('Id', '')
            # A hidden editor NPC and decorative crop copies must not spawn as town inhabitants/harvests.
            if kind == 'druid' and ('NPC' in components or item.startswith('Plant_Crop_')):
                continue
            block = components.get('BlockEntity', {}).get('BlockTypeKey')
            if block and not block_asset(block):
                components.pop('BlockEntity', None)
            cleaned_entities.append(entity)
        prefab['entities'] = cleaned_entities

        if kind == 'druid':
            place(management, 'Aetherhaven_Management_Block', 2)
            place(stand, 'Empty', replace=('Plant_Moss_Rug_Green',))
            for crop, pos in CROPS:
                assert cells[pos[0], pos[1] - 1, pos[2]]['name'] == 'Soil_Dirt_Crystal'
                place(pos, 'Plant_Crop_' + crop, replace=('Plant_Moss_Rug_Green', 'Plant_Moss_Rug_Lime', 'Plant_Moss_Green'))
                if crop.endswith('3'):
                    upper = (pos[0], pos[1] + 1, pos[2])
                    place(upper, 'Plant_Crop_' + crop)
                    cells[upper]['filler'] = 1024
        elif kind == 'warehouse':
            place(management, 'Aetherhaven_Management_Block')
            place((3, 1, 2), 'Aetherhaven_Warehouse_Ledger_Desk')
            place((2, 1, 2), 'Aetherhaven_Warehouse_Ledger_Desk')
            cells[2, 1, 2]['filler'] = 31  # Native ledger hitbox extends one cell to the left.
        elif kind == 'monster_hunter':
            place(management, 'Aetherhaven_Management_Block')
            place((3, 0, 0), 'Aetherhaven_Hunt_Beacon', replace=('Soil_Pathway',))
            for dx in (0, 1):
                for dy in (0, 1):
                    place((-7 + dx, 1 + dy, 2), 'Aetherhaven_Production_Storage', 2)
                    if dx or dy:
                        cells[-7 + dx, 1 + dy, 2]['filler'] = dx | (dy << 10)
        assert cells[management]['name'] == 'Aetherhaven_Management_Block'
        prefab['blocks'] = list(cells.values())
        definition = read(definition_path)
        definition['plotAnchorOffset'] = [0, 0, 0]
        definition['managementBlockLocalPos'] = list(management)
        definition['frontFacing'] = front
        definition['boundsLocal'] = {
            'min': [min(p[i] for p in cells) for i in range(3)],
            'max': [max(p[i] for p in cells) for i in range(3)],
        }
        definition['autonomyNavRoofExclusionYBelowMaxY'] = 3.0
        definition['pois'] = [{
            'localX': stand[0], 'localY': stand[1], 'localZ': stand[2],
            'tags': ['WORK', 'workActivity:' + activity], 'capacity': 1,
            'blockTypeId': cells[stand[0], stand[1] - 1, stand[2]]['name'],
            'interactionKind': 'WORK_SURFACE',
            'workResidentKind': 'warehouse_manager' if kind == 'warehouse' else kind,
            'interactionTargetYawDegrees': 90.0 if kind == 'alchemist' else 180.0 if kind in ('warehouse', 'monster_hunter') else 0.0,
        }]
        solids = [b for b in prefab['blocks'] if b['name'] != 'Empty' and not b.get('filler')]
        wood = sum(b['name'].lstrip('*').startswith('Wood_') for b in solids)
        rock = sum(b['name'].startswith('Rock_') for b in solids)
        definition['materials'] = [
            {'resourceTypeId': 'Wood_All', 'count': ((wood + 9) // 10) * 10},
            {'resourceTypeId': 'Rock', 'count': ((rock + 9) // 10) * 10},
        ]
        for item, count in {
            'alchemist': [('Bench_Alchemy', 1)], 'doctor': [('Cloth_Block_Wool_White', 12)],
            'druid': [('Bench_Farming', 1), ('Soil_Dirt_Crystal', 34)],
            'monster_hunter': [('Aetherhaven_Hunt_Beacon', 1)],
            'warehouse': [('Aetherhaven_Warehouse_Ledger_Desk', 1)],
        }[kind]:
            definition['materials'].append({'itemId': item, 'count': count})
        write(prefab_path, prefab)
        write(definition_path, definition)
        reports.append({'id': definition['id'], 'source': str(source_path),
                        'sourceSha256': hashlib.sha256(source_path.read_bytes()).hexdigest(),
                        'prefab': definition['prefabPath'], 'changes': changes,
                        'removedEntities': len(read(source_path).get('entities', [])) - len(cleaned_entities)})

    decoration_path = BUILDINGS / 'plot_simple_workplace.json'
    decoration_prefab = SERVER / 'Prefabs/Simple_Workplace.prefab.json'
    for path in (decoration_path, decoration_prefab):
        save_original(path)
    shutil.copy2(source / 'Simple_Workplace.prefab.json', decoration_prefab)
    write(decoration_path, {
        'id': 'plot_simple_workplace', 'displayName': 'Simple Workplace',
        'description': 'A sheltered space for your own workbenches and furnishings.',
        'prefabPath': 'Simple_Workplace.prefab.json', 'plotTokenItemId': 'Aetherhaven_Plot_Token',
        'decorationPlot': True, 'excludeFromTownJournal': True, 'tags': ['decoration'],
        'styleId': 'core', 'plotAnchorOffset': [0, 0, 0], 'rotationYaw': 'None', 'frontFacing': 'South',
        'boundsLocal': {'min': [-3, 0, -3], 'max': [4, 6, 4]},
        'selfBuildGameDays': 1, 'treasuryGoldCoinCost': 15,
        'materials': [{'resourceTypeId': 'Wood_All', 'count': 130}],
        'displayNameLangKey': 'aetherhaven_buildings.aetherhaven.buildings.plot_simple_workplace.displayName',
    })
    reports.append({'id': 'plot_simple_workplace', 'source': str(source/'Simple_Workplace.prefab.json'),
                    'sourceSha256': hashlib.sha256((source/'Simple_Workplace.prefab.json').read_bytes()).hexdigest(),
                    'prefab': 'Simple_Workplace.prefab.json', 'changes': [], 'removedEntities': 0})

    retired = []
    for kind in RETIRED:
        path = BUILDINGS / 'Stormwind' / ('plot_stormwind_' + kind + '.json')
        if not path.exists():
            continue
        definition = read(path)
        for target in (path, SERVER/'Prefabs'/definition['prefabPath'],
                       BUILDINGS/'PrefabMaterials'/('plot_stormwind_' + kind + '.json')):
            target = target.resolve()
            assert target.is_relative_to(SERVER.resolve()), target
            if target.exists():
                save_original(target)
                target.unlink()
                retired.append(str(target.relative_to(ROOT)))
    sys.path.insert(0, str(ROOT/'scripts'))
    import generate_prefab_materials as materials
    conversions = materials.load_conversions()
    for report in reports:
        out = BUILDINGS/'PrefabMaterials'/(report['id']+'.json')
        save_original(out)
        counts = materials.count_prefab_blocks(SERVER/'Prefabs'/report['prefab'], conversions)
        write(out, {'constructionId': report['id'], 'prefabPath': report['prefab'],
                    'materials': materials.material_counts_to_json(counts)})
    write(backup/'import-manifest.json', {'imports': reports, 'removedStormwindAssets': retired})
    write(ROOT/'build/v4-user-buildings-import.json', {'backup': str(backup), 'imports': reports})
    print('Imported six author prefabs. Original assets backed up to', backup)


if __name__ == '__main__':
    main()
