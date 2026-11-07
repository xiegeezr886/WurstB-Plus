# -*- coding: utf-8 -*-
"""1.21.2-1.21.4 API fixes.

- ClientInput stores key state in keyPresses (Input record) and exposes
  forwardImpulse/leftImpulse, but no jumping/shiftKeyDown fields.
- ClientboundPlayerPositionPacket became a record: id()/change().
- ServerboundMovePlayerPacket.Pos gained the horizontalCollision argument.
- Direction.getNearest(double,double,double) -> getApproximateNearest.
- Direction.getNormal() -> getUnitVec3().
- Registry.get(ResourceLocation) -> getValue(ResourceLocation).
"""
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
	@Inject(at = @At("TAIL"), method = "tick(ZF)V")
	private void onTick(boolean slowDown, float slowDownFactor,
		CallbackInfo ci)
	{
		KeyboardInput self = (KeyboardInput)(Object)this;
		Input keys = self.keyPresses;
		MoveInputEvent event = new MoveInputEvent(self.forwardImpulse,
			self.leftImpulse, keys.jump(), keys.shift());
		EventManager.fire(event);

		self.forwardImpulse = event.getForward();
		self.leftImpulse = event.getSideways();
		if(keys.jump() == event.isJump() && keys.shift() == event.isSneak())
			return;
		self.keyPresses = new Input(keys.forward(), keys.backward(),
			keys.left(), keys.right(), event.isJump(), event.isSneak(),
			keys.sprint());
	}
}
'''


def patch(project):
    root = Path(project) / 'src/main/java/net/wurstclient'

    (root / 'mixin/KeyboardInputMixin.java').write_text(
        KEYBOARD_MIXIN, encoding='utf-8', newline='\n')

    p = root / 'hacks/ScaffoldWalkHack.java'
    t = p.read_text(encoding='utf-8')
    t = t.replace('MC.player.input.jumping',
                  'MC.player.input.keyPresses.jump()')
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
