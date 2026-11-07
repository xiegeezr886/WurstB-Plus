# -*- coding: utf-8 -*-
"""Write the remaining Soar hacks into one or more Gradle projects."""
import argparse
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import REPO, project_dirs
from soar_remaining_plain import PLAIN
from soar_remaining_hud import HUD

FIELDS = {
    'AutoGGHack': 'autoGGHack',
    'ForceMainHandHack': 'forceMainHandHack',
    'JumpResetIndicatorHack': 'jumpResetIndicatorHack',
    'PitchDisplayHack': 'pitchDisplayHack',
    'SnapTapHack': 'snapTapHack',
    'YawDisplayHack': 'yawDisplayHack',
    'EntityCullingHack': 'entityCullingHack',
    'ChunkAnimatorHack': 'chunkAnimatorHack',
    'ClearGlassHack': 'clearGlassHack',
    'ClearWaterHack': 'clearWaterHack',
    'ColorSaturationHack': 'colorSaturationHack',
    'CustomHeldItemsHack': 'customHeldItemsHack',
    'DamageTiltHack': 'damageTiltHack',
    'DamageTintHack': 'damageTintHack',
    'FPSSpooferHack': 'fpsSpooferHack',
    'GlintColorHack': 'glintColorHack',
    'HitColorHack': 'hitColorHack',
    'HitDelayFixHack': 'hitDelayFixHack',
    'ItemPhysicsHack': 'itemPhysicsHack',
    'Items2DHack': 'items2DHack',
    'MinimalDamageShakeHack': 'minimalDamageShakeHack',
    'MoBendsHack': 'moBendsHack',
    'MotionBlurHack': 'motionBlurHack',
    'OldAnimationsHack': 'oldAnimationsHack',
    'OverlayEditorHack': 'overlayEditorHack',
    'ParticleCustomizerHack': 'particleCustomizerHack',
    'RawInputHack': 'rawInputHack',
    'RearviewHack': 'rearviewHack',
    'Skin3DHack': 'skin3DHack',
    'SkinProtectHack': 'skinProtectHack',
    'SlowSwingHack': 'slowSwingHack',
    'SoundModifierHack': 'soundModifierHack',
    'SoundSubtitlesHack': 'soundSubtitlesHack',
    'ViaVersionHack': 'viaVersionHack',
    'WaveyCapesHack': 'waveyCapesHack',
    'WebBrowserHack': 'webBrowserHack',
}

OLD_HELPERS = r'''
	private int argb(java.awt.Color color, int alpha)
	{
		int a = Math.max(0, Math.min(255, alpha));
		return a << 24 | color.getRGB() & 0xFFFFFF;
	}

	private boolean uiHidden()
	{
		try
		{
			Object gui = MC.getClass().getField("gui").get(MC);
			if(gui.getClass().getMethod("screen").invoke(gui) != null)
				return true;
		}catch(Exception ignored)
		{}

		try
		{
			return Boolean.TRUE.equals(MC.options.getClass()
				.getField("hideGui").get(MC.options));
		}catch(Exception ignored)
		{
			return false;
		}
	}

	private void text(GuiGraphics context, String text, int x, int y,
		int color, boolean shadow)
	{
		context.drawString(MC.font, text, x, y, color, shadow);
	}

	private void centeredText(GuiGraphics context, String text, int x,
		int y, int color)
	{
		context.drawCenteredString(MC.font, text, x, y, color);
	}
'''

NEW_HELPERS = r'''
	private int argb(java.awt.Color color, int alpha)
	{
		int a = Math.max(0, Math.min(255, alpha));
		return a << 24 | color.getRGB() & 0xFFFFFF;
	}

	private boolean uiHidden()
	{
		try
		{
			Object gui = MC.getClass().getField("gui").get(MC);
			if(gui.getClass().getMethod("screen").invoke(gui) != null)
				return true;
		}catch(Exception ignored)
		{}

		try
		{
			return Boolean.TRUE.equals(MC.options.getClass()
				.getField("hideGui").get(MC.options));
		}catch(Exception ignored)
		{
			return false;
		}
	}

	private void text(GuiGraphicsExtractor context, String text, int x,
		int y, int color, boolean shadow)
	{
		context.text(MC.font, text, x, y, color, shadow);
	}

	private void centeredText(GuiGraphicsExtractor context, String text,
		int x, int y, int color)
	{
		context.centeredText(MC.font, text, x, y, color);
	}
'''


