# -*- coding: utf-8 -*-
"""Build _recon/port/jars-test from the production jars.

Forge/NeoForge copies have the nested Baritone jar removed because several
bundled Baritone builds crash on startup (mixin refmap / LootModifierManager).
The production jars in _recon/port/jars are left untouched.
"""
import json
import shutil
import zipfile
from pathlib import Path

SRC = Path('_recon/port/jars')
DST = Path('_recon/port/jars-test')


def strip(src, dst):
    with zipfile.ZipFile(src) as z:
        names = z.namelist()
        baritone = any(
            n.startswith('META-INF/jarjar/baritone-') and n.endswith('.jar')
            for n in names)
        if not baritone:
            shutil.copy2(src, dst)
            return False
        with zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED) as out:
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
                new_info = zipfile.ZipInfo(info.filename,
                                           date_time=info.date_time)
                new_info.compress_type = zipfile.ZIP_DEFLATED
                new_info.external_attr = info.external_attr
                out.writestr(new_info, data)
        return True


if __name__ == '__main__':
    DST.mkdir(parents=True, exist_ok=True)
    stripped = 0
    for p in sorted(SRC.glob('*.jar')):
        dst = DST / p.name
        is_forge = ('Forge' in p.name and 'Fabric' not in p.name
                    and '-26.' not in p.name)
        if is_forge and strip(p, dst):
            stripped += 1
        else:
            shutil.copy2(p, dst)
    print('copied', len(list(SRC.glob('*.jar'))), 'stripped', stripped)
