"""Verify final batch9 files, registrations and matching full-input builds."""
import hashlib
import json
from compile_four_client_matrix import ROOT, PLAN, build_inputs, source_manifest


def main():
    targets = json.loads((PLAN / 'target-inventory.json').read_text(encoding='utf-8'))
    outputs = json.loads((PLAN / 'batch9-outputs.json').read_text(encoding='utf-8'))
    changed = [relative for relative, digest in outputs.items()
               if hashlib.sha256((ROOT / relative).read_bytes()).hexdigest() != digest]
    assert not changed, f'Batch9 output changed after synchronization: {changed}'
    builds = []
    for file in PLAN.glob('batch9*-compile/results.json'):
        builds.extend(json.loads(file.read_text(encoding='utf-8')))
    checked = []
    for tree in targets:
        root = ROOT / tree['root']
        java = root / 'src/main/java/net/wurstclient'
        registry = (java / 'hack/HackList.java').read_text(encoding='utf-8')
        for name in ['NoMiningTrace', 'GhostHand', 'AutoMount', 'BetterBeacons']:
            assert registry.count('new ' + name + 'Hack();') == 1, (tree['root'], name)
            assert (java / 'hacks' / (name + 'Hack.java')).is_file()
        configs = []
        for file in (root / 'src/main/resources').glob('*.mixins.json'):
            data = json.loads(file.read_text(encoding='utf-8-sig'))
            if data.get('package') == 'net.wurstclient.mixin':
                configs.append(file.name)
                for name in ['NoMiningTraceMixin', 'BeaconPowerButtonMixin', 'BetterBeaconsScreenMixin']:
                    assert data['client'].count(name) == 1, (file, name)
                    assert (java / 'mixin' / (name + '.java')).is_file()
        assert configs, root
        translations = json.loads((root / 'src/main/resources/assets/wurst/translations/zh_cn.json').read_text(encoding='utf-8-sig'))
        assert all('hack.name.' + name in translations for name in ['nominingtrace', 'ghosthand']), root
        assert 'minecraft:happy_ghast' in (java / 'hacks/AutoMountHack.java').read_text(encoding='utf-8'), root
        manifest, inputs = source_manifest(tree), build_inputs(tree)
        matches = [row for row in builds if row['root'] == tree['root'] and row['exit'] == 0
                   and row.get('source_unchanged') and row.get('source_sha256') == manifest
                   and row.get('build_inputs_unchanged') and row.get('build_input_sha256') == inputs]
        checked.append({'root': tree['root'], 'registered': True, 'compiled_current_inputs': bool(matches),
                        'log': matches[-1]['log'] if matches else None})
    report = {'total_targets': len(targets), 'output_files': len(outputs), 'behavioral_probe': str(PLAN / 'batch9/tests/provenance.json'),
              'compiled_current_inputs': sum(row['compiled_current_inputs'] for row in checked), 'targets': checked,
              'runtime_status': 'In-game integration has not been validated.'}
    (PLAN / 'batch9-verification.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(f"Batch9: {len(outputs)} file hashes and {len(targets)} registrations verified; {report['compiled_current_inputs']}/{len(targets)} current full-input compileJava builds.")
    if report['compiled_current_inputs'] != len(targets):
        print('Pending:', ', '.join(row['root'] for row in checked if not row['compiled_current_inputs']))
        raise SystemExit(1)


if __name__ == '__main__':
    main()
