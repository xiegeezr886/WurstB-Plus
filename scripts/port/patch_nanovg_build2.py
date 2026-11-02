# -*- coding: utf-8 -*-
"""Add NanoVG to a 26.x Fabric/Forge/NeoForge project (idempotent)."""
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

FROM = '''
    from({ configurations.hudNatives.collect { zipTree(it) } }) {
        include "org/lwjgl/nanovg/**", "windows/**", "linux/**", "macos/**"
        exclude "META-INF/**", "module-info.class", "**/*.signature"
    }
'''

FABRIC_PROCESS = re.compile(
    r'processResources\s*\{\s*inputs\.property "version", project\.version\s*'
    r'filesMatching\("fabric\.mod\.json"\)\s*\{\s*expand "version": project\.version\s*\}\s*\}',
    re.S)


def mc_of(path):
    m = re.search(r'^\s*minecraft_version\s*=\s*(.+?)\s*$',
                  (path / 'gradle.properties').read_text(encoding='utf-8'),
                  re.M)
    return m.group(1) if m else '26.1'


def nano_of(mc):
    v = tuple(int(x) for x in mc.split('.'))
    if v <= (1, 20, 4):
        return '3.3.2'
    if v <= (1, 21, 11):
        return '3.3.3'
    if v <= (26, 2):
        return '3.4.1'
    return '3.4.3'


def patch_build(p, mc):
    t = p.read_text(encoding='utf-8')
    if 'hudNatives' not in t:
        if 'net.fabricmc.fabric-loom' in t:
            if re.search(r'configurations\s*\{', t):
                t = re.sub(r'configurations\s*\{',
                           'configurations {\n\thudNatives', t, count=1)
            else:
                t = t.replace('repositories {',
                              'configurations {\n\thudNatives\n}\n\nrepositories {',
                              1)
            anchor = ('\tannotationProcessor '
                      '"io.github.llamalad7:mixinextras-common:0.5.4"\n')
            t = t.replace(anchor, anchor + '\n' + DEPS + '\n', 1)
            m = FABRIC_PROCESS.search(t)
            if m:
                block = m.group(0)
                block = block[:-1] + FROM + '}\n'
                t = t[:m.start()] + block + t[m.end():]
            else:
                raise SystemExit('fabric processResources not found in %s' % p)
        elif 'allJarEmbed' in t:
            t = t.replace('configurations {\n    allJarEmbed\n}',
                          'configurations {\n    allJarEmbed\n    hudNatives\n}',
                          1)
            anchor = ('    testImplementation '
                      '"org.junit.jupiter:junit-jupiter:5.10.2"\n')
            t = t.replace(anchor, anchor + '\n' + DEPS, 1)
            t = t.replace(
                '    exclude "META-INF/jarjar/metadata.json"',
                FROM + '\n    exclude "META-INF/jarjar/metadata.json"', 1)
        else:
            t = t.replace(
                'configurations {\n    runtimeClasspath.extendsFrom localRuntime\n}',
                'configurations {\n    runtimeClasspath.extendsFrom localRuntime\n    hudNatives\n}',
                1)
            anchor = '\tcompileOnly "com.google.code.findbugs:jsr305:3.0.2"\n'
            t = t.replace(anchor, anchor + '\n' + DEPS, 1)
            t = t.replace("tasks.named('jar', Jar).configure {",
                          'processResources {' + FROM + '}\n\n'
                          "tasks.named('jar', Jar).configure {", 1)
    p.write_text(t, encoding='utf-8', newline='\n')

    props = p.with_name('gradle.properties')
    pt = props.read_text(encoding='utf-8')
    if 'lwjgl_nanovg_version' not in pt:
        pt += '\nlwjgl_nanovg_version=%s\n' % nano_of(mc)
        props.write_text(pt, encoding='utf-8', newline='\n')


if __name__ == '__main__':
    for target in sys.argv[1:]:
        d = Path(target)
        patch_build(d / 'build.gradle', mc_of(d))
        print('patched', target)
