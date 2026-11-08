"""Verify Parkour/Sneak output, behavior provenance and all current builds."""
import hashlib
import json
from compile_four_client_matrix import ROOT, PLAN, build_inputs, source_manifest


def main():
    trees = json.loads((PLAN / 'target-inventory.json').read_text(encoding='utf-8'))
    outputs = json.loads((PLAN / 'batch15-outputs.json').read_text(encoding='utf-8'))
    assert len(outputs) == len(trees) * 2
    for path, digest in outputs.items():
        assert hashlib.sha256((ROOT / path).read_bytes()).hexdigest() == digest, path
    for name in ['tests-fabric', 'tests-fabric-versions-26.3']:
        data = json.loads((PLAN / 'batch15' / name / 'provenance.json').read_text(encoding='utf-8'))
        assert not data['staged']
        for relative, digest in data['sha256'].items():
            file = ROOT / data['target'] / 'src/main/java/net/wurstclient' / relative
            assert hashlib.sha256(file.read_bytes()).hexdigest() == digest, file
    builds = []
    for file in PLAN.glob('*-compile/results.json'):
        try:
            builds.extend(json.loads(file.read_text(encoding='utf-8')))
        except json.JSONDecodeError:
            continue
    checked = []
    for tree in trees:
        java = ROOT / tree['root'] / 'src/main/java/net/wurstclient'
        registry = (java / 'hack/HackList.java').read_text(encoding='utf-8')
        for name in ['Parkour', 'Sneak']:
            assert registry.count('new ' + name + 'Hack();') == 1, (java, name)
        parkour = (java / 'hacks/ParkourHack.java').read_text(encoding='utf-8')
        sneak = (java / 'hacks/SneakHack.java').read_text(encoding='utf-8')
        assert 'getBlockCollisions(MC.player, adjustedBox)' in parkour and 'blockCollisionsOnly' in parkour
        assert sneak.count('offWhileFlying.isChecked() && isFlying()') == 2, java
        assert 'owner == MC.player' in sneak and 'releaseSneak();' in sneak
        manifest, inputs = source_manifest(tree), build_inputs(tree)
        matches = [row for row in builds if row['root'] == tree['root'] and row['exit'] == 0
                   and row.get('source_unchanged') and row.get('source_sha256') == manifest
                   and row.get('build_inputs_unchanged') and row.get('build_input_sha256') == inputs]
        checked.append({'root': tree['root'], 'compiled_current_inputs': bool(matches),
                        'log': matches[-1]['log'] if matches else None})
    report = {'targets': checked, 'total_targets': len(trees), 'batch15_files': len(outputs),
              'compiled_current_inputs': sum(row['compiled_current_inputs'] for row in checked),
              'runtime_status': 'No in-game integration or multiplayer validation performed.'}
    (PLAN / 'batch15-verification.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(f"Batch15: {len(outputs)} output hashes and66 registrations verified; {report['compiled_current_inputs']}/{len(trees)} current full-input builds.")
    pending = [row['root'] for row in checked if not row['compiled_current_inputs']]
    if pending:
        print('Pending:', ', '.join(pending))
        raise SystemExit(1)


if __name__ == '__main__':
    main()
