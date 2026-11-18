"""Restore the actual LSD pass on 1.21.2+ without the removed PostChain loader.

Only touches LSD, its world-render hook and lifecycle cleanup. Does not run the
broader port adapters or replace target-specific GameRenderer implementations.
"""
import argparse
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
JAVA = 'src/main/java/net/wurstclient/'
TEMPLATES = Path(__file__).parent / 'templates/lsd'


def configure(project):
    from prepare_v152 import version_key, props
    mc = props(project / 'gradle.properties')['minecraft_version']
    if version_key(mc) < (1, 21, 2):
        raise ValueError(f'{project}: legacy LSD loader is still available')
    hack = (TEMPLATES / 'LsdHack.java').read_text('utf-8')
    if version_key(mc) >= (1, 21, 5):
        hack = hack.replace('target.frameBufferId',
            'net.wurstclient.util.render.V152Gpu.fbo(target)')
    if version_key(mc) >= (26, 2):
        hack = hack.replace('MC.getMainRenderTarget()', 'MC.gameRenderer.mainRenderTarget()')
    path = project / (JAVA + 'mixin/GameRendererMixin.java')
    mixin = path.read_text('utf-8-sig')
    if 'wurst$renderLsd' not in mixin:
        descriptor = re.search(r'"(renderLevel\([^"\n]+|renderLevel\(\)V)"', mixin)[1]
        mixin = mixin.rstrip()[:-1] + '''
    @Inject(method = "%s", at = @At("RETURN"))
    private void wurst$renderLsd(CallbackInfo ci) {
        WurstClient.INSTANCE.getHax().lsdHack.renderFrame();
    }

    @Inject(method = "close()V", at = @At("HEAD"))
    private void wurst$closeLsd(CallbackInfo ci) {
        net.wurstclient.util.render.LsdPostEffect.INSTANCE.close();
    }
}
''' % descriptor
        mixin, count = re.subn(r'(private void onPrepareHud\([^{}]*\)\s*\{)',
            r'\1\n        WurstClient.INSTANCE.getHax().lsdHack.prepareFrame();', mixin)
        if count != 1:
            raise ValueError(f'{project}: expected one HUD preparation hook')
    # Forge can render its resource-loading screen before FMLClientSetupEvent
    # initializes the module list. Keep the pass ready once initialization is
    # complete without dereferencing that list during the loading screen.
    for call in ('prepareFrame', 'renderFrame'):
        mixin = re.sub(
            r'(?m)^        WurstClient\.INSTANCE\.getHax\(\)\.lsdHack\.' + call + r'\(\);$',
            '        if(WurstClient.INSTANCE.getHax() != null)\n'
            '            WurstClient.INSTANCE.getHax().lsdHack.' + call + '();', mixin)
    program = project / (JAVA + 'util/render/LsdPostEffect.java')
    program.write_text((TEMPLATES / program.name).read_text('utf-8'), encoding='utf-8')
    (project / (JAVA + 'hacks/LsdHack.java')).write_text(hack, encoding='utf-8')
    path.write_text(mixin, encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--target', action='append', required=True)
    args = parser.parse_args()
    for target in args.target:
        project = (ROOT / target).resolve()
        if not project.is_relative_to(ROOT):
            raise ValueError('Target must be inside the repository')
        configure(project)
        print('Restored LSD: ' + target)


if __name__ == '__main__':
    main()
