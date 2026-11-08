"""Validate weather getter targets and vanilla composition in23 mapped jars."""
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
    base = PLAN / 'batch18/generated' if args.staged else ROOT

    def check(row):
        file = base / row['root'] / 'src/main/java/net/wurstclient/mixin/WeatherChangerLevelMixin.java'
        jar = audit.Jar(row['jar'], javap)
        assert audit.resolve_target(file.read_text(encoding='utf-8')) == 'net.minecraft.world.level.Level'
        findings = audit.check_file(str(file), jar, row['mc'], file.name)
        assert not findings, findings
        code = jar.bytecode('net.minecraft.world.level.Level')['getThunderLevel']
        assert sum('getRainLevel:(F)F' in line for line in code) == 1
        assert sum('fmul' in line for line in code) == 1
        print(row['mc'] + ': weather targets passed', flush=True)
        return {'mc': row['mc'], 'jar': row['jar'], 'source': file.relative_to(base).as_posix(),
                'sha256': hashlib.sha256(file.read_bytes()).hexdigest(), 'findings': findings}

    rows = json.loads((PLAN / 'minecraft-api.json').read_text(encoding='utf-8'))
    with ThreadPoolExecutor(max_workers=3) as pool:
        results = list(pool.map(check, rows))
    (PLAN / 'batch18/hook-targets.json').write_text(json.dumps({'staged': args.staged, 'targets': results}, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
