import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location('modpack', Path(__file__).parents[1] / 'modpack-compat.py')
modpack = importlib.util.module_from_spec(spec)
spec.loader.exec_module(modpack)


class ModpackPreparationTest(unittest.TestCase):
    def test_runtime_selects_only_matching_native_architecture(self):
        for classifier in ('natives-windows-arm64', 'natives-windows-x86', 'natives-linux'):
            self.assertFalse(modpack.windows_x64_library({'name': 'org.lwjgl:lwjgl:3.3.1:' + classifier}))
        self.assertTrue(modpack.windows_x64_library({'name': 'org.lwjgl:lwjgl:3.3.1:natives-windows'}))
        self.assertFalse(modpack.windows_x64_library({'name': 'example:lib:1',
            'rules': [{'action': 'allow', 'os': {'name': 'linux'}}]}))

    def test_pack_paths_cannot_leave_isolated_instance(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            for name in ('../user/options.txt', '/absolute.jar', 'C:/outside.jar', r'..\outside.jar'):
                with self.subTest(name=name), self.assertRaises(ValueError):
                    modpack.safe_path(root, name)
            self.assertEqual(root / 'mods/test.jar', modpack.safe_path(root, 'mods/test.jar'))

    def test_hash_failure_keeps_previous_file_and_cleans_temporary_download(self):
        with tempfile.TemporaryDirectory() as folder:
            destination = Path(folder) / 'mod.jar'
            destination.write_bytes(b'previous')
            wanted = hashlib.sha512(b'expected').hexdigest()
            with patch.object(modpack.urllib.request, 'urlopen', side_effect=lambda *a, **k: io.BytesIO(b'corrupt')):
                with patch.object(modpack.time, 'sleep'), self.assertRaises(RuntimeError):
                    modpack.download(['https://example.test/mod.jar'], destination, {'sha512': wanted}, None)
            self.assertEqual(b'previous', destination.read_bytes())
            self.assertEqual([destination], list(Path(folder).iterdir()))

    def test_valid_cached_file_is_verified_without_network(self):
        with tempfile.TemporaryDirectory() as folder:
            destination = Path(folder) / 'mod.jar'
            destination.write_bytes(b'verified')
            with patch.object(modpack.urllib.request, 'urlopen') as network:
                modpack.download(['https://example.test/mod.jar'], destination,
                                 {'sha512': hashlib.sha512(b'verified').hexdigest()}, None)
                network.assert_not_called()

    def test_wrong_pack_pin_rejects_before_instance_is_created(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            pack = root / 'pack.mrpack'
            pack.write_bytes(b'wrong archive')
            output = root / 'instance'
            with self.assertRaises(ValueError):
                modpack.prepare(pack, output, '0' * 128)
            self.assertFalse(output.exists())

    def test_inventory_reads_nested_mods_and_fabric_lenient_description(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / 'mods').mkdir()
            nested = io.BytesIO()
            with zipfile.ZipFile(nested, 'w') as jar:
                jar.writestr('fabric.mod.json', json.dumps({'id': 'nested', 'version': '1'}))
            with zipfile.ZipFile(root / 'mods/test.jar', 'w') as jar:
                jar.writestr('fabric.mod.json', '{"id":"outer","version":"1","description":"raw\nnewline",'
                             '"jars":[{"file":"META-INF/jars/nested.jar"}]}')
                jar.writestr('META-INF/jars/nested.jar', nested.getvalue())
            inventory = modpack.inventory_mods(root)
            self.assertEqual(['outer', 'nested'], [m['id'] for m in inventory])
            self.assertIn('!/META-INF/jars/nested.jar', inventory[1]['source'])


if __name__ == '__main__':
    unittest.main()
