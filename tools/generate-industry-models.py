"""Writes the JSON assets of the add-on's industry machines and items.

For every machine: blockstate (facing × active), block model (Data Center's synthesizer with the machine's
front and tray, see generate-industry-textures.py), item model, loot table and the pickaxe tag entry.
For every item: a flat item model.

usage: python tools/generate-industry-models.py
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/data_center_addon')
DATA = os.path.join(ROOT, 'src/main/resources/data')

MACHINES = ['smelter', 'rolling_mill', 'wire_drawer']
ITEMS = ['plastic_pellets', 'silicon', 'copper_foil', 'gold_contacts', 'copper_wire']


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def machine(name):
    variants = {}
    for active in ('false', 'true'):
        for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
            v = {'model': f'data_center_addon:block/{name}'}
            if y:
                v['y'] = y
            variants[f'active={active},facing={facing}'] = v
    write(os.path.join(ASSETS, 'blockstates', name + '.json'), {'variants': variants})
    write(os.path.join(ASSETS, 'models/block', name + '.json'), {
        'parent': 'datacenter:block/synthesizer',
        'textures': {'front': f'data_center_addon:block/industry/{name}_front',
                     'tray': f'data_center_addon:block/industry/{name}_tray'}})
    write(os.path.join(ASSETS, 'models/item', name + '.json'), {'parent': f'data_center_addon:block/{name}'})
    write(os.path.join(DATA, 'data_center_addon/loot_table/blocks', name + '.json'), {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'data_center_addon:{name}'}],
                   'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})


def item(name):
    write(os.path.join(ASSETS, 'models/item', name + '.json'), {
        'parent': 'minecraft:item/generated',
        'textures': {'layer0': f'data_center_addon:item/industry/{name}'}})


def main():
    for name in MACHINES:
        machine(name)
    for name in ITEMS:
        item(name)
    tag = os.path.join(DATA, 'minecraft/tags/block/mineable/pickaxe.json')
    with open(tag, encoding='utf-8') as f:
        data = json.load(f)
    for name in MACHINES:
        if f'data_center_addon:{name}' not in data['values']:
            data['values'].append(f'data_center_addon:{name}')
    write(tag, data)
    print(f'Wrote assets of {len(MACHINES)} machines and {len(ITEMS)} items')


if __name__ == '__main__':
    main()
