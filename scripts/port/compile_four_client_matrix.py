"""Compile the selected v1.5.1 source matrix without cleaning or publishing."""
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
import argparse
import hashlib
import json
import os
import subprocess
import time
import zipfile

ROOT = Path(__file__).resolve().parents[2]
PLAN = ROOT / '.planning/2026-11-07-four-client-v151-port'


def build_inputs(tree):
    """Track all project sources and build files, including concurrent edits."""
    root = ROOT / tree['root']
    files = list((root / 'src').rglob('*'))
    files += [root / name for name in ['build.gradle', 'settings.gradle', 'gradle.properties',
                                     'gradle/wrapper/gradle-wrapper.properties']]
    return {file.relative_to(ROOT).as_posix(): hashlib.sha256(file.read_bytes()).hexdigest()
            for file in sorted(files) if file.is_file()}


def wait_for_existing_build(tree):
    prefix = str(ROOT / tree['root']).lower() + '\\'
    query = "@(Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'java.exe' -and $_.CommandLine -match 'org.gradle.wrapper.GradleWrapperMain' } | Select-Object -ExpandProperty CommandLine) | ConvertTo-Json -Compress"
    reported = False
    while True:
        result = subprocess.run(['powershell', '-NoProfile', '-Command', query], capture_output=True, check=True)
        active = json.loads(result.stdout.decode('utf-8-sig') or '[]')
        if isinstance(active, str):
            active = [active]
        if not any(prefix + '\\gradle\\wrapper' in cmd.lower() or prefix + 'gradle\\wrapper' in cmd.lower() for cmd in active):
            return
        if not reported:
            print('Waiting for existing build:', tree['root'], flush=True)
            reported = True
        time.sleep(5)

def source_manifest(tree):
    manifest = {}
    for path in sorted(PLAN.glob('batch*-outputs.json')):
        for relative in json.loads(path.read_text(encoding='utf-8')):
            if relative.startswith(tree['root'] + '/src/'):
                manifest[relative] = hashlib.sha256((ROOT / relative).read_bytes()).hexdigest()
    return manifest

