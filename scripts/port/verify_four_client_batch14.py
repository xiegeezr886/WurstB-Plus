"""Verify current13/14 outputs, callback/test provenance and all66 builds."""
import hashlib
import json
from compile_four_client_matrix import ROOT, PLAN, build_inputs, source_manifest
from test_four_client_batch14 import fov_callbacks


def main():
    targets = json.loads((PLAN / 'target-inventory.json').read_text(encoding='utf-8'))
    outputs = {}
    for batch in [11, 12, 13, 14]:
        outputs.update(json.loads((PLAN / f'batch{batch}-outputs.json').read_text(encoding='utf-8')))
    for relative, digest in outputs.items():
        assert hashlib.sha256((ROOT / relative).read_bytes()).hexdigest() == digest, relative
    for name in ['batch13/tests', 'batch13/tests-fabric-versions-26.3',
                 *['batch14/tests-' + target.replace('/', '-') for target in
                   ['fabric', 'fabric/versions/1.21.5', 'fabric/versions/1.21.10', 'fabric/versions/26.3']]]:
        data = json.loads((PLAN / name / 'provenance.json').read_text(encoding='utf-8'))
        assert data['staged'] is False, name
        root = ROOT / data.get('target', 'fabric') / 'src/main/java/net/wurstclient'
        for relative, digest in data['sha256'].items():
            assert hashlib.sha256((root / relative).read_bytes()).hexdigest() == digest, (name, relative)
    for batch in [13, 14]:
        hooks = json.loads((PLAN / f'batch{batch}/hook-targets.json').read_text(encoding='utf-8'))
        assert len(hooks) == 23
        for row in hooks:
            assert not row['findings']
            hashes = row['sha256'] if batch == 13 else {row['source']: row['sha256']}
            for relative, digest in hashes.items():
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
        for name in ['NoRotate', 'PortalGodMode', 'CustomFov', 'ChatHider', 'Collisions', 'AutoBreed', 'AutoTrash']:
            assert registry.count('new ' + name + 'Hack();') == 1, (root, name)
        configs = []
        for config in (root / 'src/main/resources').glob('*.mixins.json'):
            data = json.loads(config.read_text(encoding='utf-8-sig'))
            if data.get('package') != 'net.wurstclient.mixin':
                continue
            configs.append(config.name)
            for name in ['NoRotatePacketMixin', 'CollisionsShapeMixin', 'CollisionsMovementMixin', 'CollisionsBorderMixin']:
                assert data['client'].count(name) == 1, (config, name)
        assert configs, root
        fov = (java / 'hacks/CustomFovHack.java').read_text(encoding='utf-8')
        assert 'world ? fov.getValue() : itemFov.getValue()' in fov, root
        camera = java / 'mixin/CameraMixin.java'
        hook = camera if camera.exists() and 'onCalculateFov' in camera.read_text(encoding='utf-8') else java / 'mixin/GameRendererMixin.java'
        methods = fov_callbacks(hook.read_text(encoding='utf-8'))
        for config in configs:
            data = json.loads((root / 'src/main/resources' / config).read_text(encoding='utf-8-sig'))
            assert data['client'].count(hook.stem) == 1, (config, hook)
        if hook == camera:
            assert '.changeFov(original, true)' in methods and '.changeFov(original, false)' in methods, hook
        else:
            assert '.changeFov(original, world)' in methods and 'boolean world)' in methods, hook
        manifest, inputs = source_manifest(tree), build_inputs(tree)
        matches = [row for row in builds if row['root'] == tree['root'] and row['exit'] == 0
                   and row.get('source_unchanged') and row.get('source_sha256') == manifest
                   and row.get('build_inputs_unchanged') and row.get('build_input_sha256') == inputs]
        checked.append({'root': tree['root'], 'compiled_current_inputs': bool(matches), 'log': matches[-1]['log'] if matches else None})
    report = {'targets': checked, 'total_targets': len(targets), 'batch11_14_files': len(outputs),
              'compiled_current_inputs': sum(row['compiled_current_inputs'] for row in checked),
              'runtime_status': 'In-game Mixin application, rendering, multiplayer interaction and mod compatibility remain untested.'}
    (PLAN / 'batch14-verification.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(f"Batches13/14: {len(outputs)} cumulative11-14 output hashes,23 mapped hook variants and66 registrations verified; {report['compiled_current_inputs']}/{len(targets)} current full-input builds.")
    pending = [row['root'] for row in checked if not row['compiled_current_inputs']]
    if pending:
        print('Pending:', ', '.join(pending))
        raise SystemExit(1)


if __name__ == '__main__':
    main()
