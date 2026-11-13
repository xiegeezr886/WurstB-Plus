import importlib.util
import io
import json
from pathlib import Path
import unittest
import zipfile

spec = importlib.util.spec_from_file_location(
    'crash_checks', Path(__file__).parents[1] / 'audit-crash-regressions.py')
checks = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checks)


def archive(entries):
    data = io.BytesIO()
    with zipfile.ZipFile(data, 'w') as jar:
        for name, value in entries.items():
            jar.writestr(name, value)
    data.seek(0)
    return zipfile.ZipFile(data)


class CrashGatesTest(unittest.TestCase):
    def test_obsolete_262_baritone_is_rejected_even_with_rewritten_metadata(self):
        with archive({'mixins.baritone.json': '{}',
                      'fabric.mod.json': json.dumps({'version': '1.18.0-26.2',
                          'depends': {'minecraft': ['26.2']},
                          'mixins': ['mixins.baritone.json']}),
                      'baritone/api/utils/LegacyRenderPipelineBuilder.class': b'old'}) as inner:
            nested = inner.fp.getvalue()
        # Rename the nested jar to prove metadata/path rewriting cannot hide it.
        with archive({'META-INF/jars/baritone-renamed.jar': nested}) as outer:
            self.assertTrue(any('obsolete Baritone 26.2 port' in error
                                for error in checks.check_archive(outer)))

    def test_runtime_accessor_cast_and_static_invoker_require_registration(self):
        sources = {
            'mixin/Foo.java': 'package net.wurstclient.mixin; @Mixin(X.class) interface Foo { @Accessor("x") int getX(); }',
            'mixin/Bar.java': 'package net.wurstclient.mixin; @Mixin(Y.class) interface Bar { @Invoker("y") static void y() {} }',
            'hacks/Use.java': 'package net.wurstclient.hacks; import net.wurstclient.mixin.Foo; class Use { int x = ((Foo)target).getX(); void f() { net.wurstclient.mixin.Bar.y(); } }'}
        self.assertEqual(['Bar', 'Foo'], checks.used_accessor_gaps(sources, set()))
        self.assertEqual([], checks.used_accessor_gaps(sources, {'Foo', 'Bar'}))

    def test_unused_import_comments_and_same_named_local_helper_do_not_require_mixin(self):
        sources = {
            'mixin/Foo.java': 'package net.wurstclient.mixin; @Mixin(X.class) interface Foo { @Accessor("x") int x(); }',
            'hacks/Import.java': 'import net.wurstclient.mixin.Foo; /* (Foo)target */ // Foo.x()\n class Import {}',
            'util/Helper.java': 'package net.wurstclient.util; class Helper { static class Foo {} Foo value; }'}
        self.assertEqual([], checks.used_accessor_gaps(sources, set()))

    def test_annotation_parser_handles_nested_annotations_and_descriptors(self):
        text = '@ModifyConstant(method="pick(F)V", constant=@Constant(doubleValue=6.0), require=0)'
        self.assertIn('require=0', list(checks.annotations(text, 'ModifyConstant'))[0])

    def test_unterminated_annotation_is_rejected(self):
        with self.assertRaises(ValueError):
            list(checks.annotations('@ModifyConstant(method="pick(F)V"', 'ModifyConstant'))

    def test_equal_counts_do_not_hide_different_mixin_names(self):
        own = {'compatibilityLevel': 'JAVA_21', 'client': ['MixinOld']}
        bundled = {'compatibilityLevel': 'JAVA_17', 'client': ['MixinRenderPipelines']}
        self.assertFalse(checks.compare_flat_config(own, bundled))

    def test_flat_java_level_can_be_raised_but_not_lowered(self):
        old = {'compatibilityLevel': 'JAVA_17', 'client': ['MixinMinecraft']}
        new = dict(old, compatibilityLevel='JAVA_21')
        self.assertTrue(checks.compare_flat_config(new, old))
        self.assertFalse(checks.compare_flat_config(old, new))

    def test_nested_config_shadow_is_rejected(self):
        with archive({'mixins.baritone.json': '{}'}) as inner:
            nested = inner.fp.getvalue()
        with archive({'META-INF/jarjar/baritone.jar': nested,
                      'mixins.baritone.json': '{}'}) as outer:
            self.assertIn('outer mixins.baritone.json shadows nested Baritone',
                          checks.check_archive(outer))

    def test_nested_baritone_owns_its_configuration(self):
        with archive({'mixins.baritone.json': '{}'}) as inner:
            nested = inner.fp.getvalue()
        with archive({'META-INF/jarjar/baritone.jar': nested}) as outer:
            self.assertEqual([], checks.check_archive(outer))

    def test_nested_fabric_baritone_uses_declared_config_name(self):
        config = 'mixins.baritone-meteor.json'
        for entry in (config, {'config': config, 'environment': 'client'}):
            with self.subTest(entry=entry):
                with archive({'fabric.mod.json': json.dumps({'mixins': [entry]}),
                              config: '{}'}) as inner:
                    nested = inner.fp.getvalue()
                with archive({'META-INF/jars/baritone.jar': nested}) as outer:
                    self.assertEqual([], checks.check_archive(outer))
                with archive({'META-INF/jars/baritone.jar': nested, config: '{}'}) as outer:
                    self.assertIn('outer ' + config + ' shadows nested Baritone',
                                  checks.check_archive(outer))

    def test_missing_declared_baritone_config_is_rejected(self):
        with archive({'fabric.mod.json': json.dumps({'mixins': ['missing.json']}),
                      'mixins.baritone.json': '{}'}) as inner:
            nested = inner.fp.getvalue()
        with archive({'META-INF/jars/baritone.jar': nested}) as outer:
            self.assertIn('META-INF/jars/baritone.jar: missing Baritone mixin config',
                          checks.check_archive(outer))

    def test_dangling_neoforge_entrypoint_is_rejected(self):
        with archive({checks.STUB: b'class',
                      'META-INF/neoforge.mods.toml': 'modId="wurstpenguin"'}) as jar:
            self.assertIn('dangling Baritone @Mod entrypoint', checks.check_archive(jar))

    def test_legitimate_baritone_metadata_is_allowed(self):
        with archive({checks.STUB: b'class',
                      'META-INF/neoforge.mods.toml': 'modId="baritoe"'}) as jar:
            self.assertEqual([], checks.check_archive(jar))

    def test_shipped_pipeline_requires_both_shader_sources(self):
        with archive({'net/wurstclient/WurstShaderPipelines.class': b'wurst:core/fogless_lines',
                      'assets/wurst/shaders/core/fogless_lines.vsh': 'shader'}) as jar:
            self.assertEqual(['missing shader assets/wurst/shaders/core/fogless_lines.fsh'],
                             checks.check_archive(jar))

    def test_fabric_release_cannot_depend_on_ambient_mixinextras(self):
        with archive({'fabric.mod.json': json.dumps({'id': 'wurstpenguin'})}) as jar:
            self.assertIn('Fabric release does not embed MixinExtras', checks.check_archive(jar))


if __name__ == '__main__':
    unittest.main()
