import io
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
            wrong.write_bytes(jar({'net/wurstclient/WurstClient.class': b'fixture'}))
            self.assertIn('missing asset', preflight.check(root, dist, 'v1.5.2', [target])[0])
            current = wrong.rename(dist / wrong.name.replace('1.5.1', '1.5.2'))
            self.assertEqual(preflight.check(root, dist, 'v1.5.2', [target]), [])
            current.write_bytes(b'invalid')
            self.assertIn('unreadable archive', preflight.check(root, dist, 'v1.5.2', [target])[0])
            registry.write_text('ForceOpHack registered')
            self.assertIn('in source', preflight.check(root, dist, 'v1.5.2', [target])[0])
