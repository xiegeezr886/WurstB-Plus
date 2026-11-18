"""Restore reviewed native SeedCracker hooks for selected MC 1.20.2-26.3 targets.

The matching shared source and solver library are pinned under native/seedcrackerx.
This script never edits another version's source or launches/builds a client.
"""
from pathlib import Path
import argparse
import re
import json
import shutil
import zipfile
from native_seed_versions import FAMILIES, upstream_for, cloth_for

ROOT = Path(__file__).resolve().parents[2]
JAVA = 'src/main/java/net/wurstclient/'


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.is_file() or path.read_text('utf-8-sig') != text:
        path.write_text(text, encoding='utf-8', newline='\n')


def configure(project, family='1204'):
    project = project.resolve()
    if not project.is_relative_to(ROOT):
        raise ValueError('Project outside repository')
    relative = project.relative_to(ROOT).as_posix()
    supported = FAMILIES
    if family not in supported or relative not in [p + v for p in ('versions/', 'neoforge/versions/')
                        for v in supported[family]] or relative == 'versions/1.20.5':
        raise ValueError('Unreviewed native backend target: ' + relative)
    neo = relative.startswith('neoforge/')
    version = project.name
    upstream = upstream_for(version)
    donor = ROOT / 'fabric/versions' / version
    base = project / JAVA
    path = base / 'mixin/MultiplayerScreenMixin.java'
    text = path.read_text('utf-8-sig')
    text = text.replace(
        '@Override public void onClose(){if(opai$menu!=null)opai$menu.close();else minecraft.setScreen(lastScreen);}',
        '@Inject(method="onClose()V",at=@At("HEAD"),cancellable=true)\n'
        ' private void opai$close(CallbackInfo ci){if(opai$menu!=null){opai$menu.close();ci.cancel();}}')
    write(path, text)
    for name in ('seed/SeedCrackerBridge.java', 'hacks/SeedCrackerHack.java'):
        text = (ROOT / JAVA / name).read_text('utf-8-sig')
        if version in ('26.2', '26.3'):
            text = text.replace('MC.screen', 'MC.gui.screen()')
        write(base / name, text)
    platform = 'net.neoforged.fml' if neo else 'net.minecraftforge.fml'
    write(base / 'seed/SeedPlatform.java', f'''package net.wurstclient.seed;

public final class SeedPlatform {{
  private SeedPlatform() {{}}
  public static java.nio.file.Path configDir() {{
    return {platform}.loading.FMLPaths.CONFIGDIR.get();
  }}
}}
''')
    path = base / 'hack/HackList.java'
    text = path.read_text('utf-8-sig')
    if 'SeedCrackerHack seedCrackerHack' not in text:
        text = text.replace('public final SeedMineHack seedMineHack',
                            'public final SeedCrackerHack seedCrackerHack = new SeedCrackerHack();\n'
                            '    public final SeedMineHack seedMineHack')
    write(path, text)
    path = base / 'mixin/SeedSessionMixin.java'
    text = (ROOT / JAVA / 'mixin/SeedSessionMixin.java').read_text('utf-8-sig')
    text = text.replace('packet.seed()', 'packet.commonPlayerSpawnInfo().seed()')
    text = text.replace('packet.getSeed()', 'packet.commonPlayerSpawnInfo().seed()')
    if version == '26.3':
        text = text.replace('packet.getX()', 'packet.x()').replace('packet.getZ()', 'packet.z()')
    write(path, text)
    path = base / 'mixin/ClientPlayerEntityMixin.java'
    if not path.is_file():
        path = base / 'mixin/LocalPlayerMixin.java'
    text = path.read_text('utf-8-sig')
    if 'wurst$tickSeedCracker' not in text:
        text = text.rstrip()[:-1] + '''    @org.spongepowered.asm.mixin.injection.Inject(method = "tick()V", at = @org.spongepowered.asm.mixin.injection.At("HEAD"))
    private void wurst$tickSeedCracker(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        var cracker = kaptainwutax.seedcrackerX.SeedCracker.get();
        if(cracker != null) cracker.getDataStorage().tick();
    }
}
'''
    write(path, text)
    path = base / 'WurstForgeInitializer.java'
    text = path.read_text('utf-8-sig')
    bus = 'NeoForge' if neo else 'MinecraftForge'
    api = 'net.neoforged.neoforge' if neo else 'net.minecraftforge'
    if 'SeedCrackerBridge.initialize();' not in text:
        text = text.replace('WurstClient.INSTANCE.initialize();',
            'WurstClient.INSTANCE.initialize();\n            net.wurstclient.seed.SeedCrackerBridge.initialize();')
        text = text.replace('.buildBrigadierDispatcher(event.getDispatcher());',
            '.buildBrigadierDispatcher(event.getDispatcher());\n        kaptainwutax.seedcrackerX.init.ClientCommands.registerCommands(event.getDispatcher());')
        listener = 'onRenderGui' if '.EVENT_BUS.addListener(this::onRenderGui);' in text else 'onRenderLevel'
        render_matrix = 'event.getPoseStack()' if family == '1204' else (
            'event.getModelViewMatrix()' if neo else
            'new org.joml.Matrix4f().rotate(event.getCamera().rotation().conjugate(new org.joml.Quaternionf()))')
        if family == '1218' and neo and version == '1.21.5':
            anchor = 'EventManager.fire(new RenderEvent(poseStack, partialTick));'
            if anchor not in text:
                raise ValueError('Missing reviewed NeoForge stage hook')
            text = text.replace(anchor, anchor + '\n'
                '        kaptainwutax.seedcrackerX.finder.FinderQueue.get().renderNative(poseStack, camera);')
        elif family in ('1218', '12111', '263'):
            render_path = base / 'mixin/LevelRendererMixin.java'
            render_text = render_path.read_text('utf-8-sig')
            anchor = ('EventManager.fire(new RenderEvent(matrixStack, tickProgress));'
                      if version == '1.21.5' else 'EventManager.fire(event);')
            if anchor not in render_text:
                raise ValueError('Missing reviewed GPU render hook')
            render_text = render_text.replace(anchor, anchor + '\n'
                '        kaptainwutax.seedcrackerX.finder.FinderQueue.get().renderNative('
                + ('matrixStack' if version == '1.21.5' or family == '263' else 'matrices')
                + ', ' + ('net.minecraft.client.Minecraft.getInstance().gameRenderer.'
                          + ('mainCamera()' if version in ('26.2', '26.3') else 'getMainCamera()')
                          if family == '263' else 'camera') + ');')
            write(render_path, render_text)
        elif family == '1214' and not neo:
            # Forge 53/54 no longer has the stage event; use its existing
            # mandatory LevelRenderer hook with the actual view matrix.
            render_path = base / 'mixin/LevelRendererMixin.java'
            render_text = render_path.read_text('utf-8-sig')
            if 'renderFinders(' not in render_text:
                anchor = 'EventManager.fire(new RenderEvent(matrixStack, tickProgress));'
                if anchor not in render_text:
                    raise ValueError('Missing reviewed native render hook')
                render_text = render_text.replace(anchor, anchor + '''
        try(var ignored = net.wurstclient.hud2.nanovg.GlStateGuard.capture()) {
            kaptainwutax.seedcrackerX.finder.FinderQueue.get().renderFinders(viewMatrix, camera);
        }''')
                write(render_path, render_text)
        else:
            text = text.replace(bus + '.EVENT_BUS.addListener(this::' + listener + ');',
            bus + '.EVENT_BUS.addListener(this::' + listener + ');\n        ' + bus + '.EVENT_BUS.addListener((' + api + '''.client.event.RenderLevelStageEvent event) -> {
            if(initialized && event.getStage() == ''' + api + '''.client.event.RenderLevelStageEvent.Stage.AFTER_LEVEL)
                try(var ignored = net.wurstclient.hud2.nanovg.GlStateGuard.capture()) {
                    kaptainwutax.seedcrackerX.finder.FinderQueue.get().renderFinders(''' + render_matrix + ''', event.getCamera());
                }
        });''')
    if family == '263':
        # Also repair an already restored hook without depending on initializer
        # insertion; getMainCamera was renamed only in 26.2.
        render_path = base / 'mixin/LevelRendererMixin.java'
        render_text = render_path.read_text('utf-8-sig')
        if version not in ('26.2', '26.3'):
            render_text = render_text.replace(
                'net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera()',
                'net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera()')
        write(render_path, render_text)
    if 'ClientPlayerNetworkEvent.LoggingOut' not in text:
        text = text.replace(bus + '.EVENT_BUS.addListener(this::onRegisterClientCommands);',
            bus + '.EVENT_BUS.addListener(this::onRegisterClientCommands);\n        ' + bus + '.EVENT_BUS.addListener(('
            + api + '.client.event.ClientPlayerNetworkEvent.LoggingOut event) -> net.wurstclient.seed.SeedCrackerBridge.disconnect());')
    write(path, text)
    path = base / 'util/TestControlServer.java'
    text = path.read_text('utf-8-sig')
    text = text.replace('+ false + "}";',
        '+ kaptainwutax.seedcrackerX.config.Config.get().active'
        ' + ",\\\"structureData\\\":" + kaptainwutax.seedcrackerX.SeedCracker.get().getDataStorage().baseSeedData.size()'
        ' + ",\\\"hashedSeed\\\":" + (kaptainwutax.seedcrackerX.SeedCracker.get().getDataStorage().hashedSeedData != null) + "}";')
    if 'case "disconnect":' not in text:
        text = text.replace('case "current":\n\t\t\t\tbreak;', '''case "current":
				break;
				case "disconnect":
				if(WurstClient.MC.level != null) WurstClient.MC.level.disconnect();
				WurstClient.MC.disconnect();
				WurstClient.MC.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
				break;''')
    text = text.replace('WurstClient.MC.screen.mouseClicked(x, y, 0);\n\t\t\t\t\t\tWurstClient.MC.screen.mouseReleased(x, y, 0);',
        'var clickedScreen = WurstClient.MC.screen;\n\t\t\t\t\t\tclickedScreen.mouseClicked(x, y, 0);\n'
        '\t\t\t\t\t\tif(WurstClient.MC.screen == clickedScreen) clickedScreen.mouseReleased(x, y, 0);')
    text = re.sub(r'(?m)^(\s*)WurstClient\.MC\.screen\.mouseClicked\(x, y, 0\);\n\s*WurstClient\.MC\.screen\.mouseReleased\(x, y, 0\);',
        r'\1var clickedScreen = WurstClient.MC.screen;\n\1clickedScreen.mouseClicked(x, y, 0);\n'
        r'\1if(WurstClient.MC.screen == clickedScreen) clickedScreen.mouseReleased(x, y, 0);', text)
    text = text.replace('WurstClient.MC.clearLevel();', 'WurstClient.MC.disconnect();')
    for screen in ('WurstClient.MC.screen', 'WurstClient.MC.gui.screen()'):
        clicked = screen + '.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(x, y, new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);'
        released = screen + '.mouseReleased(new net.minecraft.client.input.MouseButtonEvent(x, y, new net.minecraft.client.input.MouseButtonInfo(0, 0)));'
        text = text.replace(clicked + '\n\t\t\t\t\t' + released,
            'var clickedScreen = ' + screen + ';\n\t\t\t\t\t'
            + clicked.replace(screen, 'clickedScreen') + '\n\t\t\t\t\t'
            + 'if(' + screen + ' == clickedScreen) ' + released.replace(screen, 'clickedScreen'))
    write(path, text)
    path = project / 'build.gradle'
    text = path.read_text('utf-8-sig')
    marker = 'seedcracker-native' + family + '.gradle'
    if marker not in text:
        text += '\n// Native SeedCrackerX source, solver and Cloth Config runtime.\napply from: "' + ('../../../' if neo else '../../') + 'gradle/' + marker + '"\n'
    write(path, text)
    write(project / 'src/test/java/net/wurstclient/seed/NativeSeedHashTest.java',
          (ROOT / 'src/test/java/net/wurstclient/seed/NativeSeedHashTest.java').read_text('utf-8'))
    resources = project / 'src/main/resources'
    if family in ('1218', '12111', '263'):
        at = resources / 'META-INF/accesstransformer.cfg'
        access = at.read_text('utf-8-sig')
        pipeline = ('com/mojang/renderpearl/api/pipeline/RenderPipeline' if version == '26.3'
                    else 'com/mojang/blaze3d/pipeline/RenderPipeline')
        lines = [
            'public net.minecraft.client.renderer.RenderPipelines LINES_SNIPPET',
            'public net.minecraft.client.renderer.RenderPipelines register(L' + pipeline + ';)L' + pipeline + ';',
            'public-f net.minecraft.client.gui.components.Button onPress',
            'public net.minecraft.client.KeyMapping key',
        ]
        if family == '1218' or version in ('1.21.9', '1.21.10'):
            lines += [
                'public net.minecraft.client.renderer.RenderStateShard VIEW_OFFSET_Z_LAYERING',
                'public net.minecraft.client.renderer.RenderStateShard$LineStateShard <init>(Ljava/util/OptionalDouble;)V',
                'public net.minecraft.client.renderer.RenderType create(Ljava/lang/String;ILcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/client/renderer/RenderType$CompositeState;)Lnet/minecraft/client/renderer/RenderType$CompositeRenderType;',
            ]
        else:
            lines.append('public net.minecraft.client.renderer.rendertype.RenderType create(Ljava/lang/String;Lnet/minecraft/client/renderer/rendertype/RenderSetup;)Lnet/minecraft/client/renderer/rendertype/RenderType;')
        for line in lines:
            if line not in access.splitlines(): access += '\n' + line
        write(at, access.rstrip() + '\n')
    notices = resources / 'META-INF/licenses/seedcrackerx-native'
    shutil.copytree(ROOT / 'src/main/resources/META-INF/licenses/seedcrackerx-native', notices, dirs_exist_ok=True)
    write(notices / 'NOTICE.txt', f'''SeedCrackerX {upstream} native Forge/NeoForge adaptation for Minecraft {version}.
Upstream: https://github.com/19MisterX98/SeedcrackerX (MIT, KaptainWutax and contributors).
Shared Mojang-mapped sources and pinned solver provenance: native/seedcrackerx/{upstream}/manifest.json.
Changes: loader lifecycle, commands, configuration directory, module/chunk/hash/tick hooks,
local Baritone handoff, Log.warn overload recursion and logout-safe structure persistence.
Only com/seedfinding classes are extracted from the official upstream artifact.
The upstream seedfinding library licenses are retained alongside this notice.
Cloth Config is bundled as a native nested mod with its own LGPL notice.
''')
    if family in ('1218', '12111', '263') and not neo:
        write(notices / 'NOTICE.txt', (notices / 'NOTICE.txt').read_text('utf-8').replace(
            'Cloth Config is bundled as a native nested mod with its own LGPL notice.',
            'Cloth Config full configuration-screen and math APIs are merged into this mod.\n'
            'The pinned official NeoForge artifact has only its distribution annotations mapped to Forge;\n'
            'loader entrypoints and AutoConfig are excluded. See native/cloth-config manifests and\n'
            'scripts/port/extract_v152_cloth_core.py. Cloth Config is LGPL-3.0; source: https://github.com/shedaniel/cloth-config.'))
        cloth_notices = resources / 'META-INF/licenses/cloth-config-core'
        write(cloth_notices / 'LICENSE-LGPL-3.0.txt', (ROOT / 'src/main/resources/META-INF/licenses/baritone-api-forge/GNU-LGPL-3.0-LGPL-3.0-only.txt').read_text('utf-8'))
        write(cloth_notices / 'NOTICE.txt',
            'Cloth Config ' + cloth_for(version) + ' by shedaniel and contributors.\n'
            'LGPL-3.0. Upstream source: https://github.com/shedaniel/cloth-config\n'
            'Complete configuration-screen and math API extracted from the fixed official artifact.\n'
            'Only Dist/OnlyIn annotation namespaces are changed for Forge; no loader entrypoint or AutoConfig.\n'
            'Rebuild instructions and artifact hashes: scripts/port/extract_v152_cloth_core.py and native/cloth-config/.\n')
    if version == '1.20.5':
        donor = ROOT / 'fabric/versions/1.20.6'
    with zipfile.ZipFile(donor / ('libs/seedcrackerX-' + upstream + '.jar')) as archive:
        for name in archive.namelist():
            if name.startswith('assets/seedcrackerx/') and not name.endswith('/'):
                dest = resources / name
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_bytes(archive.read(name))
    if family == '1214' and not neo:
        metadata = resources / 'META-INF/jarjar/metadata.json'
        data = json.loads(metadata.read_text('utf-8-sig'))
        cloth = '17.0.144' if version == '1.21.4' else '16.0.143'
        data['jars'] = [j for j in data['jars'] if j.get('identifier', {}).get('artifact') != 'cloth-config-forge']
        data['jars'].append({
            'identifier': {'group': 'me.shedaniel.cloth', 'artifact': 'cloth-config-forge'},
            'version': {'range': '[' + cloth + ',' + str(int(cloth.split('.')[0]) + 1) + ')', 'artifactVersion': cloth},
            'path': 'META-INF/jarjar/cloth-config-forge-' + cloth + '-forge.jar', 'isObfuscated': False})
        write(metadata, json.dumps(data, indent=2) + '\n')
    print('Restored native SeedCrackerX ' + upstream + ':', relative)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('targets', nargs='+')
    parser.add_argument('--family', choices=FAMILIES, default='1204')
    args = parser.parse_args()
    for target in args.targets:
        configure(ROOT / target, family=args.family)
