# -*- coding: utf-8 -*-
"""Apply the Fabric 1.20.1 delta to one target project.

Usage:
    python _recon/port_fabric_one.py fabric/versions/26.3 [--dry-run]

This is the canary-stage tool: it copies new and modified files from the
manifest, applies the family transforms, deletes obsolete HUD files, and writes
a report. It does not touch build.gradle/gradle.properties (those are patched
separately) and it never writes outside the target project.
"""
import argparse
import json
import re
import shutil
import subprocess
import tempfile
from pathlib import Path

REPO = Path('.')
MANIFEST = REPO / '_recon/port/manifest.json'

NATIVE = {'26.1', '26.1.1', '26.1.2', '26.2', '26.3'}
WRAPPER_OLD = {'1.21.6', '1.21.7', '1.21.8'}
WRAPPER_EVENT = {'1.21.9', '1.21.10', '1.21.11'}


def mc_of(project):
    props = (project / 'gradle.properties').read_text(encoding='utf-8')
    m = re.search(r'^\s*minecraft_version\s*=\s*(.+?)\s*$', props, re.M)
    return m.group(1)


def family_of(mc):
    if mc in NATIVE:
        return 'native'
    if mc in WRAPPER_OLD:
        return 'wrapper_old'
    if mc in WRAPPER_EVENT:
        return 'wrapper_event'
    return 'legacy'


def add_pose_args(text):
    for method, extra in (('translate', '0'), ('scale', '1')):
        marker = 'graphics.pose().' + method + '('
        pos = 0
        while True:
            start = text.find(marker, pos)
            if start < 0:
                break
            open_paren = start + len(marker) - 1
            depth = 0
            commas = 0
            index = open_paren
            while index < len(text):
                char = text[index]
                if char == '(':
                    depth += 1
                elif char == ')':
                    depth -= 1
                    if depth == 0:
                        break
                elif char == ',' and depth == 1:
                    commas += 1
                index += 1
            if commas == 1:
                text = text[:index] + ', ' + extra + text[index:]
            pos = index + 10
    return text


def to_legacy(text):
    text = text.replace(
        'import net.wurstclient.util.render.GuiGraphicsExtractor;',
        'import net.minecraft.client.gui.GuiGraphics;')
    text = text.replace(
        'net.wurstclient.util.render.GuiGraphicsExtractor',
        'net.minecraft.client.gui.GuiGraphics')
    text = text.replace('GuiGraphicsExtractor', 'GuiGraphics')
    text = text.replace('.pushMatrix()', '.pushPose()')
    text = text.replace('.popMatrix()', '.popPose()')
    text = add_pose_args(text)
    text = re.sub(r'\bgraphics\.text\(', 'graphics.drawString(', text)
    text = re.sub(r'\bcontext\.text\(', 'context.drawString(', text)
    text = text.replace('graphics.centeredText(', 'graphics.drawCenteredString(')
    text = text.replace('context.centeredText(',
                        'context.drawCenteredString(')
    return text


def to_native(text):
    # 26.x uses Identifier and the native GuiGraphicsExtractor.
    text = text.replace(
        'import net.minecraft.resources.ResourceLocation;',
        'import net.minecraft.resources.Identifier;')
    text = text.replace('net.minecraft.resources.ResourceLocation',
                        'net.minecraft.resources.Identifier')
    text = re.sub(r'\bResourceLocation\b', 'Identifier', text)
    text = text.replace(
        'net.minecraft.world.entity.animal.horse.AbstractHorse',
        'net.minecraft.world.entity.animal.equine.AbstractHorse')
    text = text.replace('net.minecraft.Util', 'net.minecraft.util.Util')
    text = text.replace(
        'net.minecraft.world.entity.animal.WaterAnimal',
        'net.minecraft.world.entity.animal.fish.WaterAnimal')
    text = text.replace('WaterlilyBlock', 'LilyPadBlock')
    text = text.replace('EnchantmentTableBlock', 'EnchantingTableBlock')
    text = text.replace('RedStoneWireBlock', 'RedstoneWireBlock')
    text = text.replace(
        'net.minecraft.client.gui.screens.OptionsSubScreen',
        'net.minecraft.client.gui.screens.options.OptionsSubScreen')
    text = text.replace(
        'net.minecraft.client.gui.screens.OptionsScreen',
        'net.minecraft.client.gui.screens.options.OptionsScreen')
    text = text.replace(
        'net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen',
        'net.minecraft.client.gui.screens.inventory.AbstractContainerScreen')
    text = text.replace('EffectRenderingInventoryScreen',
                        'AbstractContainerScreen')
    text = text.replace(
        'import net.minecraft.client.gui.GuiGraphics;',
        'import net.minecraft.client.gui.GuiGraphicsExtractor;')
    text = re.sub(r'\bGuiGraphics\b', 'GuiGraphicsExtractor', text)
    text = text.replace('.pushPose()', '.pushMatrix()')
    text = text.replace('.popPose()', '.popMatrix()')
    text = re.sub(r'\bgraphics\.drawString\(', 'graphics.text(', text)
    text = re.sub(r'\bcontext\.drawString\(', 'context.text(', text)
    text = text.replace('graphics.drawCenteredString(',
                        'graphics.centeredText(')
    text = text.replace('context.drawCenteredString(',
                        'context.centeredText(')
    text = text.replace('WURST.MC.screen', 'WURST.MC.gui.screen()')
    text = text.replace('WurstClient.MC.screen', 'WurstClient.MC.gui.screen()')
    text = text.replace('MC.screen', 'MC.gui.screen()')

    # 26.x replaces GLFW with SDL; the input constants live in
    # InputConstants. Handle the two function calls first.
    text = re.sub(
        r'boolean leftDown = GLFW\.glfwGetMouseButton\(window,\s*'
        r'GLFW\.GLFW_MOUSE_BUTTON_LEFT\) == GLFW\.GLFW_PRESS',
        'boolean leftDown = MC.options.keyAttack.isDown()', text)
    text = re.sub(
        r'boolean rightDown = GLFW\.glfwGetMouseButton\(window,\s*'
        r'GLFW\.GLFW_MOUSE_BUTTON_RIGHT\) == GLFW\.GLFW_PRESS',
        'boolean rightDown = MC.options.keyUse.isDown()', text)
    text = text.replace(
        'GLFW.glfwGetKeyName(keyCode, 0)',
        'InputConstants.Type.KEYBOARD.getOrCreate(keyCode).getName()')
    text = text.replace('import org.lwjgl.glfw.GLFW;',
                        'import com.mojang.blaze3d.platform.InputConstants;')
    text = text.replace('GLFW.GLFW_', 'InputConstants.')
    return text


