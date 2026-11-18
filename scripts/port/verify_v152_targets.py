"""Build, run real JUnit tests and recursively audit selected v1.5.2 targets.

Uses isolated matrix output directories. Does not clean, change sources or
publish. Input is an array of {project, loader, mc}; see prepare_v152.projects.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import hashlib
import json
import os
import re
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET

from prepare_v152 import ROOT, production_task, props, version_key
from verify_v152_package import inspect
from native_seed_versions import upstream_for, cloth_for


def fingerprint(project):
    files = [p for folder in ('src/main', 'src/test')
             for p in (project / folder).rglob('*') if p.is_file()]
    files += [project / n for n in ('build.gradle', 'gradle.properties',
                                   'settings.gradle', 'gradle/wrapper/gradle-wrapper.properties')]
    shared_seed = re.search(r'seedcracker-native(1204|1206|1214|1218|12111|263)\.gradle', (project / 'build.gradle').read_text('utf-8-sig'))
    if shared_seed:
        mc = props(project / 'gradle.properties')['minecraft_version']
        upstream = upstream_for(mc)
        files += [p for p in (ROOT / 'native/seedcrackerx' / upstream).rglob('*') if p.is_file()]
        if shared_seed[1] in ('1218', '12111', '263'):
            cloth = cloth_for(mc)
            files += [p for p in (ROOT / 'native/cloth-config' / cloth).rglob('*') if p.is_file()]
        files.append(ROOT / ('gradle/seedcracker-native' + shared_seed[1] + '.gradle'))
    digest = hashlib.sha256()
    for path in sorted(set(files)):
        if not path.is_file():
            continue
        digest.update(path.relative_to(ROOT).as_posix().encode())
        digest.update(hashlib.sha256(path.read_bytes()).digest())
    return digest.hexdigest()


def verify(row, logs, online, init_scripts=()):
    relative, loader, mc = row['project'], row['loader'], row['mc']
    project = (ROOT / relative).resolve()
    if not project.is_relative_to(ROOT) or not (project / 'gradlew.bat').is_file():
        raise ValueError('Invalid project: ' + relative)
    source = fingerprint(project)
    major = 25 if mc.startswith('26.') else 17 if version_key(mc) <= (1, 20, 4) else 21
    toolchains = ROOT / '.dsh-tmp/toolchains'
    java = next(p for p in toolchains.glob(f'zulu{major}.*') if (p / 'bin/java.exe').is_file())
    paths = ','.join(str(p) for p in toolchains.glob('zulu*') if (p / 'bin/java.exe').is_file())
    options = ('--patch-module=jdk.zipfs=' + str(ROOT / f'tools/build-env/zipfs-patch{major}.jar')
               + ' -Dorg.gradle.java.installations.paths=' + paths)
    env = os.environ.copy()
    env.update(JAVA_HOME=str(java), GRADLE_USER_HOME=str(ROOT / '.dsh-tmp/gradle-home'))
    env['JAVA_TOOL_OPTIONS'] = '' if loader == 'Forge' else options
    env['GRADLE_OPTS'] = options if loader == 'Forge' else ''
    output = ROOT / '.dsh-tmp/v152-matrix' / ('root' if relative == '.' else relative.replace('/', '-'))
    temp = output / 'tool-tmp'
    temp.mkdir(parents=True, exist_ok=True)
    env.update(TEMP=str(temp), TMP=str(temp))
    task = production_task(loader, mc, relative)
    command = [str(project / 'gradlew.bat'), '-p', str(project),
               '-I', str(ROOT / 'scripts/port/verify_v152_matrix.gradle'),
               'test', task, '--console=plain', '--max-workers=2', '--no-configuration-cache']
    for script in init_scripts:
        command += ['-I', str(script.resolve())]
    if not online:
        command.append('--offline')
    log = logs / (relative.replace('/', '-') + '.log')
    start = time.monotonic()
    print('START ' + relative, flush=True)
    with log.open('w', encoding='utf-8') as stream:
        result = subprocess.run(command, cwd=ROOT, env=env, stdout=stream, stderr=subprocess.STDOUT)
    errors = []
    if result.returncode:
        errors.append('Gradle exit ' + str(result.returncode))
    if fingerprint(project) != source:
        errors.append('Source inputs changed during verification')
    tests = dict(tests=0, failures=0, errors=0, skipped=0)
    for xml in (output / 'test-results/test').glob('TEST-*.xml'):
        suite = ET.parse(xml).getroot()
        for key in tests:
            tests[key] += int(suite.get(key, 0))
    if tests['tests'] == 0 or any(tests[k] for k in ('failures', 'errors', 'skipped')):
        errors.append('JUnit did not pass without skips: ' + str(tests))
    version = props(project / 'gradle.properties')['mod_version']
    candidates = list((output / 'libs').glob('*' + version + '.jar'))
    package = None
    if result.returncode == 0 and len(candidates) == 1:
        package = inspect(candidates[0])
        errors.extend(package['errors'])
        import zipfile
        with zipfile.ZipFile(candidates[0]) as archive:
            if version_key(mc) >= (1, 21, 2) and 'net/wurstclient/util/render/LsdPostEffect.class' not in archive.namelist():
                errors.append('LSD implementation missing from production jar')
    elif result.returncode == 0:
        errors.append('Expected exactly one production jar, found ' + str(len(candidates)))
    report = dict(**row, source_sha256=source, log=str(log.relative_to(ROOT)),
                  seconds=round(time.monotonic() - start, 1), tests=tests,
                  package=package, errors=errors)
    if init_scripts:
        report['additional_init_scripts'] = [
            {'path': str(path.resolve()), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
            for path in init_scripts]
    print(('FAIL ' if errors else 'PASS ') + relative + f' ({report["seconds"]}s)', flush=True)
    return report


def collect(row, logs):
    """Recover evidence from completed builds without rerunning Gradle.

    This mode explicitly records a post-build input digest and rejects source
    files newer than the jar or test reports. It does not claim a before/after
    digest check. Use only after the builds finish and before further edits.
    """
    relative = row['project']
    project = ROOT / relative
    output = ROOT / '.dsh-tmp/v152-matrix' / ('root' if relative == '.' else relative.replace('/', '-'))
    log = logs / (relative.replace('/', '-') + '.log')
    errors = []
    if not log.is_file() or 'BUILD SUCCESSFUL' not in log.read_text('utf-8', errors='replace'):
        errors.append('No successful Gradle build in selected log')
    version = props(project / 'gradle.properties')['mod_version']
    jars = list((output / 'libs').glob('*' + version + '.jar'))
    package = inspect(jars[0]) if len(jars) == 1 else None
    if package is None:
        errors.append('Expected one production jar')
    else:
        errors.extend(package['errors'])
        import zipfile
        with zipfile.ZipFile(jars[0]) as archive:
            if version_key(row['mc']) >= (1, 21, 2) and 'net/wurstclient/util/render/LsdPostEffect.class' not in archive.namelist():
                errors.append('LSD implementation missing from production jar')
    tests = dict(tests=0, failures=0, errors=0, skipped=0)
    xmls = list((output / 'test-results/test').glob('TEST-*.xml'))
    for xml in xmls:
        suite = ET.parse(xml).getroot()
        for key in tests:
            tests[key] += int(suite.get(key, 0))
    if tests['tests'] == 0 or any(tests[k] for k in ('failures', 'errors', 'skipped')):
        errors.append('Missing or failed/skipped JUnit tests')
    sources = [p for folder in ('src/main', 'src/test')
               for p in (project / folder).rglob('*') if p.is_file()]
    sources += [project / name for name in ('build.gradle', 'settings.gradle', 'gradle.properties')]
    latest_source = max(p.stat().st_mtime for p in sources if p.is_file())
    if package is not None and (latest_source > jars[0].stat().st_mtime
            or latest_source > max((p.stat().st_mtime for p in xmls), default=0)):
        errors.append('Sources newer than production jar or JUnit reports')
    return dict(**row, source_sha256=fingerprint(project),
                input_verification='digest captured after build; source mtimes precede jar and tests',
                log=str(log.relative_to(ROOT)), tests=tests, package=package, errors=errors)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('targets', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--workers', type=int, default=2, choices=(1, 2, 3))
    parser.add_argument('--online', action='store_true')
    parser.add_argument('--init-script', type=Path, action='append', default=[],
                        help='Additional build-host init script, recorded in the audit')
    parser.add_argument('--collect-existing', type=Path, metavar='LOG_DIRECTORY',
                        help='Audit completed builds without invoking Gradle')
    args = parser.parse_args()
    rows = json.loads(args.targets.read_text('utf-8-sig'))
    if len({row['project'] for row in rows}) != len(rows):
        raise ValueError('Duplicate project')
    args.output = args.output.resolve()
    if args.collect_existing:
        results = [collect(row, args.collect_existing.resolve()) for row in rows]
        args.output.write_text(json.dumps(results, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        failed = [r['project'] for r in results if r['errors']]
        print(f'COLLECTED {len(results)-len(failed)}/{len(rows)} passed; failures={failed}')
        raise SystemExit(bool(failed))
    logs = args.output.parent / (args.output.stem + '-logs')
    logs.mkdir(parents=True, exist_ok=True)
    results = []
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = {pool.submit(verify, row, logs, args.online, args.init_script): row for row in rows}
        for future in as_completed(futures):
            try:
                results.append(future.result())
            except Exception as exc:
                results.append(dict(**futures[future], errors=[str(exc)]))
            results.sort(key=lambda r: r['project'])
            args.output.write_text(json.dumps(results, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    failed = [r['project'] for r in results if r['errors']]
    print(f'FINISHED {len(results) - len(failed)}/{len(rows)} passed; failures={failed}')
    raise SystemExit(bool(failed))


if __name__ == '__main__':
    main()
