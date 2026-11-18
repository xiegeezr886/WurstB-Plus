"""Three-way port between nearby versions. Refuses unreviewed conflicts.

Source API adapters and loader metadata remain target-owned. First-run originals
and full conflict files are recoverable under .dsh-tmp; never deletes unrelated
target-only sources. Invoke one API family at a time and verify each build.
"""
import argparse
import io
import json
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile
import shutil

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / '.dsh-tmp/v152-siblings'
TEXT = {'.java', '.json', '.gradle', '.properties', '.accesswidener', '.txt',
        '.md', '.cfg', '.toml', '.fsh', '.vsh', '.glsl'}
JAVA = 'src/main/java/net/wurstclient/'


def adapt(target, rel, data):
    if not rel.endswith('.java'):
        return data
    text = data.decode('utf-8-sig')
    if target == 'fabric/versions/26.1':
        from adapt_v152_fabric261 import adapt as api_adapt
        return api_adapt(rel, text).encode()
    if target == 'fabric/versions/1.21.11':
        from adapt_v152_fabric12111 import adapt as api_adapt
        return api_adapt(rel, text).encode()
    if target == 'fabric/versions/1.21.9':
        from adapt_v152_fabric1219 import adapt as api_adapt
        return api_adapt(rel, text).encode()
    if target == 'fabric/versions/1.21.6':
        from adapt_v152_fabric1216 import adapt as api_adapt
        return api_adapt(rel, text).encode()
    if target == 'fabric/versions/1.21.5':
        from adapt_v152_fabric1215 import adapt as api_adapt
        return api_adapt(rel, text).encode()
    if target == 'fabric/versions/1.20.6':
        from adapt_v152_fabric1206 import adapt as api_adapt
        return api_adapt(rel, text).encode()
    if target in ('fabric/versions/1.21', 'fabric/versions/1.21.1'):
        from adapt_v152_fabric1211 import adapt as api_adapt
        return api_adapt(rel, text).encode()
    if target in ('fabric/versions/1.21.2', 'fabric/versions/1.21.3'):
        from adapt_v152_fabric1213 import adapt as api_adapt
        return api_adapt(rel, text).encode()
    if target == 'fabric/versions/1.21.4':
        from adapt_v152_fabric1214 import adapt as api_adapt
        return api_adapt(rel, text).encode()
    if target.startswith(('versions/', 'neoforge/')):
        text = text.replace('import net.fabricmc.loader.api.FabricLoader;', 'import net.wurstclient.util.PlatformUtils;')
        text = text.replace('net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(', 'net.wurstclient.util.PlatformUtils.isModLoaded(')
        text = text.replace('FabricLoader.getInstance().isModLoaded(', 'PlatformUtils.isModLoaded(')
        if rel == JAVA + 'hack/HackList.java':
            text = re.sub(r'^\s*public final SeedCrackerHack seedCrackerHack = new SeedCrackerHack\(\);\s*$', '', text, flags=re.M)
        if rel == JAVA + 'hud2/mods/ArmorStatusMod.java':
            text = re.sub(r'    var provider =\s*net\.fabricmc\.fabric\.api\.client\.rendering\.v1\.ColorProviderRegistry\.ITEM\.get\(stack\.getItem\(\)\);\s*'
                          r'int color = provider == null \? 0xFFFFFFFF : provider\.getColor\(stack, 0\) \| 0xFF000000;',
                          '    int color = MC.getItemColors().getColor(stack, 0) | 0xFF000000;', text)
        if rel == JAVA + 'util/TestControlServer.java':
            text = re.sub(r'\s*net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register\(\s*client -> client.options.pauseOnLostFocus = false\);', '', text)
            text = text.replace('kaptainwutax.seedcrackerX.config.Config.get().active', 'false')
        if rel == JAVA + 'gui/title/WurstTitleMenu.java':
            text = text.replace(' / Fabric', ' / ' + ('NeoForge' if target.startswith('neoforge/') else 'Forge'))
        if rel == JAVA+'mixin/IngameHudMixin.java' and 'Objective objective' in text and 'import net.minecraft.world.scores.Objective;' not in text:
            text=text.replace('package net.wurstclient.mixin;', 'package net.wurstclient.mixin;\nimport net.minecraft.world.scores.Objective;')
        if rel == JAVA+'mixin/IngameHudMixin.java' and 'HudManager hud' in text and 'import net.wurstclient.hud2.HudManager;' not in text:
            text=text.replace('package net.wurstclient.mixin;', 'package net.wurstclient.mixin;\nimport net.wurstclient.hud2.HudManager;\nimport net.wurstclient.hud2.HUDMod;')
    if target.endswith('/1.20.2'):
        text = text.replace('WorldListEntry::canJoin', 'WorldListEntry::isSelectable').replace('world.canJoin()', 'world.isSelectable()')
        text = text.replace('.checkForBackupAndLoad(name, () -> WurstClient.MC.setScreen(new net.minecraft.client.gui.screens.TitleScreen()))',
                            '.loadLevel(WurstClient.MC.screen, name)')
        text = text.replace('ReceivingLevelScreen(() -> false)', 'ReceivingLevelScreen()')
        text = text.replace('WorldPresets::createNormalWorldDimensions, WurstClient.MC.screen)',
                            'WorldPresets::createNormalWorldDimensions)')
    return text.encode()


