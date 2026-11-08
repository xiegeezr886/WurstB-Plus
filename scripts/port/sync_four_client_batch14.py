"""Connect IMG world/hand FOV through existing zoom callbacks and guard chat cleanup."""
import argparse
import hashlib
import json
import re
import shutil
from compile_four_client_matrix import wait_for_existing_build
from sync_four_client_batch import ROOT, PLAN, read, write
from sync_four_client_batch2 import merge

STAGE = PLAN / 'batch14/src/net/wurstclient'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--target', action='append')
    parser.add_argument('--preview', action='store_true')
    args = parser.parse_args()
    manifest = PLAN / 'batch14-outputs.json'
    previous = json.loads(read(manifest)) if manifest.exists() else {}
    for tree in json.loads(read(PLAN / 'target-inventory.json')):
        if args.target and tree['root'] not in args.target:
            continue
        if not args.preview:
            wait_for_existing_build(tree)
        java = ROOT / tree['root'] / 'src/main/java/net/wurstclient'
        api = read(PLAN / 'batch14' / f"api-{tree['mc']}.txt")
        planned, observed = {}, {}

        def original(file):
            observed[file] = file.read_bytes()
            backup = PLAN / 'batch14/before' / file.relative_to(ROOT)
            if backup.exists():
                return read(backup)
            if not args.preview:
                backup.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(file, backup)
            return read(file)

        file = java / 'hacks/CustomFovHack.java'
        planned[file] = merge(original(file), read(PLAN / 'batch14/base/hacks/CustomFovHack.java'), read(STAGE / 'hacks/CustomFovHack.java'), file)
        file = java / 'hacks/ChatHiderHack.java'
        text = original(file)
        assert text.count('if(MC.player == null)') == 1, file
        planned[file] = text.replace('if(MC.player == null)', 'if(!isEnabled() || MC.player == null)')
        if 'calculateFov(float);' in api:
            assert 'calculateHudFov(float);' in api
            file = java / 'mixin/CameraMixin.java'
            text = original(file)
            for name, world in [('onCalculateFov', 'true'), ('onCalculateHudFov', 'false')]:
                pattern = r'(private float ' + name + r'\(float original\)\s*\{)\s*return \(float\)WurstClient.INSTANCE.getOtfs\(\).zoomOtf\s*\.changeFovBasedOnZoom\(original\);'
                text, count = re.subn(pattern, r'\1\n\t\treturn (float)WurstClient.INSTANCE.getHax().customFovHack\n\t\t\t.changeFov(original, ' + world + ');', text)
                assert count == 1, (file, name)
        else:
            result_type = 'double' if 'double getFov(net.minecraft.client.Camera, float, boolean);' in api else 'float'
            assert result_type + ' getFov(net.minecraft.client.Camera, float, boolean);' in api
            file = java / 'mixin/GameRendererMixin.java'
            text = original(file)
            pattern = r'private ' + result_type + r' onGetFov\(' + result_type + r' original\)\s*\{\s*return (?:\(float\))?WurstClient.INSTANCE.getOtfs\(\).zoomOtf\s*\.changeFovBasedOnZoom\(original\);\s*\}'
            body = 'private ' + result_type + ' onGetFov(' + result_type + ' original,\n\t\tnet.minecraft.client.Camera camera, float partialTicks, boolean world)\n\t{\n\t\treturn (' + result_type + ')WurstClient.INSTANCE.getHax().customFovHack\n\t\t\t.changeFov(original, world);\n\t}'
            text, count = re.subn(pattern, lambda _: body, text)
            if count == 0:
                # These native 1.21.6-1.21.11 trees lost the zoom callback.
                # Refuse to add a second callback if another mixin owns FOV.
                assert 'getFov' not in text and 'onGetFov' not in text, file
                for other in (java / 'mixin').glob('*.java'):
                    assert 'changeFovBasedOnZoom' not in read(other), other
                annotation = 'import com.llamalad7.mixinextras.injector.ModifyReturnValue;'
                if annotation not in text:
                    text = text.replace('package net.wurstclient.mixin;',
                                        'package net.wurstclient.mixin;\n\n' + annotation)
                descriptor = 'D' if result_type == 'double' else 'F'
                hook = '\n\t@ModifyReturnValue(method = "getFov(Lnet/minecraft/client/Camera;FZ)' + descriptor + '",\n\t\tat = @At("RETURN"))\n\t' + body + '\n'
                assert text.rstrip().endswith('}'), file
                text = text.rstrip()[:-1] + hook + '}\n'
            else:
                assert count == 1, file
        planned[file] = text
        for file in planned:
            rel = file.relative_to(ROOT).as_posix()
            if rel in previous:
                assert hashlib.sha256(file.read_bytes()).hexdigest() == previous[rel], f'Concurrent edit: {file}'
            assert file.read_bytes() == observed[file], f'Changed during merge: {file}'
        for file, text in planned.items():
            dest = PLAN / 'batch14/generated' / file.relative_to(ROOT) if args.preview else file
            write(dest, text)
            if not args.preview:
                previous[file.relative_to(ROOT).as_posix()] = hashlib.sha256(file.read_bytes()).hexdigest()
        if not args.preview:
            write(manifest, json.dumps(previous, indent=2) + '\n')
        print('Batch 14 ' + ('preview: ' if args.preview else 'synchronized: ') + tree['root'], flush=True)


if __name__ == '__main__':
    main()