def hud_source(name, source, variant):
    if variant == 'old':
        source = source.replace('__GUI_IMPORT__',
                                'import net.minecraft.client.gui.GuiGraphics;')
        source = source.replace('__GUI__', 'GuiGraphics')
        helpers = OLD_HELPERS
    elif variant == 'shim':
        source = source.replace(
            '__GUI_IMPORT__',
            'import net.wurstclient.util.render.GuiGraphicsExtractor;')
        source = source.replace('__GUI__', 'GuiGraphicsExtractor')
        helpers = NEW_HELPERS
    elif variant == 'native':
        source = source.replace(
            '__GUI_IMPORT__',
            'import net.minecraft.client.gui.GuiGraphicsExtractor;')
        source = source.replace('__GUI__', 'GuiGraphicsExtractor')
        helpers = NEW_HELPERS
    else:
        raise ValueError(variant)

    if '__GUI__' in source or '__GUI_IMPORT__' in source \
            or '__HELPERS__' not in source:
        raise ValueError('unresolved HUD placeholder in ' + name)
    return source.replace('__HELPERS__', helpers)


def project_variant(project):
    txt = str(project).replace('\\', '/')
    m = re.search(r'/(?:fabric/versions|versions|neoforge/versions)/([^/]+)$',
                  txt)
    if not m:
        return 'old'
    ver = m.group(1)
    if ver.startswith('26.'):
        return 'native'
    if ver in ('1.21.6', '1.21.7', '1.21.8', '1.21.9', '1.21.10',
               '1.21.11'):
        return 'shim'
    return 'old'


def register(project, names):
    list_path = project / 'src/main/java/net/wurstclient/hack/HackList.java'
    text = list_path.read_text(encoding='utf-8')
    added = 0
    for cls in names:
        field = FIELDS[cls]
        if field in text:
            continue
        m = re.search(
            r'^([ \t]*)public final \w+ notifierHack\s*=', text, re.M)
        if not m:
            raise RuntimeError('no anchor in ' + str(list_path))
        end = text.find(';', m.end())
        eol = text.find('\n', end) if end != -1 else -1
        if end == -1 or eol == -1:
            raise RuntimeError('bad anchor in ' + str(list_path))
        line = '\tpublic final %s %s = new %s();' % (cls, field, cls)
        text = text[:eol + 1] + line + '\n' + text[eol + 1:]
        added += 1
    list_path.write_text(text, encoding='utf-8', newline='\n')
    return added


def write_project(project, variant=None):
    variant = variant or project_variant(project)
    hack_dir = project / 'src/main/java/net/wurstclient/hacks'
    hack_dir.mkdir(parents=True, exist_ok=True)

    names = list(PLAIN) + list(HUD)
    for name in PLAIN:
        target = hack_dir / (name + '.java')
        # Some versions already ship a full EntityCulling implementation.
        if name == 'EntityCullingHack' and target.exists():
            continue
        target.write_text(PLAIN[name], encoding='utf-8', newline='\n')
    for name in HUD:
        source = hud_source(name, HUD[name], variant)
        (hack_dir / (name + '.java')).write_text(
            source, encoding='utf-8', newline='\n')

    added = register(project, names)
    return len(names), added, variant


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--all', action='store_true')
    ap.add_argument('--only', nargs='*')
    ap.add_argument('--variant', choices=['old', 'shim', 'native'])
    args = ap.parse_args()

    if args.only:
        projects = [Path(p).resolve() for p in args.only]
    elif args.all:
        projects = [p for p in project_dirs()
                    if p.resolve() != REPO.resolve()]
    else:
        projects = [REPO / 'fabric/versions/1.21.11']

    stats = {}
    for project in projects:
        count, added, variant = write_project(project, args.variant)
        key = (variant, added)
        stats[key] = stats.get(key, 0) + 1
        print('%s -> %s, classes=%d, fields_added=%d' %
              (project, variant, count, added))
    print('summary', stats)


if __name__ == '__main__':
    main()
