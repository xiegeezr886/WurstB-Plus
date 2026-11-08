"""Check collision/rotation hook descriptors and invocation sites in23 mapped jars."""
from concurrent.futures import ThreadPoolExecutor
import hashlib
import importlib.util
import json
from pathlib import Path
from sync_four_client_batch import ROOT, PLAN


def main():
    spec = importlib.util.spec_from_file_location('mixin_audit', ROOT / 'scripts/audit-mixin-injections.py')
    audit = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(audit)
    javap = str(Path.home() / '.jdks/jdk25.0.4_10/bin/javap.exe')

    def check(row):
        jar = audit.Jar(row['jar'], javap)
        files = [ROOT / row['root'] / 'src/main/java/net/wurstclient/mixin' / (name + '.java')
                 for name in ['CollisionsShapeMixin', 'CollisionsMovementMixin', 'CollisionsBorderMixin']]
        files += [ROOT / row['root'] / 'src/main/java/net/wurstclient/mixin/NoRotatePacketMixin.java']
        findings, hashes = [], {}
        for file in files:
            findings.extend(audit.check_file(str(file), jar, row['mc'], file.name))
            hashes[file.relative_to(ROOT).as_posix()] = hashlib.sha256(file.read_bytes()).hexdigest()
        # The common auditor only inspects invocation points on descriptor
        # selectors. computeNext is generic, so verify its actual body here.
        text = files[0].read_text(encoding='utf-8')
        targets = audit.string_values(text, 'target')
        assert len(targets) == 1, row['mc']
        owner, ref = targets[0][1:].split(';', 1)
        name, desc = ref.split('(', 1)
        key = owner + '.' + name + ':(' + desc
        code = '\n'.join(jar.bytecode('net.minecraft.world.level.BlockCollisions')['computeNext'])
        assert code.count(key) == 1, (row['mc'], key)
        assert not findings, findings
        print(row['mc'] + ': collision and rotation hook targets passed', flush=True)
        return {'mc': row['mc'], 'jar': row['jar'], 'sha256': hashes, 'findings': findings}

    rows = json.loads((PLAN / 'minecraft-api.json').read_text(encoding='utf-8'))
    with ThreadPoolExecutor(max_workers=3) as pool:
        results = list(pool.map(check, rows))
    (PLAN / 'batch13/hook-targets.json').write_text(json.dumps(results, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