def resolve(target, rel, text, theirs):
    if target == 'fabric/versions/26.3':
        from adapt_v152_fabric263 import conflicts
        return conflicts(rel,text,theirs)
    if target == 'fabric/versions/26.2':
        from adapt_v152_fabric262 import conflicts
        return conflicts(rel,text,theirs)
    if target in ('fabric/versions/26.1.1','fabric/versions/26.1.2'):
        from adapt_v152_fabric261patch import conflicts
        return conflicts(rel,text,theirs)
    if target in ('versions/26.1','neoforge/versions/26.1',
        'versions/26.1.1','neoforge/versions/26.1.1','versions/26.1.2','neoforge/versions/26.1.2','versions/26.2','neoforge/versions/26.2','versions/26.3','neoforge/versions/26.3'):
        from adapt_v152_native261 import conflicts
        return conflicts(rel,text,theirs)
    if target == 'fabric/versions/26.1':
        from adapt_v152_fabric261 import conflicts
        return conflicts(rel,text,theirs)
    if target in ('versions/1.21.11','neoforge/versions/1.21.11'):
        from adapt_v152_native12111 import conflicts
        return conflicts(rel,text,theirs)
    if target == 'fabric/versions/1.21.11':
        from adapt_v152_fabric12111 import conflicts
        return conflicts(rel, text, theirs)
    if target in ('versions/1.21.9', 'neoforge/versions/1.21.9',
                  'versions/1.21.10', 'neoforge/versions/1.21.10'):
        from adapt_v152_native1219 import conflicts
        return conflicts(rel, text, theirs)
    if target == 'fabric/versions/1.21.10':
        from adapt_v152_fabric12110 import conflicts
        return conflicts(rel, text, theirs)
    if target == 'fabric/versions/1.21.9':
        from adapt_v152_fabric1219 import conflicts
        return conflicts(rel, text, theirs)
    if target in ('fabric/versions/1.21.7', 'fabric/versions/1.21.8'):
        from adapt_v152_fabric1218 import conflicts
        return conflicts(rel, text, theirs)
    if target in ('versions/1.21.6', 'neoforge/versions/1.21.6',
                  'versions/1.21.7', 'neoforge/versions/1.21.7',
                  'versions/1.21.8', 'neoforge/versions/1.21.8'):
        from adapt_v152_native1216 import conflicts
        return conflicts(rel, text, theirs)
    if target == 'fabric/versions/1.21.6':
        from adapt_v152_fabric1216 import conflicts
        return conflicts(rel, text, theirs)
    if target == 'fabric/versions/1.21.5':
        from adapt_v152_fabric1215 import conflicts
        return conflicts(rel, text, theirs)
    if target == 'neoforge/versions/1.20.5' and rel == JAVA+'gui/title/WurstTitleMenu.java':
        return theirs
    if target == 'fabric/versions/1.20.5' and rel == JAVA+'WurstClient.java':
        text = re.sub(r'<<<<<<<[^\n]*\n(.*?)=======\n(.*?)>>>>>>>[^\n]*\n?',
                      lambda m:m[2], text, flags=re.S)
        return text.replace('MC_VERSION = "1.20.6"', 'MC_VERSION = "1.20.5"').encode()
    if target in ('versions/1.21.3', 'neoforge/versions/1.21.2',
            'neoforge/versions/1.21.3', 'versions/1.21.4', 'neoforge/versions/1.21.4',
            'versions/1.21.5', 'neoforge/versions/1.21.5'):
        from adapt_v152_native1214 import conflicts
        return conflicts(rel, text, theirs)
    if target in ('versions/1.20.6', 'neoforge/versions/1.20.6'):
        # Same vanilla API. Replace obsolete UI/combat implementations and use
        # the donor's static creative-tab accessor with its matching call.
        if rel in {JAVA+n for n in ('gui/title/WurstTitleMenu.java',
                'clickgui2/NavigatorScreen.java', 'hacks/AutoArmorHack.java',
                'hacks/TriggerBotHack.java')}:
            return theirs
        if rel in {JAVA+n for n in ('altmanager/screens/AltEditorScreen.java',
                'mixin/IngameHudMixin.java', 'hacks/InvWalkHack.java')}:
            return re.sub(r'<<<<<<<[^\n]*\n(.*?)=======\n(.*?)>>>>>>>[^\n]*\n?',
                lambda m:m[2],
                text, flags=re.S).encode()
    if target == 'fabric/versions/1.20.6':
        from adapt_v152_fabric1206 import conflicts
        return conflicts(rel, text, theirs)
    if target in ('versions/1.21','neoforge/versions/1.21','versions/1.21.1','neoforge/versions/1.21.1'):
        if rel in {JAVA+n for n in ('gui/title/WurstTitleMenu.java','hacks/NukerHack.java','hacks/TreeBotHack.java')} or rel == 'src/test/java/net/wurstclient/util/render/PostEffectResourcesTest.java':
            return theirs
        if rel in {JAVA+n for n in ('hacks/InvWalkHack.java','mixin/IngameHudMixin.java',
                'altmanager/screens/AltEditorScreen.java','options/EnterProfileNameScreen.java',
                'options/PressAKeyScreen.java','serverfinder/ServerFinderScreen.java')}:
            return re.sub(r'<<<<<<<[^\n]*\n(.*?)=======\n(.*?)>>>>>>>[^\n]*\n?',lambda m:m[2],text,flags=re.S).encode()
    if target in ('fabric/versions/1.21', 'fabric/versions/1.21.1'):
        from adapt_v152_fabric1211 import conflicts
        return conflicts(target, rel, text, theirs)
    if target in ('fabric/versions/1.21.2', 'fabric/versions/1.21.3'):
        from adapt_v152_fabric1213 import conflicts
        return conflicts(target, rel, text, theirs)
    if target == 'fabric/versions/1.21.4':
        from adapt_v152_fabric1214 import conflicts
        return conflicts(rel, text, theirs)
    # Reviewed 1.20.2 differences: only removed list widget constructor and
    # historical QA endpoints. Both classes have been intentionally replaced.
    if target in ('fabric/versions/1.20.2', 'fabric/versions/1.20.3'):
        if rel in (JAVA + 'altmanager/screens/AltManagerScreen.java', JAVA + 'util/TestControlServer.java'):
            return theirs
        if rel == JAVA + 'WurstClient.java':
            mc = target.rsplit('/', 1)[1]
            text = re.sub(r'<<<<<<<[^\n]*\n(.*?)=======\n(.*?)>>>>>>>[^\n]*\n?',
                          lambda m: m[2], text, flags=re.S)
            text = re.sub(r'MC_VERSION = "[^"]+"', 'MC_VERSION = "' + mc + '"', text)
            return text.encode()
    if target.startswith(('versions/', 'neoforge/versions/')) and target.rsplit('/',1)[1] in ('1.20.2','1.20.3','1.20.4') and rel == JAVA + 'gui/title/WurstTitleMenu.java':
        return theirs
    return None


