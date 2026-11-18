"""Merge v1.5.2 features into Fabric 1.20.4 while retaining its API adapters.

Uses the v1.5.1 tag as the three-way base and reviewed 1.20.4 conflict rules.
First-run originals and merge reports are kept locally. Unrecognized conflicts
must be reviewed before this adapter is extended to another target.
"""
import argparse
import hashlib
import io
import json
import re
from pathlib import Path
import subprocess
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]
DONOR = ROOT / 'fabric'
TARGET = DONOR / 'versions/1.20.4'
WORK = ROOT / '.dsh-tmp/v152-fabric1204-port'
BASE = ROOT / '.dsh-tmp/v152-fabric-tag-base/fabric'
SKIP = {'src/main/resources/fabric.mod.json',
        }
JAVA = 'src/main/java/net/wurstclient/'
# These classes were intentionally replaced by the new implementations. Apply
# 1.20.4 API changes afterward instead of retaining fragments of the old UI/module.
REPLACED = {JAVA + name for name in (
    'altmanager/screens/AltEditorScreen.java', 'altmanager/screens/AltManagerScreen.java',
    'clickgui2/ClickGuiScreen.java', 'clickgui2/NavigatorScreen.java',
    'hacks/AimAssistHack.java', 'hacks/AntiKnockbackHack.java', 'hacks/AutoArmorHack.java',
    'hacks/AutoPlaceBlockHack.java', 'hacks/AutoSprintHack.java', 'hacks/AutoTorchHack.java',
    'hacks/BacktrackHack.java', 'hacks/KeepSprintHack.java', 'hacks/KillauraHack.java',
    'hacks/MultiAuraHack.java', 'hacks/ReachHack.java', 'hacks/ScaffoldWalkHack.java',
    'hacks/TreeBotHack.java', 'hacks/autoplace/DirectionalPaver.java',
    'hacks/treebot/SaplingFarm.java', 'hud2/HudManager.java',
    'hud2/mods/TargetInfoMod.java', 'hud2/nanovg/NanoVGContext.java',
    'util/TestControlServer.java')}
REVIEWED = REPLACED | {JAVA + name for name in (
    'altmanager/Encryption.java', 'hack/HackList.java', 'hacks/AntiHungerHack.java',
    'hacks/AutoTrashHack.java', 'hacks/AutoWalkHack.java', 'hacks/BuildHeightHack.java',
    'hacks/CustomFovHack.java', 'hacks/EXPThrowerHack.java', 'hacks/GhostHandHack.java',
    'hacks/MultitaskHack.java', 'hacks/NoInteractHack.java', 'hacks/NoMiningTraceHack.java',
    'hacks/PotionSaverHack.java', 'hud2/compat/HudItemIcons.java',
    'hud2/nanovg/GlStateGuard.java', 'mixin/OpenOpalInputMixin.java',
    'options/PressAKeyScreen.java', 'options/ZoomManagerScreen.java',
    'other_features/VanillaSpoofOtf.java', 'util/CmdUtils.java', 'WurstClient.java')}
REVIEWED |= {'src/main/resources/assets/wurst/translations/zh_cn.json',
             'src/main/resources/wurstpenguin.mixins.json'}


