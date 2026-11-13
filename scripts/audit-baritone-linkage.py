#!/usr/bin/env python3
"""Check Baritone's Minecraft member references without starting the game.

Usage: python scripts/audit-baritone-linkage.py --minecraft MC.jar --baritone Baritone.jar
This checks JVM descriptors and inherited members, not mixin injection success,
rendering or pathfinding. Use the matching mapped Minecraft jar for each loader.
"""
import argparse
import json
import struct
import zipfile
from contextlib import ExitStack
from functools import lru_cache
from pathlib import Path


def parse_class(data):
    offset = 8

    def take(fmt):
        nonlocal offset
        result = struct.unpack_from('>' + fmt, data, offset)
        offset += struct.calcsize('>' + fmt)
        return result[0] if len(result) == 1 else result

    pool = [None] * take('H')
    i = 1
    while i < len(pool):
        tag = take('B')
        if tag == 1:
            size = take('H')
            value = data[offset:offset + size].decode('utf-8', errors='replace')
            offset += size
        elif tag in (7, 8, 16, 19, 20):
            value = take('H')
        elif tag in (9, 10, 11, 12, 17, 18):
            value = take('HH')
        elif tag == 15:
            value = take('BH')
        elif tag in (3, 4):
            value = take('I')
        elif tag in (5, 6):
            value = take('Q')
        else:
            raise ValueError(f'Unknown constant pool tag {tag}')
        pool[i] = (tag, value)
        i += 2 if tag in (5, 6) else 1

    def utf(index):
        return pool[index][1]

    def cls(index):
        return utf(pool[index][1]) if index else None

    take('H')
    name, parent = cls(take('H')), cls(take('H'))
    parents = [parent] if parent else []
    parents += [cls(take('H')) for _ in range(take('H'))]

    def members():
        nonlocal offset
        result = set()
        for _ in range(take('H')):
            take('H')
            result.add((utf(take('H')), utf(take('H'))))
            for _ in range(take('H')):
                take('H')
                size = take('I')
                offset += size
        return result

    fields, methods = members(), members()
    refs = set()
    for entry in pool:
        if entry and entry[0] in (9, 10, 11):
            owner, signature = entry[1]
            member, descriptor = pool[signature][1]
            refs.add((entry[0] == 9, cls(owner), utf(member), utf(descriptor)))
    return dict(name=name, parents=parents, fields=fields, methods=methods, refs=refs)


def audit(minecraft, baritone, jdk=None):
    with ExitStack() as stack:
        mc = stack.enter_context(zipfile.ZipFile(minecraft))
        bari = stack.enter_context(zipfile.ZipFile(baritone))
        base = stack.enter_context(zipfile.ZipFile(jdk / 'jmods/java.base.jmod')) if jdk else None
        mc_names = set(mc.namelist())
        bari_names = set(bari.namelist())
        if 'net/minecraft/client/Minecraft.class' not in mc_names:
            raise ValueError('Expected a populated Minecraft jar in Mojang mappings')

        @lru_cache(None)
        def get_class(name):
            path = name + '.class'
            if path in mc_names:
                return parse_class(mc.read(path))
            if path in bari_names:
                return parse_class(bari.read(path))
            if base and 'classes/' + path in base.namelist():
                return parse_class(base.read('classes/' + path))
            return None

        @lru_cache(None)
        def exists(owner, name, descriptor, field):
            info = get_class(owner)
            if info is None:
                return False
            if (name, descriptor) in info['fields' if field else 'methods']:
                return True
            # Constructors are never inherited.
            return name != '<init>' and any(
                exists(p, name, descriptor, field) for p in info['parents'])

        errors = {}
        checked = 0
        for path in sorted(bari_names):
            if not path.startswith('baritone/') or not path.endswith('.class'):
                continue
            info = parse_class(bari.read(path))
            for field, owner, name, desc in sorted(info['refs']):
                if not owner.startswith(('net/minecraft/', 'com/mojang/blaze3d/')):
                    continue
                # java.lang.Object members inherited by Minecraft classes.
                if (name, desc) in {('toString', '()Ljava/lang/String;'),
                                   ('hashCode', '()I'), ('equals', '(Ljava/lang/Object;)Z'),
                                   ('getClass', '()Ljava/lang/Class;'),
                                   ('notify', '()V'), ('notifyAll', '()V'), ('wait', '()V')}:
                    continue
                checked += 1
                if not exists(owner, name, desc, field):
                    key = f'{owner}.{name}{desc}'
                    errors.setdefault(key, []).append(info['name'])
        return dict(minecraft=str(minecraft), baritone=str(baritone),
                    references_checked=checked, unresolved=errors)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--minecraft', required=True, type=Path)
    parser.add_argument('--baritone', required=True, type=Path)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--jdk', type=Path, help='JDK home for inherited java.base members')
    args = parser.parse_args()
    result = audit(args.minecraft, args.baritone, args.jdk)
    text = json.dumps(result, ensure_ascii=False, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text + '\n', encoding='utf-8')
    if args.output:
        print(f"Checked {result['references_checked']} references; "
              f"unresolved: {len(result['unresolved'])}; report: {args.output}")
    else:
        print(text)
    return bool(result['unresolved'])


if __name__ == '__main__':
    raise SystemExit(main())
