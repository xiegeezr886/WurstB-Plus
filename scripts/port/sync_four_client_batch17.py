"""Merge Reach modes and remove conflicting native hooks; preview by default opt-in."""
import argparse
import hashlib
import json
import shutil
from compile_four_client_matrix import wait_for_existing_build
from sync_four_client_batch import ROOT, PLAN, read, write


def method_span(text, name):
    index = text.index(' ' + name + '(')
    start = text.rfind('\n\t@', 0, index) + 1
    assert start > 0
    end = text.index('\n\t}', index) + len('\n\t}')
    return start, end


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--target', action='append')
    parser.add_argument('--exclude-target', action='append', default=[])
    parser.add_argument('--preview', action='store_true')
    args = parser.parse_args()
    batch = PLAN / 'batch17'
    stage = batch / 'src/net/wurstclient'
    manifest = PLAN / 'batch17-outputs.json'
    previous = json.loads(read(manifest)) if manifest.exists() else {}
    # Native Reach modules are identical in all66 checked source trees.
    expected = '6f74355b547d06996f52994d03c8292e319a5ee13f7c4a8838697d665652bb53'
    for tree in json.loads(read(PLAN / 'target-inventory.json')):
        if (args.target and tree['root'] not in args.target) or tree['root'] in args.exclude_target:
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

        legacy = tree['mc'] in ['1.20.1', '1.20.2', '1.20.3', '1.20.4']
        file = java / 'hacks/ReachHack.java'
        before = original(file)
        backup = batch / 'before' / file.relative_to(ROOT)
        assert hashlib.sha256(backup.read_bytes() if backup.exists() else observed[file]).hexdigest() == expected, file
        text = read(stage / 'hacks/ReachHack.java')
        if legacy:
            text = text.replace('MC.player == null ? 0 : (float)MC.player.entityInteractionRange()', '(float)changeEntityRange(MC.gameMode != null\n\t\t\t&& MC.gameMode.hasInfiniteItems() ? 6 : 3)')
            text = text.replace('MC.player == null ? 0 : (float)MC.player.blockInteractionRange()', '(float)changeBlockRange(MC.gameMode != null\n\t\t\t&& MC.gameMode.hasInfiniteItems() ? 5 : 4.5)')
        planned[file] = text
        hook = 'ReachLegacyRendererMixin' if legacy else 'ReachPlayerMixin'
        file = java / 'mixin' / (hook + '.java')
        assert not file.exists() or file.relative_to(ROOT).as_posix() in previous, file
        planned[file] = read(stage / 'mixin' / file.name)
        if not legacy:
            # Apply at Player range returns exactly once, after attributes and
            # vanilla bonuses. Remove the old attribute and subclass overrides.
            for file in (java / 'mixin').glob('*.java'):
                if file.name == 'ReachPlayerMixin.java':
                    continue
                current = read(file)
                if 'reachHack' not in current and file.relative_to(ROOT).as_posix() not in previous:
                    continue
                text = original(file)
                if file.name == 'LocalPlayerMixin.java':
                    for name in ['blockInteractionRange', 'entityInteractionRange']:
                        start, end = method_span(text, name)
                        assert 'reachHack' in text[start:end]
                        text = text[:start] + text[end:]
                elif file.name == 'LivingEntityMixin.java':
                    marker = '\t\tif(hax.reachHack.isEnabled())'
                    start = text.index(marker)
                    end = text.index('\n\t\t}', start) + len('\n\t\t}')
                    text = text[:start] + text[end:]
                elif file.name == 'GameRendererMixin.java':
                    start, end = method_span(text, 'trimReachResult')
                    text = text[:start] + text[end:]
                else:
                    raise AssertionError(('Unreviewed Reach hook', file))
                planned[file] = text
        configs = []
        for file in (ROOT / tree['root'] / 'src/main/resources').glob('*.mixins.json'):
            text = original(file)
            data = json.loads(text.lstrip('\ufeff'))
            if data.get('package') != 'net.wurstclient.mixin':
                continue
            assert hook not in data['client'], file
            planned[file] = text.replace('"client": [', '"client": [\n    "' + hook + '",')
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
        print('Batch17 ' + ('preview: ' if args.preview else 'synchronized: ') + tree['root'], flush=True)


if __name__ == '__main__':
    main()
