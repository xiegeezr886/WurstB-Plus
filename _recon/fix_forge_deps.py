# -*- coding: utf-8 -*-
"""Move the NanoVG dependencies from buildscript.dependencies to the project
dependencies block in Forge 1.20.x projects."""
import re
from pathlib import Path

DEPS = '''    implementation "org.lwjgl:lwjgl-nanovg:${lwjgl_nanovg_version}"
    runtimeOnly "org.lwjgl:lwjgl-nanovg:${lwjgl_nanovg_version}:natives-windows"
    runtimeOnly "org.lwjgl:lwjgl-nanovg:${lwjgl_nanovg_version}:natives-windows-arm64"
    runtimeOnly "org.lwjgl:lwjgl-nanovg:${lwjgl_nanovg_version}:natives-linux"
    runtimeOnly "org.lwjgl:lwjgl-nanovg:${lwjgl_nanovg_version}:natives-linux-arm64"
    runtimeOnly "org.lwjgl:lwjgl-nanovg:${lwjgl_nanovg_version}:natives-macos"
    runtimeOnly "org.lwjgl:lwjgl-nanovg:${lwjgl_nanovg_version}:natives-macos-arm64"
    hudNatives "org.lwjgl:lwjgl-nanovg:${lwjgl_nanovg_version}"
    hudNatives "org.lwjgl:lwjgl-nanovg:${lwjgl_nanovg_version}:natives-windows"
    hudNatives "org.lwjgl:lwjgl-nanovg:${lwjgl_nanovg_version}:natives-windows-arm64"
    hudNatives "org.lwjgl:lwjgl-nanovg:${lwjgl_nanovg_version}:natives-linux"
    hudNatives "org.lwjgl:lwjgl-nanovg:${lwjgl_nanovg_version}:natives-linux-arm64"
    hudNatives "org.lwjgl:lwjgl-nanovg:${lwjgl_nanovg_version}:natives-macos"
    hudNatives "org.lwjgl:lwjgl-nanovg:${lwjgl_nanovg_version}:natives-macos-arm64"
'''

PATTERN = re.compile(
    r'\n    implementation "org\.lwjgl:lwjgl-nanovg.*?'
    r'natives-macos-arm64"\n', re.S)


def patch(path):
    p = Path(path) / 'build.gradle'
    t = p.read_text(encoding='utf-8')
    # Remove the misplaced copy from buildscript.dependencies (if any).
    t, n = PATTERN.subn('\n', t, count=1)
    anchor = 'minecraft "net.minecraftforge:forge'
    if anchor in t and 'hudNatives "org.lwjgl:lwjgl-nanovg' not in t:
        t = t.replace(anchor, DEPS + '    ' + anchor, 1)
    p.write_text(t, encoding='utf-8', newline='\n')
    print('patched', p, 'removed', n)


if __name__ == '__main__':
    import sys
    for v in sys.argv[1:]:
        patch('versions/' + v)
