"""1.20.5 hotfix sibling with a source-built, version-specific seed backend."""
from pathlib import Path
import hashlib
import json


def configure(project: Path):
    from adapt_v152_fabric1211 import configure as common
    root = Path(__file__).resolve().parents[2]
    version = '2.14.7-wurstb-mc1.20.5'
    source = root/'.dsh-tmp/v152-seedcracker1205-source/build/libs'/('seedcrackerX-'+version+'.jar')
    if not source.is_file():
        raise ValueError('Build the pinned SeedCrackerX sources for 1.20.5 first')
    import zipfile
    with zipfile.ZipFile(source) as z:
        assert z.testzip() is None
        assert json.loads(z.read('fabric.mod.json'))['depends']['minecraft'] == '1.20.5'
    cached = root/'.dsh-tmp/v152-upstream-seed'/source.name
    cached.write_bytes(source.read_bytes())
    common(project, seed_version=version,
           seed_digest=hashlib.sha256(source.read_bytes()).hexdigest(), seed_mc='1.20.5')
    # Record that this is our source rebuild, rather than an upstream release.
    notice = project/'src/main/resources/META-INF/licenses/seedcrackerx/NOTICE.txt'
    notice.write_text('SeedCrackerX 2.14.7, MIT, rebuilt from source for Minecraft 1.20.5.\n'
        'https://github.com/19MisterX98/SeedcrackerX/tree/0ae7b3649e4cd386f89a13003598a88e3beab16a\n'
        'Recipe: scripts/port/prepare_seedcracker1205.py\n'
        'SHA-256: '+hashlib.sha256(source.read_bytes()).hexdigest()+'\n'
        'Minecraft-facing classes compiled against Yarn 1.20.5+build.1; shaded pure-Java '
        'seedfinding and the nested Cloth dependency retain upstream licenses.\n', encoding='utf-8')
