# -*- coding: utf-8 -*-
"""Add the NanoVG dependency, native unpack and version property to a 26.x
Forge or NeoForge project. Idempotent."""
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

RES = '''processResources {
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
        if 'allJarEmbed' in t:
            t = t.replace('configurations {\n    allJarEmbed\n}',
                          'configurations {\n    allJarEmbed\n    hudNatives\n}', 1)
            anchor = '    testImplementation "org.junit.jupiter:junit-jupiter:5.10.2"\n'
            t = t.replace(anchor, anchor + '\n' + DEPS, 1)
            t = t.replace('    exclude "META-INF/jarjar/metadata.json"',
                          RES + '    exclude "META-INF/jarjar/metadata.json"', 1)
        else:
            t = t.replace(
                'configurations {\n    runtimeClasspath.extendsFrom localRuntime\n}',
                'configurations {\n    runtimeClasspath.extendsFrom localRuntime\n    hudNatives\n}',
                1)
            anchor = '\tcompileOnly "com.google.code.findbugs:jsr305:3.0.2"\n'
            t = t.replace(anchor, anchor + '\n' + DEPS, 1)
            t = t.replace("tasks.named('jar', Jar).configure {",
                          RES + "tasks.named('jar', Jar).configure {", 1)
    p.write_text(t, encoding='utf-8', newline='\n')

    props = p.with_name('gradle.properties')
    pt = props.read_text(encoding='utf-8')
    if 'lwjgl_nanovg_version' not in pt:
        pt += '\nlwjgl_nanovg_version=3.4.3\n'
        props.write_text(pt, encoding='utf-8', newline='\n')


if __name__ == '__main__':
    for target in sys.argv[1:]:
        patch(Path(target) / 'build.gradle')
        print('patched', target)
