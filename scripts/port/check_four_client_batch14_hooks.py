"""Check only the changed FOV selectors against actual mapped Minecraft jars."""
from concurrent.futures import ThreadPoolExecutor
import hashlib
import importlib.util
import json
from pathlib import Path
from sync_four_client_batch import ROOT, PLAN
from test_four_client_batch14 import fov_callbacks


def main():
    spec = importlib.util.spec_from_file_location('mixin_audit', ROOT / 'scripts/audit-mixin-injections.py')
    audit = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(audit)
    javap = str(Path.home() / '.jdks/jdk25.0.4_10/bin/javap.exe')
    out = PLAN / 'batch14/hooks'
    out.mkdir(exist_ok=True)

    def check(row):
        root = ROOT / row['root'] / 'src/main/java/net/wurstclient/mixin'
        camera = root / 'CameraMixin.java'
        file = camera if camera.exists() and 'onCalculateFov' in camera.read_text(encoding='utf-8') else root / 'GameRendererMixin.java'
        text = file.read_text(encoding='utf-8')
        owner = 'net.minecraft.client.Camera' if file == camera else 'net.minecraft.client.renderer.GameRenderer'
        # Copy the exact changed method declarations/annotations/bodies. Other
        # native callbacks are outside this batch and its signature check.
        fixture = out / (row['mc'] + '.java')
        fixture.write_text('import ' + owner + ';\n@Mixin(' + owner.rsplit('.', 1)[1] + '.class)\nclass FovHook {\n' + fov_callbacks(text) + '\n}\n', encoding='utf-8')
        jar = audit.Jar(row['jar'], javap)
        findings = audit.check_file(str(fixture), jar, row['mc'], file.name)
        assert not findings, findings
        print(row['mc'] + ': FOV callback selectors passed', flush=True)
        return {'mc': row['mc'], 'jar': row['jar'], 'source': file.relative_to(ROOT).as_posix(),
                'sha256': hashlib.sha256(file.read_bytes()).hexdigest(), 'findings': findings}

    rows = json.loads((PLAN / 'minecraft-api.json').read_text(encoding='utf-8'))
    with ThreadPoolExecutor(max_workers=3) as pool:
        results = list(pool.map(check, rows))
    (PLAN / 'batch14/hook-targets.json').write_text(json.dumps(results, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