def configure_early_fabric(donor, target):
    """Retain version-specific dependencies and reviewed API adapters."""
    if target == 'fabric/versions/26.3':
        if donor != 'fabric/versions/26.2':raise ValueError('26.3 requires 26.2 donor')
        from adapt_v152_fabric263 import configure
        configure(ROOT/target,ROOT/donor)
        return
    if target == "fabric/versions/26.2":
        if donor != "fabric/versions/26.1.2": raise ValueError("26.2 requires 26.1.2 donor")
        from adapt_v152_fabric262 import configure
        configure(ROOT/target,ROOT/donor)
        return
    if target in ('fabric/versions/26.1.1','fabric/versions/26.1.2'):
        if donor != 'fabric/versions/26.1':
            raise ValueError('26.1 patches require the reviewed 26.1 donor')
        from adapt_v152_fabric261patch import configure
        configure(ROOT/target,ROOT/donor)
        return
    if target == 'fabric/versions/26.1':
        if donor != 'fabric/versions/1.21.11':
            raise ValueError('26.1 requires the reviewed 1.21.11 donor')
        from adapt_v152_fabric261 import configure
        configure(ROOT / target, ROOT / donor)
        return
    if target == 'fabric/versions/1.21.11':
        if donor != 'fabric/versions/1.21.10':
            raise ValueError('1.21.11 requires the reviewed 1.21.10 donor')
        from adapt_v152_fabric12111 import configure
        configure(ROOT / target, ROOT / donor)
        return
    if target == 'fabric/versions/1.21.10':
        if donor != 'fabric/versions/1.21.9':
            raise ValueError('1.21.10 requires the reviewed 1.21.9 donor')
        from adapt_v152_fabric12110 import configure
        configure(ROOT / target, ROOT / donor)
        return
    if target == 'fabric/versions/1.21.9':
        if donor != 'fabric/versions/1.21.8':
            raise ValueError('1.21.9 requires the reviewed 1.21.8 donor')
        from adapt_v152_fabric1219 import configure
        configure(ROOT / target, ROOT / donor)
        return
    if target in ('fabric/versions/1.21.7', 'fabric/versions/1.21.8'):
        if donor != 'fabric/versions/1.21.6':
            raise ValueError('1.21.7-8 requires the reviewed 1.21.6 donor')
        from adapt_v152_fabric1218 import configure
        configure(ROOT / target, ROOT / donor)
        return
    if target == 'fabric/versions/1.21.6':
        if donor != 'fabric/versions/1.21.5':
            raise ValueError('1.21.6 requires the reviewed 1.21.5 donor')
        from adapt_v152_fabric1216 import configure
        configure(ROOT / target)
        return
    if target == 'fabric/versions/1.21.5':
        if donor != 'fabric/versions/1.21.4':
            raise ValueError('1.21.5 requires the reviewed 1.21.4 donor')
        from adapt_v152_fabric1215 import configure
        configure(ROOT / target)
        return
    if target == 'fabric/versions/1.20.5':
        if donor != 'fabric/versions/1.20.6':
            raise ValueError('1.20.5 requires the reviewed 1.20.6 donor')
        from adapt_v152_fabric1205 import configure
        configure(ROOT / target)
        return
    if target == 'fabric/versions/1.20.6':
        if donor != 'fabric/versions/1.21':
            raise ValueError('1.20.6 requires the reviewed 1.21 donor')
        from adapt_v152_fabric1206 import configure
        configure(ROOT / target)
        return
    if target in ('fabric/versions/1.21', 'fabric/versions/1.21.1'):
        from adapt_v152_fabric1211 import configure
        configure(ROOT / target)
        return
    if target in ('fabric/versions/1.21.2', 'fabric/versions/1.21.3'):
        from adapt_v152_fabric1213 import configure
        configure(ROOT / target)
        return
    if target == 'fabric/versions/1.21.4':
        from adapt_v152_fabric1214 import configure
        configure(ROOT / target)
        return
    if donor != 'fabric/versions/1.20.4' or target not in ('fabric/versions/1.20.2', 'fabric/versions/1.20.3'):
        raise ValueError('No reviewed build adapter for ' + target)
    project = ROOT / target
    from sync_v152_fabric1204 import configure_build, vendor_seed_backend
    import sync_v152_fabric1204 as adapter
    previous = adapter.TARGET
    try:
        adapter.TARGET = project
        configure_build()
        vendor_seed_backend()
    finally:
        adapter.TARGET = previous
    properties = project / 'gradle.properties'
    mc = target.rsplit('/', 1)[1]
    properties.write_text(re.sub(r'^mod_version=.*$', 'mod_version=1.5.2-dev-Fabric-' + mc,
                          properties.read_text('utf-8-sig'), flags=re.M), encoding='utf-8')
    from sync_v152_1201 import REPLACED_SCAFFOLD
    for rel in REPLACED_SCAFFOLD:
        (project / rel).unlink(missing_ok=True)


