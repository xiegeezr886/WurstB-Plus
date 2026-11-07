# -*- coding: utf-8 -*-
"""Remove the resourcesDir=classesDir override from Forge build scripts.

With that override, processResources owns build/classes/java/main as its
output directory and wipes the compiled classes, so jarJar/allJar can end up
containing resources and nested jars but no mod classes (and no refmap).
"""
import re
import sys
from pathlib import Path

PATTERN = re.compile(
    r'sourceSets\.main\.output\.resourcesDir\s*=\s*\n'
    r'\s*sourceSets\.main\.java\.destinationDirectory\.get\(\)\.asFile\s*\n',
    re.MULTILINE)


def patch(path):
    p = Path(path)
    t = p.read_text(encoding='utf-8')
    new = PATTERN.sub('', t)
    if new == t:
        print('unchanged', path)
        return
    p.write_text(new, encoding='utf-8', newline='\n')
    print('patched', path)


if __name__ == '__main__':
    for arg in sys.argv[1:]:
        patch(arg)
