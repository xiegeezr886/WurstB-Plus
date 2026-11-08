"""Inspect TimeChanger getter targets and tick storage in actual mapped jars."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import importlib.util
import json
from pathlib import Path
from sync_four_client_batch import ROOT, PLAN


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--staged', action='store_true')
    args = parser.parse_args()
    spec = importlib.util.spec_from_file_location('mixin_audit', ROOT / 'scripts/audit-mixin-injections.py')
    audit = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(audit)
    javap = str(Path.home() / '.jdks/jdk25.0.4_10/bin/javap.exe')
    base = PLAN / 'batch16/generated' if args.staged else ROOT

    def check(row):
        root = base / row['root'] / 'src/main/java/net/wurstclient'
        modern = row['mc'].startswith('26.')
        file = root / ('mixin/ClientClockManagerMixin.java' if modern else 'mixin/TimeChangerLevelMixin.java')
        jar = audit.Jar(row['jar'], javap)
        findings = audit.check_file(str(file), jar, row['mc'], file.name)
        assert not findings, findings
        if not modern:
            code = jar.bytecode('net.minecraft.client.multiplayer.ClientLevel')['tickTime']
            # Client ticking reads the original data, not our visual getter.
            assert any('LevelData.getDayTime:()J' in line for line in code), row['mc']
            text = file.read_text(encoding='utf-8')
            if row['mc'] != '1.21.11':
                assert 'public long dayTime()' in text and 'return getDayTime();' in text
                for owner in ['net.minecraft.world.level.Level', 'net.minecraft.client.multiplayer.ClientLevel']:
                    assert 'dayTime' not in jar.bytecode(owner), (row['mc'], owner)
        print(row['mc'] + ': time getter selectors and storage checks passed', flush=True)
        return {'mc': row['mc'], 'jar': row['jar'], 'source': file.relative_to(base).as_posix(),
                'sha256': hashlib.sha256(file.read_bytes()).hexdigest(), 'findings': findings}

    rows = json.loads((PLAN / 'minecraft-api.json').read_text(encoding='utf-8'))
    with ThreadPoolExecutor(max_workers=3) as pool:
        results = list(pool.map(check, rows))
    (PLAN / 'batch16/hook-targets.json').write_text(json.dumps({'staged': args.staged, 'targets': results}, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
