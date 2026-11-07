"""Verify the current batch10 output and cumulative source matrix."""
import hashlib
import json
from compile_four_client_matrix import ROOT, PLAN, build_inputs, source_manifest


def main():
    targets = json.loads((PLAN / 'target-inventory.json').read_text(encoding='utf-8'))
    latest = {}
    files = sorted(PLAN.glob('batch*-outputs.json'), key=lambda file: int(file.name.split('-')[0].removeprefix('batch')))
    for file in files:
        latest.update(json.loads(file.read_text(encoding='utf-8')))
    # Other work may edit shared registries/resources; only assert latest batch10
    # ownership here and report all actual input hashes with the matching builds.
    outputs = json.loads((PLAN / 'batch10-outputs.json').read_text(encoding='utf-8'))
    for relative, digest in outputs.items():
        assert hashlib.sha256((ROOT / relative).read_bytes()).hexdigest() == digest, relative
    builds = []
    for file in PLAN.glob('*-compile/results.json'):
        try:
            builds.extend(json.loads(file.read_text(encoding='utf-8')))
        except json.JSONDecodeError:
            continue
    checked = []
    for tree in targets:
        root = ROOT / tree['root']
        java = root / 'src/main/java/net/wurstclient'
        registry = (java / 'hack/HackList.java').read_text(encoding='utf-8')
        for name in ['AutoWalk', 'AutoNametag', 'Rotation', 'NoMiningTrace', 'GhostHand']:
            assert registry.count('new ' + name + 'Hack();') == 1, (root, name)
        configs = []
        for config in (root / 'src/main/resources').glob('*.mixins.json'):
            data = json.loads(config.read_text(encoding='utf-8-sig'))
            if data.get('package') != 'net.wurstclient.mixin':
                continue
            configs.append(config.name)
            for name in ['AutoWalkMovementMixin', 'NoMiningTraceMixin', 'BetterBeaconsScreenMixin', 'BeaconPowerButtonMixin']:
                assert data['client'].count(name) == 1, (config, name)
        assert configs, root
        manifest, inputs = source_manifest(tree), build_inputs(tree)
        matches = [row for row in builds if row['root'] == tree['root'] and row['exit'] == 0
                   and row.get('source_unchanged') and row.get('source_sha256') == manifest
                   and row.get('build_inputs_unchanged') and row.get('build_input_sha256') == inputs]
        checked.append({'root': tree['root'], 'compiled_current_inputs': bool(matches),
                        'log': matches[-1]['log'] if matches else None})
    report = {'targets': checked, 'total_targets': len(targets), 'batch10_files': len(outputs),
              'compiled_current_inputs': sum(row['compiled_current_inputs'] for row in checked),
              'behavioral_probe': str(PLAN / 'batch10/tests/provenance.json'),
              'runtime_status': 'In-game integration has not been validated.'}
    (PLAN / 'batch10-verification.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(f"Batch10: {len(outputs)} output hashes and {len(targets)} registrations verified; {report['compiled_current_inputs']}/{len(targets)} current full-input builds.")
    pending = [row['root'] for row in checked if not row['compiled_current_inputs']]
    if pending:
        print('Pending:', ', '.join(pending))
        raise SystemExit(1)


if __name__ == '__main__':
    main()