def adapt(rel, data):
    if not rel.endswith('.java'):
        return data
    text = data.decode('utf-8-sig').replace('\r\n', '\n')
    if rel == JAVA + 'WurstClient.java':
        text = text.replace('MC_VERSION = "1.20.1"', 'MC_VERSION = "1.20.4"')
    text = text.replace('renderBackground(context);',
                        'renderBackground(context, mouseX, mouseY, partialTicks);')
    if rel == JAVA + 'hacks/ImgWorldStatsHack.java':
        text = text.replace('renderBackground(context, mouseX, mouseY, partialTicks);',
                            'renderBackground(context);')
    if rel.startswith(JAVA + 'leaves/'):
        text = text.replace('renderBackground(g);', 'renderBackground(g, x, y, delta);')
    text = text.replace('WURST.MC.options.renderDebug', 'WURST.MC.getDebugOverlay().showDebugScreen()')
    common_packets = ('ClientboundDisconnectPacket', 'ClientboundKeepAlivePacket',
                      'ClientboundPingPacket', 'ServerboundPongPacket')
    for packet in common_packets:
        if packet in text and 'import net.minecraft.network.protocol.common.' + packet + ';' not in text:
            text = text.replace('package net.wurstclient.hacks;',
                'package net.wurstclient.hacks;\nimport net.minecraft.network.protocol.common.' + packet + ';')
    if rel == JAVA + 'events/ConnectionPacketOutputListener.java':
        text = text.replace('            if(!isCancelled()) net.wurstclient.opal.OpenOpalPacketBridge.beforeSend(packet);\n', '')
    if rel == JAVA + 'hacks/DeathRenderHack.java':
        text = text.replace('world.putNonPlayerEntity(id, lightning);',
                            'lightning.setId(id);\n          world.addEntity(lightning);')
    # Scroll input gained a horizontal axis in 1.20.2.
    text = re.sub(r'mouseScrolled\(double mouseX, double mouseY,\s*double (\w+)\)',
        r'mouseScrolled(double mouseX, double mouseY, double horizontal, double \1)', text)
    text = re.sub(r'super.mouseScrolled\(mouseX, mouseY, (\w+)\)',
        r'super.mouseScrolled(mouseX, mouseY, horizontal, \1)', text)
    text = re.sub(r'mouseScrolled\(double x, double y, double (\w+)\)',
        r'mouseScrolled(double x, double y, double horizontal, double \1)', text)
    if rel == JAVA + 'altmanager/screens/AltEditorScreen.java':
        text = text.replace('nameOrEmailBox.tick();', '').replace('passwordBox.tick();', '')
        text = re.sub(r'(?m)^[\t ]+$', '', text)
    if rel == JAVA + 'neoraven/music/MusicScreen.java':
        text = text.replace('search.tick();', '').replace('search.moveCursorToEnd();', 'search.moveCursorToEnd(false);')
    if rel == JAVA + 'mixin/SeedSessionMixin.java':
        text = text.replace('packet.seed()', 'packet.commonPlayerSpawnInfo().seed()')
        text = text.replace('packet.getSeed()', 'packet.commonPlayerSpawnInfo().seed()')
    if rel == JAVA + 'mixin/LeavesCrystalSpawnMixin.java':
        text = text.replace('leaves$spawn(int id, Entity entity, CallbackInfo ci)',
                            'leaves$spawn(Entity entity, CallbackInfo ci)')
    if rel == JAVA + 'mixin/NeoRavenLevelLoadingMixin.java':
        text = text.replace('Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V',
                            'Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V')
        text = text.replace('      String text,', '      Component text,')
    if rel == JAVA + 'seed/SeedCrackerBridge.java' and 'seedMineHack.setEnabled(false)' not in text:
        text = text.replace('          SESSION.clear();', '          SESSION.clear();\n'
            '          if (WurstClient.INSTANCE.getHax().seedMineHack.isEnabled())\n'
            '            WurstClient.INSTANCE.getHax().seedMineHack.setEnabled(false);')
    if rel in {JAVA + 'mixin/SelectWorldScreenMixin.java', JAVA + 'mixin/MultiplayerScreenMixin.java'}:
        text = text.replace('mouseScrolled(double x,double y,double d)',
                            'mouseScrolled(double x,double y,double horizontal,double d)')
        text = text.replace('super.mouseScrolled(x,y,d)', 'super.mouseScrolled(x,y,horizontal,d)')
        text = text.replace('WorldListEntry::isSelectable', 'WorldListEntry::canJoin').replace('world.isSelectable()', 'world.canJoin()')
    if rel == JAVA + 'mixin/SelectWorldScreenMixin.java':
        if 'opai$primaryLabel' not in text:
            text = text.replace(' @Unique private List<OpaiSelectionMenu.Row> opai$rows(){',
                ' @Inject(method="updateButtonStatus",at=@At("TAIL"))\n'
                ' private void opai$primaryLabel(net.minecraft.world.level.storage.LevelSummary summary,CallbackInfo ci){\n'
                '  if(WurstClient.INSTANCE.isEnabled())children().stream().filter(Button.class::isInstance).map(Button.class::cast)\n'
                '   .findFirst().ifPresent(button->button.setMessage(Component.literal("进入世界")));\n'
                ' }\n @Unique private List<OpaiSelectionMenu.Row> opai$rows(){')
        text = text.replace(' @Inject(method="keyPressed",at=@At("HEAD"),cancellable=true)\n'
            ' private void opai$key(int key,int scan,int mods,CallbackInfoReturnable<Boolean> cir){if(opai$menu!=null)cir.setReturnValue(opai$menu.key(key));}',
            ' @Override public boolean keyPressed(int key,int scan,int mods){return opai$menu!=null?opai$menu.key(key):super.keyPressed(key,scan,mods);}')
        text = text.replace(' @Inject(method="charTyped",at=@At("HEAD"),cancellable=true)\n'
            ' private void opai$char(char c,int mods,CallbackInfoReturnable<Boolean> cir){if(opai$menu!=null)cir.setReturnValue(false);}',
            ' @Override public boolean charTyped(char c,int mods){return opai$menu==null&&super.charTyped(c,mods);}')
    if rel == JAVA + 'util/TestControlServer.java':
        if 'ClientLifecycleEvents.CLIENT_STARTED.register' not in text:
            text = text.replace('if(port <= 0)\n\t\t\t\treturn;',
                'if(port <= 0)\n\t\t\t\treturn;\n'
                '\t\t\tnet.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(\n'
                '\t\t\t\tclient -> client.options.pauseOnLostFocus = false);')
        text = text.replace('\t\t\tWurstClient.MC.options.pauseOnLostFocus = false;\n', '')
        if '0, Double.parseDouble(query.getOrDefault("delta", "0")))' not in text:
            text = text.replace('Double.parseDouble(query.getOrDefault("delta", "0")))',
                '0, Double.parseDouble(query.getOrDefault("delta", "0")))')
        text = text.replace('.loadLevel(WurstClient.MC.screen, name);',
            '.checkForBackupAndLoad(name, () -> WurstClient.MC.setScreen(new net.minecraft.client.gui.screens.TitleScreen()));')
        text = text.replace('new net.minecraft.client.gui.screens.ReceivingLevelScreen()',
            'new net.minecraft.client.gui.screens.ReceivingLevelScreen(() -> false)')
        text = text.replace('WorldPresets::createNormalWorldDimensions);',
            'WorldPresets::createNormalWorldDimensions, WurstClient.MC.screen);')
    return text.encode()


