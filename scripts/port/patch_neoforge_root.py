# -*- coding: utf-8 -*-
"""Add NanoVG to the neoforge 1.20.1 root project."""
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


def main():
    p = Path('neoforge/build.gradle')
    t = p.read_text(encoding='utf-8')
    if 'hudNatives' not in t:
        t = t.replace('jarJar.enable()',
                      'jarJar.enable()\n\nconfigurations {\n\thudNatives\n}', 1)
        i = t.rfind('dependencies {')
        j = i + len('dependencies {')
        t = t[:j] + '\n' + DEPS + t[j:]
        p.write_text(t, encoding='utf-8', newline='\n')
    props = Path('neoforge/gradle.properties')
    pt = props.read_text(encoding='utf-8')
    if 'lwjgl_nanovg_version' not in pt:
        pt += '\nlwjgl_nanovg_version=3.3.1\n'
        props.write_text(pt, encoding='utf-8', newline='\n')
    print('patched neoforge root')


if __name__ == '__main__':
    main()