def compile_tree(tree, logdir, online=False, verified_neo_cache=False, init_script=None, isolated_fabric=False):
    mc = tree['mc']
    major = 25 if mc.startswith('26.') else 17 if mc in ['1.20.1', '1.20.2', '1.20.3', '1.20.4'] else 21
    choices = {
        17: Path('C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'),
        21: Path.home() / '.jdks/jdk21.0.12_12',
        25: Path.home() / '.jdks/jdk25.0.4_10',
    }
    java = Path(os.environ.get(f'WURSTBPLUS_JAVA{major}', choices[major]))
    assert (java / 'bin/javac.exe').is_file(), java
    env = os.environ.copy()
    env['JAVA_HOME'] = str(java)
    log = logdir / (tree['root'].replace('/', '-') + '.log')
    wait_for_existing_build(tree)
    started = time.monotonic()
    manifest = source_manifest(tree)
    inputs = build_inputs(tree)
    extra_args = []
    if verified_neo_cache and tree['root'].startswith('neoforge/versions/1.20.'):
        cache = ROOT / tree['root'] / '.gradle/caches/minecraft/versions' / mc
        downloads = json.loads((cache / 'metadata.json').read_text(encoding='utf-8'))['downloads']
        for key, name in [('client', 'client.jar'), ('server', 'server.jar'),
                          ('client_mappings', 'client.txt'), ('server_mappings', 'server.txt')]:
            artifact = cache / name
            expected = downloads[key]
            if not artifact.exists() and key == 'client':
                # NeoGradle's offline downloader deletes this file before failing.
                candidate = Path.home() / '.gradle/caches/fabric-loom' / mc / 'minecraft-client.jar'
                raw = candidate.read_bytes()
                assert len(raw) == expected['size'] and hashlib.sha1(raw).hexdigest() == expected['sha1'], candidate
                artifact.write_bytes(raw)
            assert artifact.stat().st_size == expected['size'], artifact
            assert hashlib.sha1(artifact.read_bytes()).hexdigest() == expected['sha1'], artifact
        from verify_neo_libraries import verify_libraries
        verify_libraries(tree)
        extra_args = ['--no-build-cache', '-Pnet.neoforged.gradle.caching.enabled=false',
                      '-I', str(PLAN / 'verified-neo-artifacts.gradle')]
        game_jars = [file for file in (ROOT / tree['root'] / '.gradle/repositories/ng_dummy_ng/net/neoforged/neoforge').glob('*/*.jar')
                     if not file.name.endswith('-sources.jar')]
        assert len(game_jars) == 1, game_jars
        with zipfile.ZipFile(game_jars[0]) as archive:
            has_game = 'net/minecraft/client/Minecraft.class' in archive.namelist()
        if not has_game:
            extra_args += ['-I', str(PLAN / 'rebuild-neoform.gradle')]
    if init_script:
        extra_args += ['--no-configuration-cache', '-I', str(Path(init_script).resolve())]
    if isolated_fabric and tree['root'].startswith('fabric'):
        extra_args += ['--no-configuration-cache', '-I', str(PLAN / 'isolated-fabric-compile.gradle')]
    with log.open('wb') as output:
        run = subprocess.run([str(ROOT / tree['root'] / 'gradlew.bat'), *([] if online else ['--offline']),
            '--no-daemon', '--console=plain', '--max-workers=2', *extra_args, 'compileJava'],
            cwd=ROOT / tree['root'], env=env, stdout=output, stderr=subprocess.STDOUT)
    return {**tree, 'exit': run.returncode, 'seconds': round(time.monotonic() - started, 1),
            'jdk': str(java), 'log': str(log), 'source_sha256': manifest,
            'verified_neo_cache': verified_neo_cache and tree['root'].startswith('neoforge/versions/1.20.'),
            'init_script': str(Path(init_script).resolve()) if init_script else None,
            'isolated_fabric': isolated_fabric and tree['root'].startswith('fabric'),
            'build_input_sha256': inputs, 'build_inputs_unchanged': inputs == build_inputs(tree),
            'source_unchanged': all(hashlib.sha256((ROOT / p).read_bytes()).hexdigest() == h for p, h in manifest.items())}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--target', action='append')
    parser.add_argument('--exclude-target', action='append', default=[])
    parser.add_argument('--workers', type=int, default=2)
    parser.add_argument('--batch', default='batch1')
    parser.add_argument('--online', action='store_true')
    parser.add_argument('--init-script', help='Optional existing Gradle repository/init configuration.')
    parser.add_argument('--isolated-fabric', action='store_true', help='Use isolated Fabric build outputs during concurrent packaging work.')
    parser.add_argument('--reuse-verified', action='store_true', help='Reuse a successful run only when all current task file hashes match.')
    parser.add_argument('--verified-neo-cache', action='store_true', help='Validate all Mojang artifact hashes before skipping destructive offline NeoGradle cache tasks.')
    args = parser.parse_args()
    rows = json.loads((PLAN / 'target-inventory.json').read_text(encoding='utf-8'))
    if args.target:
        rows = [r for r in rows if r['root'] in args.target]
    rows = [r for r in rows if r['root'] not in args.exclude_target]
    logdir = PLAN / (args.batch + '-compile')
    logdir.mkdir(exist_ok=True)
    results = []
    pending = rows
    if args.reuse_verified:
        prior = []
        for path in PLAN.glob('*-compile/results.json'):
            prior.extend(json.loads(path.read_text(encoding='utf-8')))
        pending = []
        for row in rows:
            manifest = source_manifest(row)
            inputs = build_inputs(row)
            matches = [r for r in prior if r['root'] == row['root'] and r['exit'] == 0
                       and r.get('source_unchanged') and r.get('source_sha256') == manifest
                       and r.get('build_inputs_unchanged') and r.get('build_input_sha256') == inputs]
            if matches:
                results.append({**matches[-1], 'reused_verified_result': True})
                print('Reused verified sources:', row['root'], flush=True)
            else:
                pending.append(row)
        (logdir / 'results.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = [pool.submit(compile_tree, r, logdir, args.online, args.verified_neo_cache, args.init_script, args.isolated_fabric) for r in pending]
        for future in as_completed(futures):
            row = future.result()
            results.append(row)
            (logdir / 'results.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
            print(f"{len(results)}/{len(rows)} {row['root']} exit={row['exit']} {row['seconds']}s", flush=True)
    failed = [r for r in results if r['exit']]
    print(f'Completed: {len(results) - len(failed)}/{len(results)} compileJava passed.', flush=True)
    raise SystemExit(bool(failed))

if __name__ == '__main__':
    main()
