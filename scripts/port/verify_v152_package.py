"""Check a selected v1.5.2 production jar, including nested runtime libraries."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import zipfile

REQUIRED = [
    'net/wurstclient/neoraven/music/MusicManager.class',
    'net/wurstclient/hud2/mods/DynamicIslandMod.class',
    'net/wurstclient/hud2/mods/MoonLogoMod.class',
    'net/wurstclient/seed/SeedOrePredictor.class',
    'net/wurstclient/clickgui2/NavigatorScreen.class',
    'javazoom/jl/decoder/Decoder.class',
    'com/google/zxing/BarcodeFormat.class',
    'org/java_websocket/client/WebSocketClient.class',
    'io/netty/handler/proxy/ProxyHandler.class',
    'io/netty/handler/codec/socksx/v5/Socks5InitialRequest.class',
    'org/lwjgl/nanovg/NanoVG.class',
    'baritone/api/BaritoneAPI.class',
]


def inspect(path):
    errors, entries, nested = [], set(), []

    def walk(archive, label):
        bad = archive.testzip()
        if bad: errors.append('CRC: ' + label + '/' + bad)
        entries.update(archive.namelist())
        for name in archive.namelist():
            if name.endswith('.jar'):
                nested.append(label + '/' + name)
                with zipfile.ZipFile(io.BytesIO(archive.read(name))) as child:
                    walk(child, label + '/' + name)

    with zipfile.ZipFile(path) as archive:
        walk(archive, path.name)
        for name in REQUIRED:
            if name not in entries: errors.append('Missing runtime: ' + name)
        bridge = 'net/wurstclient/seed/SeedCrackerBridge.class'
        native_seed = 'kaptainwutax/seedcrackerX/SeedCracker.class'
        if 'fabric.mod.json' not in archive.namelist() and bridge in entries and b'kaptainwutax/seedcrackerX' in archive.read(bridge):
            for name in (native_seed,
                         'kaptainwutax/seedcrackerX/cracker/storage/TimeMachine.class',
                         'kaptainwutax/seedcrackerX/finder/structure/DesertPyramidFinder.class',
                         'kaptainwutax/seedcrackerX/config/ConfigScreen.class',
                         'com/seedfinding/mccore/rand/seed/WorldSeed.class',
                         'com/seedfinding/mcfeature/structure/DesertPyramid.class',
                         'me/shedaniel/clothconfig2/api/ConfigBuilder.class',
                         'META-INF/licenses/seedcrackerx-native/NOTICE.txt'):
                if name not in entries: errors.append('Missing native SeedCracker runtime: ' + name)
        lsd = 'net/wurstclient/hacks/LsdHack.class'
        if lsd in entries and b'LsdPostEffect' in archive.read(lsd):
            for name in ('net/wurstclient/util/render/LsdPostEffect.class',
                         'assets/wurst/shaders/program/lsd_wobble.fsh'):
                if name not in entries: errors.append('Missing LSD runtime: ' + name)
        for pattern in ('assets/wurstpenguin/nanovg/fonts/', 'assets/wurstpenguin/title-nextgen/',
                        'META-INF/licenses/epsilon/', 'META-INF/licenses/neoraven/',
                        'META-INF/licenses/jlayer/', 'META-INF/licenses/zxing/'):
            if not any(n.startswith(pattern) for n in entries): errors.append('Missing assets/notices: ' + pattern)
        config = 'wurstpenguin.mixins.json' if 'fabric.mod.json' in archive.namelist() else 'wurst.mixins.json'
        meta = json.loads(archive.read(config))
        for name in meta.get('client', []) + meta.get('mixins', []):
            clazz = meta['package'].replace('.', '/') + '/' + name.replace('.', '/') + '.class'
            if clazz not in archive.namelist(): errors.append('Missing registered mixin: ' + clazz)
    return {'jar': str(path.resolve()), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
            'nested_jar_count': len(nested), 'runtime_classes_checked': len(REQUIRED),
            'errors': errors}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jar', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    result = inspect(args.jar)
    value = json.dumps(result, ensure_ascii=False, indent=2)
    print(value)
    if args.output: args.output.write_text(value + '\n', encoding='utf-8')
    raise SystemExit(bool(result['errors']))


if __name__ == '__main__': main()
