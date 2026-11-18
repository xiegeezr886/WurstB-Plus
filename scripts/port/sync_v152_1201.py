"""Port the Fabric 1.20.1 baseline to the two ForgeGradle 1.20.1 builds.

Native lifecycle, metadata and the source-based SeedCracker integration remain
target-owned. Never replace the native backend with a Fabric binary or stub.
Run one target at a time and compile before using it as a donor.
"""
from pathlib import Path
import argparse
import hashlib
import json
import re

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'fabric'
JAVA = 'src/main/java/net/wurstclient/'
SKIP = {JAVA + name for name in ('WurstInitializer.java', 'util/PlatformUtils.java',
        'seed/SeedCrackerBridge.java', 'hacks/SeedCrackerHack.java')}
RESOURCE_SKIP = {'src/main/resources/fabric.mod.json',
                 'src/main/resources/wurstpenguin.accesswidener',
                 'src/main/resources/wurstpenguin.mixins.json'}
REPLACED_SCAFFOLD = [JAVA + name for name in (
    'hacks/scaffold/HeypixelScaffoldMode.java', 'hacks/scaffold/HypixelScaffoldMode.java',
    'hacks/scaffold/OpenOpalScaffoldRecovery.java', 'hacks/scaffold/ScaffoldMode.java',
    'hacks/scaffold/SilenceTellyScaffoldMode.java', 'hacks/scaffold/SimpleTowerMode.java',
    'hacks/scaffold/TellyScaffoldMode.java', 'mixin/OpenOpalPostMoveMixin.java',
    'mixin/OpenOpalSkipTickMixin.java', 'opal/OpenOpalPacketBridge.java')]

BRIDGE = '''package net.wurstclient.seed;

import net.minecraft.client.Minecraft;
import net.wurstclient.WurstClient;
import net.wurstclient.util.ChatUtils;

/** Session validation shared by known-seed mining and future Forge crackers. */
public final class SeedCrackerBridge {
  public static final SeedSession SESSION = new SeedSession();

  public static void disconnect() {
    SESSION.clear();
    var hax = WurstClient.INSTANCE.getHax();
    if (hax != null && hax.seedMineHack.isEnabled()) hax.seedMineHack.setEnabled(false);
  }

  public void pushWorldSeed(long seed) {
    long generation = SESSION.generation();
    Minecraft.getInstance().execute(() -> {
      var mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null || !SESSION.accept(generation, seed)) return;
      ChatUtils.message("Seed: " + seed);
    });
  }
}
'''

# Plain JUnit does not use ModLauncher, which normally supplies default event
# constructors. Seed listener lists for the network bootstrap only; these tests
# exercise module/settings construction, not Forge networking or transformations.
TEST_BOOTSTRAP = '''package net.wurstclient.test;

import net.minecraft.server.Bootstrap;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventListenerHelper;
import net.minecraftforge.network.NetworkEvent;

public final class ForgeTestBootstrap {
  private static boolean prepared;

  public static synchronized void bootstrap() {
    if (!prepared) {
      try {
        var method = EventListenerHelper.class.getDeclaredMethod(
            "getListenerListInternal", Class.class, boolean.class);
        method.setAccessible(true);
        prepare(method, NetworkEvent.class);
        for (var type : NetworkEvent.class.getDeclaredClasses())
          if (Event.class.isAssignableFrom(type)) prepare(method, type);
        prepared = true;
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("Forge unit-test bootstrap failed", e);
      }
    }
    Bootstrap.bootStrap();
  }

  private static void prepare(java.lang.reflect.Method method, Class<?> type)
      throws ReflectiveOperationException {
    if (type == Event.class) return;
    prepare(method, type.getSuperclass());
    method.invoke(null, type, true);
  }
}
'''

FORGE_PRELOAD = '''\tprivate static void preloadMining()
\t{
\t\ttry
\t\t{
\t\t\t// Warm the public API, without relying on Fabric's patched private stub
\t\t\t// or reflection names for remapped Minecraft methods.
\t\t\tClass.forName("baritone.api.utils.BlockOptionalMeta")
\t\t\t\t.getConstructor(Block.class)
\t\t\t\t.newInstance(net.minecraft.world.level.block.Blocks.STONE);
\t\t\tminingPreloadState = PRELOAD_READY;
\t\t}catch(Throwable e)
\t\t{
\t\t\tminingPreloadState = PRELOAD_FAILED;
\t\t\tSystem.err.println("Baritone preload failed: " + e);
\t\t}
\t}
'''


