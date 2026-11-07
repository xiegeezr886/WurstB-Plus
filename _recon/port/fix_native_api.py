# -*- coding: utf-8 -*-
"""26.x game API fixes for the ported 1.5.1 code."""
import re
import sys
from pathlib import Path

CANCEL_BLOCK = re.compile(
    r'(?m)^(\s*)(\w+)\.input\.forwardImpulse = 0\.0F;\r?\n'
    r'\s*\w+\.input\.leftImpulse = 0\.0F;\r?\n')


def cancel_movement(match):
    indent, recv = match.group(1), match.group(2)
    return (indent + '((net.wurstclient.mixin.ClientInputAccessor)'
            + recv + '.input)\n' + indent
            + '\t.wurst$setMoveVector(net.minecraft.world.phys.Vec2.ZERO);\n')


def fix_selected_slots(text):
    text = re.sub(
        r'([\w.()]+)\.getInventory\(\)\.selected = ([^;\n]+);',
        r'\1.getInventory().setSelectedSlot(\2);', text)
    return text.replace('getInventory().selected',
                        'getInventory().getSelectedSlot()')


def patch(project):
    root = Path(project) / 'src/main/java/net/wurstclient'

    for sub in ('hacks/scaffold',):
        for p in (root / sub).glob('*.java'):
            t = p.read_text(encoding='utf-8')
            original = t
            t = fix_selected_slots(t)
            t = t.replace('MobEffects.JUMP)', 'MobEffects.JUMP_BOOST)')
            t = t.replace('.input.jumping', '.input.keyPresses.jump()')
            t = CANCEL_BLOCK.sub(cancel_movement, t)
            t = t.replace('.input.forwardImpulse',
                          '.input.getMoveVector().y')
            t = t.replace('.input.leftImpulse', '.input.getMoveVector().x')
            if t != original:
                p.write_text(t, encoding='utf-8', newline='\n')

    for name in ('ScaffoldWalkHack.java', 'KillauraHack.java'):
        p = root / ('hacks/' + name)
        if not p.exists():
            continue
        t = p.read_text(encoding='utf-8')
        t = fix_selected_slots(t)
        t = t.replace('MobEffects.JUMP)', 'MobEffects.JUMP_BOOST)')
        t = t.replace('.input.jumping', '.input.keyPresses.jump()')
        t = t.replace(
            'stack.isEdible()',
            'stack.has(net.minecraft.core.component.DataComponents.FOOD)')
        p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'hacks/GAntiLagHack.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        t = t.replace(
            'new Vec3(packet.getX(), packet.getY(), packet.getZ())',
            'packet.change().position()')
        t = t.replace('packet.getId()', 'packet.id()')
        p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'hacks/GcoreTpDiagnosticHack.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        t = t.replace('p.getId(), p.getX(), p.getY(),\n\t\t\tp.getZ()',
                      'p.id(), p.change().position().x,\n'
                      '\t\t\tp.change().position().y, '
                      'p.change().position().z')
        p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'util/ServerEquivTp.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        t = t.replace(
            'new ServerboundMovePlayerPacket.Pos(x, y, z, onGround)',
            'new ServerboundMovePlayerPacket.Pos(x, y, z, onGround,\n'
            '\t\t\t\tfalse)')
        p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'util/TpCombatUtil.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        t = t.replace(
            'Direction.getNearest(eyes.x - center.x, eyes.y - center.y,',
            'Direction.getApproximateNearest(eyes.x - center.x,\n'
            '\t\t\t\teyes.y - center.y,')
        t = t.replace(
            '.add(Vec3.atLowerCornerOf(side.getNormal()).scale(0.5));',
            '.add(side.getUnitVec3().scale(0.5));')
        p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'hacks/AbstractTpAnchorAuraHack.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        t = t.replace(
            'MC.level.dimensionType().respawnAnchorWorks()',
            'MC.level.dimension() == net.minecraft.world.level.Level.NETHER')
        p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'clickgui2/window/Window.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        t = t.replace('.pose().translate(x1 + 2, y1 + 2, 0);',
                      '.pose().translate(x1 + 2, y1 + 2);')
        t = t.replace('.pose().scale(0.6f, 0.6f, 1f);',
                      '.pose().scale(0.6f, 0.6f);')
        p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'clickgui2/FlatRenderer.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        if 'int safeRadius' not in t:
            t = t.replace(
                '\t\tint x2, int y2, int radius, FlatTheme theme, '
                'boolean focused)\n\t{\n',
                '\t\tint x2, int y2, int radius, FlatTheme theme, '
                'boolean focused)\n\t{\n\t\tint safeRadius = Math.max(0, '
                'radius);\n')
        p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'clickgui2/NavigatorScreen.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        t = t.replace('GLFW.GLFW_MOUSE_BUTTON_LEFT',
                      'InputConstants.MOUSE_BUTTON_LEFT')
        t = t.replace('GLFW.GLFW_MOUSE_BUTTON_RIGHT',
                      'InputConstants.MOUSE_BUTTON_RIGHT')
        t = t.replace('GLFW.GLFW_KEY_ESCAPE', 'InputConstants.KEY_ESCAPE')
        t = t.replace('org.lwjgl.glfw.GLFW.GLFW_', 'InputConstants.')
        if 'GLFW.GLFW_' in t:
            t = t.replace('GLFW.GLFW_', 'InputConstants.')
        if 'InputConstants.' in t \
            and 'import com.mojang.blaze3d.platform.InputConstants;' not in t:
            t = t.replace(
                'import net.minecraft.client.Minecraft;',
                'import com.mojang.blaze3d.platform.InputConstants;\n'
                'import net.minecraft.client.Minecraft;')
        p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'clickgui2/screens/EditBookOfferScreen.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        t = t.replace('new Identifier("enchanted_book")',
                      'Identifier.parse("enchanted_book")')
        t = t.replace('BuiltInRegistries.ITEM.get(',
                      'BuiltInRegistries.ITEM.getValue(')
        p.write_text(t, encoding='utf-8', newline='\n')

    for p in (root / 'clickgui2/screens').glob('*.java'):
        t = p.read_text(encoding='utf-8')
        t2 = t.replace('renderBackground(context, mouseX, mouseY, '
                       'partialTicks);',
                       'extractBackground(context, mouseX, mouseY, '
                       'partialTicks);')
        if t2 != t:
            p.write_text(t2, encoding='utf-8', newline='\n')

    p = root / 'hacks/aura/OpenAura.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        if 'MultiBufferSource' in t:
            t = t.replace(
                'import net.minecraft.client.renderer.MultiBufferSource;\n',
                '')
            pattern = re.compile(
                r'\t\tMultiBufferSource\.BufferSource bufferSource = '
                r'RenderUtils\.getVCP\(\);\n'
                r'\t\tRenderType layer = WurstRenderLayers\.getLines\('
                r'false\);\n'
                r'\t\tVertexConsumer buffer = '
                r'bufferSource\.getBuffer\(layer\);\n(.*?)'
                r'\t\tbufferSource\.endBatch\(layer\);\n', re.S)
            t = pattern.sub(
                lambda m: '\t\tRenderType layer = '
                'WurstRenderLayers.getLines(false);\n'
                '\t\tRenderUtils.submit(matrices, layer, buffer -> {\n'
                + m.group(1) + '\t\t});\n', t)
            p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'clickgui2/screens/SelectFileScreen.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        anchor = ('\t\tpublic void extractContent(GuiGraphicsExtractor '
                  'context, int mouseX,\n\t\t\tint mouseY, boolean hovered, '
                  'float tickDelta)\n\t\t{\n')
        if anchor in t and 'getContentX()' not in t:
            t = t.replace(
                anchor, anchor + '\t\t\tint x = getContentX();\n'
                '\t\t\tint y = getContentY();\n', 1)
        p.write_text(t, encoding='utf-8', newline='\n')

    # The native HudManager renders through GUIRenderListener.
    p = root / 'mixin/GameRendererMixin.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        lines = t.splitlines()
        start = end = None
        for i, line in enumerate(lines):
            if 'Composites the HUD right after the vanilla GUI pass' in line:
                start = i - 1 if i > 0 and lines[i - 1].strip() == '/**' \
                    else i
            if start is not None and 'renderFrame(partialTick);' in line:
                end = i
        if start is not None and end is not None:
            while end < len(lines) and lines[end].strip() != '}':
                end += 1
            del lines[start:end + 1]
            p.write_text('\n'.join(lines) + '\n', encoding='utf-8',
                         newline='\n')

    # 26.1.x still expose Minecraft.screen; 26.2+ use MC.gui.screen().
    props = (Path(project) / 'gradle.properties').read_text(encoding='utf-8')
    mc = re.search(r'^\s*minecraft_version\s*=\s*(.+?)\s*$', props,
                   re.M).group(1)

    p = root / 'hacks/scaffold/ScaffoldUtils.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        if mc == '26.3':
            t = t.replace('RedStoneWireBlock', 'RedstoneWireBlock')
        else:
            t = t.replace('RedstoneWireBlock', 'RedStoneWireBlock')
        p.write_text(t, encoding='utf-8', newline='\n')

    if mc == '26.3':
        p = root / 'hacks/KillauraHack.java'
        if p.exists():
            t = p.read_text(encoding='utf-8')
            t = t.replace('import net.minecraft.world.item.AxeItem;\n', '')
            t = t.replace(
                'if(!(stack.getItem() instanceof AxeItem))',
                'if(!stack.is(ItemTags.AXES))')
            p.write_text(t, encoding='utf-8', newline='\n')

        p = root / 'hacks/GcoreTpDiagnosticHack.java'
        if p.exists():
            t = p.read_text(encoding='utf-8')
            t = t.replace(
                'import net.minecraft.network.protocol.game.'
                'ServerboundSwingPacket;',
                'import net.minecraft.network.protocol.game.'
                'ServerboundAttackPacket;')
            t = t.replace('ServerboundSwingPacket',
                          'ServerboundAttackPacket')
            p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'gui/title/WurstTitleMenu.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        if 'FabricModListScreen' in t \
            and 'import net.wurstclient.gui.mods.FabricModListScreen;' not in t:
            t = t.replace(
                'import net.wurstclient.gui.MainMenuBackground;',
                'import net.wurstclient.gui.MainMenuBackground;\n'
                'import net.wurstclient.gui.mods.FabricModListScreen;')
            p.write_text(t, encoding='utf-8', newline='\n')

    if mc.startswith('26.1'):
        for p in root.rglob('*.java'):
            t = p.read_text(encoding='utf-8')
            if '.gui.screen()' in t:
                t = t.replace('.gui.screen()', '.screen')
                p.write_text(t, encoding='utf-8', newline='\n')

    print('fixed', project)


if __name__ == '__main__':
    for arg in sys.argv[1:]:
        patch(arg)
