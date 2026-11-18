"""Inventory all builds before porting Fabric 1.20.1 to the full v1.5.2 matrix.

Reads source/build inputs and writes reports only; never copies source, changes
mod_version, runs Gradle or publishes artifacts. Counts describe file differences,
not compatibility or completed features. Use --check to verify saved reports.
"""
from pathlib import Path
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
import argparse
import hashlib
import json
import re
import subprocess
import zipfile
from native_seed_versions import upstream_for, cloth_for

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_OUT = ROOT / 'docs/generated/v1.5.2-porting'
TEXT = {'.java', '.json', '.gradle', '.properties', '.cfg', '.toml', '.txt',
        '.md', '.accesswidener', '.frag', '.vert', '.fsh', '.vsh', '.glsl', '.xml'}
JAVA = 'src/main/java/net/wurstclient/'


def props(path):
    return dict(re.findall(r'^([\w.]+)\s*=\s*(.*?)\s*$',
                          path.read_text('utf-8-sig'), re.M))


def version_key(version):
    return tuple(map(int, version.split('.')))


def seed_backend(project, loader):
    if loader != 'Fabric':
        native_family = re.search(r'seedcracker-native(1204|1206|1214|1218|12111|263)\.gradle', (project / 'build.gradle').read_text('utf-8-sig'))
        if native_family:
            mc = props(project / 'gradle.properties')['minecraft_version']
            upstream = upstream_for(mc)
            base = ROOT / 'native/seedcrackerx' / upstream
            manifest = json.loads((base / 'manifest.json').read_text('utf-8'))
            source = hashlib.sha256()
            for path in sorted((base / 'src/main/java').rglob('*.java')):
                source.update(path.relative_to(base).as_posix().encode())
                source.update(path.read_bytes())
            return {'kind': 'shared-native-source', **manifest, 'source_sha256': source.hexdigest(),
                    'automatic_cracking': 'integrated-not-verified-by-inventory'}
        native = project / 'src/main/java/kaptainwutax/seedcrackerX'
        if (native / 'cracker/storage/TimeMachine.java').is_file():
            libraries = json.loads((ROOT / 'native/seedcrackerx/libraries.json').read_text('utf-8'))
            return {'kind': 'native-source', 'upstream_version': '2.14.4',
                    'source_files': len(list(native.rglob('*.java'))),
                    'solver_libraries': libraries,
                    'automatic_cracking': 'integrated-not-verified-by-inventory'}
        return {'kind': 'known-seed-bridge-only', 'automatic_cracking': 'pending'}
    build=(project/'build.gradle').read_text('utf-8-sig')
    version=re.search(r'local\.seedfinding:seedcrackerX:([^"\s]+)', build)
    if version is None:
        return {'kind': 'needs-mc-adaptation', 'automatic_cracking': 'pending'}
    jar=project/'libs'/('seedcrackerX-'+version[1]+'.jar')
    if not jar.is_file():
        return {'kind': 'missing-upstream-jar', 'version':version[1]}
    with zipfile.ZipFile(jar) as archive:
        metadata=json.loads(archive.read('fabric.mod.json'))
    return {'kind':'upstream-fabric', 'version':metadata['version'],
            'minecraft_range':metadata['depends']['minecraft'],
            'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),
            'automatic_cracking':'integrated-not-verified-by-inventory'}


def band(mc):
    key = version_key(mc)
    if key <= (1, 20, 4):
        return 'B1-java17'
    if key <= (1, 21, 4):
        return 'B2-java21-legacy'
    if key == (1, 21, 5):
        return 'B3-java21-components'
    if key <= (1, 21, 8):
        return 'B4-java21-gui-state'
    if key <= (1, 21, 10):
        return 'B5-java21-event-input'
    if key == (1, 21, 11):
        return 'B6-java21-render-setup'
    return 'B7-java25'


def production_task(loader, mc, relative):
    if loader == 'Fabric':
        return 'jar' if mc.startswith('26.') else 'remapJar'
    if loader == 'Forge' or relative == 'neoforge':
        return 'jarJar' if version_key(mc) <= (1, 21, 1) else 'allJar'
    if loader == 'NeoForge' and (1, 20, 2) <= version_key(mc) <= (1, 20, 6):
        return 'jarJar'
    return 'jar'


