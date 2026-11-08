"""Verify batches17/18 merged outputs, behavior and current complete builds."""
import hashlib
import json
from compile_four_client_matrix import ROOT, PLAN, build_inputs, source_manifest


def main():
    trees = json.loads((PLAN / 'target-inventory.json').read_text(encoding='utf-8'))
    outputs = {}
    for batch in [17, 18]:
        outputs.update(json.loads((PLAN / f'batch{batch}-outputs.json').read_text(encoding='utf-8')))
    for path, digest in outputs.items():
        assert hashlib.sha256((ROOT / path).read_bytes()).hexdigest() == digest, path
    for batch, targets in [(17, ['fabric', 'fabric/versions/1.20.5', 'fabric/versions/26.3']),
                           (18, ['fabric', 'fabric/versions/26.3'])]:
        for target in targets:
            data = json.loads((PLAN / f'batch{batch}' / ('tests-' + target.replace('/', '-')) / 'provenance.json').read_text(encoding='utf-8'))
            assert not data['staged'] and data['target'] == target
            for path, digest in data['sha256'].items():
                file = ROOT / target / 'src/main/java/net/wurstclient' / path
                assert hashlib.sha256(file.read_bytes()).hexdigest() == digest, file
        hooks = json.loads((PLAN / f'batch{batch}/hook-targets.json').read_text(encoding='utf-8'))
        assert not hooks['staged'] and len(hooks['targets']) == 23
        for row in hooks['targets']:
            assert not row['findings']
            assert hashlib.sha256((ROOT / row['source']).read_bytes()).hexdigest() == row['sha256'], row['source']
    builds = []
    for file in PLAN.glob('*-compile/results.json'):
        try:
            builds.extend(json.loads(file.read_text(encoding='utf-8')))
        except json.JSONDecodeError:
            continue
    checked = []
    for tree in trees:
        root = ROOT / tree['root']
        java = root / 'src/main/java/net/wurstclient'
        registry = (java / 'hack/HackList.java').read_text(encoding='utf-8')
        for name in ['Reach', 'WeatherChanger']:
            assert registry.count('new ' + name + 'Hack();') == 1, java
        weather = (java / 'hacks/WeatherChangerHack.java').read_text(encoding='utf-8')
        assert all(s not in weather for s in ['PacketInputListener', 'setRainLevel', 'setThunderLevel', 'oldRainLevel'])
        legacy = tree['mc'] in ['1.20.1', '1.20.2', '1.20.3', '1.20.4']
        hook = 'ReachLegacyRendererMixin' if legacy else 'ReachPlayerMixin'
        configs = [json.loads(f.read_text(encoding='utf-8-sig')) for f in (root / 'src/main/resources').glob('*.mixins.json')]
        configs = [c for c in configs if c.get('package') == 'net.wurstclient.mixin']
        assert configs and all(c['client'].count(hook) == 1 and c['client'].count('WeatherChangerLevelMixin') == 1 for c in configs), root
        if not legacy:
            for file in (java / 'mixin').glob('*.java'):
                if file.name != 'ReachPlayerMixin.java':
                    assert 'reachHack' not in file.read_text(encoding='utf-8'), file
        manifest, inputs = source_manifest(tree), build_inputs(tree)
        matches = [row for row in builds if row['root'] == tree['root'] and row['exit'] == 0
                   and row.get('source_unchanged') and row.get('source_sha256') == manifest
                   and row.get('build_inputs_unchanged') and row.get('build_input_sha256') == inputs]
        checked.append({'root': tree['root'], 'compiled_current_inputs': bool(matches),
                        'log': matches[-1]['log'] if matches else None})
    report = {'targets': checked, 'total_targets': len(trees), 'batch17_18_files': len(outputs),
              'hook_variants_per_batch': 23, 'compiled_current_inputs': sum(r['compiled_current_inputs'] for r in checked),
              'runtime_status': 'No in-game Mixin application, targeting, weather visuals or multiplayer validation performed.'}
    (PLAN / 'batch18-verification.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(f"Batch17/18: {len(outputs)} merged output hashes,23 hook variants per batch and66 registrations verified; {report['compiled_current_inputs']}/{len(trees)} current full-input builds.")
    pending = [r['root'] for r in checked if not r['compiled_current_inputs']]
    if pending:
        print('Pending:', ', '.join(pending))
        raise SystemExit(1)


if __name__ == '__main__':
    main()
