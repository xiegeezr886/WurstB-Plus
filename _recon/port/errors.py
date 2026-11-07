# -*- coding: utf-8 -*-
"""List unique javac errors from a UTF-16 Gradle log."""
import io
import re
import sys


def main(path):
    text = io.open(path, encoding='utf-16', errors='replace').read()
    lines = text.replace('\r', '').splitlines()
    current_path = None
    seen = set()
    for i, line in enumerate(lines):
        stripped = line.strip()
        if stripped.startswith('C:\\') and stripped.endswith('.java'):
            current_path = stripped
            continue
        m = re.search(r'([A-Za-z0-9_]+\.java):(\d+): error: (.*)$', stripped)
        if not m:
            continue
        symbol = ''
        for j in range(i + 1, min(i + 4, len(lines))):
            if 'symbol:' in lines[j]:
                symbol = lines[j].strip()
                break
        key = (m.group(1), m.group(2), m.group(3), symbol)
        if key in seen:
            continue
        seen.add(key)
        print('%s:%s: %s | %s' % (m.group(1), m.group(2), m.group(3),
                                   symbol))


if __name__ == '__main__':
    main(sys.argv[1])