def projects():
    result = [('.', 'Forge'), ('fabric', 'Fabric'), ('neoforge', 'NeoForge')]
    for parent, loader in [('versions', 'Forge'), ('fabric/versions', 'Fabric'),
                           ('neoforge/versions', 'NeoForge')]:
        candidates = [p for p in (ROOT / parent).iterdir() if p.name[0].isdigit()]
        for path in sorted(candidates, key=lambda p: version_key(p.name)):
            if (path / 'build.gradle').is_file() and (path / 'gradle.properties').is_file():
                result.append((path.relative_to(ROOT).as_posix(), loader))
    return result


def category(relative):
    if relative.startswith(JAVA):
        tail = relative[len(JAVA):]
        if tail.startswith(('hacks/', 'hack/')):
            return 'modules'
        if tail.startswith(('hud2/', 'render/')):
            return 'hud-render'
        if tail.startswith(('gui/', 'clickgui/', 'clickgui2/', 'navigator/', 'altmanager/screens/')):
            return 'gui'
        if tail.startswith('neoraven/music/'):
            return 'music'
        if tail.startswith(('music/', 'seed/')):
            return tail.split('/')[0]
        if tail.startswith('mixin/'):
            return 'mixins'
        return 'java-other'
    if relative.startswith('src/main/java/'):
        return 'java-other'
    if relative.startswith('src/main/resources/'):
        return 'resources'
    return 'tests'


def snapshot(project):
    files = {}
    for folder in ('src/main', 'src/test'):
        for path in sorted((project / folder).rglob('*')):
            if path.is_file():
                data = path.read_bytes()
                if path.suffix.lower() in TEXT:
                    data = data.replace(b'\r\n', b'\n')
                files[path.relative_to(project).as_posix()] = hashlib.sha256(data).hexdigest()
    return files


def delta(source, target):
    missing = sorted(source.keys() - target.keys())
    different = sorted(p for p in source.keys() & target.keys() if source[p] != target[p])
    target_only = sorted(target.keys() - source.keys())
    # Target-only files include loader glue and newer MC capabilities. Never
    # turn this inventory into a deletion list.
    return {'source_only': missing, 'different': different, 'target_only': target_only}


def tree_digest(files):
    return hashlib.sha256(json.dumps(files, sort_keys=True).encode()).hexdigest()


def registrations(project):
    path = project / JAVA / 'hack/HackList.java'
    return sorted(set(re.findall(r'public final\s+[\w.]+\s+(\w+)\s*=',
                                path.read_text('utf-8-sig'))))


