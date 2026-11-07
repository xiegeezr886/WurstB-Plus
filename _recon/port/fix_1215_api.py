# -*- coding: utf-8 -*-
"""1.21.5+ game API fixes that are independent of the GUI family."""
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

    p = root / 'clickgui2/screens/EditBookOfferScreen.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        t = t.replace('BuiltInRegistries.ITEM.get(',
                      'BuiltInRegistries.ITEM.getValue(')
        p.write_text(t, encoding='utf-8', newline='\n')

    print('fixed', project)


if __name__ == '__main__':
    for arg in sys.argv[1:]:
        patch(arg)