def translate(text, family):
    if family == 'legacy':
        return to_legacy(text)
    if family == 'native':
        return to_native(text)
    # wrapper families keep the project shim, but may still contain
    # 1.20.1-style pose calls in newly copied files.
    text = text.replace('.pushPose()', '.pushMatrix()')
    text = text.replace('.popPose()', '.popMatrix()')
    text = re.sub(r'\bgraphics\.drawString\(', 'graphics.text(', text)
    text = re.sub(r'\bcontext\.drawString\(', 'context.text(', text)
    text = text.replace('graphics.drawCenteredString(',
                        'graphics.centeredText(')
    return text


BASE = 'f560fb35^'
# The HUD tree was already ported and carries family-specific runtime fixes
# (sampler unbind, blitters, listener wiring); do not overwrite it.
SKIP_CATEGORIES = {'build', 'test', 'hud-core', 'nanovg', 'mods', 'compat'}
REPLACE_CATEGORIES = set()


def git_show(rev, path):
    r = subprocess.run(['git', 'show', '%s:%s' % (rev, path)],
                       cwd=str(REPO), capture_output=True)
    if r.returncode != 0:
        return None
    return r.stdout.decode('utf-8-sig')


def merge3(ours, base, theirs):
    with tempfile.TemporaryDirectory() as td:
        td = Path(td)
        op = td / 'ours'
        bp = td / 'base'
        tp = td / 'theirs'
        op.write_text(ours, encoding='utf-8', newline='\n')
        bp.write_text(base, encoding='utf-8', newline='\n')
        tp.write_text(theirs, encoding='utf-8', newline='\n')
        r = subprocess.run(
            ['git', 'merge-file', '-p', '--diff3', str(op), str(bp), str(tp)],
            cwd=str(REPO), capture_output=True)
        return r.stdout.decode('utf-8', 'replace'), r.returncode


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('project')
    ap.add_argument('--dry-run', action='store_true')
    ap.add_argument('--no-delete', action='store_true')
    args = ap.parse_args()

    project = Path(args.project)
    if not project.exists():
        raise SystemExit('project not found: %s' % project)
    target = project.resolve()
    root = REPO.resolve()
    if root not in target.parents:
        raise SystemExit('refusing to write outside the repository')

    mc = mc_of(project)
    fam = family_of(mc)
    manifest = json.loads(MANIFEST.read_text(encoding='utf-8-sig'))

    copied = deleted = skipped = 0
    report = []
    for entry in manifest['Entries']:
        action = entry['Action']
        rel = entry['RelPath']
        cat = entry['Category']
        if cat in SKIP_CATEGORIES:
            continue
        src = root / 'fabric' / rel
        dst = target / rel

        if action == 'D':
            if args.no_delete or not dst.exists():
                skipped += 1
                continue
            if not args.dry_run:
                dst.unlink()
            deleted += 1
            report.append('D %s' % rel)
            continue

        if not src.exists():
            skipped += 1
            continue

        if src.suffix.lower() in {'.ttf', '.otf', '.png', '.jpg', '.jpeg',
                                  '.ogg', '.wav', '.jar', '.zip'}:
            if not args.dry_run:
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(str(src), str(dst))
            copied += 1
            report.append('%s %s (binary)' % (action, rel))
            continue

        theirs = translate(src.read_text(encoding='utf-8-sig'), fam)

        if action == 'M' and cat not in REPLACE_CATEGORIES:
            base = git_show(BASE, 'fabric/' + rel)
            ours = git_show('HEAD', (project / rel).as_posix())
            if base is None or ours is None:
                merged = theirs
                conflicts = 0
            else:
                base = translate(base, fam)
                merged, code = merge3(ours, base, theirs)
                conflicts = 1 if code != 0 else 0
        else:
            merged = theirs
            conflicts = 0

        if not args.dry_run:
            dst.parent.mkdir(parents=True, exist_ok=True)
            dst.write_text(merged, encoding='utf-8', newline='\n')
        copied += 1
        report.append('%s %s%s' %
                      (action, rel, ' CONFLICT' if conflicts else ''))

    out = root / '_recon/port/reports'
    out.mkdir(parents=True, exist_ok=True)
    report_path = out / (project.as_posix().replace('/', '__') + '.txt')
    report_path.write_text(
        'project=%s mc=%s family=%s copied=%d deleted=%d skipped=%d\n\n' %
        (project.as_posix(), mc, fam, copied, deleted, skipped) +
        '\n'.join(report) + '\n', encoding='utf-8')
    print('project=%s mc=%s family=%s copied=%d deleted=%d skipped=%d' %
          (project.as_posix(), mc, fam, copied, deleted, skipped))
    print('report=%s' % report_path)


if __name__ == '__main__':
    main()
