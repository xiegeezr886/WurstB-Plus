"""Stage/merge TimeChanger read overrides without mutating server clock state."""
import argparse
import hashlib
import json
import shutil
from compile_four_client_matrix import wait_for_existing_build
from sync_four_client_batch import ROOT, PLAN, read, write

REVIEWED = {
    'c29edd4b41a8c4f24267d70038e7a1ca98cf5e20f95cca4dd37cfc290413e30c',
    'abef7d4ac9ac261dee142eec9f10f09fdb10464b8c2203a2c7e9c10bdebeaf51',
    'ef1929684702f43ad25937b005fde4ab5d939d78df3410d9a643dd624f2e3fd8',
    '359642998548eae338215a4076b3b17d984a2490dcec7f4e9a9dd272be8ac2f5',
}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--target', action='append')
    parser.add_argument('--preview', action='store_true')
    args = parser.parse_args()
    batch = PLAN / 'batch16'
    stage = batch / 'src/net/wurstclient'
    manifest = PLAN / 'batch16-outputs.json'
    previous = json.loads(read(manifest)) if manifest.exists() else {}
    for tree in json.loads(read(PLAN / 'target-inventory.json')):
        if args.target and tree['root'] not in args.target:
            continue
        if not args.preview:
            wait_for_existing_build(tree)
        java = ROOT / tree['root'] / 'src/main/java/net/wurstclient'
        planned, observed = {}, {}

        def original(file):
            observed[file] = file.read_bytes()
            backup = batch / 'before' / file.relative_to(ROOT)
            if not backup.exists() and not args.preview:
                backup.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(file, backup)
            return read(backup if backup.exists() else file)

        file = java / 'hacks/TimeChangerHack.java'
        before = original(file)
        assert hashlib.sha256(before.encode('utf-8')).hexdigest() in REVIEWED, file
        planned[file] = read(stage / 'hacks/TimeChangerHack.java')
        hooks = []
        if tree['mc'].startswith('26.'):
            file = java / 'mixin/ClientClockManagerMixin.java'
            text = original(file)
            marker = '\t\tNoWeatherHack noWeather = WurstClient.INSTANCE.getHax().noWeatherHack;'
            assert text.count(marker) == 1, file
            text = text.replace(marker, '\t\tvar hax = WurstClient.INSTANCE.getHax();\n\t\tif(hax == null)\n\t\t\treturn original;\n\t\tNoWeatherHack noWeather = hax.noWeatherHack;')
            if tree['mc'] != '26.3':
                text = text.replace('import net.minecraft.client.ClientClockManager;', 'import net.minecraft.client.ClientClockManager;\nimport net.minecraft.client.Minecraft;')
                marker = '\t\t// Only modify the overworld clock'
                assert text.count(marker) == 1, file
                text = text.replace(marker, '\t\tvar level = Minecraft.getInstance().level;\n\t\tif(level == null || level.clockManager() != (Object)this)\n\t\t\treturn original;\n\n' + marker)
            marker = 'if(!noWeather.isTimeChanged())'
            assert text.count(marker) == 1, file
            text = text.replace(marker, 'if(!noWeather.isTimeChanged()\n\t\t\t&& !WurstClient.INSTANCE.getHax().timeChangerHack.isEnabled())')
            marker = '\t\t// Replace the time-of-day while keeping the day number'
            assert text.count(marker) == 1, file
            text = text.replace(marker, '\t\tif(!noWeather.isTimeChanged())\n\t\t\treturn WurstClient.INSTANCE.getHax().timeChangerHack\n\t\t\t\t.changeTime(original);\n\n' + marker)
            planned[file] = text
        else:
            api = read(batch / ('time-api-' + tree['mc'] + '.txt'))
            assert 'public long getDayTime();' in api
            hooks = ['TimeChangerLevelMixin']
            for name in hooks:
                file = java / 'mixin' / (name + '.java')
                assert not file.exists() or file.relative_to(ROOT).as_posix() in previous, file
                text = read(stage / 'mixin' / file.name)
                if 'public default long dayTime();' in api:
                    # Old Mixin versions reject injection into interfaces. Add
                    # the concrete interface implementation to Level instead,
                    # following the native WorldMixin override pattern.
                    assert 'public long dayTime();' not in api, file
                    text = text.replace('import net.minecraft.world.level.Level;', 'import net.minecraft.world.level.Level;\nimport net.minecraft.world.level.LevelAccessor;\nimport org.spongepowered.asm.mixin.Shadow;')
                    text = text.replace('class TimeChangerLevelMixin\n{', 'class TimeChangerLevelMixin implements LevelAccessor\n{\n\t@Shadow\n\tpublic abstract long getDayTime();\n\n\t@Override\n\tpublic long dayTime()\n\t{\n\t\treturn getDayTime();\n\t}\n')
                planned[file] = text
            file = java / 'mixin/WorldMixin.java'
            text = original(file)
            # Existing old NoWeather rendering bypasses LevelAccessor.dayTime.
            # Route its unchanged fallback through the client-only read hook.
            marker = ': getLevelData().getDayTime();'
            if marker in text:
                assert text.count(marker) == 1
                planned[file] = text.replace(marker, ': dayTime();')
        configs = []
        for file in (ROOT / tree['root'] / 'src/main/resources').glob('*.mixins.json'):
            text = original(file)
            data = json.loads(text.lstrip('\ufeff'))
            if data.get('package') != 'net.wurstclient.mixin':
                continue
            if hooks:
                assert not any(name in data['client'] for name in hooks), file
                addition = ''.join('\n    "' + name + '",' for name in hooks)
                planned[file] = text.replace('"client": [', '"client": [' + addition)
            else:
                count = data['client'].count('ClientClockManagerMixin')
                assert count <= 1, file
                if count == 0:
                    planned[file] = text.replace('"client": [', '"client": [\n    "ClientClockManagerMixin",')
            configs.append(file)
        assert configs
        for file in planned:
            rel = file.relative_to(ROOT).as_posix()
            if rel in previous:
                assert hashlib.sha256(file.read_bytes()).hexdigest() == previous[rel], f'Concurrent edit: {file}'
            if file in observed:
                assert file.read_bytes() == observed[file], f'Changed during merge: {file}'
        for file, text in planned.items():
            dest = batch / 'generated' / file.relative_to(ROOT) if args.preview else file
            write(dest, text)
            if not args.preview:
                previous[file.relative_to(ROOT).as_posix()] = hashlib.sha256(file.read_bytes()).hexdigest()
        if not args.preview:
            write(manifest, json.dumps(previous, indent=2) + '\n')
        print('Batch16 ' + ('preview: ' if args.preview else 'synchronized: ') + tree['root'], flush=True)


if __name__ == '__main__':
    main()
