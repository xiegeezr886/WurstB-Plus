#!/usr/bin/env python3
"""Check a Fabric (intermediary-named) Baritone jar against a named Minecraft jar.

Fabric mods ship with intermediary names (`net/minecraft/class_310`,
`method_1507`). The cached Minecraft jars are Mojang-named, so a direct
reference check does not work. This tool translates every Minecraft reference
through the Loom `mappings-base.tiny` file (namespaces: intermediary, named,
official) and then applies the same inherited-member check as
audit-baritone-linkage.py.

Usage:
    python scripts/audit-fabric-linkage.py --baritone B.jar \
        --minecraft NAMED.jar --mappings mappings-base.tiny
"""
import argparse
import json
import re
import struct
import zipfile
from contextlib import ExitStack
from functools import lru_cache
from pathlib import Path

import importlib.util

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location(
    'linkage', ROOT / 'scripts/audit-baritone-linkage.py')
linkage = importlib.util.module_from_spec(spec)
spec.loader.exec_module(linkage)

OBJECT_MEMBERS = {
    ('toString', '()Ljava/lang/String;'), ('hashCode', '()I'),
    ('equals', '(Ljava/lang/Object;)Z'), ('getClass', '()Ljava/lang/Class;'),
    ('notify', '()V'), ('notifyAll', '()V'), ('wait', '()V'),
}


def class_name(desc, start):
    end = start
    while end < len(desc) and desc[end] != ';':
        end += 1
    return desc[start + 1:end]


def map_descriptor(desc, class_map):
    out = []
    i = 0
    while i < len(desc):
        ch = desc[i]
        if ch == 'L':
            end = desc.find(';', i)
            if end < 0:
                out.append(desc[i:])
                break
            name = desc[i + 1:end]
            out.append('L' + class_map.get(name, name) + ';')
            i = end + 1
        else:
            out.append(ch)
            i += 1
    return ''.join(out)


def read_mappings(path):
    classes = {}
    members = {}
    global_members = {}
    owner = None
    for line in Path(path).read_text('utf-8', errors='replace').splitlines():
        if line.startswith('c\t'):
            parts = line.split('\t')
            inter, named = parts[1], parts[2]
            classes[inter] = named
            owner = inter
        elif line.startswith(('\tf\t', '\tm\t')) and owner:
            parts = line.split('\t')
            kind, desc, inter, named = parts[1], parts[2], parts[3], parts[4]
            key = (kind == 'f', inter, desc)
            members.setdefault(owner, {})[key] = named
            global_members.setdefault(key, named)
    return classes, members, global_members


def audit(baritone, minecraft, mappings, jdk=None):
    class_map, member_map, global_members = read_mappings(mappings)
    with ExitStack() as stack:
        mc = stack.enter_context(zipfile.ZipFile(minecraft))
        bari = stack.enter_context(zipfile.ZipFile(baritone))
        base = stack.enter_context(
            zipfile.ZipFile(Path(jdk) / 'jmods/java.base.jmod')) if jdk else None
        mc_names = set(mc.namelist())
        bari_names = set(bari.namelist())

        @lru_cache(None)
        def get_class(name):
            path = name + '.class'
            if path in mc_names:
                return linkage.parse_class(mc.read(path))
            if path in bari_names:
                return linkage.parse_class(bari.read(path))
            if base is not None and 'classes/' + path in base.namelist():
                return linkage.parse_class(base.read('classes/' + path))
            return None

        @lru_cache(None)
        def exists(owner, name, descriptor, field):
            info = get_class(owner)
            if info is None:
                return False
            if (name, descriptor) in info['fields' if field else 'methods']:
                return True
            return name != '<init>' and any(
                exists(parent, name, descriptor, field) for parent in info['parents'])

        unresolved = {}
        checked = 0
        for path in sorted(bari_names):
            if not path.startswith('baritone/') or not path.endswith('.class'):
                continue
            info = linkage.parse_class(bari.read(path))
            for field, owner, name, desc in sorted(info['refs']):
                if not owner.startswith(('net/minecraft/', 'com/mojang/blaze3d/')):
                    continue
                if not field and (name, desc) in OBJECT_MEMBERS:
                    continue
                named_owner = class_map.get(owner, owner)
                translated = member_map.get(owner, {}).get((field, name, desc))
                if not translated:
                    # Inherited members are mapped on their declaring class;
                    # fall back to the global (name, descriptor) match.
                    translated = global_members.get((field, name, desc))
                if translated:
                    named_name, named_desc = translated, map_descriptor(desc, class_map)
                else:
                    named_name, named_desc = name, map_descriptor(desc, class_map)
                checked += 1
                if not exists(named_owner, named_name, named_desc, field):
                    key = '%s.%s%s' % (named_owner, named_name, named_desc)
                    unresolved.setdefault(key, []).append(info['name'])
        return dict(baritone=str(baritone), minecraft=str(minecraft),
                    mappings=str(mappings), references_checked=checked,
                    unresolved=unresolved)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baritone', required=True, type=Path)
    parser.add_argument('--minecraft', required=True, type=Path)
    parser.add_argument('--mappings', required=True, type=Path)
    parser.add_argument('--jdk', type=Path, help='JDK home for java.base inheritance')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    result = audit(args.baritone, args.minecraft, args.mappings, args.jdk)
    text = json.dumps(result, ensure_ascii=False, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text + '\n', encoding='utf-8')
    print('checked %d references; unresolved %d' % (
        result['references_checked'], len(result['unresolved'])))
    for key in list(result['unresolved'])[:10]:
        print('  ' + key)
    return bool(result['unresolved'])


if __name__ == '__main__':
    raise SystemExit(main())