def normalized(data, relative):
    text_suffixes = {'.java', '.json', '.gradle', '.properties', '.accesswidener',
                     '.txt', '.md', '.cfg', '.toml', '.fsh', '.vsh', '.glsl'}
    if data is not None and Path(relative).suffix.lower() in text_suffixes:
        return data.replace(b'\r\n', b'\n')
    return data


def configure_build():
    build_path = TARGET / 'build.gradle'
    build = build_path.read_text('utf-8-sig')
    if '// v1.5.2 music and seed backend' not in build:
        build += '''
// v1.5.2 music and seed backend for the 1.20.2-1.20.4 protocol family.
dependencies {
    include implementation("com.googlecode.soundlibs:jlayer:1.0.1.4") { transitive = false }
    include implementation("com.google.zxing:core:3.5.3")
    compileOnly "net.java.dev.jna:jna:5.13.0"
    modImplementation "local.seedfinding:seedcrackerX:2.14.6"
    include "local.seedfinding:seedcrackerX:2.14.6"
    modImplementation fileTree(dir: "libs/seedcrackerx-deps", include: ["*.jar"])
}
tasks.withType(Test).configureEach { useJUnitPlatform() }
'''
    # Both Groovy call syntaxes occur in sibling projects. Embed only NanoVG's
    # API; LWJGL core must continue to come from the game distribution.
    build = re.sub(r'(?m)^(\s*)implementation(?:\("(org\.lwjgl:lwjgl-nanovg:[^"\n]+)"\)| "(org\.lwjgl:lwjgl-nanovg:[^"\n]+)")(?: \{ transitive = false \})?$',
        lambda m: m[1] + 'include implementation("' + (m[2] or m[3]) + '") { transitive = false }', build)
    # Classifier dependencies also have transitive metadata. Keep Minecraft's
    # core/GLFW pair intact in development and only extract NanoVG natives.
    build = re.sub(r'(runtimeOnly|hudNatives) "(org.lwjgl:lwjgl-nanovg:[^"]+)"',
        r'\1("\2") { transitive = false }', build)
    build = re.sub(r'    hudNatives\("org.lwjgl:lwjgl-nanovg:\$\{lwjgl_nanovg_version\}"\) \{\s*transitive = false\s*\}\n', '', build)
    build = build.replace('include "org/lwjgl/nanovg/**", "windows/**", "linux/**", "macos/**"',
                          'include "windows/**", "linux/**", "macos/**"\n'
                          '        into "assets/wurstpenguin/nanovg/natives"')
    build_path.write_text(build, encoding='utf-8', newline='\n')
    properties = TARGET / 'gradle.properties'
    properties.write_text(re.sub(r'^mod_version=.*$', 'mod_version=1.5.2-dev-Fabric-1.20.4',
        properties.read_text('utf-8-sig'), flags=re.M), encoding='utf-8', newline='\n')
    metadata_path = TARGET / 'src/main/resources/fabric.mod.json'
    metadata = json.loads(metadata_path.read_text('utf-8-sig'))
    metadata['entrypoints']['seedcrackerx'] = ['net.wurstclient.seed.SeedCrackerBridge']
    metadata_path.write_text(json.dumps(metadata, indent=2) + '\n', encoding='utf-8')


