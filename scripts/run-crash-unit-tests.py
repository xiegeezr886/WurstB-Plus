#!/usr/bin/env python3
"""Compile and test security fixes and rendering callbacks without Minecraft or Gradle.

Requires JDK 17+ (JAVA_HOME or --java-home) and network access on the first run.
Artifacts and temporary files stay under build/crash-tests. No credential store
is accessed: EncryptionTest injects its in-memory credential implementation.
"""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
DEPS = {
    'junit.jar': 'org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar',
    'gson.jar': 'com/google/code/gson/gson/2.10.1/gson-2.10.1.jar',
    'jna.jar': 'net/java/dev/jna/jna/5.17.0/jna-5.17.0.jar',
    'jna-platform.jar': 'net/java/dev/jna/jna-platform/5.17.0/jna-platform-5.17.0.jar',
    'lwjgl.jar': 'org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1.jar',
    'mixinextras.jar': 'io/github/llamalad7/mixinextras-common/0.5.5/mixinextras-common-0.5.5.jar',
    'asm.jar': 'org/ow2/asm/asm/9.9/asm-9.9.jar',
    'asm-tree.jar': 'org/ow2/asm/asm-tree/9.9/asm-tree-9.9.jar',
    'sponge-mixin.jar': 'https://maven.fabricmc.net/net/fabricmc/sponge-mixin/0.17.4+mixin.0.8.7/sponge-mixin-0.17.4+mixin.0.8.7.jar',
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path, default=os.environ.get('JAVA_HOME'))
    args = parser.parse_args()
    suffix = '.exe' if os.name == 'nt' else ''
    java = str(args.java_home / 'bin' / ('java' + suffix)) if args.java_home else shutil.which('java')
    javac = str(args.java_home / 'bin' / ('javac' + suffix)) if args.java_home else shutil.which('javac')
    if not java or not javac:
        parser.error('JDK 17+ is required; set JAVA_HOME or pass --java-home')
    work = ROOT / 'build/crash-tests'
    deps, classes, temp = work / 'deps', work / 'classes', work / 'temp'
    for folder in (deps, classes, temp):
        folder.mkdir(parents=True, exist_ok=True)
    for name, path in DEPS.items():
        target = deps / name
        if not target.exists():
            download = target.with_suffix('.download')
            try:
                url = path if path.startswith('https://') else 'https://repo.maven.apache.org/maven2/' + path
                urllib.request.urlretrieve(url, download)
                download.replace(target)
            finally:
                download.unlink(missing_ok=True)
    sources = [ROOT / p for p in (
        'common/src/main/java/net/wurstclient/altmanager/Encryption.java',
        'src/main/java/net/wurstclient/util/BundledNativeFiles.java',
        'fabric/src/main/java/net/wurstclient/hud2/nanovg/NanoVGNativeLoader.java',
        'src/test/java/net/wurstclient/altmanager/EncryptionTest.java',
        'src/test/java/net/wurstclient/util/BundledNativeFilesTest.java',
        'fabric/src/test/java/net/wurstclient/hud2/nanovg/NanoVGNativeLoaderTest.java')]
    for folder in ('altmanager/credentials', 'util/json'):
        sources.extend((ROOT / 'common/src/main/java/net/wurstclient' / folder).glob('*.java'))
    cp = os.pathsep.join(str(p) for p in deps.glob('*.jar'))
    subprocess.run([javac, '-encoding', 'UTF-8', '--release', '17', '-cp', cp,
                    '-d', str(classes), *map(str, sources)], check=True, cwd=ROOT)
    subprocess.run([java, '-Djava.io.tmpdir=' + str(temp), '-jar', str(deps / 'junit.jar'),
                    'execute', '--class-path', str(classes) + os.pathsep + cp,
                    '--scan-class-path', '--details', 'summary', '--disable-ansi-colors'],
                   check=True, cwd=ROOT)
    # Compile the actual save wrapper separately from Minecraft. These stubs
    # model only the Options/Wurst boundary; this does not claim Mixin weaving
    # or a client launch was tested.
    fixtures = {
        'org/spongepowered/asm/mixin/Mixin.java': '''package org.spongepowered.asm.mixin;
public @interface Mixin { Class<?>[] value(); }''',
        'net/minecraft/client/OptionInstance.java': '''package net.minecraft.client;
public class OptionInstance<T> implements net.wurstclient.mixinterface.ISimpleOption<T> {
 private T value; public OptionInstance(T value) { this.value=value; }
 public T get() { return value; } public void forceSetValue(T value) { this.value=value; }
}''',
        'net/minecraft/client/Options.java': '''package net.minecraft.client;
public class Options extends net.wurstclient.mixin.OptionsMixin {
 private final OptionInstance<Double> gamma;
 public Options(double value) { gamma=new OptionInstance<>(value); }
 public OptionInstance<Double> gamma() { return gamma; }
}''',
        'net/wurstclient/WurstClient.java': '''package net.wurstclient;
public class WurstClient {
 public static final WurstClient INSTANCE=new WurstClient();
 public Hax hax; public Hax getHax() { return hax; }
 public static class Hax { public Fullbright fullbrightHack=new Fullbright(); }
 public static class Fullbright { public double value=0.5; public double getDefaultGamma() { return value; } }
}''',
    }
    gamma_sources = []
    for name, text in fixtures.items():
        source = work / 'fixtures' / name
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_text(text, encoding='utf-8')
        gamma_sources.append(source)
    gamma_sources.extend(ROOT / p for p in (
        'src/main/java/net/wurstclient/mixin/OptionsMixin.java',
        'src/main/java/net/wurstclient/mixinterface/ISimpleOption.java',
        'scripts/tests/java/OptionsMixinTest.java'))
    gamma_classes = work / 'gamma-classes'
    gamma_classes.mkdir(exist_ok=True)
    subprocess.run([javac, '-proc:none', '-encoding', 'UTF-8', '--release', '17',
                    '-cp', cp, '-d', str(gamma_classes), *map(str, gamma_sources)],
                   check=True, cwd=ROOT)
    subprocess.run([java, '-jar', str(deps / 'junit.jar'), 'execute', '--class-path',
                    str(gamma_classes) + os.pathsep + cp, '--scan-class-path',
                    '--details', 'summary', '--disable-ansi-colors'], check=True, cwd=ROOT)
    run_sodium_tests(work, cp, java, javac, deps)


