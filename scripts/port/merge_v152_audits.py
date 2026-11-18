"""Merge successful build evidence only when current sources and JARs match.

Does not run Gradle or convert failed/stale results into successful evidence.
"""
import argparse
import hashlib
import json
from pathlib import Path

from prepare_v152 import ROOT
from verify_v152_targets import fingerprint


def merge(targets, audits):
    candidates = {}
    for path in audits:
        rows = json.loads(path.read_text('utf-8-sig'))
        if not isinstance(rows, list):
            continue
        for row in rows:
            if isinstance(row, dict) and row.get('project') and not row.get('errors'):
                candidates.setdefault(row['project'], []).append((path, row))
    results = []
    for target in targets:
        relative = target['project']
        source = fingerprint(ROOT / relative)
        accepted = None
        reasons = []
        for audit, row in reversed(candidates.get(relative, [])):
            if row.get('source_sha256') != source:
                reasons.append(str(audit) + ': sources changed')
                continue
            tests = row.get('tests', {})
            if not tests.get('tests') or any(tests.get(k, 0) for k in ('failures', 'errors', 'skipped')):
                reasons.append(str(audit) + ': missing or failing tests')
                continue
            package = row.get('package') or {}
            jar = Path(package.get('jar', ''))
            if package.get('errors') or not jar.is_file() or hashlib.sha256(jar.read_bytes()).hexdigest() != package.get('sha256'):
                reasons.append(str(audit) + ': production JAR differs or is missing')
                continue
            accepted = dict(row, evidence_audit=str(audit.resolve().relative_to(ROOT)))
            break
        if accepted is None:
            accepted = dict(target, source_sha256=source,
                            errors=['No successful audit matches current source and production JAR'],
                            rejected_evidence=reasons)
        results.append(accepted)
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('targets', type=Path)
    parser.add_argument('audits', nargs='+', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    targets = json.loads(args.targets.read_text('utf-8-sig'))
    if len({row['project'] for row in targets}) != len(targets):
        raise ValueError('Duplicate target')
    rows = merge(targets, args.audits)
    args.output.write_text(json.dumps(rows, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    failures = [r['project'] for r in rows if r['errors']]
    print(f'MERGED {len(rows) - len(failures)}/{len(rows)}; failures={failures}')
    raise SystemExit(bool(failures))


if __name__ == '__main__':
    main()
