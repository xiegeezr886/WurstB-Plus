# -*- coding: utf-8 -*-
"""Add the NanoVG dependency/configuration to a legacy project for compileJava."""
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
    if 'hudNatives' not in t:
        m = re.search(r'dependencies\s*\{', t)
        if not m:
            raise SystemExit('no dependencies block in %s' % p)
        t = t[:m.end()] + '\n' + DEPS + t[m.end():]
        if re.search(r'configurations\s*\{', t):
            t = re.sub(r'configurations\s*\{',
                       'configurations {\n\thudNatives', t, count=1)
        else:
            t = re.sub(r'(?m)^repositories\s*\{',
                       'configurations {\n\thudNatives\n}\n\nrepositories {',
                       t, count=1)
        p.write_text(t, encoding='utf-8', newline='\n')
    props = Path(build) / 'gradle.properties'
    pt = props.read_text(encoding='utf-8')
    if 'lwjgl_nanovg_version' not in pt:
        pt += '\nlwjgl_nanovg_version=3.3.2\n'
        props.write_text(pt, encoding='utf-8', newline='\n')
    print('patched', build)


if __name__ == '__main__':
    for b in sys.argv[1:]:
        patch(b)