def run_sodium_tests(work, cp, java, javac, deps):
    # Real Mixin annotations/callbacks and production adapters; only the game
    # objects and event dispatch boundary are fixtures. This is not weaving.
    fixtures = {
        'net/minecraft/core/BlockPos.java': 'package net.minecraft.core; public record BlockPos(int x, int y, int z) {}',
        'net/minecraft/core/Direction.java': 'package net.minecraft.core; public enum Direction { UP, DOWN }',
        'net/minecraft/world/level/block/state/BlockState.java': 'package net.minecraft.world.level.block.state; public class BlockState {}',
        'net/wurstclient/event/EventManager.java': '''package net.wurstclient.event;
import net.wurstclient.events.ShouldDrawSideListener.ShouldDrawSideEvent;
public class EventManager {
 public static Boolean decision; public static ShouldDrawSideEvent last;
 public static void fire(ShouldDrawSideEvent event) { last=event; event.setRendered(decision); }
}''',
        'net/wurstclient/events/ShouldDrawSideListener.java': '''package net.wurstclient.events;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
public interface ShouldDrawSideListener {
 class ShouldDrawSideEvent {
  private Boolean rendered; public final BlockState state; public final BlockPos pos;
  public ShouldDrawSideEvent(BlockState state, BlockPos pos) { this.state=state; this.pos=pos; }
  public Boolean isRendered() { return rendered; } public void setRendered(Boolean value) { rendered=value; }
 }
}''',
    }
    sources = []
    for name, text in fixtures.items():
        source = work / 'sodium-fixtures' / name
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_text(text, encoding='utf-8')
        sources.append(source)
    base = ROOT / 'fabric/versions/1.21.11/src/main/java/net/wurstclient/mixin'
    sources.extend((base / 'sodium/AbstractBlockRenderContextMixin.java',
                    base / 'sodium/DefaultFluidRendererMixin.java',
                    base / 'WurstMixinConfigPlugin.java',
                    ROOT / 'scripts/tests/java/SodiumXRayMixinTest.java'))
    classes = work / 'sodium-classes'
    classes.mkdir(exist_ok=True)
    subprocess.run([javac, '-proc:none', '-encoding', 'UTF-8', '--release', '17',
                    '-cp', cp, '-d', str(classes), *map(str, sources)], check=True, cwd=ROOT)
    subprocess.run([java, '-jar', str(deps / 'junit.jar'), 'execute', '--class-path',
                    str(classes) + os.pathsep + cp, '--scan-class-path', '--details',
                    'summary', '--disable-ansi-colors'], check=True, cwd=ROOT)


if __name__ == '__main__':
    main()
