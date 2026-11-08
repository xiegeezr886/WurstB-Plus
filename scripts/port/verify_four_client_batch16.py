"""Verify TimeChanger provenance, registration and current complete builds."""
import hashlib
import json
from compile_four_client_matrix import ROOT, PLAN, build_inputs, source_manifest


def main():
    trees = json.loads((PLAN / 'target-inventory.json').read_text(encoding='utf-8'))
    outputs = json.loads((PLAN / 'batch16-outputs.json').read_text(encoding='utf-8'))
    for path, digest in outputs.items():
        assert hashlib.sha256((ROOT / path).read_bytes()).hexdigest() == digest, path
    for target in ['fabric', 'fabric/versions/1.21.11', 'fabric/versions/26.1', 'fabric/versions/26.3']:
        data = json.loads((PLAN / 'batch16' / ('tests-' + target.replace('/', '-')) / 'provenance.json').read_text(encoding='utf-8'))
        assert not data['staged'] and data['target'] == target
        for path, digest in data['sha256'].items():
            file = ROOT / target / 'src/main/java/net/wurstclient' / path
            assert hashlib.sha256(file.read_bytes()).hexdigest() == digest, file
    hooks = json.loads((PLAN / 'batch16/hook-targets.json').read_text(encoding='utf-8'))
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
        assert registry.count('new TimeChangerHack();') == 1, java
        module = (java / 'hacks/TimeChangerHack.java').read_text(encoding='utf-8')
        assert 'PacketInputListener' not in module and 'setDayTime' not in module and 'setGameTime' not in module
        hook = 'ClientClockManagerMixin' if tree['mc'].startswith('26.') else 'TimeChangerLevelMixin'
        configs = [json.loads(f.read_text(encoding='utf-8-sig')) for f in (root / 'src/main/resources').glob('*.mixins.json')]
        configs = [c for c in configs if c.get('package') == 'net.wurstclient.mixin']
        assert configs and all(c['client'].count(hook) == 1 for c in configs), root
        assert all('TimeChangerAccessMixin' not in c['client'] for c in configs), root
        manifest, inputs = source_manifest(tree), build_inputs(tree)
        matches = [row for row in builds if row['root'] == tree['root'] and row['exit'] == 0
                   and row.get('source_unchanged') and row.get('source_sha256') == manifest
                   and row.get('build_inputs_unchanged') and row.get('build_input_sha256') == inputs]
        checked.append({'root': tree['root'], 'compiled_current_inputs': bool(matches),
                        'log': matches[-1]['log'] if matches else None})
    report = {'targets': checked, 'total_targets': len(trees), 'batch16_files': len(outputs),
              'hook_variants': len(hooks['targets']), 'compiled_current_inputs': sum(r['compiled_current_inputs'] for r in checked),
              'runtime_status': 'No in-game Mixin application, visual integration or multiplayer validation performed.'}
    (PLAN / 'batch16-verification.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(f"Batch16: {len(outputs)} output hashes,23 hook targets and66 registrations verified; {report['compiled_current_inputs']}/{len(trees)} current full-input builds.")
    pending = [r['root'] for r in checked if not r['compiled_current_inputs']]
    if pending:
        print('Pending:', ', '.join(pending))
        raise SystemExit(1)


if __name__ == '__main__':
    main()
