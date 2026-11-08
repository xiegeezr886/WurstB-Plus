"""Compile v1.5.1 combat changes using the existing verified build adapters."""
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
import argparse
import hashlib
import json

import compile_four_client_matrix as builds
from refactor_openopal_combat import ROOT, PLAN, PACKAGE, TRANSFORMS, NEW_FILES, projects


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--target', action='append')
    parser.add_argument('--workers', type=int, default=2)
    parser.add_argument('--reuse', action='store_true')
    parser.add_argument('--label', help='Separate result directory for a focused concurrent probe')
    parser.add_argument('--repair-cache', action='store_true', help='Retry missing dependency/metadata failures online, without changing project files')
    args = parser.parse_args()
    logdir = PLAN / 'compile'
    if args.label:
        assert args.label.isidentifier(), 'Use a simple identifier as label'
        logdir = logdir / args.label
    logdir.mkdir(parents=True, exist_ok=True)
    result_path = logdir / 'results.json'
    prior = json.loads(result_path.read_text(encoding='utf-8')) if result_path.exists() else []
    results = {r['root']: r for r in prior}
    def reusable(row):
        current = builds.build_inputs(row)
        for result_file in sorted((PLAN/'compile').rglob('results.json'), key=lambda p:p.stat().st_mtime, reverse=True):
            for r in json.loads(result_file.read_text(encoding='utf-8')):
                if r['root']==row['root'] and r['exit']==0 and r.get('build_inputs_unchanged') and r.get('build_input_sha256')==current:
                    return r
        return None
    rows = []
    for project in projects():
        props = dict(line.split('=', 1) for line in project.joinpath('gradle.properties').read_text(encoding='utf-8-sig').splitlines() if '=' in line and not line.startswith('#'))
        row = {'root': project.relative_to(ROOT).as_posix(), 'mc': props['minecraft_version']}
        if args.target and row['root'] not in args.target:
            continue
        old = reusable(row) if args.reuse else None
        if old:
            results[row['root']] = old
            print('Reused', row['root'], flush=True)
            continue
        rows.append(row)
    def compile_one(row):
        if args.reuse:
            cached=reusable(row)
            if cached:return cached
        init = builds.PLAN / 'forge7-mavenizer-offline.gradle' if row['root'].startswith('versions/') else None
        if row['root']=='neoforge/versions/26.3':init=PLAN/'central-lwjgl.gradle'
        result=builds.compile_tree(row, logdir, verified_neo_cache=True, init_script=init, isolated_fabric=True)
        if result['exit'] and args.repair_cache:
            log=Path(result['log'])
            diagnostic=log.read_text(encoding='utf-8',errors='replace')
            if any(message in diagnostic for message in ['Offline mode is enabled','available for offline mode','No cached version']):
                log.with_suffix('.offline.log').write_bytes(log.read_bytes())
                online_init=PLAN/'central-lwjgl.gradle' if row['root']=='neoforge/versions/26.3' else None
                result=builds.compile_tree(row, logdir, online=True, verified_neo_cache=True, init_script=online_init, isolated_fabric=True)
                result['cache_repaired_online']=True
        return result
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = {pool.submit(compile_one, row): row for row in rows}
        for future in as_completed(futures):
            row = futures[future]
            try:
                result = future.result()
            except Exception as error:
                result = {**row, 'exit': -1, 'error': repr(error)}
            results[row['root']] = result
            result_path.write_text(json.dumps(list(results.values()), indent=2), encoding='utf-8')
            print(row['root'], result['exit'], result.get('seconds'), result.get('error', ''), flush=True)
    result_path.write_text(json.dumps(list(results.values()), indent=2), encoding='utf-8')
    bad = [r['root'] for r in results.values() if r['exit'] != 0 or not r.get('build_inputs_unchanged') or r.get('build_input_sha256')!=builds.build_inputs(r)]
    print('Recorded:', len(results), 'Failed or changed during build:', bad)
    raise SystemExit(bool(bad))


if __name__ == '__main__':
    main()
