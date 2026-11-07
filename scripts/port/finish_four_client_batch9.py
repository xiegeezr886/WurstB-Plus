"""Apply the final batch9 refinement only after each previous compile finishes."""
import json
from pathlib import Path
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
PLAN = ROOT / '.planning/2026-11-07-four-client-v151-port'


def main():
    targets = json.loads((PLAN / 'target-inventory.json').read_text(encoding='utf-8'))
    state = PLAN / 'batch9-revision2-targets.json'
    done = set(json.loads(state.read_text(encoding='utf-8'))) if state.exists() else set()
    queues = ['batch9-wave1', 'batch9-wave2', 'batch9-neo263']
    while len(done) < len(targets):
        ready = set()
        for queue in queues:
            file = PLAN / (queue + '-compile/results.json')
            if file.exists():
                try:
                    ready.update(row['root'] for row in json.loads(file.read_text(encoding='utf-8')))
                except json.JSONDecodeError:
                    # The producer may be in the middle of replacing its results.
                    continue
        pending = sorted(ready - done)
        if pending:
            result = subprocess.run([sys.executable, '-X', 'utf8', 'scripts/port/sync_four_client_batch9.py', '--skip-building',
                                     *[arg for root in pending for arg in ['--target', root]]], cwd=ROOT, capture_output=True, text=True, encoding='utf-8')
            for line in result.stdout.splitlines():
                if line.startswith('Batch 9 synchronized: '):
                    done.add(line.split(': ', 1)[1])
            state.write_text(json.dumps(sorted(done), indent=2), encoding='utf-8')
            print(f'Final batch9 refinement synchronized: {len(done)}/{len(targets)}', flush=True)
            if result.returncode:
                print(result.stderr, flush=True)
                raise SystemExit(result.returncode)
        if len(done) < len(targets):
            time.sleep(5)
    subprocess.run([sys.executable, '-X', 'utf8', 'scripts/port/test_four_client_batch9.py'], cwd=ROOT, check=True)
    raise SystemExit(subprocess.call([sys.executable, '-X', 'utf8', '-u', 'scripts/port/compile_four_client_matrix.py',
                                     '--batch', 'batch9-final', '--workers', '3', '--reuse-verified', '--verified-neo-cache'], cwd=ROOT))


if __name__ == '__main__':
    main()
