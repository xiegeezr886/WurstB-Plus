# -*- coding: utf-8 -*-
"""Print javac error blocks from a UTF-16 Gradle log, joining wrapped paths."""
import io
import re
import sys


def dump(path):
    text = io.open(path, encoding='utf-16', errors='replace').read()
    lines = text.replace('\r', '').splitlines()
    blocks = []
    current = []
    for line in lines:
        stripped = line.strip()
        if re.search(r'\.java:\d+: (error|warning):', stripped) \
            or (current and stripped.startswith('C:\\')):
            if stripped.startswith('C:\\'):
                if current:
                    blocks.append(current)
                current = [stripped]
            else:
                current.append(stripped)
        elif current:
            current.append(stripped)
    if current:
        blocks.append(current)
    seen = set()
    for block in blocks:
        key = tuple(block)
        if key in seen:
            continue
        seen.add(key)
        print('\n'.join(block))
        print('-----')


if __name__ == '__main__':
    dump(sys.argv[1])
