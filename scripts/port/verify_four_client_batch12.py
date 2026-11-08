"""Verify batches11/12 output, runtime wiring, behavioral provenance and complete builds."""
import hashlib
import json
from compile_four_client_matrix import ROOT, PLAN, build_inputs, source_manifest


def main():
    targets = json.loads((PLAN / 'target-inventory.json').read_text(encoding='utf-8'))
    outputs = {}
    for batch in [11, 12]:
        outputs.update(json.loads((PLAN / f'batch{batch}-outputs.json').read_text(encoding='utf-8')))
    for relative, digest in outputs.items():
        assert hashlib.sha256((ROOT / relative).read_bytes()).hexdigest() == digest, relative
    for name in ['batch11/tests', 'batch12/tests-legacy', 'batch12/tests-modern']:
        data = json.loads((PLAN / name / 'provenance.json').read_text(encoding='utf-8'))
        assert data['staged'] is False, name
        root = ROOT / data.get('target', 'fabric') / 'src/main/java/net/wurstclient'
        for relative, digest in data['sha256'].items():
            assert hashlib.sha256((root / relative).read_bytes()).hexdigest() == digest, (name, relative)
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
        for name in ['Collisions', 'AutoBreed', 'AutoTrash', 'AutoWalk', 'AutoNametag', 'Rotation']:
            assert registry.count('new ' + name + 'Hack();') == 1, (root, name)
        expected = ['CollisionsShapeMixin', 'CollisionsMovementMixin', 'CollisionsBorderMixin', 'AutoWalkMovementMixin']
        configs = []
        for config in (root / 'src/main/resources').glob('*.mixins.json'):
            data = json.loads(config.read_text(encoding='utf-8-sig'))
            if data.get('package') != 'net.wurstclient.mixin':
                continue
            configs.append(config.name)
            for name in expected:
                assert data['client'].count(name) == 1, (config, name)
                assert (java / 'mixin' / (name + '.java')).is_file(), (config, name)
        assert configs, root
        translations = json.loads((root / 'src/main/resources/assets/wurst/translations/zh_cn.json').read_text(encoding='utf-8-sig'))
        assert translations.get('hack.name.collisions') and translations.get('description.wurst.hack.collisions'), root
        manifest, inputs = source_manifest(tree), build_inputs(tree)
        matches = [row for row in builds if row['root'] == tree['root'] and row['exit'] == 0
                   and row.get('source_unchanged') and row.get('source_sha256') == manifest
                   and row.get('build_inputs_unchanged') and row.get('build_input_sha256') == inputs]
        checked.append({'root': tree['root'], 'compiled_current_inputs': bool(matches), 'log': matches[-1]['log'] if matches else None})
    report = {'targets': checked, 'total_targets': len(targets), 'batch11_12_files': len(outputs),
              'compiled_current_inputs': sum(row['compiled_current_inputs'] for row in checked),
              'runtime_status': 'In-game integration and Lithium collision compatibility have not been validated.',
              'remaining_behavior': 'FO AutoTrash graphical all-enchantment editor and module linkage remain pending.'}
    (PLAN / 'batch12-verification.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(f"Batches11/12: {len(outputs)} output hashes and {len(targets)} registrations verified; {report['compiled_current_inputs']}/{len(targets)} current full-input builds.")
    pending = [row['root'] for row in checked if not row['compiled_current_inputs']]
    if pending:
        print('Pending:', ', '.join(pending))
        raise SystemExit(1)


if __name__ == '__main__':
    main()
