import io
import json
import sys
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import release_preflight as preflight


def jar(entries):
    out = io.BytesIO()
    with ZipFile(out, 'w') as z:
        for name, content in entries.items():
            z.writestr(name, content)
    return out.getvalue()


class ReleasePreflightTests(unittest.TestCase):
    @staticmethod
    def fml(version, mc='1.20.1', runtime='forge'):
        return (f'[[mods]]\nmodId="wurstpenguin"\nversion="{version}"\n'
                f'[[dependencies.wurstpenguin]]\nmodId="{runtime}"\n'
                f'[[dependencies.wurstpenguin]]\nmodId="minecraft"\nversionRange="[{mc}]"')

    def test_plain_neo_version_requires_target_dependencies(self):
        for mc, runtime in [('26.3', 'neoforge'), ('26.2', 'neoforge'), ('26.3', 'forge')]:
            with self.subTest(mc=mc, runtime=runtime):
                data = jar({'META-INF/neoforge.mods.toml': self.fml('1.5.2', mc, runtime)})
                scan = preflight.artifact_errors(io.BytesIO(data), target='NeoForge-26.3', release='1.5.2')
                if (mc, runtime) == ('26.3', 'neoforge'):
                    self.assertEqual(list(scan), [])
                else:
                    with self.assertRaisesRegex(ValueError, 'wrong loader or Minecraft'):
                        list(scan)

    def test_fabric_metadata_identity(self):
        for mod_id, version, ok in [('wurstpenguin', '1.5.2-Fabric-1.21', True),
                                    ('another', '1.5.2-Fabric-1.21', False),
                                    ('wurstpenguin', '1.5.1-Fabric-1.21', False)]:
            with self.subTest(mod_id=mod_id, version=version):
                meta = {'id': mod_id, 'version': version, 'depends': {'minecraft': '~1.21', 'fabricloader': '*'}}
                scan = preflight.artifact_errors(io.BytesIO(jar({'fabric.mod.json': json.dumps(meta)})),
                                                 target='Fabric-1.21', release='1.5.2')
                if ok:
                    self.assertEqual(list(scan), [])
                else:
                    with self.assertRaises(ValueError):
                        list(scan)

    def test_throw_registration_in_nested_archive(self):
        path = 'net/wurstclient/hack/HackList.class'
        data = jar({'nested.jar': jar({path: b'net/wurstclient/hacks/ThrowHack'})})
        self.assertEqual(list(preflight.artifact_errors(io.BytesIO(data))), ['nested.jar!' + path])

    def test_matrix(self):
        targets = dict(preflight.projects(Path('.')))
        self.assertEqual(len(targets), 67)
        for loader, count in [('Forge', 21), ('Fabric', 23), ('NeoForge', 23)]:
            self.assertEqual(sum(t.startswith(loader + '-') for t in targets), count)
        self.assertNotIn('Forge-1.20.5', targets)
        self.assertNotIn('Forge-1.21.2', targets)

    def test_recursive_artifact_scan(self):
        path = 'net/wurstclient/hack/HackList.class'
        data = jar({'META-INF/jars/module.jar': jar({path: b'ForceOpHack'})})
        self.assertEqual(list(preflight.artifact_errors(io.BytesIO(data))),
                         ['META-INF/jars/module.jar!' + path])

    def test_crash_item_not_shipped(self):
        path = 'net/wurstclient/hacks/CrashChestHack.class'
        self.assertEqual(list(preflight.artifact_errors(io.BytesIO(jar({path: b''})))), [path])

    def test_requires_exact_asset_version_and_project(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            target = 'Forge-1.20.1'
            registry = root / 'src/main/java/net/wurstclient/hack/HackList.java'
            registry.parent.mkdir(parents=True)
            registry.write_text('class HackList {}')
            (root / 'build.gradle').touch()
            dist = root / 'dist'; dist.mkdir()
            wrong = dist / ('WurstB+.Plus-1.5.1-' + target + '.jar')
            wrong.write_bytes(jar({'META-INF/mods.toml': self.fml('1.5.1-' + target)}))
            self.assertIn('missing asset', preflight.check(root, dist, 'v1.5.2', [target])[0])
            current = wrong.rename(dist / wrong.name.replace('1.5.1', '1.5.2'))
            self.assertIn('wrong internal version', preflight.check(root, dist, 'v1.5.2', [target])[0])
            current.write_bytes(jar({'META-INF/mods.toml': self.fml('1.5.2-' + target)}))
            self.assertEqual(preflight.check(root, dist, 'v1.5.2', [target]), [])
            current.write_bytes(b'invalid')
            self.assertIn('invalid archive', preflight.check(root, dist, 'v1.5.2', [target])[0])
            registry.write_text('ForceOpHack registered')
            self.assertIn('in source', preflight.check(root, dist, 'v1.5.2', [target])[0])
