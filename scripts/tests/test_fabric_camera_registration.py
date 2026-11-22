import json
from pathlib import Path
import unittest


class FabricCameraRegistrationTest(unittest.TestCase):
    def test_client_camera_is_registered_with_the_tested_plugin(self):
        fabric = Path(__file__).resolve().parents[2] / 'fabric'
        projects = [fabric, *(f.parent for f in sorted((fabric / 'versions').glob('*/build.gradle')))]
        for project in projects:
            with self.subTest(project=project.name):
                resources = project / 'src/main/resources'
                mod = json.loads((resources / 'fabric.mod.json').read_bytes())
                registrations = []
                for entry in mod['mixins']:
                    if isinstance(entry, dict) and entry.get('environment') == 'server':
                        continue
                    name = entry['config'] if isinstance(entry, dict) else entry
                    config = json.loads((resources / name).read_bytes())
                    for mixin in config.get('mixins', []) + config.get('client', []):
                        if mixin == 'OpenOpalCameraMixin':
                            registrations.append((config['package'], config.get('plugin')))
                self.assertEqual(registrations, [('net.wurstclient.mixin',
                                  'net.wurstclient.mixin.WurstMixinConfigPlugin')])
