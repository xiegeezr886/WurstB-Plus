#!/usr/bin/env python3
"""Run seven desktop GL profiles covering all v1.5.1 render source variants.

Pass --java-home <JDK21 directory>. The 26.3 profile uses LWJGL 3.3.3 because
3.4.3 GLFW diagnostic artifacts are unavailable; production dependencies stay
unchanged. No Minecraft client is launched and Mixin weaving is not exercised.
"""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
PROFILES = (
    ('fabric', '3.3.3'),
    ('neoforge', '3.3.1'),
    ('fabric/versions/1.21.6', '3.3.3'),
    ('fabric/versions/1.21.11', '3.3.3'),
    ('neoforge/versions/26.1', '3.4.1'),
    ('neoforge/versions/26.2', '3.4.1'),
    ('fabric/versions/26.3', '3.3.3'),
)


def fingerprint(path):
    text = path.read_text(encoding='utf-8')
    # GlStateManager moved twice. Normalize only its import; keep all method
    # bodies, conditionals and actual Minecraft GPU/GUI signatures intact.
    text = re.sub(r'import com\.mojang\.[\w.]+\.GlStateManager;', 'import GL_STATE_MANAGER;', text)
    return hashlib.sha256(text.encode()).hexdigest()


def summarize():
    spec = importlib.util.spec_from_file_location('render_audit', ROOT / 'scripts/audit-render-regressions.py')
    gate = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(gate)
    tested, rows = {}, []
    for project, lwjgl in PROFILES:
        work = ROOT / 'build/render263-tests' / project.replace('/', '-')
        inputs = json.loads((work / 'inputs.json').read_text(encoding='utf-8'))
        assert inputs['exit_code'] == 0 and inputs['lwjgl'] == lwjgl, project
        for relative, digest in inputs['production_sha256'].items():
            path = ROOT / relative
            assert hashlib.sha256(path.read_bytes()).hexdigest() == digest, f'stale GL result: {relative}'
            tested.setdefault(path.name, set()).add(fingerprint(path))
        output = (work / 'result.txt').read_text(encoding='utf-8')
        count = int(re.search(r'PASS: (\d+) assertions', output).group(1))
        rows.append(dict(project=project, lwjgl=lwjgl, assertions=count))
    coverage = {}
    for name in ('GlStateGuard.java', 'NanoVGContext.java', 'NanoVgBlitter.java'):
        paths = [p / 'src/main/java/net/wurstclient/hud2/nanovg' / name for p in gate.projects()]
        paths = [p for p in paths if p.exists()]
        missing = [str(p.relative_to(ROOT)) for p in paths if fingerprint(p) not in tested[name]]
        assert not missing, f'untested {name} variants: {missing}'
        coverage[name] = dict(copies=len(paths), variants=len({fingerprint(p) for p in paths}))
    result = dict(profiles=rows, assertions=sum(r['assertions'] for r in rows), coverage=coverage)
    (ROOT / 'build/render263-tests/matrix-result.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    print(json.dumps(result, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path)
    parser.add_argument('--check-results', action='store_true', help='validate current saved results and source hashes without rerunning GL')
    args = parser.parse_args()
    if not args.check_results:
        if not args.java_home:
            parser.error('--java-home <JDK21 directory> is required')
        for project, lwjgl in PROFILES:
            print(f'Checking {project} / LWJGL {lwjgl}', flush=True)
            subprocess.run([sys.executable, str(ROOT / 'scripts/run-render263-tests.py'),
                            '--project', project, '--lwjgl-version', lwjgl,
                            '--java-home', str(args.java_home), '--java-release', '21'], check=True, timeout=300)
    summarize()


if __name__ == '__main__':
    main()
