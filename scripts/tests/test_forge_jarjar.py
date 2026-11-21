import io
import json
from pathlib import Path
import runpy
import unittest
import zipfile


audit = runpy.run_path(str(Path(__file__).resolve().parents[1] / 'validate-release-jars.py'))


def jar(files):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, 'w') as z:
        for name, data in files.items():
            z.writestr(name, data)
    return buf.getvalue()


class ForgeJarJarTest(unittest.TestCase):
    def check_packages(self, outer_class):
        path = 'META-INF/jarjar/library.jar'
        outer = jar({outer_class: b'', path: jar({'library/api/One.class': b''}),
                     'META-INF/jarjar/metadata.json': json.dumps({'jars': [{'path': path}]})})
        with zipfile.ZipFile(io.BytesIO(outer)) as z:
            return audit['forge_split_packages'](z)

    def test_different_classes_in_same_package_are_rejected(self):
        self.assertIn('library/api', self.check_packages('library/api/Two.class')[0])

    def test_distinct_packages_are_allowed(self):
        self.assertEqual([], self.check_packages('library/impl/One.class'))
