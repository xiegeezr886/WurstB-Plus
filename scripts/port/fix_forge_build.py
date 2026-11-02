# -*- coding: utf-8 -*-
"""Remove misplaced NanoVG deps from buildscript and insert them into the
project dependencies block."""
import re
import sys
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


def patch(build):
    p = Path(build) / 'build.gradle'
    t = p.read_text(encoding='utf-8')
    # Remove the first (misplaced) implementation/runtimeOnly block.
    t = re.sub(
        r'\n    implementation "org\.lwjgl:lwjgl-nanovg.*?'
        r'natives-macos-arm64"\n', '\n', t, count=1, flags=re.S)
    t = re.sub(
        r'\n    hudNatives "org\.lwjgl:lwjgl-nanovg.*?'
        r'natives-macos-arm64"\n', '\n', t, count=1, flags=re.S)
    # Insert into the project dependencies block (last one).
    i = t.rfind('dependencies {')
    if i < 0:
        raise SystemExit('no dependencies block in %s' % p)
    j = i + len('dependencies {')
    t = t[:j] + '\n' + DEPS + t[j:]
    p.write_text(t, encoding='utf-8', newline='\n')
    print('fixed', p)


if __name__ == '__main__':
    for b in sys.argv[1:]:
        patch(b)
