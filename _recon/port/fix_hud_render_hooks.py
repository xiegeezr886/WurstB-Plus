# -*- coding: utf-8 -*-
"""Retarget the NanoVG HUD frame hooks to the real GameRenderer.render API.

GameRenderer.render(float,long,boolean) only exists up to 1.20.6. From 1.21 to
26.2 it is render(DeltaTracker,boolean); in 26.3 it is render(). Fabric never
validates @Inject targets, so the stale signature only shows up as a Forge
compile error or a runtime crash.

Usage:
    python _recon/port/fix_hud_render_hooks.py [--apply]
"""
import argparse
import re
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]

OLD_HEAD = (
    b'@Inject(at = @At("HEAD"), method = "render(FJZ)V")\n'
    b'\tprivate void onPrepareHud(float partialTick, long nanoTime,\n'
    b'\t\tboolean renderLevel, CallbackInfo ci)\n'
    b'\t{\n'
    b'\t\tif(WurstClient.INSTANCE.getHudManager() != null)\n'
    b'\t\t\tWurstClient.INSTANCE.getHudManager().prepareFrame(partialTick);\n'
    b'\t}')

NEW_HEAD_DELTA = (
    b'@Inject(at = @At("HEAD"),\n'
    b'\t\tmethod = "render(Lnet/minecraft/client/DeltaTracker;Z)V")\n'
    b'\tprivate void onPrepareHud(DeltaTracker deltaTracker,\n'
    b'\t\tboolean renderLevel, CallbackInfo ci)\n'
    b'\t{\n'
    b'\t\tif(WurstClient.INSTANCE.getHudManager() != null)\n'
    b'\t\t\tWurstClient.INSTANCE.getHudManager().prepareFrame(\n'
    b'\t\t\t\tdeltaTracker.getGameTimeDeltaPartialTick(false));\n'
    b'\t}')

NEW_HEAD_NOARG = (
    b'@Inject(at = @At("HEAD"), method = "render()V")\n'
    b'\tprivate void onPrepareHud(CallbackInfo ci)\n'
    b'\t{\n'
    b'\t\tif(WurstClient.INSTANCE.getHudManager() != null)\n'
    b'\t\t\tWurstClient.INSTANCE.getHudManager().prepareFrame(\n'
    b'\t\t\t\tMinecraft.getInstance().getDeltaTracker()\n'
    b'\t\t\t\t\t.getGameTimeDeltaPartialTick(false));\n'
    b'\t}')

OLD_RENDER = (
    b'@Inject(at = @At(value = "INVOKE",\n'
    b'\t\ttarget = "Lnet/minecraft/client/gui/Gui;render('
    b'Lnet/minecraft/client/gui/GuiGraphics;F)V",\n'
    b'\t\tshift = At.Shift.AFTER),\n'
    b'\t\tmethod = "render(FJZ)V")\n'
    b'\tprivate void onRenderHud(float partialTick, long nanoTime,\n'
    b'\t\tboolean renderLevel, CallbackInfo ci)\n'
    b'\t{\n'
    b'\t\tif(WurstClient.INSTANCE.getHudManager() != null)\n'
    b'\t\t\tWurstClient.INSTANCE.getHudManager().renderFrame(partialTick);\n'
    b'\t}')

NEW_RENDER_DELTA = (
    b'@Inject(at = @At(value = "INVOKE",\n'
    b'\t\ttarget = "Lnet/minecraft/client/gui/Gui;render('
    b'Lnet/minecraft/client/gui/GuiGraphics;'
    b'Lnet/minecraft/client/DeltaTracker;)V",\n'
    b'\t\tshift = At.Shift.AFTER),\n'
    b'\t\tmethod = "render(Lnet/minecraft/client/DeltaTracker;Z)V")\n'
    b'\tprivate void onRenderHud(DeltaTracker deltaTracker,\n'
    b'\t\tboolean renderLevel, CallbackInfo ci)\n'
    b'\t{\n'
    b'\t\tif(WurstClient.INSTANCE.getHudManager() != null)\n'
    b'\t\t\tWurstClient.INSTANCE.getHudManager().renderFrame(\n'
    b'\t\t\t\tdeltaTracker.getGameTimeDeltaPartialTick(false));\n'
    b'\t}')


def version_tuple(text):
    return tuple(int(p) for p in text.split('.'))


def projects():
    out = [REPO / 'fabric'] + list((REPO / 'fabric/versions').iterdir()) + \
        list((REPO / 'versions').iterdir()) + [REPO / 'neoforge'] + \
        list((REPO / 'neoforge/versions').iterdir())
    return [p for p in out if (p / 'gradle.properties').exists()]


def add_import(data, imp):
    if imp in data:
        return data
    marker = b'import net.minecraft.'
    pos = data.find(marker)
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
        vt = version_tuple(mc)
        if vt < (1, 21):
            continue
        family = 'noarg' if vt >= (26, 3) else 'delta'
        path = project / 'src/main/java/net/wurstclient/mixin/GameRendererMixin.java'
        if not path.exists():
            continue
        raw = path.read_bytes()
        eol = b'\r\n' if b'\r\n' in raw else b'\n'
        data = raw.replace(b'\r\n', b'\n')
        count = data.count(OLD_HEAD)
        if not count:
            continue
        new = NEW_HEAD_NOARG if family == 'noarg' else NEW_HEAD_DELTA
        data = data.replace(OLD_HEAD, new)
        if family == 'delta':
            data = data.replace(OLD_RENDER, NEW_RENDER_DELTA)
            data = add_import(data,
                              b'import net.minecraft.client.DeltaTracker;')
        else:
            data = add_import(data, b'import net.minecraft.client.Minecraft;')
        rel = project.relative_to(REPO).as_posix()
        print('%-28s mc=%-7s family=%s hooks=%d' % (rel, mc, family, count))
        changed += 1
        if args.apply:
            if eol == b'\r\n':
                data = data.replace(b'\n', b'\r\n')
            path.write_bytes(data)
    print('projects changed:', changed)


if __name__ == '__main__':
    main()
