# -*- coding: utf-8 -*-
"""Replace the old HUD with the Fabric 1.20.1 NanoVG HUD in a legacy/wrapper
project. Keeps clickgui/gui/hacks untouched.

Usage: python _recon/port_hud_legacy.py fabric/versions/1.20.2 [--wrapper]
"""
import shutil
import sys
from pathlib import Path

ROOT = Path('.').resolve()
SRC = Path('fabric/src/main/java/net/wurstclient')


def main():
    target = Path(sys.argv[1]).resolve()
    if ROOT not in target.parents:
        raise SystemExit('target outside repo')
    wrapper = '--wrapper' in sys.argv
    dst = target / 'src/main/java/net/wurstclient'

    # Replace the whole HUD2 tree with the 1.20.1 implementation.
    hud_src = SRC / 'hud2'
    hud_dst = dst / 'hud2'
    if hud_dst.exists():
        shutil.rmtree(hud_dst)
    shutil.copytree(hud_src, hud_dst)

    # Remove the old HUD-only classes that the 1.20.1 tree no longer has.
    for rel in ['hud/HackListHUD.java', 'hud/WurstLogo.java']:
        p = dst / rel
        if p.exists():
            p.unlink()

    # Copy the shared event interface and fonts.
    shutil.copyfile(SRC / 'events/GUIRenderListener.java',
                    dst / 'events/GUIRenderListener.java')
    fonts_src = ROOT / 'fabric/src/main/resources/assets/wurstpenguin/nanovg/fonts'
    fonts_dst = target / 'src/main/resources/assets/wurstpenguin/nanovg/fonts'
    fonts_dst.mkdir(parents=True, exist_ok=True)
    for font in fonts_src.glob('*.ttf'):
        shutil.copyfile(font, fonts_dst / font.name)

    # Category.HUD and IngameHUD/Hack cleanups are handled by a separate
    # idempotent pass because those files are version-specific.
    print('copied HUD to', target, 'wrapper=%s' % wrapper)


if __name__ == '__main__':
    main()
