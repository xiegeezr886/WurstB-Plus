"""Negative controls for the matrix render gate using real source inputs."""
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('render_audit', ROOT / 'scripts/audit-render-regressions.py')
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class RenderGateTest(unittest.TestCase):
    def setUp(self):
        self.sources = gate.load(ROOT / 'fabric/versions/1.21.6')

    def reject(self, key, before, after, message):
        self.assertIn(before, self.sources[key])
        self.sources[key] = self.sources[key].replace(before, after)
        self.assertTrue(any(message in e for e in gate.audit(self.sources)))

    def test_current_source(self):
        self.assertEqual(gate.audit(self.sources), [])

    def test_duplicate_hud_dispatch(self):
        self.sources = gate.load(ROOT / 'fabric/versions/1.21.1')
        self.sources['initializer'] += '\nEventManager.fire(new GUIRenderEvent(graphics, partialTick));'
        self.assertTrue(any('both initializer' in e for e in gate.audit(self.sources)))

    def test_callback_only_legacy_hud(self):
        self.sources = gate.load(ROOT / 'fabric')
        self.assertIn('new GUIRenderEvent(', self.sources['initializer'])
        self.assertEqual(gate.audit(self.sources), [])

    def test_unregistered_hud_does_not_duplicate_callback(self):
        self.sources = gate.load(ROOT / 'fabric/versions/1.21.1')
        self.sources['initializer'] += '\nEventManager.fire(new GUIRenderEvent(graphics, partialTick));'
        self.sources['registered'].remove('IngameHudMixin')
        self.assertEqual(gate.audit(self.sources), [])

    def test_missing_init_guard(self):
        self.reject('context', 'return createContext();', 'return false;', 'initialization')

    def test_upload_pbo(self):
        self.reject('context', 'GL_PIXEL_UNPACK_BUFFER, 0', 'GL_PIXEL_UNPACK_BUFFER, 1', 'unsafe upload')

    def test_stencil_mask(self):
        self.reject('context', 'glStencilMask(~0)', 'glStencilMask(0)', 'clear must set')

    def test_sampler_restore(self):
        self.reject('guard', 'glBindSampler(0, sampler0)', 'glBindSampler(0, 0)', 'restoration missing')

    def test_texture_cache_restore(self):
        self.reject('guard', 'GlStateManager._bindTexture(texture0);', '', 'cache synchronization missing')

    def test_reset_before_consumption(self):
        self.reject('reset', '@At("RETURN")', '@At("HEAD")', 'after GUI reset')

    def test_missing_registration(self):
        self.sources['registered'].remove('NanoVgGuiRenderStateMixin')
        self.assertIn('reset mixin must be registered exactly once', gate.audit(self.sources))

    def test_wrong_state_package(self):
        self.reject('reset', 'net.minecraft.client.gui.render.state', 'net.minecraft.client.renderer.state.gui', 'wrong API package')

    def test_duplicate_release(self):
        self.reject('blitter', 'pending.remove(state)', 'pending.get(state)', 'only once')

    def test_fixed_ring(self):
        self.sources['blitter'] += '\n// MAX_SLOTS fixed ring restored\n'
        self.assertTrue(any('fixed ring' in e for e in gate.audit(self.sources)))

    def test_inherited_selection_render(self):
        self.sources = gate.load(ROOT / 'fabric/versions/1.20.4')
        self.reject('selection', 'renderWidget(', 'render(', 'declared renderWidget')


if __name__ == '__main__':
    unittest.main()