def generate(details=False):
    source_root = ROOT / 'fabric'
    if not props(source_root / 'gradle.properties')['mod_version'].startswith('1.5.2-'):
        raise ValueError('Fabric 1.20.1 must be the v1.5.2 baseline')
    source = snapshot(source_root)
    base_hacks = registrations(source_root)
    commit = subprocess.run(['git', 'log', '-1', '--format=%H', '--',
                             'fabric/src', 'fabric/build.gradle', 'fabric/gradle.properties'],
                            cwd=ROOT, capture_output=True, text=True, check=True).stdout.strip()
    matrix, detailed = [], {}
    project_rows = projects()
    # Independent source trees contain large repeated fonts and textures.
    # Hash in parallel, preserving deterministic project/report ordering.
    with ThreadPoolExecutor(max_workers=4) as pool:
        pending = {relative: pool.submit(snapshot, ROOT / relative)
                   for relative, _ in project_rows if relative != 'fabric'}
        trees = {relative: future.result() for relative, future in pending.items()}
    for relative, loader in project_rows:
        project = ROOT / relative
        properties = props(project / 'gradle.properties')
        mc = properties['minecraft_version']
        is_baseline = relative == 'fabric'
        tree = source if is_baseline else trees[relative]
        changes = delta(source, tree)
        counts = {name: dict(sorted(Counter(category(p) for p in paths).items()))
                  for name, paths in changes.items()}
        hacks = registrations(project)
        row = {
            'project': relative, 'loader': loader, 'minecraft': mc,
            'mod_version': properties['mod_version'], 'role': 'baseline' if is_baseline else 'target',
            'band': band(mc), 'jdk': 25 if mc.startswith('26.') else 17 if version_key(mc) <= (1, 20, 4) else 21,
            'production_task': production_task(loader, mc, relative),
            'source_files': len(tree),
            'source_sha256': tree_digest(tree),
            'build_input_sha256': {
                name: hashlib.sha256((project / name).read_bytes().replace(b'\r\n', b'\n')).hexdigest()
                for name in ('build.gradle', 'settings.gradle', 'gradle.properties', 'gradle/wrapper/gradle-wrapper.properties')
                if (project / name).is_file()},
            'delta_counts': {name: len(paths) for name, paths in changes.items()},
            'delta_categories': counts,
            'hack_fields': len(hacks),
            'missing_hack_fields': sorted(set(base_hacks) - set(hacks)),
            'target_only_hack_fields': sorted(set(hacks) - set(base_hacks)),
            'seedcracker_binary': seed_backend(project, loader),
            'verification': 'not-run-by-inventory',
        }
        matrix.append(row)
        if details:
            detailed[relative] = changes
        print(f'Inventoried {relative}: {len(tree)} files', flush=True)
    loaders = dict(sorted(Counter(row['loader'] for row in matrix).items()))
    if len(matrix) != 67 or loaders != {'Fabric': 23, 'Forge': 21, 'NeoForge': 23}:
        raise ValueError(f'Unexpected matrix: {len(matrix)} projects, {loaders}')
    report = {
        'schema_version': 2, 'baseline': 'fabric', 'baseline_git_revision': commit,
        'comparison': 'working-tree SHA256; text CRLF normalized to LF',
        'meaning': 'file differences only; target_only files must be reviewed and retained when required',
        'baseline_source_sha256': tree_digest(source),
        'projects': len(matrix), 'targets': len(matrix) - 1, 'loaders': loaders,
        'target_bands': dict(sorted(Counter(row['band'] for row in matrix if row['role'] == 'target').items())),
        'matrix': matrix,
    }
    lines = ['# v1.5.2 全版本同步准备清单', '',
             '> 由 `scripts/port/prepare_v152.py` 生成；只表示文件差异，不表示功能同步或编译通过。', '',
             f'基线：`fabric/`，最近源码提交 `{commit[:8]}`。', '',
             '共 67 个工程：Fabric 23、Forge 21、NeoForge 23；1 个基线，66 个同步目标。', '',
             '文本比较统一 CRLF/LF。目标独有文件包含版本功能和加载器适配，须逐项保留或合并。', '',
             '| 工程 | 当前版本 | 批次 | JDK | 打包任务 | 基线独有 | 同路径不同 | 目标独有 |',
             '| --- | --- | --- | ---: | --- | ---: | ---: | ---: |']
    for row in matrix:
        n = row['delta_counts']
        lines.append(f"| `{row['project']}` | {row['mod_version']} | {row['band']} | {row['jdk']} | "
                     f"`{row['production_task']}` | {n['source_only']} | {n['different']} | {n['target_only']} |")
    lines += ['', '完整分类计数、模块注册差异和能力适配状态见 [matrix.json](matrix.json)。', '',
              '准备步骤不修改工程源码、版本号或 GitHub Release。验收步骤见',
              '[同步开发计划](../../PORTING-v1.5.2.md)。', '']
    return report, '\n'.join(lines), detailed


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=DEFAULT_OUT)
    parser.add_argument('--details', type=Path, help='Optional full per-file JSON; prefer .dsh-tmp/')
    parser.add_argument('--check', action='store_true', help='Compare reports without writing them')
    args = parser.parse_args()
    if args.check and args.details:
        parser.error('--check does not write --details')
    report, markdown, details = generate(args.details is not None)
    expected = {'matrix.json': json.dumps(report, ensure_ascii=False, indent=2) + '\n',
                'README.md': markdown}
    if args.check:
        stale = [name for name, content in expected.items()
                 if not (args.output / name).is_file()
                 or (args.output / name).read_text('utf-8') != content]
        if stale:
            raise SystemExit('STALE: ' + ', '.join(stale))
    else:
        args.output.mkdir(parents=True, exist_ok=True)
        for name, content in expected.items():
            (args.output / name).write_text(content, encoding='utf-8', newline='\n')
        if args.details:
            args.details.parent.mkdir(parents=True, exist_ok=True)
            args.details.write_text(json.dumps(details, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f"PASS: {report['projects']} projects, {report['targets']} targets; reports {'verified' if args.check else 'written'}")


if __name__ == '__main__':
    main()
