# -*- coding: utf-8 -*-
"""Create Baritone-free copies of Forge/NeoForge test jars.

The bundled Baritone builds crash on some Forge versions (mixin refmap /
LootModifierManager). NanoVG verification does not depend on Baritone, so the
test matrix uses jars with the nested Baritone jar removed. Originals are kept
in _recon/port/jars-full.
"""
import json
import shutil
import sys
import time
import zipfile
from pathlib import Path

JARS = Path('_recon/port/jars')
FULL = Path('_recon/port/jars-full')


def strip(path):
    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        if not any(n.startswith('META-INF/jarjar/baritone-') and
                   n.endswith('.jar') for n in names):
            return False
        FULL.mkdir(parents=True, exist_ok=True)
        backup = FULL / path.name
        if not backup.exists():
            shutil.copy2(path, backup)
        tmp = path.with_suffix('.tmp')
        with zipfile.ZipFile(tmp, 'w', zipfile.ZIP_DEFLATED) as out:
            for info in z.infolist():
                name = info.filename
                if name.startswith('META-INF/jarjar/baritone-') and \
                        name.endswith('.jar'):
                    continue
                data = z.read(name)
                if name == 'META-INF/jarjar/metadata.json':
                    try:
                        meta = json.loads(data.decode('utf-8'))
                        meta['jars'] = [
                            j for j in meta.get('jars', [])
                            if 'baritone' not in
                            (j.get('identifier', {}).get('artifact', ''))]
                        data = json.dumps(meta, indent=4).encode('utf-8')
                    except Exception:
                        pass
                out.writestr(info, data)
        for _ in range(15):
            try:
                tmp.replace(path)
                break
            except PermissionError:
                time.sleep(1)
        else:
            raise SystemExit('could not replace %s' % path)
        return True


if __name__ == '__main__':
    count = 0
    for p in sorted(JARS.glob('*.jar')):
        if 'Forge' not in p.name:
            continue
        if 'Fabric' in p.name:
            continue
        if strip(p):
            print('stripped', p.name)
            count += 1
    print('total', count)
