# -*- coding: utf-8 -*-
"""1.21.5+ API fixes.

1.21.5 moves ClientInput movement state into keyPresses + a protected
moveVector and removes forwardImpulse/leftImpulse, so the scaffold code has to
read getMoveVector() and write it through an accessor mixin. The inventory
selected slot also became a getter/setter and MobEffects.JUMP was renamed.
"""
import re
import sys
from pathlib import Path

KEYBOARD_MIXIN = '''/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.MoveInputListener.MoveInputEvent;

/**
 * Fires {@link MoveInputEvent} after the vanilla movement impulses have been
 * computed, so hacks (Scaffold, Telly, SilenceTelly) can add a jump or cancel
 * movement exactly like OpenOpal's {@code KeyboardInputMixin}.
 */
@Mixin(KeyboardInput.class)
public class KeyboardInputMixin
{
	@Inject(at = @At("TAIL"), method = "tick()V")
	private void onTick(CallbackInfo ci)
	{
		KeyboardInput self = (KeyboardInput)(Object)this;
		Input keys = self.keyPresses;
		Vec2 move = self.getMoveVector();
		MoveInputEvent event = new MoveInputEvent(move.y, move.x, keys.jump(),
			keys.shift());
		EventManager.fire(event);

		if(keys.jump() == event.isJump() && keys.shift() == event.isSneak())
			return;
		self.keyPresses = new Input(keys.forward(), keys.backward(),
			keys.left(), keys.right(), event.isJump(), event.isSneak(),
			keys.sprint());
	}
}
'''

ACCESSOR = '''/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;

@Mixin(ClientInput.class)
public interface ClientInputAccessor
{
	@Accessor("moveVector")
	void wurst$setMoveVector(Vec2 moveVector);
}
'''

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

    (root / 'mixin/KeyboardInputMixin.java').write_text(
        KEYBOARD_MIXIN, encoding='utf-8', newline='\n')
    (root / 'mixin/ClientInputAccessor.java').write_text(
        ACCESSOR, encoding='utf-8', newline='\n')

    p = Path(project) / 'src/main/resources/wurstpenguin.mixins.json'
    t = p.read_text(encoding='utf-8')
    if '"ClientInputAccessor"' not in t:
        t = t.replace('    "ClientConnectionMixin",\n',
                      '    "ClientConnectionMixin",\n'
                      '    "ClientInputAccessor",\n', 1)
        p.write_text(t, encoding='utf-8', newline='\n')

    for p in (root / 'hacks/scaffold').glob('*.java'):
        t = p.read_text(encoding='utf-8')
        original = t
        t = fix_selected_slots(t)
        t = t.replace('MobEffects.JUMP)', 'MobEffects.JUMP_BOOST)')
        t = t.replace('.input.jumping', '.input.keyPresses.jump()')
        t = CANCEL_BLOCK.sub(cancel_movement, t)
        t = t.replace('.input.forwardImpulse', '.input.getMoveVector().y')
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
        p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'hacks/GAntiLagHack.java'
    t = p.read_text(encoding='utf-8')
    t = t.replace('new Vec3(packet.getX(), packet.getY(), packet.getZ())',
                  'packet.change().position()')
    t = t.replace('packet.getId()', 'packet.id()')
    p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'hacks/GcoreTpDiagnosticHack.java'
    t = p.read_text(encoding='utf-8')
    t = t.replace('p.getId(), p.getX(), p.getY(),\n\t\t\tp.getZ()',
                  'p.id(), p.change().position().x,\n'
                  '\t\t\tp.change().position().y, p.change().position().z')
    p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'util/ServerEquivTp.java'
    t = p.read_text(encoding='utf-8')
    t = t.replace(
        'new ServerboundMovePlayerPacket.Pos(x, y, z, onGround)',
        'new ServerboundMovePlayerPacket.Pos(x, y, z, onGround,\n\t\t\t\tfalse)')
    p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'util/TpCombatUtil.java'
    t = p.read_text(encoding='utf-8')
    t = t.replace('Direction.getNearest(eyes.x - center.x, eyes.y - center.y,',
                  'Direction.getApproximateNearest(eyes.x - center.x,\n'
                  '\t\t\t\teyes.y - center.y,')
    t = t.replace(
        '.add(Vec3.atLowerCornerOf(side.getNormal()).scale(0.5));',
        '.add(side.getUnitVec3().scale(0.5));')
    p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'clickgui2/screens/EditBookOfferScreen.java'
    t = p.read_text(encoding='utf-8')
    t = t.replace('BuiltInRegistries.ITEM.get(',
                  'BuiltInRegistries.ITEM.getValue(')
    p.write_text(t, encoding='utf-8', newline='\n')

    print('fixed', project)


if __name__ == '__main__':
    for arg in sys.argv[1:]:
        patch(arg)