def vendor_seed_backend():
    path = TARGET / 'libs/seedcrackerX-2.14.6.jar'
    path.parent.mkdir(exist_ok=True)
    digest = 'a2822c54df0de02c7935d87db41be1588e2153f100ec964b890cff0c3d5188d5'
    url = 'https://github.com/19MisterX98/SeedcrackerX/releases/download/2.14.6/seedcrackerX-2.14.6.jar'
    data = path.read_bytes() if path.exists() else urllib.request.urlopen(url, timeout=30).read()
    if hashlib.sha256(data).hexdigest() != digest:
        raise ValueError('SeedCrackerX 2.14.6 SHA256 mismatch')
    path.write_bytes(data)
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        if archive.testzip() is not None:
            raise ValueError('Corrupt SeedCrackerX archive')
        metadata = json.loads(archive.read('fabric.mod.json'))
        # Loom dev remapping drops nested libraries; production keeps the
        # upstream jar intact and Fabric Loader resolves its nested modules.
        deps = TARGET / 'libs/seedcrackerx-deps'
        deps.mkdir(exist_ok=True)
        for jar in metadata['jars']:
            name = jar['file']
            (deps / Path(name).name).write_bytes(archive.read(name))
    notice = TARGET / 'src/main/resources/META-INF/licenses/seedcrackerx/NOTICE.txt'
    notice.write_text('SeedCrackerX 2.14.6, MIT, KaptainWutax / 19MisterX98.\n'
        'Official release: https://github.com/19MisterX98/SeedcrackerX/releases/tag/2.14.6\n'
        f'Bundled jar SHA-256: {digest}\n'
        'Upstream binary is unmodified; its metadata declares Minecraft >=1.20.2.\n'
        'The release tag currently points to later sources; do not treat it as an exact binary source revision.\n'
        'Nested dependency metadata and licenses remain in the original jar.\n', encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    if not BASE.exists():
        result = subprocess.run(['git', 'archive', '--format=zip', 'v1.5.1', 'fabric/src'],
            cwd=ROOT, stdout=subprocess.PIPE, check=True)
        with zipfile.ZipFile(io.BytesIO(result.stdout)) as archive:
            archive.extractall(BASE.parent)
    WORK.mkdir(parents=True, exist_ok=True)
    backup = WORK / 'originals'
    conflicts = []
    changed = []
    for folder in ('src/main', 'src/test'):
        for source in sorted((DONOR / folder).rglob('*')):
            if not source.is_file():
                continue
            rel = source.relative_to(DONOR).as_posix()
            if rel in SKIP:
                continue
            dest = TARGET / rel
            original = backup / rel
            # Reapplying always starts from the saved original, not a partial merge.
            exists_marker = backup / (rel + '.absent')
            if not original.exists() and not exists_marker.exists():
                original.parent.mkdir(parents=True, exist_ok=True)
                if dest.exists():
                    original.write_bytes(dest.read_bytes())
                else:
                    exists_marker.touch()
            ours = normalized(original.read_bytes(), rel) if original.exists() else None
            base_path = BASE / rel
            base = normalized(base_path.read_bytes(), rel) if base_path.exists() else None
            theirs = normalized(source.read_bytes(), rel)
            if theirs == base:
                continue
            if ours is None or ours == base or ours == theirs:
                merged = theirs
            elif rel.endswith('.java') or rel.endswith(('.json', '.accesswidener')):
                with tempfile.TemporaryDirectory(dir=WORK) as tmp:
                    paths = [Path(tmp) / n for n in ('ours', 'base', 'theirs')]
                    for p, data in zip(paths, (ours, base or b'', theirs)):
                        p.write_bytes(data)
                    result = subprocess.run(['git', 'merge-file', '-p', *map(str, paths)],
                        stdout=subprocess.PIPE)
                    if result.returncode:
                        conflict = WORK / 'conflicts' / rel
                        conflict.parent.mkdir(parents=True, exist_ok=True)
                        conflict.write_bytes(result.stdout)
                        if rel not in REVIEWED:
                            conflicts.append(rel)
                            continue
                        if rel in REPLACED:
                            merged = theirs
                        elif rel.endswith('.json') and 'translations/' in rel:
                            merged = (json.dumps({**json.loads(ours), **json.loads(theirs)},
                                ensure_ascii=False, indent=2) + '\n').encode()
                        else:
                            # Reviewed remaining conflicts: new category names,
                            # translations, renderer state, and new registrations.
                            text = result.stdout.decode()
                            text = re.sub(r'<<<<<<<[^\n]*\n(.*?)=======\n(.*?)>>>>>>>[^\n]*\n?',
                                lambda m: m[1] + 'import net.wurstclient.WurstClient;\n'
                                    if rel == JAVA + 'other_features/VanillaSpoofOtf.java'
                                    else m[2], text, flags=re.S)
                            if '<<<<<<<' in text:
                                conflicts.append(rel)
                                continue
                            merged = text.encode()
                    else:
                        merged = result.stdout
            else:
                # Resource replacements follow the new baseline; loader metadata is skipped.
                merged = theirs
            changed.append(rel)
            if args.apply:
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_bytes(adapt(rel, merged))
    if args.apply:
        # Retired OpenOpal scaffold was superseded, including its input hooks.
        from sync_v152_1201 import REPLACED_SCAFFOLD
        for rel in REPLACED_SCAFFOLD:
            (TARGET / rel).unlink(missing_ok=True)
        # The public API preloader was already verified on the first batch.
        (TARGET / (JAVA + 'util/BaritoneUtils.java')).write_bytes(
            (ROOT / (JAVA + 'util/BaritoneUtils.java')).read_bytes())
        configure_build()
        vendor_seed_backend()
        for dest in (TARGET / 'src/main/java').rglob('*.java'):
            data = dest.read_bytes()
            updated = adapt(dest.relative_to(TARGET).as_posix(), data)
            if data != updated:
                dest.write_bytes(updated)
    (WORK / 'merge-report.json').write_text(json.dumps(
        {'changed': changed, 'conflicts': conflicts}, indent=2), encoding='utf-8')
    print(f'{len(changed)} files ready; {len(conflicts)} conflicts; apply={args.apply}')
    print('\n'.join(conflicts))


if __name__ == '__main__':
    main()