def write_if_changed(path, data):
    if path.is_file() and path.read_bytes() == data:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('target', choices=['.', 'neoforge'])
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    target = (ROOT / args.target).resolve()
    loader = 'Forge' if args.target == '.' else 'NeoForge'
    assert target in (ROOT, ROOT / 'neoforge')
    properties = (target / 'gradle.properties').read_text('utf-8-sig')
    assert re.search(r'^minecraft_version=1\.20\.1\s*$', properties, re.M)
    files = [p for parent in ('src/main', 'src/test')
             for p in (SOURCE / parent).rglob('*') if p.is_file()]
    selected = [p for p in files if p.relative_to(SOURCE).as_posix() not in SKIP | RESOURCE_SKIP]
    print(f'{args.target}: {len(selected)} source/resource/test files; Forge lifecycle and metadata retained')
    if not args.apply:
        return
    native_seed = (target / 'src/main/java/kaptainwutax/seedcrackerX/SeedCracker.java').is_file()
    native_owned = {JAVA + name for name in (
        'hack/HackList.java', 'mixin/SeedSessionMixin.java',
        'mixin/ClientPlayerEntityMixin.java', 'util/TestControlServer.java')}
    manifest = {}
    for path in selected:
        rel = path.relative_to(SOURCE).as_posix()
        if native_seed and rel in native_owned:
            continue
        data = path.read_bytes()
        if path.suffix == '.java':
            text = data.decode('utf-8-sig')
            text = text.replace('import net.fabricmc.loader.api.FabricLoader;',
                                'import net.wurstclient.util.PlatformUtils;')
            text = text.replace('net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(',
                                'net.wurstclient.util.PlatformUtils.isModLoaded(')
            text = text.replace('FabricLoader.getInstance().isModLoaded(', 'PlatformUtils.isModLoaded(')
            if rel == JAVA + 'hack/HackList.java':
                text = re.sub(r'^\s*public final SeedCrackerHack seedCrackerHack = new SeedCrackerHack\(\);\s*$', '', text, flags=re.M)
            if rel == JAVA + 'hud2/mods/ArmorStatusMod.java':
                text = re.sub(
                    r'    var provider =\s*net\.fabricmc\.fabric\.api\.client\.rendering\.v1\.ColorProviderRegistry\.ITEM\.get\(stack\.getItem\(\)\);\s*'
                    r'int color = provider == null \? 0xFFFFFFFF : provider\.getColor\(stack, 0\) \| 0xFF000000;',
                    '    int color = MC.getItemColors().getColor(stack, 0) | 0xFF000000;', text)
            if rel == JAVA + 'util/TestControlServer.java':
                text = text.replace('kaptainwutax.seedcrackerX.config.Config.get().active', 'false')
                text = text.replace('if(port <= 0)\n\t\t\t\treturn;',
                    'if(port <= 0)\n\t\t\t\treturn;\n'
                    '\t\t\tWurstClient.MC.options.pauseOnLostFocus = false;')
            if rel == JAVA + 'mixin/MultiplayerScreenMixin.java':
                text = text.replace(
                    '@Override public void onClose(){if(opai$menu!=null)opai$menu.close();else minecraft.setScreen(lastScreen);}',
                    '@Inject(method="onClose()V",at=@At("HEAD"),cancellable=true)\n'
                    ' private void opai$close(CallbackInfo ci){if(opai$menu!=null){opai$menu.close();ci.cancel();}}')
            if rel == JAVA + 'gui/title/WurstTitleMenu.java':
                text = text.replace('" / Fabric"', f'" / {loader}"')
            if rel == JAVA + 'hacks/SeedMineHack.java':
                text = text.replace(
                    '"Optional signed world seed. Must match this world\'s hash. Leave empty to wait for"\n'
                    '              + " SeedCrackerX."',
                    '"Known world seed. Must match this world\'s hash."')
            if rel == JAVA + 'util/BaritoneUtils.java':
                text = text.replace('private static final int PRELOAD_READY = 2;',
                    'private static final int PRELOAD_READY = 2;\n'
                    '\tprivate static final int PRELOAD_FAILED = 3;')
                text = re.sub(r'\tprivate static void preloadMining\(\).*?(?=\tpublic static boolean startMining)',
                    lambda _: FORGE_PRELOAD, text, flags=re.S)
            if rel.startswith('src/test/'):
                text = text.replace('Bootstrap.bootStrap();',
                    'net.wurstclient.test.ForgeTestBootstrap.bootstrap();')
            data = text.encode('utf-8')
        write_if_changed(target / rel, data)
        manifest[rel] = hashlib.sha256(data).hexdigest()
    if not native_seed:
        write_if_changed(target / (JAVA + 'seed/SeedCrackerBridge.java'), BRIDGE.encode())
    write_if_changed(target / 'src/test/java/net/wurstclient/test/ForgeTestBootstrap.java',
                     TEST_BOOTSTRAP.encode())
    # The old strategy classes are superseded by NeoRaven, not version-specific
    # capabilities. Refuse to remove them if any retained class still uses them.
    retired = {target / name for name in REPLACED_SCAFFOLD}
    names = [path.stem for path in retired]
    for path in (target / 'src/main/java').rglob('*.java'):
        if path not in retired:
            text = path.read_text('utf-8-sig')
            if any(re.search(r'\b' + name + r'\b', text) for name in names):
                raise RuntimeError(f'Retired scaffold still referenced by {path}')
    for path in retired:
        assert path.resolve().is_relative_to((target / 'src/main/java').resolve())
        if path.exists():
            path.unlink()
    mixins = json.loads((SOURCE / 'src/main/resources/wurstpenguin.mixins.json').read_text('utf-8-sig'))
    mixins['refmap'] = 'wurstpenguin-refmap.json'
    write_if_changed(target / 'src/main/resources/wurst.mixins.json',
                     (json.dumps(mixins, ensure_ascii=False, indent=2) + '\n').encode())
    initializer = target / (JAVA + 'WurstForgeInitializer.java')
    text = initializer.read_text('utf-8-sig')
    hook = 'MinecraftForge.EVENT_BUS.addListener(this::onRenderGui);'
    if 'SeedCrackerBridge.disconnect' not in text:
        text = text.replace(hook, hook + '''
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) ->
            net.wurstclient.seed.SeedCrackerBridge.disconnect());''')
    write_if_changed(initializer, text.encode())
    # Dedicated dev suffix avoids mislabelling partial ports as accepted releases.
    loader = 'Forge' if args.target == '.' else 'NeoForge'
    properties = re.sub(r'^mod_version=.*$', f'mod_version=v1.5.2-dev-{loader}-1.20.1', properties, flags=re.M)
    write_if_changed(target / 'gradle.properties', properties.encode())
    build = (target / 'build.gradle').read_text('utf-8-sig')
    build = build.replace('include "org/lwjgl/nanovg/**", "windows/**", "linux/**", "macos/**"',
        'if (!project.hasProperty("devClient")) include "org/lwjgl/nanovg/**"\n'
        '        include "windows/**", "linux/**", "macos/**"')
    if 'inputs.property("bundledNanoVgApi"' not in build:
        build = build.replace('processResources {\n    from({ configurations.hudNatives',
            '''processResources {
    inputs.property("bundledNanoVgApi", !project.hasProperty("devClient"))
    doFirst {
        if (project.hasProperty("devClient"))
            delete new File(destinationDir, "org/lwjgl/nanovg")
    }
    from({ configurations.hudNatives''')
    if '// v1.5.2 music and portable tests' not in build:
        build += '''
// v1.5.2 music and portable tests; game supplies LWJGL core and base Netty.
dependencies {
    jarJar(group: "org.java-websocket", name: "Java-WebSocket", version: "[1.5.3,1.5.4)")
    jarJar(group: "io.netty", name: "netty-codec-socks", version: "[4.1.82.Final,4.1.83.Final)")
    jarJar(group: "io.netty", name: "netty-handler-proxy", version: "[4.1.82.Final,4.1.83.Final)")
    implementation "com.googlecode.soundlibs:jlayer:1.0.1.4"
    jarJar(group: "com.googlecode.soundlibs", name: "jlayer", version: "[1.0.1.4,1.0.1.5)")
    implementation "com.google.zxing:core:3.5.3"
    jarJar(group: "com.google.zxing", name: "core", version: "[3.5.3,3.5.4)")
    implementation "net.java.dev.jna:jna:5.13.0"
    jarJar(group: "net.java.dev.jna", name: "jna", version: "[5.13.0,5.14.0)")
    testImplementation "org.junit.jupiter:junit-jupiter:5.10.2"
    testRuntimeOnly "org.junit.platform:junit-platform-launcher:1.10.2"
}
sourceSets.main.java {
    exclude "net/wurstclient/clickgui2/component/*.java"
    exclude "net/wurstclient/clickgui2/component.disabled/*.java"
}
tasks.withType(Test).configureEach { useJUnitPlatform() }
'''
    # ForgeGradle must put plain libraries on the game's runtime classpath, not
    # just javac's classpath. JNA itself is already supplied by Minecraft.
    for coordinate in ('org.lwjgl:lwjgl-nanovg:', 'org.java-websocket:Java-WebSocket:',
                       'io.netty:netty-codec-socks:', 'io.netty:netty-handler-proxy:',
                       'com.googlecode.soundlibs:jlayer:', 'com.google.zxing:core:'):
        build = build.replace('implementation "' + coordinate,
                              'minecraftLibrary "' + coordinate)
    build = build.replace('    implementation "net.java.dev.jna:jna:5.13.0"\n', '')
    build = build.replace('    jarJar(group: "net.java.dev.jna", name: "jna", version: "[5.13.0,5.14.0)")\n', '')
    build = re.sub(r'minecraftLibrary "([^"]+)"',
                   r'minecraftLibrary("\1") { transitive = false }', build)
    write_if_changed(target / 'build.gradle', build.encode())
    # Six-architecture native resources must also match the renderer module.
    properties = (target / 'gradle.properties').read_text('utf-8-sig')
    properties = re.sub(r'^lwjgl_nanovg_version=.*$', 'lwjgl_nanovg_version=3.3.3', properties, flags=re.M)
    write_if_changed(target / 'gradle.properties', properties.encode())
    output = ROOT / '.dsh-tmp/v152-1201-port'
    output.mkdir(exist_ok=True, parents=True)
    (output / ('forge.json' if args.target == '.' else 'neoforge.json')).write_text(
        json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    print('Applied; next run compileJava, tests, production packaging and runtime verification.')


if __name__ == '__main__':
    main()
