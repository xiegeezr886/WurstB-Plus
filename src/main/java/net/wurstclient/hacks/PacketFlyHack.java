/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"packet fly", "packetfly"})
public final class PacketFlyHack extends Hack
	implements UpdateListener, PacketOutputListener
{
	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"\u00a7lPhase\u00a7r - Caches position and adds movement.\n"
			+ "\u00a7lPacket\u00a7r - Sends position packets directly.",
		Mode.values(), Mode.PHASE);

	private final SliderSetting hSpeed = new SliderSetting("HSpeed",
		"Horizontal speed.", 0.5, 0.05, 2, 0.05, ValueDisplay.DECIMAL);

	private final SliderSetting vSpeed = new SliderSetting("VSpeed",
		"Vertical speed.", 0.5, 0.05, 2, 0.05, ValueDisplay.DECIMAL);

	private final SliderSetting fall = new SliderSetting("Fall",
		"Ticks between anti-kick falls.", 20, 0, 40, 1,
		ValueDisplay.INTEGER);

	private Vec3 cachedPos;
	private int timer;

	public PacketFlyHack()
	{
		super("PacketFly");
		setCategory(Category.MOVEMENT);
		addSetting(mode);
		addSetting(hSpeed);
		addSetting(vSpeed);
		addSetting(fall);
	}

	@Override
	protected void onEnable()
	{
		cachedPos = MC.player.position();
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketOutputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketOutputListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		if(!MC.player.isAlive())
			return;

		double hs = hSpeed.getValue();
		double vs = vSpeed.getValue();
		timer++;

		Vec3 forward = new Vec3(0, 0, hs)
			.yRot(-(float)Math.toRadians(MC.player.getYRot()));
		Vec3 move = Vec3.ZERO;

		if(MC.player.input.forwardImpulse > 0)
			move = move.add(forward);
		if(MC.player.input.forwardImpulse < 0)
			move = move.add(forward.reverse());
		if(MC.options.keyJump.isDown())
			move = move.add(0, vs, 0);
		if(MC.options.keyShift.isDown())
			move = move.add(0, -vs, 0);
		if(MC.player.input.leftImpulse > 0)
			move = move.add(forward.yRot((float)Math.toRadians(90)));
		if(MC.player.input.leftImpulse < 0)
			move = move.add(forward.yRot((float)-Math.toRadians(90)));

		MC.player.setDeltaMovement(Vec3.ZERO);

		if(timer > fall.getValueI())
		{
			move = move.add(0, -vs, 0);
			timer = 0;
		}

		Mode m = mode.getSelected();
		if(m == Mode.PHASE)
		{
			cachedPos = cachedPos.add(move);
			MC.player.connection.send(
				new ServerboundMovePlayerPacket.Pos(cachedPos.x,
					cachedPos.y, cachedPos.z, false));
			MC.player.connection.send(
				new ServerboundMovePlayerPacket.Pos(cachedPos.x,
					cachedPos.y - 0.01, cachedPos.z, true));
		}else
		{
			MC.player.connection.send(
				new ServerboundMovePlayerPacket.Pos(
					MC.player.getX() + move.x,
					MC.player.getY() + move.y,
					MC.player.getZ() + move.z, false));
			MC.player.connection.send(
				new ServerboundMovePlayerPacket.Pos(
					MC.player.getX() + move.x,
					MC.player.getY() - 420.69,
					MC.player.getZ() + move.z, true));
		}
	}

	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(event.getPacket() instanceof ServerboundMovePlayerPacket.StatusOnly
			|| event.getPacket() instanceof ServerboundMovePlayerPacket.Rot)
			return;

		if(event.getPacket() instanceof ServerboundMovePlayerPacket)
			event.cancel();
	}

	private enum Mode
	{
		PHASE("Phase"),
		PACKET("Packet");

		private final String name;

		Mode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