def configure_native(donor, target):
    mc = target.rsplit('/',1)[1]
    if donor != 'fabric/versions/' + mc or mc not in ('1.20.2','1.20.3','1.20.4','1.20.5','1.20.6','1.21','1.21.1','1.21.2','1.21.3','1.21.4','1.21.5','1.21.6','1.21.7','1.21.8','1.21.9','1.21.10','1.21.11','26.1','26.1.1','26.1.2','26.2','26.3'):
        raise ValueError('No reviewed native build adapter for ' + target)
    project = ROOT / target
    from sync_v152_1201 import BRIDGE, REPLACED_SCAFFOLD
    (project / (JAVA + 'seed/SeedCrackerBridge.java')).write_text(BRIDGE, encoding='utf-8')
    (project / (JAVA + 'hacks/SeedCrackerHack.java')).unlink(missing_ok=True)
    for rel in REPLACED_SCAFFOLD:
        (project / rel).unlink(missing_ok=True)
    config = project / 'src/main/resources/wurst.mixins.json'
    data = json.loads(config.read_text('utf-8-sig'))
    donor_config = json.loads((ROOT / donor / 'src/main/resources/wurstpenguin.mixins.json').read_text('utf-8-sig'))
    removed = {Path(p).stem for p in REPLACED_SCAFFOLD}
    data['client'] = list(dict.fromkeys(n for n in data['client'] + donor_config['client'] if n not in removed))
    if mc in ('1.20.5','1.20.6','1.21','1.21.1'):
        from native_test_bootstrap import configure as configure_native_tests
        configure_native_tests(project, target.startswith('neoforge/'))
        data['client']=list(dict.fromkeys('ToastComponentAccessor' if n=='ToastManagerAccessor' else n for n in data['client']))
        for name in data['client']:
            relative=JAVA+'mixin/'+name.replace('.','/')+'.java'
            dest=project/relative
            if not dest.exists():
                source=ROOT/donor/relative
                if not source.exists():raise ValueError('Registered mixin has no target source: '+name)
                dest.parent.mkdir(parents=True,exist_ok=True)
                dest.write_bytes(source.read_bytes())
        build_path=project/'build.gradle'
        build=build_path.read_text('utf-8-sig')
        build=re.sub(r'archiveFileName = "WurstB\+ Plus-v1\.5\.1-[^"]+"',
                     'archiveFileName = "WurstB+ Plus-${project.mod_version}.jar"',build)
        build_path.write_text(build,encoding='utf-8')
    config.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')
    initializer = project / (JAVA + 'WurstForgeInitializer.java')
    text = initializer.read_text('utf-8-sig')
    bus = 'NeoForge' if target.startswith('neoforge/') else 'MinecraftForge'
    package = 'net.neoforged.neoforge' if bus == 'NeoForge' else 'net.minecraftforge'
    if 'SeedCrackerBridge.disconnect()' not in text:
        text = text.replace(bus + '.EVENT_BUS.addListener(this::onRenderGui);',
            bus + '.EVENT_BUS.addListener(this::onRenderGui);\n        ' + bus + '.EVENT_BUS.addListener((' + package +
            '.client.event.ClientPlayerNetworkEvent.LoggingOut event) -> net.wurstclient.seed.SeedCrackerBridge.disconnect());')
    if 'MoonEntityGlow.INSTANCE.flush(' not in text:
        text = text.replace('WurstClient.INSTANCE.getPostEffectQueue().flush(poseStack, partialTick);',
            'WurstClient.INSTANCE.getPostEffectQueue().flush(poseStack, partialTick);\n'
            '        net.wurstclient.util.render.MoonEntityGlow.INSTANCE.flush(poseStack, partialTick);')
    initializer.write_text(text, encoding='utf-8')
    path = project / 'build.gradle'
    build = path.read_text('utf-8-sig')
    build = re.sub(r'(implementation|runtimeOnly|hudNatives) "(org.lwjgl:lwjgl-nanovg:[^"\n]+)"', r'\1("\2") { transitive = false }', build)
    if '// v1.5.2 portable music runtime' not in build:
        build += '''
// v1.5.2 portable music runtime. Keep host Netty and LWJGL versions.
configurations { v152MusicRuntime }
dependencies {
    v152MusicRuntime("com.googlecode.soundlibs:jlayer:1.0.1.4") { transitive = false }
    v152MusicRuntime("com.google.zxing:core:3.5.3") { transitive = false }
    implementation("com.googlecode.soundlibs:jlayer:1.0.1.4") { transitive = false }
    implementation "com.google.zxing:core:3.5.3"
    compileOnly "net.java.dev.jna:jna:5.13.0"
    testImplementation "org.junit.jupiter:junit-jupiter:5.10.2"
    testRuntimeOnly "org.junit.platform:junit-platform-launcher:1.10.2"
}
tasks.withType(Jar).configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({ project.configurations.v152MusicRuntime.collect { zipTree(it) } }) {
        exclude "META-INF/**", "module-info.class"
    }
}
sourceSets.main.java {
    exclude "net/wurstclient/clickgui2/component/*.java"
    exclude "net/wurstclient/clickgui2/component.disabled/*.java"
}
tasks.withType(Test).configureEach { useJUnitPlatform() }
'''
    if '// v1.5.2 native network runtime' not in build:
        build += '''
// v1.5.2 native network runtime: these APIs are not supplied by Minecraft.
dependencies {
    v152MusicRuntime("org.java-websocket:Java-WebSocket:1.5.3") { transitive = false }
    v152MusicRuntime("io.netty:netty-codec-socks:4.1.82.Final") { transitive = false }
    v152MusicRuntime("io.netty:netty-handler-proxy:4.1.82.Final") { transitive = false }
}
'''
    path.write_text(build, encoding='utf-8')
    if mc in ('1.20.5','1.20.6'):
        # Unlike older native donors, this donor includes the codec regression
        # tests. Keep both proxy modules on Minecraft's 4.1.97 release.
        build = re.sub(r'io.netty:(netty-codec-socks|netty-handler-proxy):4\.1\.82\.Final',
                       r'io.netty:\1:4.1.97.Final', build)
        if 'exclude "net/wurstclient/clickgui2/screens.disabled/**"' not in build:
            build += '\nsourceSets.main.java { exclude "net/wurstclient/clickgui2/screens.disabled/**" }\n'
        path.write_text(build, encoding='utf-8')
    path = project / 'gradle.properties'
    loader = 'NeoForge' if target.startswith('neoforge/') else 'Forge'
    properties = re.sub(r'^mod_version=.*$', 'mod_version=1.5.2-dev-' + loader + '-' + mc,
                        path.read_text('utf-8-sig'), flags=re.M)
    lwjgl='3.4.3' if mc=='26.3' else ('3.4.1' if mc.startswith('26.') else '3.3.3')
    properties = re.sub(r'^lwjgl_nanovg_version=.*$', 'lwjgl_nanovg_version='+lwjgl, properties, flags=re.M)
    path.write_text(properties, encoding='utf-8')
    if mc in ('1.21.2','1.21.3','1.21.4','1.21.5'):
        from adapt_v152_native1214 import configure
        configure(project, ROOT / donor)
    if mc in ('1.21.6','1.21.7','1.21.8'):
        from adapt_v152_native1216 import configure
        configure(project, ROOT / donor)
    if mc in ('1.21.9','1.21.10'):
        from adapt_v152_native1219 import configure
        configure(project, ROOT / donor)
    if mc=='1.21.11':
        from adapt_v152_native12111 import configure
        configure(project, ROOT / donor)
    if mc in ('26.1','26.1.1','26.1.2','26.2','26.3'):
        from adapt_v152_native261 import configure
        configure(project, ROOT / donor)


