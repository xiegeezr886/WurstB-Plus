# -*- coding: utf-8 -*-
"""Unpack the NanoVG classes + native libraries into every production jar.

The M1 spike added this processResources block to the 26.x projects only; the
1.20.2-1.21.11 jars currently ship fonts but no natives/classes, so a player
without the LWJGL nanovg artifact gets "UnsatisfiedLinkError".
"""
import sys
from pathlib import Path

BLOCK = '''
processResources {
    from({ configurations.hudNatives.collect { zipTree(it) } }) {
        include "org/lwjgl/nanovg/**", "windows/**", "linux/**", "macos/**"
        exclude "META-INF/**", "module-info.class", "**/*.signature"
    }
}
'''


def patch(path):
    p = Path(path)
    t = p.read_text(encoding='utf-8')
    if 'hudNatives' not in t:
        print('no hudNatives:', path)
        return
    if 'zipTree(it)' in t:
        print('already:', path)
        return
    if not t.endswith('\n'):
        t += '\n'
    t += BLOCK
    p.write_text(t, encoding='utf-8', newline='\n')
    print('patched:', path)


if __name__ == '__main__':
    if sys.argv[1:]:
        for arg in sys.argv[1:]:
            patch(arg)
    else:
        root = Path('.')
        for loader in ('fabric/versions', 'versions', 'neoforge/versions'):
            for child in Path(loader).iterdir():
                bg = child / 'build.gradle'
                if bg.is_file():
                    patch(str(bg))
        if Path('neoforge/build.gradle').is_file():
            patch('neoforge/build.gradle')
