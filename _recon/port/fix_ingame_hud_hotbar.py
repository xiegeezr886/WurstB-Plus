# -*- coding: utf-8 -*-
"""Fix IngameHudMixin on MC 1.20.5-1.21.5.

Gui.renderHotbar(float,GuiGraphics) and Gui.renderEffects(GuiGraphics) stopped
existing at 1.20.5. The replacements are:

  1.20.5-1.20.6: renderHotbarAndDecorations(GuiGraphics,float)
                 renderEffects(GuiGraphics,float)
  1.21-1.21.5:   renderHotbarAndDecorations(GuiGraphics,DeltaTracker)
                 renderEffects(GuiGraphics,DeltaTracker)

Running this file's upstream copy would crash at startup (Fabric never
validates mixin targets; Forge reports the error at compile time).

Usage: python _recon/port/fix_ingame_hud_hotbar.py [--apply]
"""
import argparse
import re
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]

OLD_HOTBAR = (
    b'\t@Inject(at = @At("HEAD"),\n'
    b'\t\tmethod = "renderHotbar(FLnet/minecraft/client/gui/GuiGraphics;)V",\n'
    b'\t\tcancellable = true)\n'
    b'\tprivate void onRenderHotbar(float partialTicks, GuiGraphics context,\n'
    b'\t\tCallbackInfo ci)')

OLD_EFFECTS = (
    b'\t@Inject(at = @At("HEAD"),\n'
    b'\t\tmethod = "renderEffects(Lnet/minecraft/client/gui/GuiGraphics;)V",\n'
    b'\t\tcancellable = true)\n'
    b'\tprivate void onRenderEffects(GuiGraphics context, CallbackInfo ci)')

NEW_HOTBAR_FLOAT = (
    b'\t@Inject(at = @At("HEAD"),\n'
    b'\t\tmethod = "renderHotbarAndDecorations('
    b'Lnet/minecraft/client/gui/GuiGraphics;F)V",\n'
    b'\t\tcancellable = true)\n'
    b'\tprivate void onRenderHotbar(GuiGraphics context, float partialTicks,\n'
    b'\t\tCallbackInfo ci)')

NEW_EFFECTS_FLOAT = (
    b'\t@Inject(at = @At("HEAD"),\n'
    b'\t\tmethod = "renderEffects(Lnet/minecraft/client/gui/GuiGraphics;F)V",\n'
    b'\t\tcancellable = true)\n'
    b'\tprivate void onRenderEffects(GuiGraphics context, float partialTicks,\n'
    b'\t\tCallbackInfo ci)')

NEW_HOTBAR_DELTA = (
    b'\t@Inject(at = @At("HEAD"),\n'
    b'\t\tmethod = "renderHotbarAndDecorations('
    b'Lnet/minecraft/client/gui/GuiGraphics;'
    b'Lnet/minecraft/client/DeltaTracker;)V",\n'
    b'\t\tcancellable = true)\n'
    b'\tprivate void onRenderHotbar(GuiGraphics context,\n'
    b'\t\tDeltaTracker deltaTracker, CallbackInfo ci)')

NEW_EFFECTS_DELTA = (
    b'\t@Inject(at = @At("HEAD"),\n'
    b'\t\tmethod = "renderEffects(Lnet/minecraft/client/gui/GuiGraphics;'
    b'Lnet/minecraft/client/DeltaTracker;)V",\n'
    b'\t\tcancellable = true)\n'
    b'\tprivate void onRenderEffects(GuiGraphics context,\n'
    b'\t\tDeltaTracker deltaTracker, CallbackInfo ci)')


def vt(text):
    return tuple(int(p) for p in text.split('.'))


def projects():
    out = [REPO / 'fabric']
    out += list((REPO / 'fabric/versions').iterdir())
    out += list((REPO / 'versions').iterdir())
    out += [REPO / 'neoforge']
    out += list((REPO / 'neoforge/versions').iterdir())
    return [p for p in out if (p / 'gradle.properties').exists()]


def add_import(data, imp):
    if imp in data:
        return data
    pos = data.find(b'import net.minecraft.')
    if pos < 0:
        return data
    return data[:pos] + imp + b'\n' + data[pos:]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--apply', action='store_true')
    args = ap.parse_args()
    changed = 0
    for project in projects():
        props = (project / 'gradle.properties').read_text(encoding='utf-8')
        mc = re.search(r'^minecraft_version\s*=\s*(.+)$', props,
                       re.MULTILINE).group(1).strip()
        v = vt(mc)
        if v < (1, 20, 5) or v > (1, 21, 5):
            continue
        path = (project / 'src/main/java/net/wurstclient/mixin'
                / 'IngameHudMixin.java')
        if not path.exists():
            continue
        raw = path.read_bytes()
        eol = b'\r\n' if b'\r\n' in raw else b'\n'
        data = raw.replace(b'\r\n', b'\n')
        if OLD_HOTBAR not in data and OLD_EFFECTS not in data:
            continue
        delta = v >= (1, 21)
        data = data.replace(OLD_HOTBAR,
                            NEW_HOTBAR_DELTA if delta else NEW_HOTBAR_FLOAT)
        data = data.replace(OLD_EFFECTS,
                            NEW_EFFECTS_DELTA if delta else NEW_EFFECTS_FLOAT)
        if delta:
            data = add_import(data, b'import net.minecraft.client.DeltaTracker;')
        print('%-28s mc=%-6s family=%s' % (project.relative_to(REPO).as_posix(),
                                           mc, 'delta' if delta else 'float'))
        changed += 1
        if args.apply:
            if eol == b'\r\n':
                data = data.replace(b'\n', b'\r\n')
            path.write_bytes(data)
    print('projects changed:', changed)


if __name__ == '__main__':
    main()
