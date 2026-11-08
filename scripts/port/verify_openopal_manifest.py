"""Verify final port hashes and complete per-project mixin/license registration."""
import hashlib
import json
import re
from refactor_openopal_combat import ROOT, PLAN, PACKAGE, projects


def main():
    manifest = json.loads((PLAN/'final-outputs.json').read_text(encoding='utf-8'))
    bad = [name for name,digest in manifest.items()
           if not (ROOT/name).is_file() or hashlib.sha256((ROOT/name).read_bytes()).hexdigest() != digest]
    assert not bad, bad
    reference = ROOT/'reference/clients/OpenOpal/src/client/java/wtf/opal'
    for name,digest in json.loads((PLAN/'reference-sha256.json').read_text(encoding='utf-8')).items():
        assert hashlib.sha256((reference/name).read_bytes()).hexdigest() == digest, name
    original_license = (ROOT/'reference/clients/OpenOpal/LICENSE').read_bytes()
    grouped = {}
    for p in projects():
        props=dict(line.split('=',1) for line in (p/'gradle.properties').read_text(encoding='utf-8-sig').splitlines() if '=' in line)
        grouped.setdefault(props['minecraft_version'],[]).append(p)
        config = next((p/'src/main/resources').glob('wurst*.mixins.json'))
        clients = json.loads(config.read_text(encoding='utf-8-sig'))['client']
        own_clients=[name for name in clients if name.startswith('OpenOpal')]
        assert len(own_clients)==len(set(own_clients)),config
        for f in (p/PACKAGE/'mixin').glob('OpenOpal*.java'):
            assert f.stem in clients,(p,f.stem)
        for f in (p/PACKAGE/'opal').rglob('*.java'):
            assert not re.search(r'^import (?:static )?(?:wtf\.opal|net\.minecraft\.util\.math|net\.minecraft\.client\.MinecraftClient)',f.read_text(encoding='utf-8'),re.M),f
        assert (p/'src/main/resources/META-INF/licenses/openopal/LICENSE').read_bytes()==original_license,p
    for version,ps in grouped.items():
        base=next(p for p in ps if p.relative_to(ROOT).as_posix().startswith('fabric'))
        for p in ps:
            for f in (base/PACKAGE/'mixin').glob('OpenOpal*.java'):
                assert f.read_bytes()==(p/PACKAGE/'mixin'/f.name).read_bytes(),(version,p,f.name)
    print(f'PASS {len(manifest)} output hashes; {len(projects())} projects; {len(grouped)} Minecraft versions; licenses and mixin registrations.')


if __name__=='__main__':main()