def normalize(data, rel):
    return data.replace(b'\r\n', b'\n') if Path(rel).suffix.lower() in TEXT else data


def archived(project):
    dest = WORK / 'head' / project
    if not (dest / 'src').exists():
        archive_path = 'src' if project == '.' else project + '/src'
        raw = subprocess.run(['git', 'archive', '--format=zip', 'HEAD', archive_path],
                             cwd=ROOT, capture_output=True, check=True).stdout
        with zipfile.ZipFile(io.BytesIO(raw)) as z:
            z.extractall(WORK / 'head')
    return dest


def plan(donor, target):
    base = archived(donor)
    backup = WORK / 'originals' / target
    generated = WORK / 'generated' / target
    conflicts, writes = [], {}
    for folder in ('src/main', 'src/test'):
        for source in sorted((ROOT / donor / folder).rglob('*')):
            if not source.is_file():
                continue
            rel = source.relative_to(ROOT / donor).as_posix()
            if rel.endswith(('fabric.mod.json', 'mods.toml', 'neoforge.mods.toml')):
                continue
            if target.startswith(('versions/', 'neoforge/')) and rel in {
                JAVA + 'WurstInitializer.java', JAVA + 'hacks/SeedCrackerHack.java',
                JAVA + 'seed/SeedCrackerBridge.java', JAVA + 'util/PlatformUtils.java',
                'src/main/resources/wurstpenguin.mixins.json',
                'src/main/resources/wurstpenguin.accesswidener'}:
                continue
            before = base / rel
            theirs = normalize(source.read_bytes(), rel)
            ancestor = normalize(before.read_bytes(), rel) if before.exists() else None
            if theirs == ancestor:
                continue
            dest = ROOT / target / rel
            original = backup / rel
            absent = backup / (rel + '.absent')
            if not original.exists() and not absent.exists():
                original.parent.mkdir(parents=True, exist_ok=True)
                if dest.exists():
                    original.write_bytes(dest.read_bytes())
                else:
                    absent.touch()
            ours = normalize(original.read_bytes(), rel) if original.exists() else None
            if ours is None or ours == ancestor or ours == theirs:
                merged = theirs
            elif Path(rel).suffix.lower() not in TEXT:
                merged = theirs
            elif '/translations/' in rel and rel.endswith('.json'):
                merged = (json.dumps({**json.loads(ours), **json.loads(theirs)},
                          ensure_ascii=False, indent=2) + '\n').encode()
            else:
                with tempfile.TemporaryDirectory(dir=WORK) as tmp:
                    files = [Path(tmp) / n for n in ('ours', 'base', 'theirs')]
                    for p, data in zip(files, (ours, ancestor or b'', theirs)):
                        p.write_bytes(data)
                    result = subprocess.run(['git', 'merge-file', '-p', *map(str, files)],
                                             cwd=ROOT, capture_output=True)
                merged = result.stdout
                if result.returncode:
                    resolution = resolve(target, rel, merged.decode('utf-8-sig'), theirs)
                    if resolution is None:
                        conflicts.append(rel)
                    else:
                        merged = resolution
            merged = adapt(target, rel, merged)
            out = generated / rel
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_bytes(merged)
            writes[rel] = merged
    return writes, conflicts


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--donor', required=True)
    parser.add_argument('--target', action='append', required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    WORK.mkdir(parents=True, exist_ok=True)
    for target in args.target:
        writes, conflicts = plan(args.donor, target)
        report = {'donor': args.donor, 'target': target, 'files': len(writes), 'conflicts': conflicts}
        report_path = WORK / (target.replace('/', '-') + '.json')
        report_path.write_text(json.dumps(report, indent=2), encoding='utf-8')
        print(json.dumps(report), flush=True)
        if args.apply:
            if conflicts:
                raise SystemExit('Resolve and encode conflicts before applying')
            for rel, data in writes.items():
                dest = ROOT / target / rel
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_bytes(data)
            if target.startswith('fabric/'):
                configure_early_fabric(args.donor, target)
            else:
                configure_native(args.donor, target)
                if target in [p + v for p in ('versions/', 'neoforge/versions/')
                              for v in ('1.20.2', '1.20.3', '1.20.4')]:
                    from restore_v152_native_seed1204 import configure as restore_native_seed
                    restore_native_seed(ROOT / target)
                elif target in [p + v for p in ('versions/', 'neoforge/versions/')
                                for v in ('1.20.5', '1.20.6', '1.21', '1.21.1')]:
                    from restore_v152_native_seed1204 import configure as restore_native_seed
                    restore_native_seed(ROOT / target, family='1206')
                elif target in [p + v for p in ('versions/', 'neoforge/versions/')
                                for v in ('1.21.2', '1.21.3', '1.21.4')]:
                    from restore_v152_native_seed1204 import configure as restore_native_seed
                    restore_native_seed(ROOT / target, family='1214')
                elif target in [p + v for p in ('versions/', 'neoforge/versions/')
                                for v in ('1.21.5', '1.21.6', '1.21.7', '1.21.8')]:
                    from restore_v152_native_seed1204 import configure as restore_native_seed
                    restore_native_seed(ROOT / target, family='1218')
                elif target in [p + v for p in ('versions/', 'neoforge/versions/')
                                for v in ('26.1', '26.1.1', '26.1.2', '26.2', '26.3')]:
                    from restore_v152_native_seed1204 import configure as restore_native_seed
                    restore_native_seed(ROOT / target, family='263')
                elif target in [p + v for p in ('versions/', 'neoforge/versions/')
                                for v in ('1.21.9', '1.21.10', '1.21.11')]:
                    from restore_v152_native_seed1204 import configure as restore_native_seed
                    restore_native_seed(ROOT / target, family='12111')
            from prepare_v152 import props, version_key
            if version_key(props(ROOT / target / 'gradle.properties')['minecraft_version']) >= (1, 21, 2):
                from restore_v152_lsd import configure as restore_lsd
                restore_lsd(ROOT / target)
            if props(ROOT / target / 'gradle.properties')['minecraft_version'] == '26.3':
                from restore_v152_sdl_input import configure as restore_sdl_input
                restore_sdl_input(ROOT / target)


if __name__ == '__main__':
    main()
